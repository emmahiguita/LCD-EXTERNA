// src/hooks/useDeviceRegistry.ts
// Device Registry v2 — persists known PCs for automatic reconnection.
// Stores Tailscale IP, hostname, last seen timestamp, and known network history.
//
// UUID is derived from the session token (not hostname) to ensure stability
// across network changes, IP changes, and PC renames.

'use client';

import { useState, useCallback, useEffect } from 'react';
import type { ConnectionMode } from './useConnectionSettings';

const STORAGE_KEY = 'smartdisplay_device_registry';

export interface RegistryEntry {
  id: string;
  uuid: string;      // Persistent unique identifier derived from session token (not hostname)
  hostname: string;
  deviceName: string;
  connectionMode: ConnectionMode;
  lanIp: string;
  tailscaleIp: string;
  lastSeen: number;
  online: boolean;
  connectionCount: number;
  /** All LAN IPs this device has been seen on across different networks */
  knownLanIps: string[];
}

export interface DeviceRegistration {
  id: string;
  uuid: string;       // Persistent unique identifier (use session token)
  hostname: string;
  deviceName?: string;
  connectionMode?: ConnectionMode;
  lanIp?: string;
  tailscaleIp?: string;
}

interface UseDeviceRegistryReturn {
  devices: RegistryEntry[];
  addOrUpdateDevice: (entry: DeviceRegistration) => void;
  markOnline: (id: string) => void;
  markOffline: (id: string) => void;
  removeDevice: (id: string) => void;
  getDevice: (id: string) => RegistryEntry | undefined;
  getDeviceByUuid: (uuid: string) => RegistryEntry | undefined;
  getLastConnected: () => RegistryEntry | undefined;
  getDeviceByTailscaleIp: (ip: string) => RegistryEntry | undefined;
  clearRegistry: () => void;
}

// 7 days — extended from 24h so paired devices are not forgotten during long gaps.
// A device is only marked offline if we haven't seen it in 7 days.
const STALE_THRESHOLD_MS = 7 * 24 * 60 * 60 * 1000;

function loadRegistry(): RegistryEntry[] {
  try {
    const stored = localStorage.getItem(STORAGE_KEY);
    if (stored) {
      const parsed: RegistryEntry[] = JSON.parse(stored);
      const now = Date.now();
      return parsed.map(d => ({
        ...d,
        // Migrate old entries without knownLanIps
        knownLanIps: d.knownLanIps ?? (d.lanIp ? [d.lanIp] : []),
        // Only mark as offline if stale for more than 7 days
        online: d.online && (now - d.lastSeen < STALE_THRESHOLD_MS),
      }));
    }
  } catch { /* ignore */ }
  return [];
}

function saveRegistry(devices: RegistryEntry[]): void {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(devices));
  } catch { /* ignore */ }
}

export function useDeviceRegistry(): UseDeviceRegistryReturn {
  const [devices, setDevices] = useState<RegistryEntry[]>(loadRegistry);

  useEffect(() => { saveRegistry(devices); }, [devices]);

  const addOrUpdateDevice = useCallback((entry: DeviceRegistration) => {
    setDevices(prev => {
      // Match by uuid first (stable token-based ID), then by id (hostname fallback)
      const existing = prev.find(d => d.uuid === entry.uuid) ?? prev.find(d => d.id === entry.id);
      if (existing) {
        // Accumulate known LAN IPs across network changes
        const knownLanIps = existing.knownLanIps ?? [];
        if (entry.lanIp && !knownLanIps.includes(entry.lanIp)) {
          knownLanIps.push(entry.lanIp);
        }
        return prev.map(d =>
          (d.uuid === entry.uuid || d.id === entry.id)
            ? {
                ...d,
                uuid: entry.uuid || d.uuid,
                hostname: entry.hostname || d.hostname,
                deviceName: entry.deviceName || d.deviceName,
                connectionMode: entry.connectionMode || d.connectionMode,
                lanIp: entry.lanIp || d.lanIp,
                tailscaleIp: entry.tailscaleIp || d.tailscaleIp,
                lastSeen: Date.now(),
                online: true,
                connectionCount: d.connectionCount + 1,
                knownLanIps,
              }
            : d
        );
      }
      // New device
      return [
        ...prev,
        {
          id: entry.id,
          uuid: entry.uuid,
          hostname: entry.hostname,
          deviceName: entry.deviceName || entry.hostname,
          connectionMode: entry.connectionMode || 'lan',
          lanIp: entry.lanIp || '',
          tailscaleIp: entry.tailscaleIp || '',
          lastSeen: Date.now(),
          online: true,
          connectionCount: 1,
          knownLanIps: entry.lanIp ? [entry.lanIp] : [],
        },
      ];
    });
  }, []);

  const markOnline = useCallback((id: string) => {
    setDevices(prev => prev.map(d => d.id === id ? { ...d, online: true, lastSeen: Date.now() } : d));
  }, []);

  const markOffline = useCallback((id: string) => {
    setDevices(prev => prev.map(d => d.id === id ? { ...d, online: false } : d));
  }, []);

  const removeDevice = useCallback((id: string) => {
    setDevices(prev => prev.filter(d => d.id !== id));
  }, []);

  const getDevice = useCallback((id: string): RegistryEntry | undefined => {
    return devices.find(d => d.id === id);
  }, [devices]);

  const getDeviceByUuid = useCallback((uuid: string): RegistryEntry | undefined => {
    return devices.find(d => d.uuid === uuid);
  }, [devices]);

  const getLastConnected = useCallback((): RegistryEntry | undefined => {
    if (devices.length === 0) return undefined;
    return devices.reduce((a, b) => a.lastSeen > b.lastSeen ? a : b);
  }, [devices]);

  const getDeviceByTailscaleIp = useCallback((ip: string): RegistryEntry | undefined => {
    return devices.find(d => d.tailscaleIp === ip);
  }, [devices]);

  const clearRegistry = useCallback(() => {
    setDevices([]);
  }, []);

  return { devices, addOrUpdateDevice, markOnline, markOffline, removeDevice, getDevice, getDeviceByUuid, getLastConnected, getDeviceByTailscaleIp, clearRegistry };
}
