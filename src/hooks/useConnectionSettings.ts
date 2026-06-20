// src/hooks/useConnectionSettings.ts
// Connection configuration store — LAN, Tailscale, Custom relay.
// v2.1 — Added: LAN IP auto-detection from /api/ip, getBestEndpoint integration.
// Persists to localStorage. No breaking changes to existing connection logic.

'use client';

import { useState, useCallback, useEffect, useRef } from 'react';
import { getBestEndpoint, isMobileNetwork } from '@/lib/ConnectionResolver';
import type { EndpointResult } from '@/lib/ConnectionResolver';

export type ConnectionMode = 'lan' | 'tailscale' | 'custom';
export type ConnectionQuality = 'local' | 'wifi' | '4g' | '5g' | 'unknown';

const STORAGE_KEY = 'smartdisplay_connection_config';

export interface ConnectionConfig {
  mode: ConnectionMode;
  tailscaleIp: string;
  customUrl: string;
  customPort: number;
  autoReconnect: boolean;
  heartbeatEnabled: boolean;
  quality: ConnectionQuality;
  lastConnectedHost: string;
}

const DEFAULT_CONFIG: ConnectionConfig = {
  mode: 'lan',
  tailscaleIp: '',
  customUrl: '',
  customPort: 3002,
  autoReconnect: true,
  heartbeatEnabled: true,
  quality: 'unknown',
  lastConnectedHost: '',
};

function loadConfig(): ConnectionConfig {
  try {
    const stored = localStorage.getItem(STORAGE_KEY);
    if (stored) {
      return { ...DEFAULT_CONFIG, ...JSON.parse(stored) };
    }
  } catch { /* ignore */ }
  return DEFAULT_CONFIG;
}

function saveConfig(config: ConnectionConfig): void {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(config));
  } catch { /* ignore */ }
}

interface UseConnectionSettingsReturn {
  config: ConnectionConfig;
  lanIp: string; // Auto-detected LAN IP of the PC
  endpoint: EndpointResult; // Best resolved endpoint
  setMode: (mode: ConnectionMode) => void;
  setTailscaleIp: (ip: string) => void;
  setCustomUrl: (url: string) => void;
  setCustomPort: (port: number) => void;
  setAutoReconnect: (on: boolean) => void;
  setHeartbeat: (on: boolean) => void;
  setQuality: (quality: ConnectionQuality) => void;
  setLastConnectedHost: (host: string) => void;
  resetConfig: () => void;
  resolveWsUrl: (basePort?: number) => string;
  resolveApiUrl: () => string;
  refreshLanIp: () => Promise<void>; // Force re-fetch of LAN IP
}

export function useConnectionSettings(): UseConnectionSettingsReturn {
  const [config, setConfig] = useState<ConnectionConfig>(loadConfig);
  const [lanIp, setLanIp] = useState<string>('');
  const [endpoint, setEndpoint] = useState<EndpointResult>(() =>
    getBestEndpoint({ ...config, autoReconnect: config.autoReconnect, heartbeatEnabled: config.heartbeatEnabled })
  );
  const fetchedRef = useRef(false);

  // Persist on change
  useEffect(() => {
    saveConfig(config);
  }, [config]);

  // Auto-detect LAN IP on mount
  useEffect(() => {
    if (fetchedRef.current) return;
    fetchedRef.current = true;
    refreshLanIp();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Re-resolve endpoint whenever config or lanIp changes
  useEffect(() => {
    const ep = getBestEndpoint(
      { ...config, autoReconnect: config.autoReconnect, heartbeatEnabled: config.heartbeatEnabled },
      lanIp || undefined,
      true, // Respect user mode selection
    );
    setEndpoint(ep);
  }, [config, lanIp]);

  const refreshLanIp = useCallback(async () => {
    try {
      const url = typeof window !== 'undefined' && window.location.protocol === 'file:'
        ? 'http://localhost:3001/api/ip'
        : '/api/ip';
      const res = await fetch(url);
      const data = await res.json();
      if (data.success && data.primaryIp) {
        setLanIp(data.primaryIp);
      }
    } catch {
      // Unable to detect LAN IP — will fall back to localhost
      console.warn('[ConnectionSettings] Could not detect LAN IP');
    }
  }, []);

  const updateConfig = useCallback((partial: Partial<ConnectionConfig>) => {
    setConfig(prev => ({ ...prev, ...partial }));
  }, []);

  const setMode = useCallback((mode: ConnectionMode) => updateConfig({ mode }), [updateConfig]);
  const setTailscaleIp = useCallback((tailscaleIp: string) => updateConfig({ tailscaleIp }), [updateConfig]);
  const setCustomUrl = useCallback((customUrl: string) => updateConfig({ customUrl }), [updateConfig]);
  const setCustomPort = useCallback((customPort: number) => updateConfig({ customPort }), [updateConfig]);
  const setAutoReconnect = useCallback((autoReconnect: boolean) => updateConfig({ autoReconnect }), [updateConfig]);
  const setHeartbeat = useCallback((heartbeatEnabled: boolean) => updateConfig({ heartbeatEnabled }), [updateConfig]);
  const setQuality = useCallback((quality: ConnectionQuality) => updateConfig({ quality }), [updateConfig]);
  const setLastConnectedHost = useCallback((lastConnectedHost: string) => updateConfig({ lastConnectedHost }), [updateConfig]);
  const resetConfig = useCallback(() => setConfig(DEFAULT_CONFIG), []);

  /**
   * Resolves the WebSocket URL based on the best endpoint.
   */
  const resolveWsUrl = useCallback((_basePort = 3002): string => {
    return endpoint.wsUrl;
  }, [endpoint.wsUrl]);

  /**
   * Resolves the HTTP API URL based on the best endpoint.
   */
  const resolveApiUrl = useCallback((): string => {
    return endpoint.apiUrl;
  }, [endpoint.apiUrl]);

  return {
    config,
    lanIp,
    endpoint,
    setMode,
    setTailscaleIp,
    setCustomUrl,
    setCustomPort,
    setAutoReconnect,
    setHeartbeat,
    setQuality,
    setLastConnectedHost,
    resetConfig,
    resolveWsUrl,
    resolveApiUrl,
    refreshLanIp,
  };
}
