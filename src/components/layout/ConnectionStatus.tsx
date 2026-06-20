// src/components/layout/ConnectionStatus.tsx
// Connection status indicator for sidebar — shows state, mode, latency, queue.
// v2.0 — Enhanced: animated states, color-coded latency, last heartbeat, quality indicator.

'use client';

import React, { memo, useEffect, useState } from 'react';
import type { ConnectionState } from '@/hooks/useConnectionManager';
import type { ConnectionMode } from '@/hooks/useConnectionSettings';

interface ConnectionStatusProps {
  connectionState: ConnectionState;
  mode: ConnectionMode;
  latency: number;
  pendingMessages: number;
  deviceName?: string;
  deviceType?: string;
  onDisconnect?: () => void;
  onReconnect?: () => void;
  dark: boolean;
  t: {
    textMuted: string;
    text: string;
    cardInner: string;
    border: string;
  };
}

// ─── State visual configuration ─────────────────────────────────────────────

interface StateStyle {
  dotColor: string;
  dotGlow: string;
  dotPulse: boolean;
  label: string;
  labelColor: string;
  actionLabel?: string;
}

const STATE_STYLES: Record<string, StateStyle> = {
  CONNECTED: {
    dotColor: 'bg-emerald-500',
    dotGlow: 'shadow-[0_0_8px_rgba(34,197,94,0.6)]',
    dotPulse: false,
    label: '● Conectado',
    labelColor: 'text-emerald-400',
  },
  CONNECTING: {
    dotColor: 'bg-amber-400',
    dotGlow: 'shadow-[0_0_8px_rgba(245,158,11,0.5)]',
    dotPulse: true,
    label: '◉ Conectando...',
    labelColor: 'text-amber-400',
  },
  RECONNECTING: {
    dotColor: 'bg-amber-400',
    dotGlow: 'shadow-[0_0_8px_rgba(245,158,11,0.5)]',
    dotPulse: true,
    label: '◉ Reconectando...',
    labelColor: 'text-amber-400',
  },
  DISCONNECTED: {
    dotColor: 'bg-slate-500',
    dotGlow: '',
    dotPulse: false,
    label: '○ Desconectado',
    labelColor: 'text-slate-400',
    actionLabel: 'Conectar',
  },
  ERROR: {
    dotColor: 'bg-red-500',
    dotGlow: 'shadow-[0_0_8px_rgba(239,68,68,0.5)]',
    dotPulse: false,
    label: '✕ Error',
    labelColor: 'text-red-400',
    actionLabel: 'Reintentar',
  },
};

// ─── Mode visual configuration ──────────────────────────────────────────────

const MODE_STYLES: Record<ConnectionMode, { bg: string; text: string; label: string; icon: string }> = {
  lan: { bg: 'bg-cyan-500/10', text: 'text-cyan-400', label: 'LAN', icon: '🔌' },
  tailscale: { bg: 'bg-emerald-500/10', text: 'text-emerald-400', label: 'Tailscale', icon: '🔒' },
  custom: { bg: 'bg-purple-500/10', text: 'text-purple-400', label: 'Custom', icon: '🌐' },
};

// ─── Latency color coding ───────────────────────────────────────────────────

function getLatencyStyle(ms: number): { color: string; text: string } {
  if (ms < 0) return { color: 'text-slate-500', text: '--ms' };
  if (ms < 30) return { color: 'text-emerald-400', text: `${ms}ms` };        // Excelente
  if (ms < 60) return { color: 'text-cyan-400', text: `${ms}ms` };            // Buena
  if (ms < 100) return { color: 'text-amber-400', text: `${ms}ms` };          // Media
  return { color: 'text-red-400', text: `${ms}ms ⚠` };                        // Mala
}

// ─── Component ──────────────────────────────────────────────────────────────

