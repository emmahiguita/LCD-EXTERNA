const { contextBridge, ipcRenderer } = require('electron');

/**
 * SmartDisplay AI — Preload / IPC Bridge
 *
 * Exposes a safe `window.api` object to the renderer process.
 * Only explicitly listed methods are exposed (no raw ipcRenderer).
 */
contextBridge.exposeInMainWorld('api', {
  // ── Session ────────────────────────────────────────────────────────────
  getToken: () => ipcRenderer.invoke('get-token'),
  getLocalIP: () => ipcRenderer.invoke('get-local-ip'),

  // ── Status ─────────────────────────────────────────────────────────────
  getStatus: () => ipcRenderer.invoke('get-status'),
  onStatusUpdate: (callback) => {
    const handler = (_event, data) => callback(data);
    ipcRenderer.on('status-update', handler);
    return () => ipcRenderer.removeListener('status-update', handler);
  },

  // ── Devices (ADB) ──────────────────────────────────────────────────────
  getDevices: () => ipcRenderer.invoke('get-devices'),
  selectDevice: (serial) => ipcRenderer.invoke('select-device', serial),

  // ── Streaming ──────────────────────────────────────────────────────────
  startStream: () => ipcRenderer.invoke('start-stream'),
  stopStream: () => ipcRenderer.invoke('stop-stream'),
  onStreamStatus: (callback) => {
    const handler = (_event, data) => callback(data);
    ipcRenderer.on('stream-status', handler);
    return () => ipcRenderer.removeListener('stream-status', handler);
  },
  onClientConnected: (callback) => {
    const handler = (_event, data) => callback(data);
    ipcRenderer.on('client-connected', handler);
    return () => ipcRenderer.removeListener('client-connected', handler);
  },

  // ── Input ──────────────────────────────────────────────────────────────
  sendClick: (x, y) => ipcRenderer.invoke('input-click', x, y),
  sendRightClick: () => ipcRenderer.invoke('input-right-click'),
  sendMove: (dx, dy) => ipcRenderer.invoke('input-move', dx, dy),
  sendKey: (key) => ipcRenderer.invoke('input-key', key),
  sendText: (text) => ipcRenderer.invoke('input-text', text),

  // ── Android ADB commands ───────────────────────────────────────────────
  adbTap: (x, y) => ipcRenderer.invoke('adb-tap', x, y),
  adbSwipe: (x1, y1, x2, y2, duration) => ipcRenderer.invoke('adb-swipe', x1, y1, x2, y2, duration),
  adbText: (text) => ipcRenderer.invoke('adb-text', text),
  adbKeyEvent: (keycode) => ipcRenderer.invoke('adb-keyevent', keycode),

  // ── Clipboard ──────────────────────────────────────────────────────────
  onClipboardChange: (callback) => {
    const handler = (_event, text) => callback(text);
    ipcRenderer.on('clipboard-change', handler);
    return () => ipcRenderer.removeListener('clipboard-change', handler);
  },

  // ── App control ────────────────────────────────────────────────────────
  quit: () => ipcRenderer.invoke('app-quit'),
  minimize: () => ipcRenderer.invoke('app-minimize'),
  getVersion: () => ipcRenderer.invoke('get-version'),

  // ── Settings ───────────────────────────────────────────────────────────
  setAutoStart: (enabled) => ipcRenderer.invoke('set-auto-start', enabled),
  setMinToTray: (enabled) => ipcRenderer.invoke('set-min-to-tray', enabled),
  getSettings: () => ipcRenderer.invoke('get-settings'),

  // ── Logs ───────────────────────────────────────────────────────────────
  onLog: (callback) => {
    const handler = (_event, line) => callback(line);
    ipcRenderer.on('app-log', handler);
    return () => ipcRenderer.removeListener('app-log', handler);
  },
});
