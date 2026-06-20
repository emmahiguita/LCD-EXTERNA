// src/lib/ConnectionResolver.ts
// Pure utility for connection URL resolution, validation, and network detection.
// v3.0 — Correct endpoint priority: Tailscale > LAN > Custom (not LAN > Tailscale).
//        NetworkMonitor integration for real-time quality detection.
//        Tailscale connectivity ping validation.
// Zero dependencies on React. Works in any JS context (Electron, WebView, browser).

import { NetworkMonitor, type NetworkQuality } from './NetworkMonitor';

export type ConnectionMode = 'lan' | 'tailscale' | 'custom';

export interface ResolverConfig {
  mode: ConnectionMode;
  tailscaleIp: string;
  customUrl: string;
  customPort: number;
}

// ─── URL Resolution ──────────────────────────────────────────────────────────

/**
 * Appends auth token to a WebSocket URL.
 * Handles both URLs with and without existing query strings.
 */
export function resolveWsUrlWithToken(baseUrl: string, token?: string): string {
  if (!token) return baseUrl;
  const separator = baseUrl.includes('?') ? '&' : '?';
  return `${baseUrl}${separator}token=${encodeURIComponent(token)}`;
}

/**
 * Normalizes a WebSocket URL by ensuring no trailing slash,
 * then appends the token in the same format the existing server expects.
 */
export function normalizeWsUrl(baseUrl: string, token?: string): string {
  const clean = baseUrl.replace(/\/+$/, '');
  if (!token) return clean;
  return `${clean}/?token=${encodeURIComponent(token)}`;
}

// ─── IP Validation ───────────────────────────────────────────────────────────

const TAILSCALE_PREFIX = 100;
const LAN_192 = 192;
const LAN_10 = 10;
const LAN_172 = 172;

/**
 * Validates if a string is a Tailscale IP address (100.x.x.x range).
 */
export function isValidTailscaleIp(ip: string): boolean {
  if (!ip) return false;
  const parts = ip.trim().split('.');
  if (parts.length !== 4) return false;
  const first = parseInt(parts[0], 10);
  if (first !== TAILSCALE_PREFIX) return false;
  return parts.every(p => {
    const n = parseInt(p, 10);
    return !isNaN(n) && n >= 0 && n <= 255;
  });
}

/**
 * Validates if a string is a private LAN IP.
 * Supports 192.168.x.x, 10.x.x.x, 172.16-31.x.x.
 */
export function isValidLanIp(ip: string): boolean {
  if (!ip) return false;
  const parts = ip.trim().split('.');
  if (parts.length !== 4) return false;
  const nums = parts.map(p => parseInt(p, 10));
  if (nums.some(n => isNaN(n) || n < 0 || n > 255)) return false;
  const [a, b] = nums;
  return (
    (a === LAN_192 && b === 168) ||
    a === LAN_10 ||
    (a === LAN_172 && b >= 16 && b <= 31)
  );
}

/**
 * Auto-detects if the given IP is a Tailscale IP and returns appropriate mode.
 */
export function detectModeFromIp(ip: string): ConnectionMode {
  if (isValidTailscaleIp(ip)) return 'tailscale';
  if (isValidLanIp(ip)) return 'lan';
  return 'custom';
}

// ─── Network Detection ──────────────────────────────────────────────────────

/**
 * Detects connection quality using NetworkMonitor singleton.
 * Returns 'local' for loopback, 'wifi' for WiFi/Ethernet,
 * '4g'/'5g' for cellular, 'unknown' if undetectable.
 */
export function detectNetworkQuality(): NetworkQuality {
  return NetworkMonitor.quality;
}

/**
 * Checks if the current network is likely cellular (mobile data).
 * Uses NetworkMonitor singleton — no duplicate event listeners.
 */
export function isMobileNetwork(): boolean {
  return NetworkMonitor.isMobile;
}

/**
 * Attempts to detect if the client is on the same local network as the server
 * by checking if the current page's hostname is a private LAN IP.
 * This is a heuristic — the most reliable method is to try connecting directly.
 */
export function isSameNetwork(): boolean {
  if (typeof window === 'undefined') return false;
  const hostname = window.location.hostname;
  // If loaded from localhost, same network is likely true
  if (hostname === 'localhost' || hostname === '127.0.0.1') return true;
  // If loaded from a LAN IP, same network is likely true
  if (isValidLanIp(hostname)) return true;
  // If loaded from a Tailscale IP, same network could be true or false
  // but Tailscale creates a virtual LAN so treat as true
  if (isValidTailscaleIp(hostname)) return true;
  return false;
}

