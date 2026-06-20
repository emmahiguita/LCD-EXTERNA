/**
 * WebRTCTransport.ts — Capa WebRTC con STUN/TURN gratuito
 * 
 * Arquitectura:
 *   - STUN gratuito: Google stun.l.google.com:19302 + Cloudflare stun.cloudflare.com:3478
 *   - TURN gratuito para pruebas: Open Relay (openrelay.metered.ca) — 50GB/mes gratis
 *   - Señalización: via WebSocket existente (electron/main.js puerto 3002)
 * 
 * Flujo:
 *   1. Caller crea offer (SDP)
 *   2. Señalización intercambia SDP via WS
 *   3. ICE gathering selecciona mejor ruta automáticamente
 *   4. DataChannel transmite H.264 binario → JMuxer
 */

// STUN/TURN servers — 100% gratuitos para desarrollo y uso personal
export const FREE_ICE_SERVERS: RTCIceServer[] = [
  // STUN gratuito — Google (el más confiable)
  { urls: 'stun:stun.l.google.com:19302' },
  { urls: 'stun:stun1.l.google.com:19302' },
  // STUN gratuito — Cloudflare
  { urls: 'stun:stun.cloudflare.com:3478' },
  // TURN gratuito — Open Relay (Metered.ca) — 50GB/mes
  // Funciona cuando STUN falla (NAT simétrico, firewalls estrictos)
  {
    urls: [
      'turn:openrelay.metered.ca:80',
      'turn:openrelay.metered.ca:443',
      'turn:openrelay.metered.ca:443?transport=tcp',
    ],
    username: 'openrelayproject',
    credential: 'openrelayproject',
  },
];

export interface WebRTCConfig {
  onFrame: (data: ArrayBuffer) => void;    // Callback para frames H.264
  onStateChange: (state: RTCIceConnectionState) => void;
  onError: (err: Error) => void;
  onLatency?: (ms: number) => void;
  iceServers?: RTCIceServer[];
}

export type SignalMessage =
  | { type: 'offer'; sdp: RTCSessionDescriptionInit }
  | { type: 'answer'; sdp: RTCSessionDescriptionInit }
  | { type: 'ice'; candidate: RTCIceCandidateInit };

/**
 * WebRTC transport que recibe H.264 via DataChannel y lo pasa a JMuxer.
 * Reutiliza el WebSocket existente como canal de señalización.
 */
export class WebRTCTransport {
  private pc: RTCPeerConnection | null = null;
  private dataChannel: RTCDataChannel | null = null;
  private config: WebRTCConfig;
  private pingInterval: ReturnType<typeof setInterval> | null = null;
  private pingStart: number = 0;
  private isInitiator: boolean;
  private pendingCandidates: RTCIceCandidateInit[] = [];
  private remoteDescSet = false;

  constructor(config: WebRTCConfig, isInitiator = true) {
    this.config = config;
    this.isInitiator = isInitiator;
  }

  /**
   * Inicializa la RTCPeerConnection con STUN/TURN gratuito.
   */
  async initialize(): Promise<void> {
    const iceServers = this.config.iceServers ?? FREE_ICE_SERVERS;

    this.pc = new RTCPeerConnection({
      iceServers,
      iceCandidatePoolSize: 10,  // Pre-gather candidates para reducir latencia ICE
      bundlePolicy: 'max-bundle',
      rtcpMuxPolicy: 'require',
    });

    // ── ICE state tracking ─────────────────────────────────────────────────
    this.pc.oniceconnectionstatechange = () => {
      const state = this.pc?.iceConnectionState ?? 'closed';
      console.info(`[WebRTC] ICE state: ${state}`);
      this.config.onStateChange(state as RTCIceConnectionState);

      if (state === 'failed') {
        // ICE restart: intenta reconectar sin cerrar la PeerConnection
        console.warn('[WebRTC] ICE failed — attempting restart...');
        this.pc?.restartIce();
      }
    };

    // ── ICE candidates ─────────────────────────────────────────────────────
    this.pc.onicecandidate = (event) => {
      if (event.candidate) {
        // El SignalingManager en page.tsx envía esto al peer via WS
        this.onLocalCandidate?.(event.candidate.toJSON());
      }
    };

    // ── DataChannel setup (lado initiator) ────────────────────────────────
    if (this.isInitiator) {
      this.dataChannel = this.pc.createDataChannel('h264-stream', {
        ordered: false,        // Sin order — priorizar velocidad sobre fiabilidad
        maxRetransmits: 0,     // UDP-like: no reenvíos (igual que Moonlight)
      });
      this.dataChannel.binaryType = 'arraybuffer';
      this.setupDataChannel(this.dataChannel);
    } else {
      // Lado receptor: espera el DataChannel del initiator
      this.pc.ondatachannel = (event) => {
        this.dataChannel = event.channel;
        this.dataChannel.binaryType = 'arraybuffer';
        this.setupDataChannel(this.dataChannel);
      };
    }
  }

