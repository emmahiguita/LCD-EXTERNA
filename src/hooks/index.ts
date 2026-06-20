// src/hooks/index.ts
export { useConnectionManager, registerGlobalSend, unregisterGlobalSend } from './useConnectionManager';
export type { ConnectionState } from './useConnectionManager';
export { useConnectionSettings } from './useConnectionSettings';
export type { ConnectionMode, ConnectionConfig, ConnectionQuality } from './useConnectionSettings';
export { useDeviceRegistry } from './useDeviceRegistry';
export type { RegistryEntry, DeviceRegistration } from './useDeviceRegistry';
