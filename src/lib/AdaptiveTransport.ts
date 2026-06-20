/**
 * AdaptiveTransport.ts — Motor de selección y migración de transporte
 * 
 * Arquitectura multi-canal:
 *   LAN WS → WebRTC P2P → Tailscale WS → TURN Relay
 * 
 * REGLA CRÍTICA: El encoder NUNCA se reinicia.
 * Solo se migra el canal de datos entre transportes.
 */

export type TransportType = 'lan_ws' | 'webrtc_p2p' | 'tailscale_ws' | 'turn_relay' | 'none';

export type VideoQuality = '4K' | '1080p' | '720p' | '480p';

export interface ChannelMetrics {
  rtt: number;          // ms, latencia de ida y vuelta (-1 si inaccesible)
  jitter: number;       // ms, variación de latencia
  packetLoss: number;   // 0.0 - 1.0 (ej. 0.05 = 5% pérdida)
  throughput: number;   // Mbps estimado
  iceState?: string;    // Estado de la conexión WebRTC ICE si aplica
  networkType: 'wifi' | 'ethernet' | 'cellular' | 'unknown';
}

export interface NetworkContext {
  lanIp?: string;
  tailscaleIp?: string;
  turnUrl?: string;
  webrtcConnected: boolean;
  tailscaleReachable: boolean;
  lanLatency: number;        // ms, -1 si no probado
  onMobileData: boolean;

  // Métricas avanzadas opcionales para cada canal
  lanMetrics?: ChannelMetrics;
  webrtcMetrics?: ChannelMetrics;
  tailscaleMetrics?: ChannelMetrics;
  turnMetrics?: ChannelMetrics;
}

export interface TransportSession {
  id: string;
  transport: TransportType;
  quality: VideoQuality;
  startedAt: number;
  migrationCount: number;
}

export interface TransportHistory {
  lastSwitchTime: number;
  switchesInLastMinute: number[];
  penalizedUntil: Record<TransportType, number>;
  backoffLevels: Record<TransportType, number>;
}

// Calidad recomendada por transporte
export const QUALITY_MAP: Record<TransportType, VideoQuality> = {
  lan_ws:       '4K',
  webrtc_p2p:   '1080p',
  tailscale_ws: '1080p',
  turn_relay:   '720p',
  none:         '480p',
};

// Latencia objetivo por transporte (ms)
export const LATENCY_TARGET: Record<TransportType, number> = {
  lan_ws:       5,
  webrtc_p2p:   40,
  tailscale_ws: 30,
  turn_relay:   80,
  none:         999,
};

// Constantes del motor adaptativo matemático
export const HYSTERESIS_MARGIN = 15;            // Margen para cambio de canal
export const MIN_TIME_BETWEEN_SWITCHES_MS = 5000; // Cooldown mínimo
export const MAX_SWITCHES_PER_MINUTE = 4;        // Flapping prevention
export const BASE_PENALTY_MS = 5000;             // Penalización base

export const initialHistory: TransportHistory = {
  lastSwitchTime: 0,
  switchesInLastMinute: [],
  penalizedUntil: {
    lan_ws: 0,
    webrtc_p2p: 0,
    tailscale_ws: 0,
    turn_relay: 0,
    none: 0,
  },
  backoffLevels: {
    lan_ws: 0,
    webrtc_p2p: 0,
    tailscale_ws: 0,
    turn_relay: 0,
    none: 0,
  },
};

// Auxiliares matemáticos
function sigmoidScore(value: number, mid: number, scale: number): number {
  if (value < 0) return 0;
  return 100 / (1 + Math.exp((value - mid) / scale));
}

function logScore(value: number, minVal: number, maxVal: number): number {
  if (value <= minVal) return 0;
  if (value >= maxVal) return 100;
  return (Math.log(value / minVal) / Math.log(maxVal / minVal)) * 100;
}

/**
 * Función de Scoring Matemática:
 * Normaliza RTT, Jitter, Pérdida de Paquetes y Throughput para obtener un score 0-100.
 */
export function calculateChannelScore(
  transport: TransportType,
  metrics: ChannelMetrics | undefined
): number {
  if (!metrics) return 0;

  // Si el canal tiene estado ICE y está desconectado/fallado
  if (metrics.iceState && ['failed', 'disconnected', 'closed'].includes(metrics.iceState)) {
    return 0;
  }

  // Si no hay ping o es inaccesible
  if (metrics.rtt < 0) {
    return 0;
  }

  // Normalizaciones
  const rttScore = sigmoidScore(metrics.rtt, 70, 20);      // 70ms es el punto de inflexión
  const jitterScore = sigmoidScore(metrics.jitter, 12, 4); // 12ms es el punto de inflexión
  const lossScore = Math.max(0, 100 * Math.pow(1 - metrics.packetLoss, 2.5)); // Penaliza fuerte la pérdida
  const throughputScore = logScore(metrics.throughput, 1, 100); // Rango 1 - 100 Mbps

  // Pesos
  const W_RTT = 0.40;
  const W_LOSS = 0.35;
  const W_JITTER = 0.15;
  const W_THROUGHPUT = 0.10;

  let score = (rttScore * W_RTT) + (lossScore * W_LOSS) + (jitterScore * W_JITTER) + (throughputScore * W_THROUGHPUT);

  // Bonificaciones/Penalizaciones por tipo de red
  if (metrics.networkType === 'ethernet') {
    score += 5;
  } else if (metrics.networkType === 'wifi') {
    score += 2;
  } else if (metrics.networkType === 'cellular') {
    score -= 10;
  }

  // Penalizaciones estructurales del transporte
  if (transport === 'turn_relay') {
    score -= 15; // TURN relay tiene overhead de servidor intermedio
  } else if (transport === 'tailscale_ws') {
    score -= 5;  // Encriptación extra
  }

  return Math.max(0, Math.min(100, score));
}