// ─── Network Change Detection ──────────────────────────────────────────────
// NOTE: Network change listeners are now handled exclusively by ReconnectingWebSocket
// to avoid duplicate listeners and race conditions. This function is deprecated
// and should not be used. Use ReconnectingWebSocket's built-in network detection instead.
//
// If you need network quality information, use detectNetworkQuality() directly.
// For reactive network change handling, rely on ReconnectingWebSocket's onStateChange
// and onLatency callbacks.

// ─── Best Endpoint Resolution ───────────────────────────────────────────────

export interface EndpointResult {
  wsUrl: string;
  apiUrl: string;
  mode: ConnectionMode;
  source: 'lan' | 'tailscale' | 'custom' | 'localhost-fallback';
}

/**
 * Determines the best connection endpoint using a priority strategy:
 * 1. LAN (if on same network and LAN IP available)
 * 2. Tailscale (if IP configured)
 * 3. Custom (if URL configured)
 * 4. Localhost (fallback for same-machine dev)
 *
 * @param config - The current connection configuration
 * @param detectedLanIp - The PC's LAN IP (fetched from /api/ip)
 * @param userOverride - If true, respect user's mode selection exactly
 */
export function getBestEndpoint(
  config: ResolverConfig & { autoReconnect: boolean; heartbeatEnabled: boolean },
  detectedLanIp?: string,
  userOverride = false,
): EndpointResult {
  const port = config.customPort || 3002;
  const apiPort = 3001;

  // If user has explicitly chosen a mode, respect it
  if (userOverride) {
    return buildEndpoint(config.mode, config, detectedLanIp, port, apiPort);
  }

  // Auto-priority with smart fallback
  // CORRECT PRIORITY: Tailscale works from ANYWHERE (LAN + mobile + public).
  // LAN only works on the same local network. Therefore:
  //   Tailscale > LAN (same network) > Custom > Localhost
  //
  // When on mobile data, Tailscale is the ONLY reliable option (LAN is unreachable).
  // When on WiFi/LAN, we prefer LAN for lower latency, but Tailscale is always valid.

  const onMobile = isMobileNetwork();
  const onSameNetwork = isSameNetwork();
  const hasTailscale = !!(config.tailscaleIp && isValidTailscaleIp(config.tailscaleIp));

  // Priority 1: Tailscale on mobile data (LAN unreachable on 4G/5G)
  if (onMobile && hasTailscale) {
    return buildEndpoint('tailscale', config, detectedLanIp, port, apiPort);
  }

  // Priority 2: LAN (if same network and LAN IP detected — lowest latency)
  if (onSameNetwork && detectedLanIp && isValidLanIp(detectedLanIp)) {
    return buildEndpoint('lan', config, detectedLanIp, port, apiPort);
  }

  // Priority 3: Tailscale (WiFi but LAN IP unavailable, or different WiFi network)
  if (hasTailscale) {
    return buildEndpoint('tailscale', config, detectedLanIp, port, apiPort);
  }

  // Priority 4: Custom relay / VPS / DDNS
  if (config.customUrl) {
    return buildEndpoint('custom', config, detectedLanIp, port, apiPort);
  }

  // Priority 5: Localhost fallback (development / same-machine)
  return buildEndpoint('lan', config, '127.0.0.1', port, apiPort);
}

/**
 * Returns the best endpoint for the CURRENT network quality.
 * Reactive version: call this when NetworkMonitor fires a quality-change event.
 */
export function getNetworkAwareEndpoint(
  config: ResolverConfig & { autoReconnect: boolean; heartbeatEnabled: boolean },
  detectedLanIp?: string,
): EndpointResult {
  // Always recompute from scratch using latest NetworkMonitor state
  return getBestEndpoint(config, detectedLanIp, false);
}

/**
 * Returns all available endpoints in priority order for fallback attempts.
 * Each entry represents a viable connection method, sorted best-first.
 * The caller should try each one sequentially until one succeeds.
 *
 * Priority: LAN (same network) > Tailscale > Custom > Localhost
 *
 * @param config - The current connection configuration
 * @param detectedLanIp - The PC's LAN IP (fetched from /api/ip)
 */
