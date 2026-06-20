// src/lib/ReconnectingWebSocket.ts
// WebSocket wrapper with automatic reconnection, exponential backoff, heartbeat,
// and offline message queue.
//
// Maintains the same interface as the native WebSocket:
//   - .send(), .close(), .readyState, .binaryType
//   - .onopen, .onclose, .onerror, .onmessage
//
// Drop-in replacement for `new WebSocket(url)` — no other code changes needed.
//
// v2.0 — Improvements:
//   - Async mutex prevents race conditions on concurrent connect() calls
//   - BIDIRECTIONAL heartbeat: detects dead server (not just dead client)
//   - Message queue TTL (60s) + size limit (150 msgs) prevents memory leaks
//   - NetworkMonitor integration: single set of network listeners globally

import { NetworkMonitor } from './NetworkMonitor';

export const BACKOFF_DELAYS = [1000, 2000, 4000, 8000, 15000, 30000, 60000];
const MAX_BACKOFF_INDEX = BACKOFF_DELAYS.length - 1;
const QUEUE_MAX_SIZE = 150;
const QUEUE_MSG_TTL_MS = 60_000; // Messages older than 60s are dropped

export interface ReconnectingWebSocketOptions {
  /** Interval in ms between heartbeat pings. 0 = disabled. Default: 10000 */
  heartbeatInterval?: number;
  /** Timeout in ms for pong response. Default: 5000 */
  pingTimeout?: number;
  /**
   * Timeout in ms waiting for ANY message from server before declaring it dead.
   * Bidirectional heartbeat: if server sends nothing within this window, reconnect.
   * Default: 30000 (30s). Set 0 to disable.
   */
  serverSilenceTimeout?: number;
  /** Maximum reconnection attempts. Default: 10 */
  maxReconnectAttempts?: number;
  /** Whether to auto-reconnect. Default: true */
  autoReconnect?: boolean;
  /** Called on each reconnect attempt with the attempt number and delay */
  onReconnectAttempt?: (attempt: number, delay: number) => void;
  /** Called when the connection state changes */
  onStateChange?: (state: 'connected' | 'disconnected' | 'reconnecting' | 'error') => void;
  /** Latency measurement callback (ms, -1 if unknown) */
  onLatency?: (ms: number) => void;
  /** Called when network quality changes — for informational/logging purposes */
  onNetworkQuality?: (quality: string) => void;
}

type WSOpenHandler = ((event: Event) => void) | null;
type WSCloseHandler = ((event: CloseEvent) => void) | null;
type WSErrorHandler = ((event: Event) => void) | null;
type WSMessageHandler = ((event: MessageEvent) => void) | null;

export class ReconnectingWebSocket {
  // ─── Public API (mirrors native WebSocket) ─────────────────────────────
  public binaryType: BinaryType = 'blob';
  public onopen: WSOpenHandler = null;
  public onclose: WSCloseHandler = null;
  public onerror: WSErrorHandler = null;
  public onmessage: WSMessageHandler = null;

  // ─── Read-only state ───────────────────────────────────────────────────
  public get readyState(): number {
    return this._ws?.readyState ?? ReconnectingWebSocket.CLOSED;
  }

  public get url(): string {
    return this._url;
  }

  // ─── Static WebSocket constants ────────────────────────────────────────
  static readonly CONNECTING = 0;
  static readonly OPEN = 1;
  static readonly CLOSING = 2;
  static readonly CLOSED = 3;

  // ─── Private state ─────────────────────────────────────────────────────
  private _url: string;
  private _ws: WebSocket | null = null;
  private _intentionalClose = false;
  private _reconnectAttempt = 0;
  private _reconnectTimer: ReturnType<typeof setTimeout> | null = null;
  private _heartbeatTimer: ReturnType<typeof setInterval> | null = null;
  private _pingTimer: ReturnType<typeof setTimeout> | null = null;
  private _serverSilenceTimer: ReturnType<typeof setTimeout> | null = null;
  /** Queue stores data + timestamp for TTL eviction */
  private _messageQueue: Array<{ data: string | ArrayBuffer | ArrayBufferView; ts: number }> = [];
  private _pingStart = 0;
  private _alive = false;
  private _options: Required<ReconnectingWebSocketOptions>;
  private _authFailed = false; // set on 4001 close — fast retry won't help a bad token
  private _pendingEventLoop = false;
  /** Async mutex: resolves when _connect is not actively running */
  private _connectMutex: Promise<void> = Promise.resolve();
  private _connectMutexRelease: (() => void) | null = null;
  /** Cleanup fn returned by NetworkMonitor.subscribe */
  private _networkUnsubscribe: (() => void) | null = null;

