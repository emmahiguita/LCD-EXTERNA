// src/components/settings/ConnectionPanel.tsx
// Connection settings panel — LAN / Tailscale / Custom relay.
// v2.2 — Added: Android quick-connect card with auto token fetch.

'use client';

import React, { memo, useCallback, useState, useEffect } from 'react';
import type { ConnectionMode, ConnectionConfig } from '@/hooks/useConnectionSettings';
import { getEndpointSourceLabel } from '@/lib/ConnectionResolver';
import type { EndpointResult } from '@/lib/ConnectionResolver';

interface ConnectionPanelProps {
  config: ConnectionConfig;
  lanIp: string;
  endpoint: EndpointResult;
  onSetMode: (mode: ConnectionMode) => void;
  onSetTailscaleIp: (ip: string) => void;
  onSetCustomUrl: (url: string) => void;
  onSetCustomPort: (port: number) => void;
  onSetAutoReconnect: (on: boolean) => void;
  onSetHeartbeat: (on: boolean) => void;
  onResetConfig: () => void;
  connectionState: string;
  wsUrl: string;
  apiUrl: string;
  dark: boolean;
  t: {
    panel: string;
    text: string;
    textMuted: string;
    textSub: string;
    border: string;
    hover: string;
    cardInner: string;
  };
}

const MODE_OPTIONS: { value: ConnectionMode; label: string; desc: string }[] = [
  { value: 'lan', label: 'LAN Local', desc: 'Conexión directa por USB o WiFi local' },
  { value: 'tailscale', label: 'Tailscale (Remoto)', desc: 'Conexión remota cifrada via Tailscale mesh VPN' },
  { value: 'custom', label: 'Servidor Personalizado', desc: 'Conexión via VPS, Cloudflare Tunnel o IP pública' },
];

const STATE_COLORS: Record<string, string> = {
  CONNECTED: 'text-emerald-400',
  CONNECTING: 'text-amber-400',
  RECONNECTING: 'text-amber-400',
  DISCONNECTED: 'text-slate-400',
  ERROR: 'text-red-400',
};