/**
 * Penaliza un transporte inestable aplicando backoff exponencial.
 */
export function penalizeTransport(
  history: TransportHistory,
  transport: TransportType
): TransportHistory {
  const level = history.backoffLevels[transport];
  const penaltyDuration = BASE_PENALTY_MS * Math.pow(2, level);
  const until = Date.now() + penaltyDuration;

  console.warn(`[FlappingPrevention] Penalizando ${transport} por ${penaltyDuration}ms (nivel ${level})`);

  return {
    ...history,
    penalizedUntil: {
      ...history.penalizedUntil,
      [transport]: until,
    },
    backoffLevels: {
      ...history.backoffLevels,
      [transport]: Math.min(6, level + 1), // Máximo nivel 6 (320s)
    },
  };
}

/**
 * Resetea el nivel de backoff de un transporte estable.
 */
export function resetTransportBackoff(
  history: TransportHistory,
  transport: TransportType
): TransportHistory {
  if (history.backoffLevels[transport] === 0) return history;
  return {
    ...history,
    backoffLevels: {
      ...history.backoffLevels,
      [transport]: 0,
    },
  };
}

/**
 * Registra un cambio en el historial.
 */
export function registerSwitch(
  history: TransportHistory,
  now = Date.now()
): TransportHistory {
  const oneMinuteAgo = now - 60000;
  const filtered = history.switchesInLastMinute.filter(t => t > oneMinuteAgo);
  return {
    ...history,
    lastSwitchTime: now,
    switchesInLastMinute: [...filtered, now],
  };
}

/**
 * Comprueba si la tasa de cambios está limitada para prevenir oscilaciones.
 */
export function isSwitchRateLimited(history: TransportHistory, now = Date.now()): boolean {
  if (now - history.lastSwitchTime < MIN_TIME_BETWEEN_SWITCHES_MS) {
    return true;
  }
  const oneMinuteAgo = now - 60000;
  const count = history.switchesInLastMinute.filter(t => t > oneMinuteAgo).length;
  if (count >= MAX_SWITCHES_PER_MINUTE) {
    return true;
  }
  return false;
}

/**
 * Selecciona el mejor transporte aplicando histéresis y protección de flapping.
 */
export function selectConnection(
  ctx: NetworkContext,
  currentTransport: TransportType,
  history: TransportHistory,
  now = Date.now()
): { bestTransport: TransportType; newHistory: TransportHistory } {
  let updatedHistory = { ...history };

  // 1. Calcular scores
  const scores: Record<TransportType, number> = {
    lan_ws: calculateChannelScore('lan_ws', ctx.lanMetrics),
    webrtc_p2p: calculateChannelScore('webrtc_p2p', ctx.webrtcMetrics),
    tailscale_ws: calculateChannelScore('tailscale_ws', ctx.tailscaleMetrics),
    turn_relay: calculateChannelScore('turn_relay', ctx.turnMetrics),
    none: 0,
  };

  // 2. Transportes viables (no penalizados y con score > 0)
  const candidateTransports: TransportType[] = ['lan_ws', 'webrtc_p2p', 'tailscale_ws', 'turn_relay'];
  const viableTransports = candidateTransports.filter(t => {
    if (scores[t] <= 0) return false;
    if (updatedHistory.penalizedUntil[t] > now) return false;
    return true;
  });

  // Si no hay viables, activar Safe Mode fallback
  if (viableTransports.length === 0) {
    let fallback: TransportType = 'none';
    if (ctx.turnUrl && updatedHistory.penalizedUntil['turn_relay'] <= now) {
      fallback = 'turn_relay'; // Safe Mode: Relay a baja resolución
    } else if (ctx.lanIp) {
      fallback = 'lan_ws';
    } else if (ctx.tailscaleIp) {
      fallback = 'tailscale_ws';
    }
    return { bestTransport: fallback, newHistory: updatedHistory };
  }

  // Encontrar el mejor transporte viable
  let bestViable: TransportType = 'none';
  let bestScore = -1;
  for (const t of viableTransports) {
    if (scores[t] > bestScore) {
      bestScore = scores[t];
      bestViable = t;
    }
  }

  // Si coincide con el actual, mantenerlo y resetear su backoff
  if (bestViable === currentTransport) {
    updatedHistory = resetTransportBackoff(updatedHistory, currentTransport);
    return { bestTransport: currentTransport, newHistory: updatedHistory };
  }

  // Si el actual ya no es viable, migrar inmediatamente pero penalizarlo
  const currentIsViable = viableTransports.includes(currentTransport) && scores[currentTransport] > 0;
  if (!currentIsViable) {
    if (currentTransport !== 'none') {
      updatedHistory = penalizeTransport(updatedHistory, currentTransport);
    }
    updatedHistory = registerSwitch(updatedHistory, now);
    updatedHistory = resetTransportBackoff(updatedHistory, bestViable);
    return { bestTransport: bestViable, newHistory: updatedHistory };
  }

  // Comprobar rate limit por flapping
  if (isSwitchRateLimited(updatedHistory, now)) {
    return { bestTransport: currentTransport, newHistory: updatedHistory };
  }

  // Histéresis: el nuevo debe ser sustancialmente mejor
  const currentScore = scores[currentTransport];
  if (bestScore > currentScore + HYSTERESIS_MARGIN) {
    console.info(`[Hysteresis] Migrando: ${currentTransport} (${currentScore.toFixed(0)}) -> ${bestViable} (${bestScore.toFixed(0)})`);
    updatedHistory = registerSwitch(updatedHistory, now);
    updatedHistory = resetTransportBackoff(updatedHistory, bestViable);
    return { bestTransport: bestViable, newHistory: updatedHistory };
  }

  return { bestTransport: currentTransport, newHistory: updatedHistory };
}

