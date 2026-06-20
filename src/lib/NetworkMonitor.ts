// src/lib/NetworkMonitor.ts
// Centralized network monitor — single source of truth for network events.
//
// Replaces all ad-hoc window.addEventListener('online') / navigator.connection.onchange
// scattered across files. All consumers subscribe here; no duplicate listeners.
//
// Events emitted:
//   'quality-change'  — WiFi ↔ 4G ↔ 5G ↔ unknown
//   'online'          — browser came back online
//   'offline'         — browser went offline
//   'ip-change'       — public IP changed (checked every 30s)
//   'type-change'     — network type changed (same as quality-change but raw)

export type NetworkQuality = 'local' | 'wifi' | '4g' | '5g' | 'unknown';

export type NetworkEventType = 'quality-change' | 'online' | 'offline' | 'ip-change' | 'visibility';

export interface NetworkEvent {
  type: NetworkEventType;
  quality?: NetworkQuality;
  previousQuality?: NetworkQuality;
  ip?: string;
  previousIp?: string;
  isVisible?: boolean;
}

type NetworkListener = (event: NetworkEvent) => void;

// ─── Singleton ─────────────────────────────────────────────────────────────────

class NetworkMonitorSingleton {
  private _listeners: Set<NetworkListener> = new Set();
  private _currentQuality: NetworkQuality = 'unknown';
  private _currentIp: string | null = null;
  private _ipCheckTimer: ReturnType<typeof setInterval> | null = null;
  private _initialized = false;

  // ─── Public API ────────────────────────────────────────────────────────────

  subscribe(listener: NetworkListener): () => void {
    this._listeners.add(listener);
    if (!this._initialized) this._init();
    // Immediately emit current state to new subscriber
    listener({ type: 'quality-change', quality: this._currentQuality });
    return () => this._listeners.delete(listener);
  }

  get quality(): NetworkQuality {
    return this._currentQuality;
  }

  get isMobile(): boolean {
    return this._currentQuality === '4g' || this._currentQuality === '5g';
  }

  get isOnline(): boolean {
    return typeof navigator !== 'undefined' ? navigator.onLine : true;
  }

  /** Force-check network quality now (useful after wake). */
  checkNow(): void {
    this._handleNetworkChange();
  }

  destroy(): void {
    this._removeListeners();
    if (this._ipCheckTimer) {
      clearInterval(this._ipCheckTimer);
      this._ipCheckTimer = null;
    }
    this._initialized = false;
  }

  // ─── Internal ──────────────────────────────────────────────────────────────

  private _init(): void {
    if (this._initialized) return;
    this._initialized = true;

    // Online/offline
    if (typeof window !== 'undefined') {
      window.addEventListener('online', this._handleOnline);
      window.addEventListener('offline', this._handleOffline);
    }

    // Visibility (sleep/wake)
    if (typeof document !== 'undefined') {
      document.addEventListener('visibilitychange', this._handleVisibility);
    }

    // Network Information API (WiFi ↔ 4G)
    if (typeof navigator !== 'undefined' && 'connection' in navigator) {
      const conn = (navigator as any).connection;
      if (conn) {
        try {
          conn.addEventListener('change', this._handleNetworkChange);
        } catch {
          conn.onchange = this._handleNetworkChange;
        }
      }
    }

    // Initial quality snapshot
    this._currentQuality = this._detectQuality();

    // Public IP monitoring — checks every 30s for roaming/NAT change detection
    // Only active in browser (not SSR)
    if (typeof window !== 'undefined') {
      this._ipCheckTimer = setInterval(this._checkPublicIp, 30_000);
    }
  }

  private _removeListeners(): void {
    if (typeof window !== 'undefined') {
      window.removeEventListener('online', this._handleOnline);
      window.removeEventListener('offline', this._handleOffline);
    }
    if (typeof document !== 'undefined') {
      document.removeEventListener('visibilitychange', this._handleVisibility);
    }
    if (typeof navigator !== 'undefined' && 'connection' in navigator) {
      const conn = (navigator as any).connection;
      if (conn) {
        try {
          conn.removeEventListener('change', this._handleNetworkChange);
        } catch {
          conn.onchange = null;
        }
      }
    }
  }

  private _handleOnline = (): void => {
    console.info('[NetworkMonitor] Browser online');
    this._emit({ type: 'online', quality: this._detectQuality() });
    this._handleNetworkChange();
  };

  private _handleOffline = (): void => {
    console.info('[NetworkMonitor] Browser offline');
    const prev = this._currentQuality;
    this._currentQuality = 'unknown';
    this._emit({ type: 'offline', quality: 'unknown', previousQuality: prev });
  };

  private _handleVisibility = (): void => {
    if (typeof document === 'undefined') return;
    const isVisible = document.visibilityState === 'visible';
    console.info(`[NetworkMonitor] Visibility: ${isVisible ? 'visible (wake)' : 'hidden (sleep)'}`);
    this._emit({ type: 'visibility', isVisible });
    if (isVisible) {
      // Re-check network after waking
      setTimeout(this._handleNetworkChange, 200);
    }
  };

  private _handleNetworkChange = (): void => {
    const newQuality = this._detectQuality();
    if (newQuality !== this._currentQuality) {
      const prev = this._currentQuality;
      this._currentQuality = newQuality;
      console.info(`[NetworkMonitor] Quality changed: ${prev} → ${newQuality}`);
      this._emit({ type: 'quality-change', quality: newQuality, previousQuality: prev });
    }
  };

  private _checkPublicIp = async (): Promise<void> => {
    // Use a fast, privacy-friendly public IP check
    // This detects roaming, NAT change, or ISP change
    try {
      const controller = new AbortController();
      const timeoutId = setTimeout(() => controller.abort(), 4000);
      const res = await fetch('https://api.ipify.org?format=json', {
        signal: controller.signal,
        cache: 'no-store',
      });
      clearTimeout(timeoutId);
      const data = await res.json();
      const newIp = data.ip as string;
      if (newIp && newIp !== this._currentIp) {
        const prev = this._currentIp;
        this._currentIp = newIp;
        console.info(`[NetworkMonitor] Public IP changed: ${prev} → ${newIp}`);
        this._emit({ type: 'ip-change', ip: newIp, previousIp: prev ?? undefined });
      }
    } catch {
      // Network unavailable or service unreachable — ignore silently
    }
  };

  private _detectQuality(): NetworkQuality {
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
    if (type === 'none') return 'unknown';
    // effectiveType fallback (Firefox, older Chrome)
    if (effectiveType === '4g') return 'wifi'; // 4g effectiveType on wifi is normal
    return 'unknown';
  }

  private _emit(event: NetworkEvent): void {
    for (const listener of this._listeners) {
      try {
        listener(event);
      } catch (err) {
        console.error('[NetworkMonitor] Listener error:', err);
      }
    }
  }
}

// ─── Export singleton ──────────────────────────────────────────────────────────

/** Global singleton — import this from anywhere, no duplicate listeners. */
export const NetworkMonitor = new NetworkMonitorSingleton();

/** Convenience: subscribe to all network events. Returns cleanup fn. */
export function onNetworkEvent(listener: NetworkListener): () => void {
  return NetworkMonitor.subscribe(listener);
}

/** Convenience: current network quality (synchronous). */
export function getNetworkQuality(): NetworkQuality {
  return NetworkMonitor.quality;
}

/** Convenience: true if on mobile data (4G/5G). */
export function isMobileData(): boolean {
  return NetworkMonitor.isMobile;
}