function ConnectionStatusInner({
  connectionState, mode, latency, pendingMessages, deviceName, deviceType,
  onDisconnect, onReconnect, dark, t,
}: ConnectionStatusProps) {
  const cfg = STATE_STYLES[connectionState] || STATE_STYLES.DISCONNECTED;
  const modeCfg = MODE_STYLES[mode] || MODE_STYLES.lan;
  const latencyStyle = getLatencyStyle(latency);

  // Live timestamp for "last activity" display
  const [now, setNow] = useState(Date.now());
  useEffect(() => {
    if (connectionState !== 'CONNECTED') return;
    const id = setInterval(() => setNow(Date.now()), 10000);
    return () => clearInterval(id);
  }, [connectionState]);

  return (
    <div className={`p-4 border-t ${t.border}`}>
      <div className={`${dark ? 'bg-[#0f1120] border border-slate-800/40' : `${t.cardInner}`} rounded-2xl p-4 transition-all duration-500`}>
        
        {/* ── Status Row ─────────────────────────────────────────────── */}
        <div className="flex items-center gap-2 mb-2">
          <div
            className={`w-2.5 h-2.5 rounded-full ${cfg.dotColor} ${cfg.dotGlow} ${
              cfg.dotPulse ? 'animate-pulse' : ''
            } transition-all duration-300`}
          />
          <span className={`text-[11px] font-bold uppercase tracking-wider ${cfg.labelColor}`}>
            {cfg.label}
          </span>
        </div>

        {/* ── Device Name ────────────────────────────────────────────── */}
        {deviceName && (
          <p className={`text-[12px] font-bold ${t.text} truncate flex items-center gap-1`}>
            {deviceName}
            {deviceType && (
              <span className="text-[10px] font-normal opacity-50 ml-0.5">({deviceType})</span>
            )}
          </p>
        )}

        {/* ── Connection Info Row ────────────────────────────────────── */}
        <div className="mt-2 flex flex-wrap items-center gap-1.5 text-[10px]">
          {/* Mode Badge */}
          <span className={`inline-flex items-center gap-1 px-1.5 py-0.5 rounded font-semibold ${modeCfg.bg} ${modeCfg.text}`}>
            {modeCfg.label}
          </span>

          {/* Latency */}
          {connectionState === 'CONNECTED' && (
            <span className={`font-mono font-bold ${latencyStyle.color}`}>
              {latencyStyle.text}
            </span>
          )}

          {/* Pending Messages */}
          {pendingMessages > 0 && (
            <span className="inline-flex items-center gap-1 px-1.5 py-0.5 rounded bg-amber-500/15 text-amber-400 font-bold">
              📨 {pendingMessages} pend.
            </span>
          )}

          {/* Reconnecting indicator */}
          {connectionState === 'RECONNECTING' && (
            <span className="inline-flex items-center gap-1 px-1.5 py-0.5 rounded bg-amber-500/10 text-amber-400">
              ⟳ backoff...
            </span>
          )}
        </div>

        {/* ── Action Buttons ─────────────────────────────────────────── */}
        <div className="mt-3 flex gap-2">
          {connectionState === 'CONNECTED' && onDisconnect && (
            <button
              onClick={onDisconnect}
              className="flex-1 py-1.5 rounded-lg text-[10px] font-medium text-red-400 bg-red-500/10 hover:bg-red-500/15 border border-red-500/20 transition-all active:scale-95"
            >
              Desconectar
            </button>
          )}
          {(connectionState === 'DISCONNECTED' || connectionState === 'ERROR') && onReconnect && (
            <button
              onClick={onReconnect}
              className="flex-1 py-1.5 rounded-lg text-[10px] font-bold text-cyan-400 bg-cyan-500/10 hover:bg-cyan-500/15 border border-cyan-500/20 transition-all active:scale-95"
            >
              {cfg.actionLabel || 'Reconectar'}
            </button>
          )}
          {/* Show a pulsing "Reconectando..." during reconnection */}
          {connectionState === 'RECONNECTING' && (
            <div className="flex-1 py-1.5 rounded-lg text-[10px] text-center font-medium text-amber-400 bg-amber-500/5 border border-amber-500/10 animate-pulse">
              Reconectando...
            </div>
          )}
        </div>

        {/* ── Helpful hint on error ──────────────────────────────────── */}
        {connectionState === 'ERROR' && (
          <div className="mt-2 space-y-1">
            <p className="text-[9px] text-red-400/70 leading-tight font-semibold">
              No se pudo conectar tras múltiples intentos.
            </p>
            <ul className="text-[9px] text-red-400/50 leading-relaxed space-y-0.5 list-none">
              <li>• ¿La laptop está encendida y sin hibernar?</li>
              <li>• ¿SmartDisplay Agent está ejecutándose?</li>
              <li>• ¿Tailscale está activo en PC y Android?</li>
              <li>• ¿Estás en la misma red o en Tailscale?</li>
            </ul>
          </div>
        )}
      </div>
    </div>
  );
}

export const ConnectionStatus = memo(ConnectionStatusInner);