  // Callback público para candidatos ICE locales
  onLocalCandidate?: (candidate: RTCIceCandidateInit) => void;

  private setupDataChannel(dc: RTCDataChannel) {
    dc.onopen = () => {
      console.info('[WebRTC] DataChannel open — starting heartbeat');
      this.startPingPong();
    };

    dc.onclose = () => {
      console.warn('[WebRTC] DataChannel closed');
      this.stopPingPong();
    };

    dc.onerror = (err) => {
      console.error('[WebRTC] DataChannel error:', err);
      this.config.onError(new Error('DataChannel error'));
    };

    dc.onmessage = (event) => {
      if (event.data instanceof ArrayBuffer) {
        const view = new DataView(event.data);
        // Detectar ping/pong (primer byte 0xFF = control, total 9 bytes)
        if (view.byteLength === 9 && view.getUint8(0) === 0xFF) {
          const ts = view.getFloat64(1);
          if (!isNaN(ts) && ts > 0) {
            const latency = Math.round(performance.now() - ts);
            this.config.onLatency?.(latency);
          }
          return;
        }
        // Frame H.264 — pasar directamente a JMuxer
        this.config.onFrame(event.data);
      }
    };
  }

  // ── Signaling ────────────────────────────────────────────────────────────

  /**
   * Crea y retorna un SDP offer (llamar solo en el initiator).
   */
  async createOffer(): Promise<RTCSessionDescriptionInit> {
    if (!this.pc) throw new Error('Not initialized');
    const offer = await this.pc.createOffer({
      offerToReceiveAudio: false,
      offerToReceiveVideo: false,
    });
    await this.pc.setLocalDescription(offer);
    return offer;
  }

  /**
   * Procesa un SDP offer recibido y retorna un SDP answer.
   */
  async handleOffer(offer: RTCSessionDescriptionInit): Promise<RTCSessionDescriptionInit> {
    if (!this.pc) throw new Error('Not initialized');
    await this.pc.setRemoteDescription(offer);
    this.remoteDescSet = true;
    // Añadir candidatos pendientes que llegaron antes del answer
    for (const c of this.pendingCandidates) {
      await this.pc.addIceCandidate(c).catch(() => {});
    }
    this.pendingCandidates = [];
    const answer = await this.pc.createAnswer();
    await this.pc.setLocalDescription(answer);
    return answer;
  }

  /**
   * Procesa el SDP answer recibido del peer.
   */
  async handleAnswer(answer: RTCSessionDescriptionInit): Promise<void> {
    if (!this.pc) throw new Error('Not initialized');
    await this.pc.setRemoteDescription(answer);
    this.remoteDescSet = true;
    for (const c of this.pendingCandidates) {
      await this.pc.addIceCandidate(c).catch(() => {});
    }
    this.pendingCandidates = [];
  }

  /**
   * Añade un candidato ICE remoto.
   */
  async addIceCandidate(candidate: RTCIceCandidateInit): Promise<void> {
    if (!this.pc) return;
    if (!this.remoteDescSet) {
      // Guardar hasta que el remote desc esté listo
      this.pendingCandidates.push(candidate);
      return;
    }
    await this.pc.addIceCandidate(candidate).catch((e) => {
      console.warn('[WebRTC] addIceCandidate error:', e);
    });
  }

  // ── Latency ping/pong ────────────────────────────────────────────────────

  private startPingPong() {
    this.pingInterval = setInterval(() => {
      if (this.dataChannel?.readyState === 'open') {
        const buf = new ArrayBuffer(9);
        const view = new DataView(buf);
        view.setUint8(0, 0xFF); // Firma de control
        view.setFloat64(1, performance.now());
        this.dataChannel.send(buf);
      }
    }, 2000);
  }

  private stopPingPong() {
    if (this.pingInterval) {
      clearInterval(this.pingInterval);
      this.pingInterval = null;
    }
  }

  /**
   * Envía datos H.264 al peer via DataChannel.
   */
  send(data: ArrayBuffer): void {
    if (this.dataChannel?.readyState === 'open') {
      this.dataChannel.send(data);
    }
  }

  /**
   * Cierra la conexión completamente.
   */
  close(): void {
    this.stopPingPong();
    this.dataChannel?.close();
    this.pc?.close();
    this.dataChannel = null;
    this.pc = null;
  }

  get isConnected(): boolean {
    const state = this.pc?.iceConnectionState;
    return state === 'connected' || state === 'completed';
  }

  get iceState(): RTCIceConnectionState | 'closed' {
    return this.pc?.iceConnectionState ?? 'closed';
  }
}
