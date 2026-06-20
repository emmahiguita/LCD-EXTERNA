'use client';

/**
 * useAdaptiveConnection.ts — Hook React para Adaptive Global Streaming
 * 
 * Gestiona:
 *   1. Motor de selección de transporte (LAN → WebRTC → Tailscale → TURN)
 *   2. Heartbeat cada 2s — evalúa si hay mejor canal disponible con scoring de canales
 *   3. Migración sin reinicio de encoder al cambiar de red
 *   4. WebRTC signaling via WebSocket existente
 *   5. Detección de cambio WiFi ↔ 4G inmediata
 */

import { useEffect, useRef, useState, useCallback } from 'react';
import {
  selectConnection,
  migrateSession,
  createSession,
  measureLatency,
  checkTailscaleReachability,
  isOnMobileData,
  getTransportLabel,
  getTransportColor,
  initialHistory,
  type TransportType,
  type TransportSession,
  type NetworkContext,
  type VideoQuality,
  type ChannelMetrics,
  type TransportHistory,
} from '@/lib/AdaptiveTransport';
import { WebRTCTransport, FREE_ICE_SERVERS, type SignalMessage } from '@/lib/WebRTCTransport';
import { ReconnectingWebSocket } from '@/lib/ReconnectingWebSocket';
import { NetworkMonitor } from '@/lib/NetworkMonitor';

// ── Types ────────────────────────────────────────────────────────────────────

export interface AdaptiveConnectionState {
  transport: TransportType;
  transportLabel: string;
  transportColor: string;
  quality: VideoQuality;
  latency: number;           // ms, -1 si desconocido
  connected: boolean;
  sessionId: string | null;
  migrationCount: number;
  networkContext: Partial<NetworkContext>;
}

export interface AdaptiveConnectionOptions {
  lanIp?: string;
  tailscaleIp?: string;
  wsPort?: number;           // Puerto WS principal (default: 3002)
  apiPort?: number;          // Puerto API (default: 3001)
  token?: string;            // Token de sesión persistente para seguridad
  onFrame?: (data: ArrayBuffer) => void;
  onStateChange?: (state: AdaptiveConnectionState) => void;
  onLog?: (msg: string) => void;
  enabled?: boolean;
}

const DEFAULT_WS_PORT = 3002;
const DEFAULT_API_PORT = 3001;
const HEARTBEAT_INTERVAL_MS = 2000;    // Evaluar canal cada 2s
const LATENCY_MEASURE_INTERVAL_MS = 5000; // Medir latencias cada 5s

// ── Hook ─────────────────────────────────────────────────────────────────────