function ConnectionPanelInner({
  config, lanIp, endpoint, onSetMode, onSetTailscaleIp, onSetCustomUrl, onSetCustomPort,
  onSetAutoReconnect, onSetHeartbeat, onResetConfig,
  connectionState, wsUrl, apiUrl, dark, t,
}: ConnectionPanelProps) {
  return (
    <div className={`glow-card rounded-2xl p-6 ${t.panel} max-w-3xl mx-auto space-y-8`}>
      {/* Header */}
      <div>
        <h2 className="text-lg font-bold">Configuración de Conexión</h2>
        <p className={`text-xs ${t.textMuted} mt-0.5`}>
          Elige cómo se conecta SmartDisplay AI a tu PC. Tailscale es la opción recomendada para uso remoto seguro.
        </p>
      </div>

      {/* Connection Status */}
      <div className={`p-4 rounded-xl ${t.cardInner} border ${t.border}`}>
        <div className="flex items-center justify-between">
          <p className="text-xs font-bold uppercase tracking-wider text-slate-500">Estado de Conexión</p>
          <div className="flex items-center gap-2">
            <span className={`text-[10px] font-semibold px-2 py-0.5 rounded ${
              endpoint.source === 'tailscale' ? 'bg-emerald-500/10 text-emerald-400' :
              endpoint.source === 'lan' ? 'bg-cyan-500/10 text-cyan-400' :
              endpoint.source === 'localhost-fallback' ? 'bg-slate-500/10 text-slate-400' :
              'bg-purple-500/10 text-purple-400'
            }`}>
              {getEndpointSourceLabel(endpoint.source)}
            </span>
            <span className={`text-[11px] font-bold ${STATE_COLORS[connectionState] || 'text-slate-400'}`}>
              {connectionState}
            </span>
          </div>
        </div>
        <div className="mt-2 space-y-1">
          <p className="text-[11px] font-mono text-slate-500">WS: <span className="text-cyan-400">{wsUrl}</span></p>
          <p className="text-[11px] font-mono text-slate-500">API: <span className="text-cyan-400">{apiUrl}</span></p>
          {lanIp && (
            <p className="text-[11px] font-mono text-slate-500">LAN IP: <span className="text-cyan-400">{lanIp}</span></p>
          )}
        </div>
      </div>

      {/* Mode Selection */}
      <div className="space-y-3">
        <p className="text-xs font-bold uppercase tracking-wider text-cyan-400">Modo de Conexión</p>
        <div className="grid gap-3">
          {MODE_OPTIONS.map(({ value, label, desc }) => (
            <button
              key={value}
              onClick={() => onSetMode(value)}
              className={`p-4 rounded-xl border text-left transition-all duration-150 ${
                config.mode === value
                  ? 'border-cyan-500/40 bg-cyan-500/10 text-cyan-300'
                  : `${t.border} ${t.hover} text-slate-400 hover:text-white`
              }`}
              aria-pressed={config.mode === value}
              aria-label={`Modo ${label}`}
            >
              <p className="font-bold text-sm">{label}</p>
              <p className={`text-[11px] mt-1 ${config.mode === value ? 'text-cyan-400/70' : t.textSub}`}>{desc}</p>
            </button>
          ))}
        </div>
      </div>

      {/* Tailscale Config */}
      {config.mode === 'tailscale' && (
        <div className="space-y-3 p-4 rounded-xl bg-slate-900/40 border border-slate-800/40">
          <p className="text-xs font-bold text-emerald-400 flex items-center gap-2">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/></svg>
            Configuración Tailscale
          </p>
          <div className="space-y-2">
            <label className="block text-[11px] font-bold text-slate-400">IP de Tailscale de tu PC</label>
            <input
              type="text"
              placeholder="Ej: 100.x.x.x"
              value={config.tailscaleIp}
              onChange={(e) => onSetTailscaleIp(e.target.value)}
              className="w-full px-3.5 py-2 text-xs rounded-xl bg-slate-950 border border-slate-800 outline-none text-white focus:border-cyan-500/40"
              aria-label="IP de Tailscale"
            />
            <p className={`text-[10px] ${t.textSub}`}>
              La encuentras en la app de Tailscale de tu PC o en{' '}
              <code className="text-cyan-400">tailscale status</code> en la terminal.
            </p>
          </div>
        </div>
      )}

      {/* Custom Config */}
      {config.mode === 'custom' && (
        <div className="space-y-3 p-4 rounded-xl bg-slate-900/40 border border-slate-800/40">
          <p className="text-xs font-bold text-purple-400">Configuración Personalizada</p>
          <div className="grid grid-cols-3 gap-3">
            <div className="col-span-2 space-y-1">
              <label className="block text-[11px] font-bold text-slate-400">URL / IP</label>
              <input
                type="text"
                placeholder="Ej: mi-pc.ddns.net o 203.0.113.10"
                value={config.customUrl}
                onChange={(e) => onSetCustomUrl(e.target.value)}
                className="w-full px-3.5 py-2 text-xs rounded-xl bg-slate-950 border border-slate-800 outline-none text-white focus:border-cyan-500/40"
                aria-label="URL personalizada"
              />
            </div>
            <div className="space-y-1">
              <label className="block text-[11px] font-bold text-slate-400">Puerto</label>
              <input
                type="number"
                placeholder="3002"
                value={config.customPort}
                onChange={(e) => onSetCustomPort(parseInt(e.target.value, 10) || 3002)}
                className="w-full px-3.5 py-2 text-xs rounded-xl bg-slate-950 border border-slate-800 outline-none text-white focus:border-cyan-500/40"
                aria-label="Puerto personalizado"
              />
            </div>
          </div>
          <p className={`text-[10px] ${t.textSub}`}>
            Usa Cloudflare Tunnel, VPS con relay, o DDNS + reenvío de puertos (no recomendado sin cifrado).
          </p>
        </div>
      )}

      {/* Options */}
      <div className="space-y-3">
        <p className="text-xs font-bold uppercase tracking-wider text-cyan-400">Opciones de Conexión</p>
        <div className="space-y-3">
          <label className="flex items-center justify-between p-3 rounded-xl bg-slate-900/40 border border-slate-800/40 cursor-pointer">
            <div>
              <p className="text-xs font-bold">Reconexión Automática</p>
              <p className={`text-[10px] ${t.textSub}`}>Reintenta conectar con backoff exponencial (1s → 60s)</p>
            </div>
            <input
              type="checkbox"
              checked={config.autoReconnect}
              onChange={(e) => onSetAutoReconnect(e.target.checked)}
              className="accent-cyan-500 rounded cursor-pointer w-4 h-4"
            />
          </label>
          <label className="flex items-center justify-between p-3 rounded-xl bg-slate-900/40 border border-slate-800/40 cursor-pointer">
            <div>
              <p className="text-xs font-bold">Heartbeat (Ping/Pong)</p>
              <p className={`text-[10px] ${t.textSub}`}>Ping cada 10s, timeout 5s — detecta caídas en ~15s</p>
            </div>
            <input
              type="checkbox"
              checked={config.heartbeatEnabled}
              onChange={(e) => onSetHeartbeat(e.target.checked)}
              className="accent-cyan-500 rounded cursor-pointer w-4 h-4"
            />
          </label>
        </div>
      </div>

      {/* Android Quick Connect */}
      <AndroidQuickConnect t={t} />

      {/* Reset */}
      <div className="pt-2">
        <button
          onClick={onResetConfig}
          className="px-4 py-2 text-xs font-bold rounded-xl border border-red-800/40 bg-red-500/5 text-red-400/70 hover:bg-red-500/10 hover:text-red-400 transition-all"
        >
          Restablecer Configuración
        </button>
      </div>
    </div>
  );
}