  constructor(url: string, options?: ReconnectingWebSocketOptions) {
    this._url = url;
    this._options = {
      heartbeatInterval: 10000,
      pingTimeout: 5000,
      serverSilenceTimeout: 30_000,
      maxReconnectAttempts: 10,
      autoReconnect: true,
      onReconnectAttempt: () => {},
      onStateChange: () => {},
      onLatency: () => {},
      onNetworkQuality: () => {},
      ...options,
    };
    this._subscribeToNetworkMonitor();
    this._connect();
  }

  // ─── Network Monitor Integration ────────────────────────────────────────
  // Single subscription to NetworkMonitor singleton — no duplicate listeners.

  private _subscribeToNetworkMonitor(): void {
    this._networkUnsubscribe = NetworkMonitor.subscribe((event) => {
      if (this._intentionalClose) return;

      if (event.type === 'online') {
        console.info('[ReconnectingWebSocket] Network online — reconnecting');
        this._reconnectAttempt = 0;
        this._cleanup();
        this._connect();
      } else if (event.type === 'quality-change' && event.quality !== 'unknown') {
        // Network type changed (WiFi ↔ 4G): reconnect to pick best endpoint
        if (event.previousQuality && event.previousQuality !== 'unknown') {
          console.info(`[ReconnectingWebSocket] Network type changed ${event.previousQuality} → ${event.quality} — reconnecting`);
          this._reconnectAttempt = 0;
          this._cleanup();
          this._connect();
        }
        this._options.onNetworkQuality(event.quality ?? 'unknown');
      } else if (event.type === 'visibility' && event.isVisible) {
        // Wake from sleep: check if WS is still alive
        const isAlive = this._ws?.readyState === WebSocket.OPEN;
        if (!isAlive) {
          console.info('[ReconnectingWebSocket] Wake detected — reconnecting');
          this._reconnectAttempt = 0;
          this._cleanup();
          this._connect();
        }
      } else if (event.type === 'ip-change') {
        // Public IP changed — roaming detected, reconnect
        console.info(`[ReconnectingWebSocket] IP roaming detected (${event.previousIp} → ${event.ip}) — reconnecting`);
        this._reconnectAttempt = 0;
        this._cleanup();
        this._connect();
      }
    });
  }

  // ─── Public Methods ────────────────────────────────────────────────────

  /**
   * Sends data through the WebSocket. If disconnected, queues for later delivery.
   * Queue has TTL (60s) and size limit (150 msgs) to prevent memory leaks.
   */
  public send(data: string | ArrayBuffer | ArrayBufferView): void {
    if (this._ws?.readyState === WebSocket.OPEN) {
      try {
        this._ws.send(data);
        return;
      } catch {
        // Fall through to queue
      }
    }
    this._enqueue(data);
  }

  /** Returns current queue size (for diagnostics). */
  public getQueueSize(): number {
    return this._messageQueue.length;
  }

  /**
   * Gracefully closes the connection. No auto-reconnect after this.
   */
  public close(): void {
    this._intentionalClose = true;
    this._cleanup();
    this._networkUnsubscribe?.();
    this._networkUnsubscribe = null;
    this._onStateChange('disconnected');
  }

  /**
   * Updates the target URL. Reconnects if currently connected or reconnecting.
   */
  public updateUrl(newUrl: string, reconnect = true): void {
    if (this._url === newUrl) return;
    this._url = newUrl;
    if (reconnect) {
      this._intentionalClose = false;
      this._reconnectAttempt = 0;
      this._cleanup();
      this._connect();
    }
  }

  /**
   * Triggers an immediate reconnect attempt.
   */
  public reconnect(): void {
    this._intentionalClose = false;
    this._reconnectAttempt = 0;
    this._cleanup();
    this._connect();
  }

  // ─── Internal Connection Logic ─────────────────────────────────────────