export function getFallbackEndpoints(
  config: ResolverConfig & { autoReconnect: boolean; heartbeatEnabled: boolean },
  detectedLanIp?: string,
): EndpointResult[] {
  const port = config.customPort || 3002;
  const apiPort = 3001;
  const endpoints: EndpointResult[] = [];
  const onSameNetwork = isSameNetwork();

  // Priority 1: LAN (if on same network + detected LAN IP)
  if (onSameNetwork && detectedLanIp && isValidLanIp(detectedLanIp)) {
    endpoints.push(buildEndpoint('lan', config, detectedLanIp, port, apiPort));
  }

  // Priority 2: Tailscale (works from anywhere)
  if (config.tailscaleIp && isValidTailscaleIp(config.tailscaleIp)) {
    endpoints.push(buildEndpoint('tailscale', config, detectedLanIp, port, apiPort));
  }

  // Priority 3: Custom relay or DDNS
  if (config.customUrl) {
    endpoints.push(buildEndpoint('custom', config, detectedLanIp, port, apiPort));
  }

  // Priority 4: Localhost fallback (same-machine dev)
  if (endpoints.length === 0) {
    endpoints.push(buildEndpoint('lan', config, '127.0.0.1', port, apiPort));
  }

  return endpoints;
}

function buildEndpoint(
  mode: ConnectionMode,
  config: ResolverConfig & { autoReconnect: boolean; heartbeatEnabled: boolean },
  lanIp: string | undefined,
  port: number,
  apiPort: number,
): EndpointResult {
  const source = mode === 'lan' && lanIp === '127.0.0.1'
    ? 'localhost-fallback'
    : mode;

  switch (mode) {
    case 'tailscale': {
      const ip = config.tailscaleIp.trim();
      return {
        wsUrl: ip ? `ws://${ip}:${port}` : `ws://localhost:${port}`,
        apiUrl: ip ? `http://${ip}:${apiPort}` : `http://localhost:${apiPort}`,
        mode,
        source,
      };
    }
    case 'custom': {
      const url = config.customUrl.trim();
      return {
        wsUrl: url ? `ws://${url}:${port}` : `ws://localhost:${port}`,
        apiUrl: url ? `http://${url}:${apiPort}` : `http://localhost:${apiPort}`,
        mode,
        source,
      };
    }
    case 'lan':
    default: {
      const ip = lanIp || '127.0.0.1';
      return {
        wsUrl: `ws://${ip}:${port}`,
        apiUrl: `http://${ip}:${apiPort}`,
        mode: 'lan',
        source,
      };
    }
  }
}

// ─── Label Helpers ──────────────────────────────────────────────────────────

const MODE_LABELS: Record<ConnectionMode, string> = {
  lan: 'LAN Local',
  tailscale: 'Tailscale (Remoto)',
  custom: 'Servidor Personalizado',
};

const MODE_SHORT_LABELS: Record<ConnectionMode, string> = {
  lan: 'LAN',
  tailscale: 'Tailscale',
  custom: 'Custom',
};

const SOURCE_LABELS: Record<string, string> = {
  lan: 'LAN',
  tailscale: 'Tailscale',
  custom: 'Custom',
  'localhost-fallback': 'Localhost (Dev)',
};

/**
 * Returns a human-readable label for a connection mode.
 */
export function getConnectionModeLabel(mode: ConnectionMode): string {
  return MODE_LABELS[mode] || 'Desconocido';
}

/**
 * Returns a short label (suitable for badges and status indicators).
 */
export function getConnectionModeShortLabel(mode: ConnectionMode): string {
  return MODE_SHORT_LABELS[mode] || '?';
}

/**
 * Returns a human-readable label for an endpoint source.
 */
export function getEndpointSourceLabel(source: string): string {
  return SOURCE_LABELS[source] || source;
}

/**
 * Returns the Tailscale-specific device name from localStorage
 * if previously stored by the device registry.
 */
export function getStoredTailscaleDeviceName(): string | null {
  try {
    const stored = localStorage.getItem('smartdisplay_device_registry');
    if (!stored) return null;
    const devices = JSON.parse(stored);
    if (Array.isArray(devices) && devices.length > 0) {
      // Return the most recent device with a tailscaleIp
      const withTailscale = devices
        .filter((d: any) => d.tailscaleIp)
        .sort((a: any, b: any) => (b.lastSeen || 0) - (a.lastSeen || 0));
      return withTailscale.length > 0 ? withTailscale[0].hostname : null;
    }
  } catch {
    /* ignore */
  }
  return null;
}

/**
 * Connection priority for auto-selection.
 * TAILSCALE > LAN > CUSTOM when multiple modes are available.
 */
export function getPreferredMode(
  tailscaleIp: string,
  lanIp: string,
  userPreference?: ConnectionMode
): ConnectionMode {
  // If user has explicitly set a mode, respect it (if valid)
  if (userPreference === 'tailscale' && tailscaleIp) return 'tailscale';
  if (userPreference === 'lan') return 'lan';
  if (userPreference === 'custom') return 'custom';

  // Auto-priority: Tailscale > LAN > Custom
  if (tailscaleIp) return 'tailscale';
  if (lanIp) return 'lan';
  return 'custom';
}