// ─── Android Quick Connect ────────────────────────────────────────────────────
// Fetches the session token from /api/connect and shows copy-ready URLs.
// The token is persistent — Android only needs to scan/copy once.

interface ConnectInfo {
  token: string;
  hostname: string;
  lan: { wsUrl: string; appUrl: string } | null;
  tailscale: { wsUrl: string; appUrl: string } | null;
}

function AndroidQuickConnect({ t }: { t: ConnectionPanelProps['t'] }) {
  const [info, setInfo] = useState<ConnectInfo | null>(null);
  const [error, setError] = useState(false);
  const [copied, setCopied] = useState<string | null>(null);

  useEffect(() => {
    const url = typeof window !== 'undefined' && window.location.protocol === 'file:'
      ? 'http://localhost:3001/api/connect'
      : '/api/connect';
    fetch(url)
      .then(r => r.ok ? r.json() : Promise.reject(r.status))
      .then((data: ConnectInfo) => { if (data.token) setInfo(data); })
      .catch(() => setError(true));
  }, []);

  const copy = useCallback((text: string, key: string) => {
    navigator.clipboard?.writeText(text).catch(() => {});
    setCopied(key);
    setTimeout(() => setCopied(null), 2000);
  }, []);

  return (
    <div className="space-y-3 p-4 rounded-xl bg-slate-900/40 border border-slate-800/40">
      <p className="text-xs font-bold text-cyan-400 flex items-center gap-2">
        📱 Conectar Android desde cualquier red
      </p>

      {!info && !error && (
        <p className={`text-[10px] ${t.textSub} animate-pulse`}>Cargando URLs de conexión...</p>
      )}

      {error && (
        <p className="text-[10px] text-red-400/70">
          SmartDisplay Agent no está corriendo o el token aún no fue generado.
          Inicia la app en la laptop primero.
        </p>
      )}

      {info && (
        <>
          <p className={`text-[10px] ${t.textSub}`}>
            Copia una URL y ábrela en el navegador de tu Android.
            {info.tailscale
              ? ' La opción Tailscale funciona desde cualquier red (WiFi o datos móviles).'
              : ' Instala Tailscale para acceso remoto fuera de tu red local.'}
          </p>

          {info.tailscale && (
            <div>
              <p className="text-[9px] font-bold text-emerald-400 mb-1">🔒 Tailscale — funciona desde cualquier red</p>
              <div className="flex items-center gap-2">
                <code className={`flex-1 text-[10px] text-emerald-400 bg-emerald-500/5 border border-emerald-500/20 rounded-lg px-2 py-1.5 truncate font-mono`}>
                  {info.tailscale.appUrl}
                </code>
                <button
                  onClick={() => copy(info.tailscale!.appUrl, 'tailscale')}
                  className="shrink-0 px-2.5 py-1.5 text-[10px] font-bold rounded-lg border border-emerald-500/20 text-emerald-400 bg-emerald-500/10 hover:bg-emerald-500/15 transition-all active:scale-95"
                >
                  {copied === 'tailscale' ? '✓' : 'Copiar'}
                </button>
              </div>
            </div>
          )}

          {info.lan && (
            <div>
              <p className="text-[9px] font-bold text-cyan-400 mb-1">🔌 LAN — solo en la misma red WiFi</p>
              <div className="flex items-center gap-2">
                <code className={`flex-1 text-[10px] text-cyan-400 bg-cyan-500/5 border border-cyan-500/20 rounded-lg px-2 py-1.5 truncate font-mono`}>
                  {info.lan.appUrl}
                </code>
                <button
                  onClick={() => copy(info.lan!.appUrl, 'lan')}
                  className="shrink-0 px-2.5 py-1.5 text-[10px] font-bold rounded-lg border border-cyan-500/20 text-cyan-400 bg-cyan-500/10 hover:bg-cyan-500/15 transition-all active:scale-95"
                >
                  {copied === 'lan' ? '✓' : 'Copiar'}
                </button>
              </div>
            </div>
          )}

          <p className={`text-[9px] ${t.textSub} opacity-60`}>
            ⚠ No compartas estas URLs. Contienen el token de sesión de tu PC.
          </p>
        </>
      )}
    </div>
  );
}

export const ConnectionPanel = memo(ConnectionPanelInner);