export function useAdaptiveConnection(opts: AdaptiveConnectionOptions) {
  const {
    lanIp,
    tailscaleIp,
    wsPort = DEFAULT_WS_PORT,
    apiPort = DEFAULT_API_PORT,
    token = '',
    onFrame,
    onStateChange,
    onLog,
    enabled = true,
  } = opts;

  // ── Reactive state ───────────────────────────────────────────────────────
  const [state, setState] = useState<AdaptiveConnectionState>({
    transport: 'none',
    transportLabel: '❌ Sin conexión',
    transportColor: 'red',
    quality: '480p',
    latency: -1,
    connected: false,
    sessionId: null,
    migrationCount: 0,
    networkContext: {},
  });

  // ── Internal refs (no re-render) ─────────────────────────────────────────
  const sessionRef      = useRef<TransportSession | null>(null);
  const wsRef           = useRef<ReconnectingWebSocket | null>(null);
  const webrtcRef       = useRef<WebRTCTransport | null>(null);
  const heartbeatRef    = useRef<ReturnType<typeof setInterval> | null>(null);
  const latencyTimerRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const historyRef      = useRef<TransportHistory>(initialHistory);
  const tokenRef        = useRef<string>(token);

  useEffect(() => {
    tokenRef.current = token;
  }, [token]);

  const onFrameRef = useRef(onFrame);
  const onStateChangeRef = useRef(onStateChange);
  const onLogRef = useRef(onLog);

  useEffect(() => {
    onFrameRef.current = onFrame;
    onStateChangeRef.current = onStateChange;
    onLogRef.current = onLog;
  }, [onFrame, onStateChange, onLog]);

  const netCtxRef       = useRef<NetworkContext>({
    lanIp,
    tailscaleIp,
    webrtcConnected: false,
    tailscaleReachable: false,
    lanLatency: -1,
    onMobileData: isOnMobileData(),
    lanMetrics: { rtt: -1, jitter: 0, packetLoss: 0, throughput: 100, networkType: 'unknown' },
    webrtcMetrics: { rtt: -1, jitter: 0, packetLoss: 0, throughput: 30, networkType: 'unknown' },
    tailscaleMetrics: { rtt: -1, jitter: 0, packetLoss: 0, throughput: 25, networkType: 'unknown' },
    turnMetrics: { rtt: -1, jitter: 0, packetLoss: 0, throughput: 8, networkType: 'unknown' },
  });
  const mountedRef      = useRef(true);

  // ── Helper Callbacks (Must be defined first due to JS hoisting limitations) ─

  const log = useCallback((msg: string) => {
    const t = new Date().toLocaleTimeString('es', { hour12: false });
    onLogRef.current?.(`${t} · ${msg}`);
    console.info(`[AdaptiveConn] ${msg}`);
  }, []);

  // ── Push state update ────────────────────────────────────────────────────
  const pushState = useCallback((session: TransportSession, latency: number, ctx: Partial<NetworkContext>) => {
    if (!mountedRef.current) return;
    const next: AdaptiveConnectionState = {
      transport: session.transport,
      transportLabel: getTransportLabel(session.transport),
      transportColor: getTransportColor(session.transport),
      quality: session.quality,
      latency,
      connected: session.transport !== 'none',
      sessionId: session.id,
      migrationCount: session.migrationCount,
      networkContext: { ...ctx },
    };
    setState(next);
    onStateChangeRef.current?.(next);
  }, []);

  // ── Network context update ────────────────────────────────────────────────
  const updateNetContext = useCallback((patch: Partial<NetworkContext>) => {
    netCtxRef.current = { ...netCtxRef.current, ...patch };
  }, []);

  // ── Signal message handler ────────────────────────────────────────────────
  const handleSignalMessage = useCallback(async (msg: SignalMessage) => {
    const rtc = webrtcRef.current;
    if (!rtc) return;

    if (msg.type === 'answer') {
      await rtc.handleAnswer(msg.sdp);
      log('✓ WebRTC answer recibido');
    } else if (msg.type === 'ice') {
      await rtc.addIceCandidate(msg.candidate);
    }
  }, [log]);

  const sendSignal = useCallback((msg: SignalMessage) => {
    const ws = wsRef.current;
    if (!ws) return;
    ws.send(JSON.stringify({ type: 'webrtc_signal', signal: msg }));
  }, []);

  // ── LAN WebSocket connection ─────────────────────────────────────────────
  const connectLanWs = useCallback((ip: string) => {
    if (wsRef.current) {
      wsRef.current.close();
      wsRef.current = null;
    }

    const currentToken = tokenRef.current;
    const url = currentToken ? `ws://${ip}:${wsPort}?token=${currentToken}` : `ws://${ip}:${wsPort}`;
    log(`Conectando WS LAN: ${url}`);

    const rws = new ReconnectingWebSocket(url, {
      heartbeatInterval: 5000,
      autoReconnect: true,
      maxReconnectAttempts: 8,
      onStateChange: (s) => {
        if (!mountedRef.current) return;
        if (s === 'connected') {
          updateNetContext({ lanLatency: 0 });
          log('✓ LAN WS conectado');
        } else if (s === 'disconnected' || s === 'error') {
          updateNetContext({ lanLatency: -1 });
          log(`✗ LAN WS ${s}`);
        }
      },
      onLatency: (ms) => {
        // Actualizar métricas LAN en tiempo real
        const prevRtt = netCtxRef.current.lanMetrics?.rtt ?? -1;
        const prevJitter = netCtxRef.current.lanMetrics?.jitter ?? 0;
        const jitter = prevRtt >= 0 ? Math.round(0.8 * prevJitter + 0.2 * Math.abs(ms - prevRtt)) : 0;
        
        netCtxRef.current.lanMetrics = {
          rtt: ms,
          jitter,
          packetLoss: 0.0,
          throughput: 100,
          networkType: isOnMobileData() ? 'cellular' : 'wifi',
        };
        updateNetContext({ lanLatency: ms });

        if (sessionRef.current?.transport === 'lan_ws') {
          pushState(sessionRef.current, ms, netCtxRef.current);
        }
      },
    });

    rws.binaryType = 'arraybuffer';
    rws.onopen = () => {
      rws.send(JSON.stringify({ type: 'register', client: 'workstation' }));
    };
    rws.onmessage = (ev) => {
      // 1. Manejar mensajes de texto (ej. señalización WebRTC en formato JSON)
      if (typeof ev.data === 'string') {
        try {
          const envelope = JSON.parse(ev.data);
          if (envelope.type === 'webrtc_signal' && envelope.signal) {
            handleSignalMessage(envelope.signal);
            return;
          }
        } catch (_) {}
        return;
      }

      // 2. Manejar mensajes binarios (frames H.264 o señalización binaria)
      if (!(ev.data instanceof ArrayBuffer) || !onFrameRef.current) return;
      const data = ev.data as ArrayBuffer;
      const view = new DataView(data);

      // Detectar si es mensaje de señalización WebRTC (JSON prefix 0x7B = '{')
      if (view.byteLength > 0 && view.getUint8(0) === 0x7B) {
        try {
          const envelope = JSON.parse(
            new TextDecoder().decode(data)
          );
          if (envelope.type === 'webrtc_signal' && envelope.signal) {
            handleSignalMessage(envelope.signal);
            return;
          }
        } catch { /* No es JSON, es frame binario */ }
      }

      // Filtrar frames de WebSocket si ya estamos en WebRTC/Relay y WebRTC está conectado y entregando frames
      const activeTransport = sessionRef.current?.transport;
      const isWebRTCActive = activeTransport === 'webrtc_p2p' || activeTransport === 'turn_relay';
      if (isWebRTCActive && webrtcRef.current?.isConnected) {
        return;
      }

      onFrameRef.current(data);
    };

    wsRef.current = rws;
  }, [wsPort, log, updateNetContext, pushState, handleSignalMessage, sendSignal]);

  // ── Tailscale WS connection ───────────────────────────────────────────────
  const connectTailscaleWs = useCallback((ip: string) => {
    connectLanWs(ip); // Mismo protocolo, diferente IP
  }, [connectLanWs]);

  // ── WebRTC setup ─────────────────────────────────────────────────────────
  const setupWebRTC = useCallback(async () => {
    if (webrtcRef.current) {
      const state = webrtcRef.current.iceState;
      if (state === 'connected' || state === 'completed' || state === 'checking' || state === 'new') {
        // Ya está activo o intentando conectar, evitar duplicar
        return;
      }
      // Si falló o se desconectó, cerramos la instancia previa limpiando recursos
      log(`Limpiando WebRTC previo inactivo (estado: ${state})`);
      webrtcRef.current.close();
      webrtcRef.current = null;
    }

    log('Iniciando WebRTC (STUN/TURN gratuito)...');

    const rtc = new WebRTCTransport({
      onFrame: (data) => onFrameRef.current?.(data),
      onStateChange: (iceState) => {
        const connected = iceState === 'connected' || iceState === 'completed';
        updateNetContext({ webrtcConnected: connected });

        // Actualizar estados de ICE en métricas
        if (netCtxRef.current.webrtcMetrics) {
          netCtxRef.current.webrtcMetrics.iceState = iceState;
          if (!connected) netCtxRef.current.webrtcMetrics.rtt = -1;
        }
        if (netCtxRef.current.turnMetrics) {
          netCtxRef.current.turnMetrics.iceState = iceState;
          if (!connected) netCtxRef.current.turnMetrics.rtt = -1;
        }

        if (connected) {
          log(`✓ WebRTC P2P conectado (ICE: ${iceState})`);
        } else if (iceState === 'disconnected') {
          log('⚠ WebRTC ICE desconectado — intentando ICE restart...');
        } else if (iceState === 'failed') {
          log('✗ WebRTC ICE falló — fallback a Tailscale/TURN');
          updateNetContext({ webrtcConnected: false });
        }
      },
      onError: (err) => {
        log(`✗ WebRTC error: ${err.message}`);
        updateNetContext({ webrtcConnected: false });
      },
      onLatency: (ms) => {
        const isTurn = sessionRef.current?.transport === 'turn_relay';
        const metricKey = isTurn ? 'turnMetrics' : 'webrtcMetrics';
        
        const prevRtt = netCtxRef.current[metricKey]?.rtt ?? -1;
        const prevJitter = netCtxRef.current[metricKey]?.jitter ?? 0;
        const jitter = prevRtt >= 0 ? Math.round(0.8 * prevJitter + 0.2 * Math.abs(ms - prevRtt)) : 0;
        
        netCtxRef.current[metricKey] = {
          rtt: ms,
          jitter,
          packetLoss: 0.0,
          throughput: isTurn ? 8 : 30,
          iceState: webrtcRef.current?.iceState || 'connected',
          networkType: isOnMobileData() ? 'cellular' : 'wifi',
        };

        const activeTransport = sessionRef.current?.transport;
        if (activeTransport === 'webrtc_p2p' || activeTransport === 'turn_relay') {
          pushState(sessionRef.current!, ms, netCtxRef.current);
        }
      },
      iceServers: FREE_ICE_SERVERS,
    }, true); // isInitiator = true (PC es el que hace offer)

    await rtc.initialize();

    // Enviar offer via WS signal channel
    rtc.onLocalCandidate = (candidate) => {
      sendSignal({ type: 'ice', candidate });
    };

    const offer = await rtc.createOffer();
    sendSignal({ type: 'offer', sdp: offer });

    webrtcRef.current = rtc;
  }, [log, updateNetContext, pushState, sendSignal]);

  // ── Connect best transport ────────────────────────────────────────────────
  const connectBestTransport = useCallback(async (transport: TransportType, ctx: NetworkContext) => {
    // Si el nuevo transporte no es WebRTC ni TURN, y tenemos una PeerConnection activa, la cerramos
    if (transport !== 'webrtc_p2p' && transport !== 'turn_relay') {
      if (webrtcRef.current) {
        log('Cerrando WebRTC por migración a transporte no-WebRTC');
        webrtcRef.current.close();
        webrtcRef.current = null;
      }
    }

    switch (transport) {
      case 'lan_ws':
        if (ctx.lanIp) connectLanWs(ctx.lanIp);
        break;
      case 'webrtc_p2p':
        await setupWebRTC();
        break;
      case 'tailscale_ws':
        if (ctx.tailscaleIp) connectTailscaleWs(ctx.tailscaleIp);
        break;
      case 'turn_relay':
        // TURN relay: WebRTC con candidate de relay
        await setupWebRTC();
        break;
      default:
        log('Sin transporte disponible');
    }
  }, [connectLanWs, connectTailscaleWs, setupWebRTC, log]);

  // ── Refs for stable callbacks (avoids effect rebuilds and re-render loops) ─
  const logRef = useRef(log);
  const pushStateRef = useRef(pushState);
  const updateNetContextRef = useRef(updateNetContext);
  const setupWebRTCRef = useRef(setupWebRTC);
  const connectBestTransportRef = useRef(connectBestTransport);

  useEffect(() => {
    logRef.current = log;
    pushStateRef.current = pushState;
    updateNetContextRef.current = updateNetContext;
    setupWebRTCRef.current = setupWebRTC;
    connectBestTransportRef.current = connectBestTransport;
  }, [log, pushState, updateNetContext, setupWebRTC, connectBestTransport]);

  // ── Effects (Must be defined AFTER variables/callbacks they reference) ───

  // ── Latency measurement loop ──────────────────────────────────────────────
  useEffect(() => {
    if (!enabled) return;

    const measureLoop = async () => {
      const ctx = netCtxRef.current;
      const onMobile = isOnMobileData();
      const networkType = onMobile ? 'cellular' : 'wifi';

      // ── LAN WS latency check
      if (ctx.lanIp) {
        const rtt = await measureLatency(ctx.lanIp, apiPort);
        const prevRtt = ctx.lanMetrics?.rtt ?? -1;
        const prevJitter = ctx.lanMetrics?.jitter ?? 0;
        const jitter = prevRtt >= 0 && rtt >= 0 ? Math.round(0.8 * prevJitter + 0.2 * Math.abs(rtt - prevRtt)) : 0;
        const packetLoss = rtt < 0 ? 1.0 : 0.0;
        
        ctx.lanMetrics = {
          rtt,
          jitter,
          packetLoss,
          throughput: 100,
          networkType,
        };
        ctx.lanLatency = rtt;
      } else {
        ctx.lanLatency = -1;
        ctx.lanMetrics = { rtt: -1, jitter: 0, packetLoss: 1.0, throughput: 0, networkType: 'unknown' };
      }

      // ── Tailscale latency check
      if (ctx.tailscaleIp) {
        const rtt = await measureLatency(ctx.tailscaleIp, apiPort);
        const reachable = rtt >= 0 && rtt < 3000;
        const prevRtt = ctx.tailscaleMetrics?.rtt ?? -1;
        const prevJitter = ctx.tailscaleMetrics?.jitter ?? 0;
        const jitter = prevRtt >= 0 && rtt >= 0 ? Math.round(0.8 * prevJitter + 0.2 * Math.abs(rtt - prevRtt)) : 0;
        const packetLoss = rtt < 0 ? 1.0 : 0.0;

        ctx.tailscaleMetrics = {
          rtt,
          jitter,
          packetLoss,
          throughput: 25,
          networkType,
        };
        ctx.tailscaleReachable = reachable;
      } else {
        ctx.tailscaleReachable = false;
        ctx.tailscaleMetrics = { rtt: -1, jitter: 0, packetLoss: 1.0, throughput: 0, networkType: 'unknown' };
      }

      // ── WebRTC / TURN checks (if not connected, reset metrics or check turnUrl)
      if (!ctx.webrtcConnected) {
        ctx.webrtcMetrics = { rtt: -1, jitter: 0, packetLoss: 1.0, throughput: 0, networkType: 'unknown' };
        ctx.turnMetrics = { rtt: -1, jitter: 0, packetLoss: 1.0, throughput: 0, networkType: 'unknown' };
      }
    };

    latencyTimerRef.current = setInterval(measureLoop, LATENCY_MEASURE_INTERVAL_MS);
    measureLoop(); // Medir inmediatamente

    return () => {
      if (latencyTimerRef.current) clearInterval(latencyTimerRef.current);
    };
  }, [enabled, lanIp, tailscaleIp, apiPort]);

  // ── Heartbeat — transport selection engine ────────────────────────────────
  useEffect(() => {
    if (!enabled) return;

    updateNetContextRef.current({ lanIp, tailscaleIp, onMobileData: isOnMobileData() });

    const heartbeat = async () => {
      if (!mountedRef.current) return;

      const ctx = netCtxRef.current;
      const current = sessionRef.current ? sessionRef.current.transport : 'none';

      // Asegurar que si el TURN relay está en las opciones, se inicialice su métrica base
      if (ctx.turnUrl && (!ctx.turnMetrics || ctx.turnMetrics.rtt === -1)) {
        ctx.turnMetrics = {
          rtt: 80, // Latencia base de un servidor TURN
          jitter: 6,
          packetLoss: 0.0,
          throughput: 8,
          networkType: isOnMobileData() ? 'cellular' : 'wifi',
          iceState: 'new',
        };
      }

      // Si el transporte activo es WebRTC o TURN, pero la conexión física falló o no existe, forzar reconexión
      const isActiveWebRTC = current === 'webrtc_p2p' || current === 'turn_relay';
      const isWebRTCFailed = webrtcRef.current && (
        webrtcRef.current.iceState === 'failed' || 
        webrtcRef.current.iceState === 'closed'
      );
      if (isActiveWebRTC && (!webrtcRef.current || isWebRTCFailed)) {
        logRef.current('Forzando reconexión WebRTC porque la conexión actual falló o no existe');
        if (webrtcRef.current) {
          webrtcRef.current.close();
          webrtcRef.current = null;
        }
        await setupWebRTCRef.current();
      }

      // Motor adaptativo con scoring, histéresis y flapping prevention
      const { bestTransport: best, newHistory } = selectConnection(ctx, current, historyRef.current);
      historyRef.current = newHistory;

      // No hay sesión aún — crear la primera
      if (!sessionRef.current) {
        const session = createSession(best);
        sessionRef.current = session;
        logRef.current(`Sesión iniciada: ${getTransportLabel(best)}`);

        // Conectar el transporte inicial
        await connectBestTransportRef.current(best, ctx);
        
        const currentLatency = best === 'lan_ws' ? ctx.lanLatency : (best === 'tailscale_ws' ? (ctx.tailscaleMetrics?.rtt ?? -1) : 40);
        pushStateRef.current(session, currentLatency, ctx);
        return;
      }

      // Evaluar si hay mejor transporte disponible
      if (best !== current) {
        logRef.current(`Migrando ${getTransportLabel(current)} → ${getTransportLabel(best)}`);
        const migrated = migrateSession(sessionRef.current, best);
        sessionRef.current = migrated;

        // Migrar sin reiniciar encoder
        await connectBestTransportRef.current(best, ctx);
        
        const currentLatency = best === 'lan_ws' ? ctx.lanLatency : (best === 'tailscale_ws' ? (ctx.tailscaleMetrics?.rtt ?? -1) : 40);
        pushStateRef.current(migrated, currentLatency, ctx);
      }
    };

    heartbeatRef.current = setInterval(heartbeat, HEARTBEAT_INTERVAL_MS);
    heartbeat(); // Evaluar inmediatamente

    return () => {
      if (heartbeatRef.current) clearInterval(heartbeatRef.current);
    };
  }, [enabled, lanIp, tailscaleIp]);

  // ── Network change detection (inmediata) ──────────────────────────────────
  useEffect(() => {
    if (!enabled) return;

    const unsubscribe = NetworkMonitor.subscribe((event) => {
      if (!mountedRef.current) return;

      if (event.type === 'offline') {
        updateNetContextRef.current({ lanLatency: -1, webrtcConnected: false, tailscaleReachable: false });
        logRef.current('Red desconectada (offline)');
        return;
      }

      if (event.type === 'online' || event.type === 'quality-change') {
        const onMobile = NetworkMonitor.isMobile;
        updateNetContextRef.current({ onMobileData: onMobile });
        logRef.current(`Red cambiada (NetworkMonitor) → ${onMobile ? '4G/5G' : 'WiFi/Ethernet'}`);

        // Forzar re-evaluación inmediata del transporte
        if (sessionRef.current && netCtxRef.current) {
          const ctx = netCtxRef.current;
          const current = sessionRef.current.transport;
          const { bestTransport: best, newHistory } = selectConnection(ctx, current, historyRef.current);
          historyRef.current = newHistory;

          if (best !== current) {
            const migrated = migrateSession(sessionRef.current, best);
            sessionRef.current = migrated;
            connectBestTransportRef.current(best, ctx);
            const currentLatency = best === 'lan_ws' ? ctx.lanLatency : (best === 'tailscale_ws' ? (ctx.tailscaleMetrics?.rtt ?? -1) : 40);
            pushStateRef.current(migrated, currentLatency, ctx);
          }
        }
      }
    });

    return () => {
      unsubscribe();
    };
  }, [enabled]);

  // ── Cleanup ───────────────────────────────────────────────────────────────
  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
      wsRef.current?.close();
      webrtcRef.current?.close();
      if (heartbeatRef.current) clearInterval(heartbeatRef.current);
      if (latencyTimerRef.current) clearInterval(latencyTimerRef.current);
    };
  }, []);

  // ── Public API ────────────────────────────────────────────────────────────
  const send = useCallback((data: string | ArrayBuffer) => {
    // Priorizar canal activo
    const transport = sessionRef.current?.transport;
    if (transport === 'webrtc_p2p' && webrtcRef.current?.isConnected) {
      if (data instanceof ArrayBuffer) {
        webrtcRef.current.send(data);
        return true;
      }
    }
    if (wsRef.current) {
      wsRef.current.send(data);
      return true;
    }
    return false;
  }, []);

  const forceTransport = useCallback(async (transport: TransportType) => {
    if (!sessionRef.current) return;
    logRef.current(`Forzando transporte: ${getTransportLabel(transport)}`);
    const migrated = migrateSession(sessionRef.current, transport);
    sessionRef.current = migrated;
    await connectBestTransportRef.current(transport, netCtxRef.current);
    const currentLatency = transport === 'lan_ws' ? netCtxRef.current.lanLatency : (transport === 'tailscale_ws' ? (netCtxRef.current.tailscaleMetrics?.rtt ?? -1) : 40);
    pushStateRef.current(migrated, currentLatency, netCtxRef.current);
  }, []);

  return {
    state,
    send,
    forceTransport,
    wsRef,
  };
}