  /**
   * Initiates a WebSocket connection.
   * Uses an async mutex so concurrent calls (from wake, online, network-change)
   * never race — only ONE connection attempt runs at a time.
   */
  private _connect(): void {
    // Chain onto existing mutex — if already connecting, this will run after it finishes
    this._connectMutex = this._connectMutex.then(() => this._connectAsync());
  }

  private _connectAsync(): Promise<void> {
    return new Promise<void>((resolve) => {
      if (this._intentionalClose || this._ws?.readyState === WebSocket.OPEN) {
        resolve();
        return;
      }

      let ws: WebSocket;
      try {
        ws = new WebSocket(this._url);
      } catch (err) {
        this._onError(err as Error);
        this._scheduleReconnect();
        resolve();
        return;
      }

      ws.binaryType = this.binaryType;
      this._ws = ws;

      ws.onopen = (event: Event) => {
        this._reconnectAttempt = 0;
        this._alive = true;
        this._startHeartbeat();
        this._resetServerSilenceTimer();
        this._flushQueue();
        this._onStateChange('connected');
        this.onopen?.(event);
        resolve();
      };

      ws.onclose = (event: CloseEvent) => {
        this._stopHeartbeat();
        this._stopServerSilenceTimer();

        // 4001 = server rejected the auth token. Retrying with the SAME token
        // is futile and just spins the backoff loop, so flag it and let the app
        // refresh the endpoint/token (via the 'error' state) instead of hammering.
        this._authFailed = event.code === 4001;

        this._onStateChange(this._intentionalClose ? 'disconnected' : 'reconnecting');
        this.onclose?.(event);

        if (!this._intentionalClose) {
          this._scheduleReconnect();
        }
        resolve();
      };

      ws.onerror = (_event: Event) => {
        // onclose will fire after onerror — reconnection handled there
        this.onerror?.(_event);
      };

      ws.onmessage = (event: MessageEvent) => {
        // Any message from server resets the silence timer (bidirectional health check)
        this._resetServerSilenceTimer();

        // Intercept pong responses for latency measurement
        if (typeof event.data === 'string') {
          try {
            const parsed = JSON.parse(event.data);
            if (parsed.type === 'pong') {
              this._alive = true;
              if (this._pingStart > 0) {
                const latency = Math.round(performance.now() - this._pingStart);
                this._pingStart = 0;
                this._options.onLatency(latency);
              }
              return; // Don't forward pong to user handler
            }
            // Server-initiated ping: respond immediately
            if (parsed.type === 'ping') {
              try { ws.send(JSON.stringify({ type: 'pong' })); } catch { /* ignore */ }
              return;
            }
          } catch {
            // Not JSON — pass through
          }
        }
        this.onmessage?.(event);
      };
    });
  }

  private _scheduleReconnect(): void {
    if (!this._options.autoReconnect) return;

    // Auth failure: a fast backoff loop won't help (token is wrong). Surface
    // an error state so the app can re-resolve the endpoint with a fresh token,
    // then enter the long cooldown instead of hammering the server.
    if (this._authFailed) {
      this._authFailed = false;
      this._onStateChange('error');
      this._reconnectTimer = setTimeout(() => {
        this._reconnectAttempt = 0;
        this._options.onStateChange('disconnected');
      }, 60_000);
      return;
    }

    if (this._reconnectAttempt >= this._options.maxReconnectAttempts) {
      this._onStateChange('error');
      // Enter cooldown: reset after 60 seconds so visibility/wake events
      // can re-establish the connection later without infinite looping.
      // Reduced from 5 minutes to 60 seconds for faster recovery in mobile scenarios.
      this._reconnectTimer = setTimeout(() => {
        this._reconnectAttempt = 0;
        this._options.onStateChange('disconnected');
      }, 60_000); // 60-second cooldown (reduced from 5 minutes)
      return;
    }

    const delayIndex = Math.min(this._reconnectAttempt, MAX_BACKOFF_INDEX);
    const delay = BACKOFF_DELAYS[delayIndex] + Math.random() * 500;

    this._reconnectAttempt++;
    this._options.onReconnectAttempt(this._reconnectAttempt, delay);
    this._onStateChange('reconnecting');

    this._reconnectTimer = setTimeout(() => {
      this._pendingEventLoop = false;
      this._connect();
    }, delay);
  }

