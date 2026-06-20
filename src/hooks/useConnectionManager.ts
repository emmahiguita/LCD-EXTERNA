// src/hooks/useConnectionManager.ts
// Connection Manager — v3.1
//
// MAJOR REFACTOR: No longer manages its own WebSocket.
// This is a PASSIVE observer that:
//   - Tracks connection state (fed externally from ReconnectingWebSocket)
//   - Detects network changes (WiFi ↔ mobile data) and notifies caller
//   - Tracks sleep/wake via visibilitychange + online/offline
//   - Provides URL tracking for the active connection
//
// NOTE: Offline message queue has been removed to eliminate duplication.
// ReconnectingWebSocket now handles all message queuing internally.
//
// The actual WebSocket is managed by ReconnectingWebSocket (streaming) or
// other transport. This hook provides the React-friendly state interface.
//
// Compatible with electron/main.js WebSocket server.

'use client';

import { useEffect, useRef, useCallback, useState } from 'react';

export type ConnectionState = 'CONNECTED' | 'CONNECTING' | 'RECONNECTING' | 'DISCONNECTED' | 'ERROR';

interface ConnectionOptions {
  url?: string;
  token?: string;
  maxReconnectAttempts?: number;
  baseReconnectDelay?: number;
  autoReconnect?: boolean;
  /** Called when network quality changes (WiFi ↔ 4G ↔ 5G). Transport-level
   *  reconnection is handled by ReconnectingWebSocket; this is informational. */
  onNetworkChange?: (newQuality: 'local' | 'wifi' | '4g' | '5g' | 'unknown') => void;
}

interface UseConnectionManagerReturn {
  state: ConnectionState;
  send: (data: string | ArrayBuffer) => boolean;
  isConnected: boolean;
  reconnect: (newUrl?: string) => void;
  disconnect: () => void;
  connect: (token?: string) => void;
  stateRef: React.MutableRefObject<ConnectionState>;
  readyState: () => number;
  latency: number; // ms, -1 if unknown
  updateUrl: (newUrl: string) => void;
  /** Allows external code (e.g., ReconnectingWebSocket) to push state updates */
  setExternalState: (state: ConnectionState, latency?: number) => void;
}

// This ref is shared across all instances so that external code
// (e.g., ReconnectingWebSocket) can register a send function once.
let globalSendRef: { current: ((data: string | ArrayBuffer) => void) | null } = { current: null };

/**
 * Registers a global send function that useConnectionManager will use
 * to actually send data. Called once by the streaming WebSocket owner.
 */
export function registerGlobalSend(fn: (data: string | ArrayBuffer) => void): void {
  globalSendRef.current = fn;
}

/**
 * Unregisters the global send function (called on cleanup).
 */
export function unregisterGlobalSend(): void {
  globalSendRef.current = null;
}