/**
 * Función legacy selectTransport para compatibilidad hacia atrás.
 */
export function selectTransport(ctx: NetworkContext): TransportType {
  if (!ctx.onMobileData && ctx.lanIp && ctx.lanLatency >= 0 && ctx.lanLatency < 50) {
    return 'lan_ws';
  }
  if (ctx.webrtcConnected) {
    return 'webrtc_p2p';
  }
  if (ctx.tailscaleIp && ctx.tailscaleReachable) {
    return 'tailscale_ws';
  }
  if (ctx.turnUrl) {
    return 'turn_relay';
  }
  return 'none';
}

/**
 * Migra el transporte de una sesión activa SIN reiniciar el encoder.
 */
export function migrateSession(
  session: TransportSession,
  newTransport: TransportType,
): TransportSession {
  if (session.transport === newTransport) return session;

  console.info(
    `[AdaptiveTransport] Migrando ${session.transport} → ${newTransport} ` +
    `(sesión ${session.id}, migración #${session.migrationCount + 1})`
  );

  return {
    ...session,
    transport: newTransport,
    quality: QUALITY_MAP[newTransport],
    migrationCount: session.migrationCount + 1,
  };
}

/**
 * Crea una nueva sesión de transporte con un ID único.
 */
export function createSession(initialTransport: TransportType): TransportSession {
  return {
    id: `session-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`,
    transport: initialTransport,
    quality: QUALITY_MAP[initialTransport],
    startedAt: Date.now(),
    migrationCount: 0,
  };
}

/**
 * Mide la latencia hacia una IP dada mediante fetch HEAD.
 */
export async function measureLatency(ip: string, port = 3001, timeout = 3000): Promise<number> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeout);
  const start = performance.now();
  try {
    await fetch(`http://${ip}:${port}/api/status`, {
      method: 'HEAD',
      signal: controller.signal,
      cache: 'no-store',
    });
    return Math.round(performance.now() - start);
  } catch {
    return -1;
  } finally {
    clearTimeout(timer);
  }
}

/**
 * Comprueba si la IP de Tailscale es alcanzable.
 */
export async function checkTailscaleReachability(tailscaleIp: string, port = 3001): Promise<boolean> {
  const latency = await measureLatency(tailscaleIp, port, 3000);
  return latency >= 0 && latency < 3000;
}

/**
 * Detecta si el dispositivo está en datos móviles.
 */
export function isOnMobileData(): boolean {
  if (typeof navigator === 'undefined') return false;
  const conn = (navigator as any).connection;
  if (!conn) return false;
  return conn.type === 'cellular';
}

/**
 * Retorna el label legible del transporte activo.
 */
export function getTransportLabel(t: TransportType): string {
  const labels: Record<TransportType, string> = {
    lan_ws:       '⚡ LAN Directa',
    webrtc_p2p:   '🌐 WebRTC P2P',
    tailscale_ws: '🔒 Tailscale VPN',
    turn_relay:   '☁️ TURN Relay',
    none:         '❌ Sin conexión',
  };
  return labels[t] ?? t;
}

/**
 * Retorna el color de badge según el transporte.
 */
export function getTransportColor(t: TransportType): string {
  const colors: Record<TransportType, string> = {
    lan_ws:       'emerald',
    webrtc_p2p:   'cyan',
    tailscale_ws: 'violet',
    turn_relay:   'amber',
    none:         'red',
  };
  return colors[t] ?? 'slate';
}