  // ─── Heartbeat (client→server ping/pong) ──────────────────────────────

  private _startHeartbeat(): void {
    this._stopHeartbeat();
    if (this._options.heartbeatInterval <= 0) return;

    this._heartbeatTimer = setInterval(() => {
      if (this._ws?.readyState !== WebSocket.OPEN) return;
      this._alive = false;
      this._pingStart = performance.now();
      try {
        this._ws.send(JSON.stringify({ type: 'ping' }));
      } catch {
        // Connection might be dead, close will trigger reconnect
      }

      this._pingTimer = setTimeout(() => {
        if (!this._alive && !this._intentionalClose) {
          // No pong received — client→server path is dead
          console.warn('[ReconnectingWebSocket] Pong timeout — closing dead connection');
          try { this._ws?.close(); } catch { /* ignore */ }
        }
      }, this._options.pingTimeout);
    }, this._options.heartbeatInterval);
  }

  private _stopHeartbeat(): void {
    if (this._heartbeatTimer !== null) {
      clearInterval(this._heartbeatTimer);
      this._heartbeatTimer = null;
    }
    if (this._pingTimer !== null) {
      clearTimeout(this._pingTimer);
      this._pingTimer = null;
    }
  }

  // ─── Bidirectional Heartbeat (server→client silence detection) ──────────
  // If server sends NOTHING for serverSilenceTimeout ms, the connection is dead.
  // This catches the case where the server crashes but TCP doesn't close cleanly.

  private _resetServerSilenceTimer(): void {
    if (this._options.serverSilenceTimeout <= 0) return;
    this._stopServerSilenceTimer();
    this._serverSilenceTimer = setTimeout(() => {
      if (!this._intentionalClose && this._ws?.readyState === WebSocket.OPEN) {
        console.warn('[ReconnectingWebSocket] Server silence timeout — reconnecting');
        try { this._ws?.close(); } catch { /* ignore */ }
      }
    }, this._options.serverSilenceTimeout);
  }

  private _stopServerSilenceTimer(): void {
    if (this._serverSilenceTimer !== null) {
      clearTimeout(this._serverSilenceTimer);
      this._serverSilenceTimer = null;
    }
  }

  // ─── Queue Management ──────────────────────────────────────────────────

  private _enqueue(data: string | ArrayBuffer | ArrayBufferView): void {
    const now = Date.now();
    // Evict expired messages first
    this._messageQueue = this._messageQueue.filter(m => now - m.ts < QUEUE_MSG_TTL_MS);
    // Enforce size limit — drop oldest if full
    if (this._messageQueue.length >= QUEUE_MAX_SIZE) {
      this._messageQueue.shift();
      console.warn('[ReconnectingWebSocket] Queue full — dropping oldest message');
    }
    this._messageQueue.push({ data, ts: now });
  }

  private _flushQueue(): void {
    if (this._messageQueue.length === 0) return;
    const now = Date.now();
    // Drop expired messages before flushing
    const live = this._messageQueue.filter(m => now - m.ts < QUEUE_MSG_TTL_MS);
    this._messageQueue = [];
    for (const { data } of live) {
      try {
        if (this._ws?.readyState === WebSocket.OPEN) {
          this._ws.send(data);
        } else {
          // Connection lost mid-flush — re-enqueue remaining
          this._enqueue(data);
        }
      } catch {
        this._enqueue(data);
      }
    }
  }

  // ─── Cleanup ───────────────────────────────────────────────────────────

  private _cleanup(): void {
    this._stopHeartbeat();
    this._stopServerSilenceTimer();
    if (this._reconnectTimer !== null) {
      clearTimeout(this._reconnectTimer);
      this._reconnectTimer = null;
    }
    if (this._ws) {
      try { this._ws.onopen = null; this._ws.onclose = null; this._ws.onerror = null; this._ws.onmessage = null; } catch { /* ignore */ }
      try { this._ws.close(); } catch { /* ignore */ }
      this._ws = null;
    }
  }

  private _onStateChange(state: 'connected' | 'disconnected' | 'reconnecting' | 'error'): void {
    this._options.onStateChange(state);
  }

  private _onError(err: Error): void {
    // Trigger onerror if set
    if (this.onerror) {
      const event = new Event('error');
      this.onerror(event);
    }
  }
}