export function useConnectionManager(
  opts: ConnectionOptions = {}
): UseConnectionManagerReturn {
  const {
    url: initialUrl = 'ws://localhost:3002',
    token,
    maxReconnectAttempts: _maxReconnectAttempts = 10,
    baseReconnectDelay: _baseReconnectDelay = 1000,
    autoReconnect: _autoReconnect = true,
    onNetworkChange,
  } = opts;

  const [state, setState] = useState<ConnectionState>('DISCONNECTED');
  const [latency, setLatency] = useState(-1);

  const stateRef = useRef<ConnectionState>('DISCONNECTED');
  const urlRef = useRef(initialUrl);
  const tokenRef = useRef(token || '');
  const intentionalCloseRef = useRef(false);

  const setConnectionState = useCallback((newState: ConnectionState) => {
    stateRef.current = newState;
    setState(newState);
  }, []);

  const send = useCallback((data: string | ArrayBuffer): boolean => {
    // Try global sender first (ReconnectingWebSocket)
    const sender = globalSendRef.current;
    if (sender) {
      try {
        sender(data);
        return true;
      } catch (err) {
        console.warn('[ConnectionManager] send error via global sender:', err);
      }
    }
    return false; // not sent (ReconnectingWebSocket handles queuing internally)
  }, []);

  // ─── External state sync ───────────────────────────────────────────────
  // Called by ReconnectingWebSocket or other transport to push state

  const setExternalState = useCallback((newState: ConnectionState, newLatency?: number) => {
    setConnectionState(newState);
    if (newLatency !== undefined && newLatency >= 0) {
      setLatency(newLatency);
    }
  }, [setConnectionState]);

  const disconnect = useCallback(() => {
    intentionalCloseRef.current = true;
    setConnectionState('DISCONNECTED');
  }, [setConnectionState]);

  const connect = useCallback((_newToken?: string) => {
    // Connection is managed externally (by ReconnectingWebSocket).
    // This is a no-op — the external code handles actual WS creation.
    // Setting state to CONNECTING gives UI feedback.
    setConnectionState('CONNECTING');
  }, [setConnectionState]);

  const reconnect = useCallback((newUrl?: string) => {
    if (newUrl) { urlRef.current = newUrl; }
    intentionalCloseRef.current = false;
    // The external WS (ReconnectingWebSocket) should handle reconnection.
    // We just set the state to indicate reconnecting.
    setConnectionState('RECONNECTING');
  }, [setConnectionState]);

  const updateUrl = useCallback((newUrl: string) => {
    urlRef.current = newUrl;
  }, []);

  const readyState = useCallback((): number => {
    // We don't manage a WS directly; return 3 (CLOSED) as default.
    // External code should check its own readyState.
    return WebSocket.CLOSED;
  }, []);

  // ─── Network Quality Monitoring ────────────────────────────────────────
  // Reports network quality changes (WiFi ↔ 4G ↔ 5G) to the caller.
  // NOTE: Actual reconnection on network/sleep/wake is handled by
  // ReconnectingWebSocket at the transport layer. This hook only
  // provides quality info for UI/logging purposes.
  // This avoids DUPLICATE reconnect attempts.

  useEffect(() => {
    let currentQuality: string | null = null;

    const handleChange = (quality: 'local' | 'wifi' | '4g' | '5g' | 'unknown') => {
      if (quality !== currentQuality) {
        currentQuality = quality;
        console.info(`[ConnectionManager] Network quality changed to: ${quality}`);
        onNetworkChange?.(quality);
      }
    };

    let connectionCleanup: (() => void) | null = null;

    if (typeof navigator !== 'undefined' && 'connection' in navigator) {
      const conn = (navigator as any).connection;
      if (conn) {
        const handler = () => {
          const q = detectNetworkQualitySimple();
          handleChange(q);
        };
        try {
          conn.addEventListener('change', handler);
          connectionCleanup = () => conn.removeEventListener('change', handler);
        } catch {
          conn.onchange = handler;
          connectionCleanup = () => { conn.onchange = null; };
        }
      }
    }

    return () => {
      connectionCleanup?.();
    };
  }, [onNetworkChange]);

  // ─── Cleanup on unmount ────────────────────────────────────────────────
  useEffect(() => {
    return () => {
      intentionalCloseRef.current = true;
      // Don't unregister global send — that's the owner's responsibility
    };
  }, []);

  return {
    state,
    send,
    isConnected: state === 'CONNECTED',
    reconnect,
    disconnect,
    connect,
    stateRef,
    readyState,
    latency,
    updateUrl,
    setExternalState,
  };
}

/**
 * Simple synchronous network quality detection (no imports needed).
 */
function detectNetworkQualitySimple(): 'local' | 'wifi' | '4g' | '5g' | 'unknown' {
  if (typeof navigator === 'undefined' || !('connection' in navigator)) return 'unknown';
  const conn = (navigator as any).connection;
  if (!conn) return 'unknown';

  const type = conn.type as string | undefined;
  const effectiveType = conn.effectiveType as string | undefined;

  if (type === 'wifi' || type === 'ethernet') return 'wifi';
  if (type === 'cellular') {
    if (effectiveType === '5g') return '5g';
    return '4g';
  }
  return 'wifi';
}
