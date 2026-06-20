'use client';

import { useEffect, useRef, useState, useCallback } from 'react';
import {
  Monitor, Smartphone, KeyRound, Wifi, WifiOff, Settings2,
  RefreshCw, CheckCircle2, XCircle, AlertCircle,
  ChevronRight, Loader2, Maximize2, RotateCcw, ZoomIn, ZoomOut,
  Network, Shield, Radio, Code, Hand, Terminal, Keyboard as KeyboardIcon,
  LayoutDashboard, Copy, Eye, EyeOff, Cpu, HardDrive, Battery, Thermometer,
  BatteryCharging, Info
} from 'lucide-react';
import { OptimizedKeyboard } from '@/components/keyboard';
import type { ModifierState, KeyboardMode } from '@/components/keyboard';
import { useConnectionSettings, useDeviceRegistry, type ConnectionMode } from '@/hooks';
import { isMobileNetwork } from '@/lib/ConnectionResolver';
import { useAdaptiveConnection } from '@/hooks/useAdaptiveConnection';
import { AdbService } from '@/lib/AdbService';

// ─────────────────────────────────────────────────────────────────────────────
// Types
// ─────────────────────────────────────────────────────────────────────────────
type StreamState = 'disconnected' | 'connecting' | 'connected' | 'reconnecting';
type ActivePanel = 'dashboard' | 'devices' | 'stream' | 'settings';

type DeviceData = {
  connected: boolean;
  connectionType?: string;
  model?: string;
  androidVersion?: string;
  serial?: string;
  resolution?: string;
  ram?: string;
  storage?: string;
  battery?: number;
  isCharging?: boolean;
  temperature?: string;
  state?: string;
  ip?: string;
};


// ─────────────────────────────────────────────────────────────────────────────
// Main Dashboard
// ─────────────────────────────────────────────────────────────────────────────
export default function SmartDisplayDashboard() {
  // Device & Connection
  const [device, setDevice] = useState<DeviceData | null>(null);
  const [devicesList, setDevicesList] = useState<{ serial: string; model: string; connectionType: string; state: string }[]>([]);
  const [activeSerial, setActiveSerial] = useState<string | null>(null);
  const [deviceIP, setDeviceIP] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  // Stream
  const [streamState, setStreamState]     = useState<StreamState>('disconnected');
  const [streamLatency, setStreamLatency] = useState(-1);
  const [streamActive, setStreamActive]   = useState(false);
  const [recording, setRecording]         = useState(false);

  // Electron & API status
  const [electronStatus, setElectronStatus] = useState({ clients: 0, stream: false });
  const [autoStart, setAutoStart]           = useState(false);
  const [minToTray, setMinToTray]           = useState(true);

  // Pairing
  const [pairingPin, setPairingPin]       = useState('');
  const [pairingStatus, setPairingStatus] = useState<'idle'|'sending'|'ok'|'error'>('idle');
  const [pairingMsg, setPairingMsg]       = useState('');

  // UI
  const [activePanel, setActivePanel]     = useState<ActivePanel>('dashboard');
  const [viewZoom, setViewZoom]           = useState(100);
  const [viewPan, setViewPan]             = useState({ x: 0, y: 0 });
  const [isPanning, setIsPanning]         = useState(false);
  const [log, setLog]                     = useState<string[]>([]);
  const [dark, setDark]                   = useState(true);
  const [touchpadMode, setTouchpadMode]   = useState(false);
  const [sessionToken, setSessionToken]   = useState<string>('');
  const [showToken, setShowToken]         = useState(false);

  // Keyboard
  const [kbOpen, setKbOpen]               = useState(false);
  const [kbMode, setKbMode]               = useState<KeyboardMode>('text');
  const [kbShift, setKbShift]             = useState(false);
  const [kbInput, setKbInput]             = useState('');
  const [kbFlash, setKbFlash]             = useState<string|null>(null);
  const [kbMods, setKbMods]               = useState<ModifierState>({ ctrl:false, alt:false, shift:false, win:false });

  // Refs
  const videoRef    = useRef<HTMLVideoElement>(null);
  const jmuxerRef   = useRef<any>(null);
  const panStart    = useRef({ x: 0, y: 0 });
  const panOrigin   = useRef({ x: 0, y: 0 });

  // Hooks
  const connSettings = useConnectionSettings();
  const deviceRegistry = useDeviceRegistry();

  const { state: adaptiveState, send: adaptiveSend, forceTransport } = useAdaptiveConnection({
    lanIp: connSettings.lanIp || undefined,
    tailscaleIp: connSettings.config.tailscaleIp || undefined,
    token: sessionToken || undefined,
    onFrame: (data) => {
      if (jmuxerRef.current) {
        jmuxerRef.current.feed({ video: new Uint8Array(data) });
      }
    },
    onLog: (msg) => {
      addLog(msg);
    },
    enabled: true,
  });

  // Sync adaptive connection state to local states for compatibility
  useEffect(() => {
    const isStreamActive = adaptiveState.connected || electronStatus.stream;
    setStreamActive(isStreamActive);
    setStreamState(isStreamActive ? 'connected' : 'disconnected');
    setStreamLatency(adaptiveState.latency);
  }, [adaptiveState.connected, adaptiveState.latency, electronStatus.stream]);

  // Sync device registry when connected
  useEffect(() => {
    if (adaptiveState.connected) {
      const params = new URLSearchParams(window.location.search);
      const token = params.get('token') || '';
      const hostname = window.location.hostname || 'smartdisplay-pc';
      const deviceUuid = token || hostname;

      let mode: ConnectionMode = 'lan';
      if (adaptiveState.transport === 'tailscale_ws') {
        mode = 'tailscale';
      } else if (adaptiveState.transport === 'webrtc_p2p' || adaptiveState.transport === 'turn_relay') {
        mode = 'custom';
      }

      deviceRegistry.addOrUpdateDevice({
        id: deviceUuid,
        uuid: deviceUuid,
        hostname,
        deviceName: hostname,
        connectionMode: mode,
        lanIp: hostname !== 'localhost' && hostname !== '127.0.0.1' ? hostname : '',
        tailscaleIp: connSettings.config.tailscaleIp,
      });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [adaptiveState.connected, adaptiveState.transport, connSettings.config.tailscaleIp]);


  // Handle JMuxer initialization when video panel is mounted
  useEffect(() => {
    let active = true;
    if (activePanel !== 'stream') return;

    import('jmuxer').then(({ default: JMuxer }) => {
      if (!active || !videoRef.current) return;
      jmuxerRef.current = new JMuxer({
        node: videoRef.current,
        mode: 'video',
        flushingTime: 10,
        fps: 60,
        debug: false,
      });
      addLog('✓ JMuxer inicializado');
    }).catch(() => addLog('✗ JMuxer no disponible'));

    return () => {
      active = false;
      if (jmuxerRef.current) {
        try {
          jmuxerRef.current.destroy();
        } catch (_) {}
        jmuxerRef.current = null;
      }
    };
  }, [activePanel]);

  // Fetch Token and Electron settings / status
  useEffect(() => {
    const initData = async () => {
      // 1. Get Token
      const params = new URLSearchParams(window.location.search);
      const urlToken = params.get('token');
      if (urlToken) {
        setSessionToken(urlToken);
      } else if (typeof window !== 'undefined' && (window as any).api) {
        try {
          const token = await (window as any).api.getToken();
          if (token) setSessionToken(token);
        } catch (_) {}
      } else {
        try {
          const res = await fetch('http://localhost:3001/api/connect');
          const data = await res.json();
          if (data.token) setSessionToken(data.token);
        } catch (_) {}
      }

      // 2. Get Electron settings
      if (typeof window !== 'undefined' && (window as any).api) {
        try {
          const settings = await (window as any).api.getSettings();
          setAutoStart(settings.autoStart);
          setMinToTray(settings.minToTray);
        } catch (_) {}
      }
    };
    initData();
  }, []);

  // Poll Electron Status
  useEffect(() => {
    const fetchStatus = async () => {
      if (typeof window !== 'undefined' && (window as any).api) {
        try {
          const status = await (window as any).api.getStatus();
          setElectronStatus({ clients: status.clients, stream: status.stream });
          return;
        } catch (_) {}
      }
      try {
        const res = await fetch('http://localhost:3001/api/status');
        const data = await res.json();
        setElectronStatus({ clients: data.clients, stream: data.stream });
      } catch (_) {}
    };

    fetchStatus();
    const interval = setInterval(fetchStatus, 3000);
    return () => clearInterval(interval);
  }, []);

  // Subscribe to Electron app-logs
  useEffect(() => {
    if (typeof window !== 'undefined' && (window as any).api) {
      try {
        const unsubscribe = (window as any).api.onLog((line: string) => {
          setLog(prev => [line, ...prev].slice(0, 100));
        });
        return () => unsubscribe();
      } catch (_) {}
    }
  }, []);

  // ── Helpers ────────────────────────────────────────────────────────────────
  const addLog = useCallback((msg: string) => {
    if (/^\[?\d{2}:\d{2}:\d{2}\]?/.test(msg)) {
      setLog(prev => [msg, ...prev].slice(0, 100));
    } else {
      const t = new Date().toLocaleTimeString('es', { hour12: false });
      setLog(prev => [`[${t}] ${msg}`, ...prev].slice(0, 100));
    }
  }, []);

  const copyLogsToClipboard = useCallback((text: string) => {
    if (navigator.clipboard && window.isSecureContext) {
      navigator.clipboard.writeText(text)
        .then(() => addLog('✓ Logs copiados al portapapeles'))
        .catch(() => fallbackCopyLogs(text));
    } else {
      fallbackCopyLogs(text);
    }
  }, [addLog]);

  const fallbackCopyLogs = useCallback((text: string) => {
    const textArea = document.createElement("textarea");
    textArea.value = text;
    textArea.style.top = "0";
    textArea.style.left = "0";
    textArea.style.position = "fixed";
    document.body.appendChild(textArea);
    textArea.focus();
    textArea.select();
    try {
      const successful = document.execCommand('copy');
      if (successful) {
        addLog('✓ Logs copiados (fallback)');
      } else {
        addLog('✗ Falló copiar');
      }
    } catch (_) {
      addLog('✗ Error al copiar');
    }
    document.body.removeChild(textArea);
  }, [addLog]);

  const copyToken = () => {
    if (navigator.clipboard) {
      navigator.clipboard.writeText(sessionToken);
      addLog('✓ Token de sesión copiado al portapapeles');
    }
  };

  const runAction = useCallback(async (action: string, desc: string, extraBody = {}) => {
    if (typeof window !== "undefined" && (window as any).AndroidBridge) {
      if (action === 'open_screen' && activeSerial) {
        addLog(`[Moonlight] Iniciando: ${activeSerial}`);
        (window as any).AndroidBridge.connectToPC(activeSerial);
        return;
      }
      addLog(`[Híbrido] ${desc}`);
      return;
    }

    addLog(`Ejecutando: ${desc}...`);
    const d = await AdbService.executeAction(action, activeSerial, deviceIP, extraBody);
    if (d.success) {
      addLog(`✓ ${d.message || desc}`);
    } else {
      addLog(`✗ ${d.error || 'Error'}`);
    }
  }, [deviceIP, activeSerial, addLog]);

  // ── Auto-discovery ─────────────────────────────────────────────────────────
  useEffect(() => {
    const lastDevice = deviceRegistry.getLastConnected();
    const onMobile = isMobileNetwork();

    if (lastDevice) {
      if (onMobile && lastDevice.tailscaleIp) {
        connSettings.setMode('tailscale');
        connSettings.setTailscaleIp(lastDevice.tailscaleIp);
      } else if (lastDevice.connectionMode === 'tailscale' && lastDevice.tailscaleIp) {
        connSettings.setTailscaleIp(lastDevice.tailscaleIp);
      } else {
        connSettings.setMode('lan');
      }
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // ── Device Fetching ────────────────────────────────────────────────────────
  const fetchDevice = useCallback(async (forcedSerial?: string) => {
    try {
      if (typeof window !== "undefined" && (window as any).AndroidBridge) {
        const pcsStr = (window as any).AndroidBridge.getDiscoveredPCs();
        if (pcsStr) {
          const pcs = JSON.parse(pcsStr);
          setDevicesList(pcs.map((pc: any) => ({ serial: pc.uuid, model: pc.name, connectionType: pc.state, state: 'device' })));
          if (pcs.length > 0 && !activeSerial) {
            setActiveSerial(pcs[0].uuid);
            setDevice({
              connected: true,
              model: pcs[0].name,
              serial: pcs[0].uuid,
              connectionType: pcs[0].state,
              battery: 100,
              isCharging: true
            });
          }
        }
        setLoading(false);
        return;
      }

      const serialToUse = forcedSerial !== undefined ? forcedSerial : activeSerial;
      const resData = await AdbService.fetchDevices(serialToUse || undefined);

      if (resData.connected && resData.devices) {
        setDevicesList(resData.devices);
        const d = resData.activeDevice;
        if (d) {
          setDevice(d);
          if (!activeSerial) setActiveSerial(d.serial || null);
          if (d.ip) {
            setDeviceIP(d.ip);
            if (deviceIP !== d.ip) addLog(`Dispositivo detectado: ${d.ip}`);
          }
        }
      } else {
        setDevice(null);
      }
    } catch (err) {
      // ignore silently
    } finally {
      setLoading(false);
    }
  }, [activeSerial, deviceIP, addLog]);

  useEffect(() => {
    fetchDevice();
    const interval = setInterval(() => fetchDevice(), 3500);
    return () => clearInterval(interval);
  }, [fetchDevice]);

  const handleDeviceClick = (serial: string) => {
    setActiveSerial(serial);
    fetchDevice(serial);
  };

  // ── Pairing ────────────────────────────────────────────────────────────────
  const sendPin = useCallback(async () => {
    if (!pairingPin || pairingPin.length !== 4) {
      setPairingMsg('Ingresa un PIN de 4 dígitos');
      setPairingStatus('error');
      return;
    }
    setPairingStatus('sending');
    setPairingMsg('Enviando PIN a Sunshine…');
    try {
      const d = await AdbService.pairPin(pairingPin);
      if (d.success) {
        setPairingStatus('ok');
        setPairingMsg('✓ PIN enviado — acepta en Sunshine si se requiere');
        addLog(`✓ PIN ${pairingPin} enviado a Sunshine`);
        setPairingPin('');
      } else {
        setPairingStatus('error');
        setPairingMsg(`Error: ${d.error || 'Rechazado por Sunshine'}`);
        addLog(`✗ Sunshine rechazó el PIN: ${d.error}`);
      }
    } catch (err) {
      setPairingStatus('error');
      setPairingMsg('No se pudo conectar con el servidor API');
      addLog('✗ No se pudo alcanzar el servidor API local');
    }
    setTimeout(() => setPairingStatus('idle'), 4000);
  }, [pairingPin, addLog]);

  const launchStream = useCallback(async () => {
    addLog('Solicitando proyección automática de la pantalla del PC...');
    try {
      const d = await AdbService.launchStream();
      if (d.success) {
        addLog('✓ Proyección de pantalla iniciada en el celular');
      } else {
        addLog(`✗ Error al iniciar proyección: ${d.error}`);
      }
    } catch (err) {
      addLog('✗ No se pudo conectar con el servidor API');
    }
  }, [addLog]);

  useEffect(() => {
    if (activeSerial) {
      const injectPc = async () => {
        try {
          await AdbService.autoDetect(activeSerial);
        } catch (_) {}
      };
      injectPc();
    }
  }, [activeSerial]);

  // Electron specific adjustments
  const handleAutoStartChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const checked = e.target.checked;
    setAutoStart(checked);
    if (typeof window !== 'undefined' && (window as any).api) {
      try {
        await (window as any).api.setAutoStart(checked);
        addLog(`✓ Iniciar con Windows: ${checked ? 'Activado' : 'Desactivado'}`);
      } catch (_) {}
    }
  };

  const handleMinToTrayChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const checked = e.target.checked;
    setMinToTray(checked);
    if (typeof window !== 'undefined' && (window as any).api) {
      try {
        await (window as any).api.setMinToTray(checked);
        addLog(`✓ Minimizar a bandeja al cerrar: ${checked ? 'Activado' : 'Desactivado'}`);
      } catch (_) {}
    }
  };

  // ── Viewport pan ──────────────────────────────────────────────────────────
  const handlePanStart = (e: React.MouseEvent) => {
    if (!isPanning) return;
    panStart.current = { x: e.clientX, y: e.clientY };
    panOrigin.current = { ...viewPan };
  };
  const handlePanMove = (e: React.MouseEvent) => {
    if (!isPanning || !(e.buttons & 1)) return;
    setViewPan({
      x: panOrigin.current.x + (e.clientX - panStart.current.x) * 0.6,
      y: panOrigin.current.y + (e.clientY - panStart.current.y) * 0.6,
    });
  };

  // ── Keyboard handlers ─────────────────────────────────────────────────────
  const handleKbKey   = useCallback((c: string) => { setKbInput(p => p+c); setKbFlash(c); setTimeout(() => setKbFlash(null), 120); }, []);
  const handleKbBspc  = useCallback(() => setKbInput(p => p.slice(0,-1)), []);
  const handleKbSpace = useCallback(() => setKbInput(p => p+' '), []);
  const handleKbEnter = useCallback(() => {
    if (!kbInput.trim()) return;
    runAction('keyevent', 'Enviar texto', { text: kbInput, modifiers: kbMods });
    setKbInput('');
    setKbMods({ ctrl:false, alt:false, shift:false, win:false });
  }, [kbInput, kbMods, runAction]);
  const handleNavKey = useCallback((keycode: string, description: string) => {
    runAction('keyevent', description, { keycode, modifiers: kbMods });
  }, [kbMods, runAction]);

  // ─────────────────────────────────────────────────────────────────────────
  // RENDER
  // ─────────────────────────────────────────────────────────────────────────
  return (
    <div className="flex flex-col h-screen bg-[#080B14] text-white overflow-hidden select-none" style={{ fontFamily: "'Inter', system-ui, sans-serif" }}>

      {/* Main Workspace Area (Sidebar + Content Panel) */}
      <div className="flex-1 flex overflow-hidden relative">

        {/* ── LEFT SIDEBAR (Collapsed by default, expands on hover) ── */}
        <aside className="w-16 hover:w-64 transition-all duration-200 ease-in-out flex-shrink-0 flex flex-col border-r border-white/5 bg-[#0D1321] group z-40">

          {/* Logo / Header */}
          <div className="px-3.5 py-5 border-b border-white/5 flex items-center gap-3">
            <div className="w-9 h-9 rounded-xl bg-gradient-to-br from-[#3B82F6] to-[#1D4ED8] flex items-center justify-center shadow-lg shadow-[#3B82F6]/10 shrink-0">
              <Monitor size={18} className="text-white" />
            </div>
            <div className="opacity-0 group-hover:opacity-100 transition-opacity duration-200 whitespace-nowrap overflow-hidden">
              <p className="text-sm font-bold text-white leading-none">SmartDisplay</p>
              <p className="text-[10px] text-white/40 mt-0.5">Workstation AI</p>
            </div>
          </div>

          {/* Navigation Items */}
          <nav className="flex-1 px-2.5 py-4 space-y-1.5 overflow-y-auto custom-scrollbar">
            <NavItem
              icon={<LayoutDashboard size={16}/>}
              label="Dashboard"
              active={activePanel === 'dashboard'}
              onClick={() => setActivePanel('dashboard')}
            />
            <NavItem
              icon={<Smartphone size={16}/>}
              label="Dispositivos"
              active={activePanel === 'devices'}
              onClick={() => setActivePanel('devices')}
            />
            <NavItem
              icon={<Monitor size={16}/>}
              label="Streaming Workspace"
              active={activePanel === 'stream'}
              onClick={() => setActivePanel('stream')}
            />
            <NavItem
              icon={<KeyboardIcon size={16}/>}
              label="Teclado NORMAL"
              active={kbOpen && kbMode !== 'dev'}
              onClick={() => {
                if (kbOpen && kbMode !== 'dev') {
                  setKbOpen(false);
                } else {
                  setKbOpen(true);
                  setKbMode('text');
                }
                setActivePanel('stream');
              }}
            />
            <NavItem
              icon={<Code size={16}/>}
              label="Teclado DEV"
              active={kbOpen && kbMode === 'dev'}
              onClick={() => {
                if (kbOpen && kbMode === 'dev') {
                  setKbOpen(false);
                } else {
                  setKbOpen(true);
                  setKbMode('dev');
                }
                setActivePanel('stream');
              }}
            />
            <NavItem
              icon={<Hand size={16}/>}
              label="Modo Touchpad"
              active={touchpadMode}
              onClick={() => {
                const nextVal = !touchpadMode;
                setTouchpadMode(nextVal);
                setActivePanel('stream');
                addLog(`✓ Modo Touchpad ${nextVal ? 'activado' : 'desactivado'}`);
              }}
            />
            <NavItem
              icon={<Settings2 size={16}/>}
              label="Configuración"
              active={activePanel === 'settings'}
              onClick={() => setActivePanel('settings')}
            />
          </nav>

          {/* Bottom info */}
          <div className="px-3.5 py-4 border-t border-white/5 text-[9px] text-white/20 whitespace-nowrap overflow-hidden">
            <div className="opacity-0 group-hover:opacity-100 transition-opacity duration-200">
              <p>v12.1 · nonRoot Debug</p>
              <p className="mt-0.5 font-mono truncate">{connSettings.resolveWsUrl()}</p>
            </div>
          </div>
        </aside>

        {/* ── MAIN CONTENT WORKSPACE ── */}
        <main className="flex-1 flex flex-col overflow-hidden relative">

          {/* Top Bar (Single Line) */}
          <header className="flex-shrink-0 h-12 px-5 flex items-center justify-between border-b border-white/5 bg-[#0D1321]">
            <div className="flex items-center gap-3 text-xs font-semibold tracking-wider text-white/40">
              <span className="text-white/80 font-bold">SmartDisplay AI</span>
              <span>|</span>
              <span>PC Principal</span>
              <span>|</span>
              <span className={adaptiveState.connected ? 'text-[#10B981]' : 'text-[#EF4444]'}>
                {adaptiveState.connected ? 'Conectado' : 'Desconectado'}
              </span>
              {adaptiveState.connected && (
                <>
                  <span>|</span>
                  <span>{adaptiveState.latency}ms</span>
                </>
              )}
            </div>
            <div className="flex items-center gap-2">
              {touchpadMode && (
                <span className="px-2.5 py-0.5 rounded bg-[#3B82F6]/10 text-[#3B82F6] text-[10px] font-bold border border-[#3B82F6]/15">
                  TOUCHPAD ACTIVO
                </span>
              )}
              {kbOpen && (
                <span className="px-2.5 py-0.5 rounded bg-[#10B981]/10 text-[#10B981] text-[10px] font-bold border border-[#10B981]/15 uppercase">
                  Teclado: {kbMode}
                </span>
              )}
            </div>
          </header>

          {/* Content Canvas */}
          <div className="flex-1 overflow-hidden relative bg-[#080B14]">

            {/* ── PANEL: DASHBOARD ── */}
            {activePanel === 'dashboard' && (
              <div className="h-full overflow-y-auto p-6 custom-scrollbar space-y-6">
                <div>
                  <h1 className="text-lg font-bold text-white flex items-center gap-2">
                    <LayoutDashboard size={20} className="text-[#3B82F6]" />
                    Dashboard de Control
                  </h1>
                  <p className="text-xs text-white/40 mt-0.5">Visión general del estado del servidor y del dispositivo</p>
                </div>

                {/* Stats Grid */}
                <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
                  {/* WebSocket Clients */}
                  <div className="p-4 rounded-xl border border-white/5 bg-[#0D1321] flex flex-col justify-between h-28 shadow-lg glow-card hover-scale">
                    <div className="flex items-center justify-between text-white/40 text-[10px] uppercase font-bold tracking-wider">
                      <span>Clientes WebSocket</span>
                      <Radio size={14} className="text-cyan-400" />
                    </div>
                    <div className="mt-2">
                      <span className="text-2xl font-black text-white">{electronStatus.clients}</span>
                      <p className="text-[10px] text-white/30 mt-0.5">Conectados al puerto 3002</p>
                    </div>
                  </div>

                  {/* Connected ADB Device */}
                  <div className="p-4 rounded-xl border border-white/5 bg-[#0D1321] flex flex-col justify-between h-28 shadow-lg glow-card hover-scale">
                    <div className="flex items-center justify-between text-white/40 text-[10px] uppercase font-bold tracking-wider">
                      <span>Dispositivo ADB</span>
                      <Smartphone size={14} className="text-emerald-400" />
                    </div>
                    <div className="mt-2">
                      <span className="text-sm font-bold text-white truncate block max-w-full">
                        {device?.model || 'Ninguno'}
                      </span>
                      <p className={`text-[10px] mt-0.5 ${device ? 'text-emerald-400/80' : 'text-white/30'}`}>
                        {device ? `Estado: ${device.state}` : 'Conecta por USB o Wi-Fi'}
                      </p>
                    </div>
                  </div>

                  {/* Stream Active */}
                  <div className="p-4 rounded-xl border border-white/5 bg-[#0D1321] flex flex-col justify-between h-28 shadow-lg glow-card hover-scale">
                    <div className="flex items-center justify-between text-white/40 text-[10px] uppercase font-bold tracking-wider">
                      <span>Proyección Activa</span>
                      <Monitor size={14} className="text-[#3B82F6]" />
                    </div>
                    <div className="mt-2">
                      <span className={`text-2xl font-black ${streamActive ? 'text-[#10B981]' : 'text-white/60'}`}>
                        {streamActive ? 'Sí' : 'No'}
                      </span>
                      <p className="text-[10px] text-white/30 mt-0.5">
                        {streamActive ? `Latencia: ${streamLatency} ms` : 'Video inactivo'}
                      </p>
                    </div>
                  </div>

                  {/* IP Address */}
                  <div className="p-4 rounded-xl border border-white/5 bg-[#0D1321] flex flex-col justify-between h-28 shadow-lg glow-card hover-scale">
                    <div className="flex items-center justify-between text-white/40 text-[10px] uppercase font-bold tracking-wider">
                      <span>Dirección IP Host</span>
                      <Wifi size={14} className="text-amber-400" />
                    </div>
                    <div className="mt-2 font-mono text-xs text-white/80">
                      <div>LAN: {connSettings.lanIp || '127.0.0.1'}</div>
                      <div className="text-[10px] text-white/40 mt-1">
                        Tailscale: {connSettings.config.tailscaleIp || 'No configurada'}
                      </div>
                    </div>
                  </div>
                </div>

                {/* Session Token Box */}
                <div className="p-4 rounded-xl border border-white/5 bg-[#0D1321] flex flex-col md:flex-row md:items-center justify-between gap-4 shadow-lg premium-panel">
                  <div className="space-y-1">
                    <h3 className="text-xs font-bold uppercase tracking-wider text-white/60">Token de Seguridad</h3>
                    <p className="text-[10px] text-white/40">Requerido por los clientes para establecer conexión segura</p>
                  </div>
                  <div className="flex-1 max-w-lg flex items-center gap-2 bg-[#080B14] border border-white/5 rounded-lg px-3 py-2 font-mono text-xs">
                    <span className="text-white/40 select-none">TOKEN:</span>
                    <span className="flex-1 text-[#3B82F6] truncate">
                      {showToken ? sessionToken : '••••••••••••••••••••••••••••••••'}
                    </span>
                    <button 
                      onClick={() => setShowToken(!showToken)}
                      className="p-1 rounded hover:bg-white/5 text-white/40 hover:text-white transition-colors"
                      title={showToken ? "Ocultar token" : "Ver token"}
                    >
                      {showToken ? <EyeOff size={14} /> : <Eye size={14} />}
                    </button>
                    <button 
                      onClick={copyToken}
                      className="p-1 rounded hover:bg-white/5 text-white/40 hover:text-white transition-colors border border-white/10"
                      title="Copiar token"
                    >
                      <Copy size={14} />
                    </button>
                  </div>
                </div>

                {/* Info Card */}
                <div className="p-4 rounded-xl border border-blue-500/10 bg-blue-500/5 flex gap-3 text-xs text-white/60 leading-relaxed shadow-lg">
                  <Info className="text-[#3B82F6] flex-shrink-0 mt-0.5" size={16} />
                  <div>
                    <p className="font-semibold text-white">¿Cómo funciona SmartDisplay AI?</p>
                    <p className="mt-1">
                      El servidor WebSocket de Electron (puerto 3002) inyecta las pulsaciones de teclado y los clics del mouse en Windows. 
                      La proyección de pantalla utiliza Sunshine y Moonlight en segundo plano. Para conectar tu celular Android, 
                      abre la aplicación móvil e introduce la IP de tu PC que se muestra arriba.
                    </p>
                  </div>
                </div>
              </div>
            )}

            {/* ── PANEL: DISPOSITIVOS ── */}
            {activePanel === 'devices' && (
              <div className="h-full overflow-hidden flex flex-col md:flex-row">
                
                {/* Left Side: Devices list */}
                <div className="w-full md:w-80 border-r border-white/5 flex flex-col overflow-hidden bg-[#0a0e1a]">
                  <div className="p-4 border-b border-white/5 flex items-center justify-between">
                    <span className="text-xs font-bold uppercase tracking-wider text-white/60">Dispositivos ADB</span>
                    <div className="flex gap-1.5">
                      <button 
                        onClick={() => fetchDevice()}
                        className="p-1.5 rounded hover:bg-white/5 text-white/60 hover:text-white transition-colors"
                        title="Refrescar"
                      >
                        <RefreshCw size={14} className={loading ? "animate-spin" : ""} />
                      </button>
                      <button 
                        onClick={() => runAction('radar', 'Escanear red (Radar)')}
                        className="p-1.5 rounded hover:bg-white/5 text-white/60 hover:text-white transition-colors border border-white/10 text-[10px] font-semibold"
                        title="Buscar en red (Puerto 5555)"
                      >
                        Radar
                      </button>
                    </div>
                  </div>

                  <div className="flex-1 overflow-y-auto p-3 space-y-2 custom-scrollbar">
                    {devicesList.map((d, i) => {
                      const isSelected = activeSerial === d.serial;
                      const isOnline = d.state === 'device';
                      return (
                        <div 
                          key={i}
                          onClick={() => handleDeviceClick(d.serial)}
                          className={`p-3 rounded-xl border cursor-pointer transition-all flex items-center gap-3
                            ${isSelected 
                              ? 'bg-cyan-500/10 border-cyan-500/30 text-cyan-300 shadow-lg' 
                              : 'bg-[#0d1321] border-white/5 text-white/60 hover:bg-white/[0.02]'}`}
                        >
                          <Smartphone size={16} className={isSelected ? 'text-cyan-400' : 'text-white/30'} />
                          <div className="flex-1 min-w-0">
                            <p className="text-xs font-bold truncate text-white">{d.model}</p>
                            <p className="text-[10px] text-white/40 truncate font-mono mt-0.5">{d.serial}</p>
                          </div>
                          <div className="flex items-center gap-1.5">
                            <span className="text-[9px] text-white/30">{d.connectionType}</span>
                            <span className={`w-1.5 h-1.5 rounded-full ${isOnline ? 'bg-emerald-400' : 'bg-amber-400'}`} />
                          </div>
                        </div>
                      );
                    })}
                    {devicesList.length === 0 && (
                      <div className="text-center py-8 text-white/20 text-xs">
                        No hay dispositivos conectados.<br/>Conecta tu celular vía USB.
                      </div>
                    )}
                  </div>
                </div>

                {/* Right Side: Device Details & Quick Actions */}
                <div className="flex-1 overflow-y-auto p-6 custom-scrollbar space-y-6 bg-[#080B14]">
                  {device ? (
                    <>
                      <div>
                        <h1 className="text-lg font-bold text-white flex items-center gap-2">
                          <Smartphone size={20} className="text-cyan-400" />
                          {device.model}
                        </h1>
                        <p className="text-xs text-white/40 mt-0.5">Detalles del celular y acciones de control remoto</p>
                      </div>

                      {/* Device Hardware Info Grid */}
                      <div className="grid grid-cols-2 md:grid-cols-3 gap-4">
                        {/* Battery */}
                        <div className="p-3 rounded-lg border border-white/5 bg-[#0D1321]/60 flex items-center gap-3">
                          {device.isCharging ? (
                            <BatteryCharging className="text-emerald-400" size={18} />
                          ) : (
                            <Battery className="text-white/50" size={18} />
                          )}
                          <div>
                            <span className="text-[10px] text-white/40 block font-bold uppercase tracking-wider">Batería</span>
                            <span className="text-xs font-semibold text-white">{device.battery}%</span>
                          </div>
                        </div>

                        {/* Temperature */}
                        <div className="p-3 rounded-lg border border-white/5 bg-[#0D1321]/60 flex items-center gap-3">
                          <Thermometer className="text-amber-400" size={18} />
                          <div>
                            <span className="text-[10px] text-white/40 block font-bold uppercase tracking-wider">Temperatura</span>
                            <span className="text-xs font-semibold text-white">{device.temperature}°C</span>
                          </div>
                        </div>

                        {/* RAM */}
                        <div className="p-3 rounded-lg border border-white/5 bg-[#0D1321]/60 flex items-center gap-3">
                          <Cpu className="text-cyan-400" size={18} />
                          <div>
                            <span className="text-[10px] text-white/40 block font-bold uppercase tracking-wider">Memoria RAM</span>
                            <span className="text-xs font-semibold text-white">{device.ram}</span>
                          </div>
                        </div>

                        {/* Storage */}
                        <div className="p-3 rounded-lg border border-white/5 bg-[#0D1321]/60 flex items-center gap-3">
                          <HardDrive className="text-white/50" size={18} />
                          <div>
                            <span className="text-[10px] text-white/40 block font-bold uppercase tracking-wider">Almacenamiento</span>
                            <span className="text-xs font-semibold text-white">{device.storage}</span>
                          </div>
                        </div>

                        {/* Resolution */}
                        <div className="p-3 rounded-lg border border-white/5 bg-[#0D1321]/60 flex items-center gap-3">
                          <Monitor className="text-[#3B82F6]" size={18} />
                          <div>
                            <span className="text-[10px] text-white/40 block font-bold uppercase tracking-wider">Resolución</span>
                            <span className="text-xs font-semibold text-white">{device.resolution || '1080 × 2400'}</span>
                          </div>
                        </div>

                        {/* Android OS */}
                        <div className="p-3 rounded-lg border border-white/5 bg-[#0D1321]/60 flex items-center gap-3">
                          <Smartphone className="text-white/50" size={18} />
                          <div>
                            <span className="text-[10px] text-white/40 block font-bold uppercase tracking-wider font-sans">Android OS</span>
                            <span className="text-xs font-semibold text-white">{device.androidVersion}</span>
                          </div>
                        </div>
                      </div>

                      {/* Quick Actions Grid */}
                      <div className="space-y-3">
                        <h3 className="text-xs font-bold uppercase tracking-wider text-white/60">Acciones Rápidas</h3>
                        <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
                          <button 
                            onClick={launchStream}
                            className="p-3 rounded-xl border border-[#3B82F6]/25 bg-[#3B82F6]/10 text-[#3B82F6] hover:bg-[#3B82F6]/20 transition-all font-semibold text-xs flex flex-col items-center justify-center gap-2 shadow-lg"
                          >
                            <Radio size={16} />
                            <span>Proyectar Pantalla</span>
                          </button>
                          
                          <button 
                            onClick={() => runAction('open_screen', 'Espejo scrcpy')}
                            className="p-3 rounded-xl border border-white/5 bg-[#0D1321] text-white/80 hover:bg-[#131C31] hover:text-white transition-all font-semibold text-xs flex flex-col items-center justify-center gap-2"
                          >
                            <Monitor size={16} />
                            <span>Espejo scrcpy</span>
                          </button>

                          <button 
                            onClick={() => runAction('enable_wifi', 'Habilitar Wi-Fi')}
                            className="p-3 rounded-xl border border-white/5 bg-[#0D1321] text-white/80 hover:bg-[#131C31] hover:text-white transition-all font-semibold text-xs flex flex-col items-center justify-center gap-2"
                          >
                            <Wifi size={16} />
                            <span>Activar Wi-Fi</span>
                          </button>

                          <button 
                            onClick={() => runAction('screenshot', 'Tomar Captura')}
                            className="p-3 rounded-xl border border-white/5 bg-[#0D1321] text-white/80 hover:bg-[#131C31] hover:text-white transition-all font-semibold text-xs flex flex-col items-center justify-center gap-2"
                          >
                            <Monitor size={16} />
                            <span>Tomar Captura</span>
                          </button>

                          <button 
                            onClick={async () => {
                              const nextRec = !recording;
                              setRecording(nextRec);
                              await runAction(nextRec ? 'start_record' : 'stop_record', nextRec ? 'Iniciar Grabación' : 'Detener Grabación');
                            }}
                            className={`p-3 rounded-xl border transition-all font-semibold text-xs flex flex-col items-center justify-center gap-2
                              ${recording 
                                ? 'border-red-500/30 bg-red-500/10 text-red-400 animate-pulse' 
                                : 'border-white/5 bg-[#0D1321] text-white/80 hover:bg-[#131C31] hover:text-white'}`}
                          >
                            <Monitor size={16} />
                            <span>{recording ? 'Detener Grabación' : 'Grabar Pantalla'}</span>
                          </button>

                          <button 
                            onClick={() => runAction('restart_adb', 'Reiniciar ADB')}
                            className="p-3 rounded-xl border border-white/5 bg-[#0D1321] text-white/80 hover:bg-[#131C31] hover:text-white transition-all font-semibold text-xs flex flex-col items-center justify-center gap-2"
                          >
                            <RefreshCw size={16} />
                            <span>Reiniciar ADB</span>
                          </button>

                          <button 
                            onClick={() => runAction('power_off', 'Apagar Celular')}
                            className="p-3 rounded-xl border border-red-500/15 bg-red-500/5 text-red-400/80 hover:bg-red-500/15 hover:text-red-400 transition-all font-semibold text-xs flex flex-col items-center justify-center gap-2"
                          >
                            <XCircle size={16} />
                            <span>Apagar Celular</span>
                          </button>
                        </div>
                      </div>
                    </>
                  ) : (
                    <div className="h-full flex flex-col items-center justify-center text-center py-20 text-white/30 space-y-3">
                      <Smartphone size={48} className="text-white/10" />
                      <div>
                        <p className="text-sm font-semibold">Ningún dispositivo seleccionado</p>
                        <p className="text-xs text-white/20 mt-1">Conecta tu celular vía USB o ejecuta el Radar para detectar dispositivos</p>
                      </div>
                    </div>
                  )}
                </div>

              </div>
            )}

            {/* ── PANEL: STREAMING WORKSPACE ── */}
            {activePanel === 'stream' && (
              <div 
                className="w-full h-full flex flex-col items-center justify-center relative overflow-hidden bg-[#080B14]"
                onMouseDown={handlePanStart}
                onMouseMove={handlePanMove}
                style={{ cursor: isPanning ? 'grab' : 'default' }}
              >
                {/* Floating zoom/pan controls */}
                <div className="absolute top-4 left-4 z-10 flex items-center gap-1.5 p-1 rounded-lg bg-[#0D1321]/80 backdrop-blur border border-white/5 shadow-xl">
                  <button 
                    onClick={() => setViewZoom(z => Math.max(50, z - 10))}
                    className="p-1.5 rounded-md hover:bg-[#131C31] text-white/60 hover:text-white transition-all"
                    title="Zoom Out"
                  >
                    <ZoomOut size={14} />
                  </button>
                  <span className="text-[10px] font-mono text-white/50 px-1">{viewZoom}%</span>
                  <button 
                    onClick={() => setViewZoom(z => Math.min(300, z + 10))}
                    className="p-1.5 rounded-md hover:bg-[#131C31] text-white/60 hover:text-white transition-all"
                    title="Zoom In"
                  >
                    <ZoomIn size={14} />
                  </button>
                  <div className="w-px h-3.5 bg-white/10 mx-1" />
                  <button 
                    onClick={() => { setViewZoom(100); setViewPan({ x: 0, y: 0 }); }}
                    className="p-1.5 rounded-md hover:bg-[#131C31] text-white/60 hover:text-white transition-all"
                    title="Reset Viewport"
                  >
                    <RotateCcw size={14} />
                  </button>
                  <button 
                    onClick={() => setIsPanning(!isPanning)}
                    className={`p-1.5 rounded-md transition-all ${isPanning ? 'bg-[#3B82F6]/20 text-[#3B82F6]' : 'hover:bg-[#131C31] text-white/60 hover:text-white'}`}
                    title="Mover Pantalla"
                  >
                    <Hand size={14} />
                  </button>
                </div>

                {/* Video Canvas Container */}
                <div 
                  className="relative transition-transform duration-100 ease-out select-none animate-in fade-in duration-300"
                  style={{
                    transform: `translate(${viewPan.x}px, ${viewPan.y}px) scale(${viewZoom / 100})`,
                  }}
                >
                  <video 
                    ref={videoRef}
                    autoPlay
                    playsInline
                    muted
                    className="rounded-lg shadow-2xl border border-white/5 max-h-[80vh] w-auto bg-black"
                    style={{ aspectRatio: '16/9' }}
                  />
                  
                  {/* Status Overlay when disconnected */}
                  {!streamActive && (
                    <div className="absolute inset-0 flex flex-col items-center justify-center bg-[#0D1321]/90 rounded-lg border border-white/5">
                      <Loader2 className="animate-spin text-[#3B82F6] mb-3" size={24} />
                      <p className="text-xs text-white/60">Esperando señal de video...</p>
                      <p className="text-[10px] text-white/30 mt-1">Inicia la transmisión en la app móvil</p>
                    </div>
                  )}
                </div>
              </div>
            )}

            {/* ── PANEL: SETTINGS ── */}
            {activePanel === 'settings' && (
              <div className="h-full overflow-y-auto p-6 custom-scrollbar bg-[#080B14]">
                <div className="max-w-xl mx-auto space-y-6">
                  
                  <div>
                    <h1 className="text-lg font-bold text-white">Configuración del Workspace</h1>
                    <p className="text-xs text-white/40 mt-0.5">Emparejamiento y transporte de red para SmartDisplay AI</p>
                  </div>

                  <div className="space-y-4">
                    {/* Step 1: Sunshine pairing */}
                    <Step num={1} title="Sunshine Pairing" done={pairingStatus === 'ok'}>
                      <div className="space-y-3">
                        <p className="text-xs text-white/50">Introduce el PIN de 4 dígitos generado en la app móvil:</p>
                        <div className="flex gap-2">
                          <input
                            type="text"
                            inputMode="numeric"
                            maxLength={4}
                            value={pairingPin}
                            onChange={e => setPairingPin(e.target.value.replace(/\D/g, '').slice(0,4))}
                            onKeyDown={e => e.key === 'Enter' && sendPin()}
                            placeholder="ej: 4823"
                            className="flex-1 px-4 py-2.5 rounded-lg bg-[#0D1321] border border-white/5 text-white text-xl font-mono tracking-[0.4em] text-center placeholder:text-white/10 placeholder:text-xs placeholder:tracking-normal focus:outline-none focus:border-[#3B82F6]/50 transition-all"
                          />
                          <button
                            onClick={sendPin}
                            disabled={pairingStatus === 'sending' || pairingPin.length !== 4}
                            className="px-5 rounded-lg font-semibold text-xs transition-all disabled:opacity-40 flex items-center gap-2 bg-[#3B82F6] hover:bg-[#3B82F6]/90 text-white disabled:bg-slate-800"
                          >
                            {pairingStatus === 'sending' ? <Loader2 size={14} className="animate-spin"/> : <ChevronRight size={14}/>}
                            Enviar
                          </button>
                        </div>
                        {pairingMsg && (
                          <p className={`text-xs ${pairingStatus === 'ok' ? 'text-[#10B981]' : 'text-[#EF4444]'}`}>
                            {pairingMsg}
                          </p>
                        )}
                      </div>
                    </Step>

                    {/* Step 2: Adaptive Global Streaming */}
                    <Step num={2} title="Adaptive Global Streaming" done={adaptiveState.connected}>
                      <div className="space-y-3">
                        <div>
                          <label className="text-[10px] text-white/40 block mb-1.5 uppercase font-bold tracking-wider">IP de Tailscale (PC Host)</label>
                          <input
                            type="text"
                            value={connSettings.config.tailscaleIp}
                            onChange={e => connSettings.setTailscaleIp(e.target.value)}
                            placeholder="ej: 100.115.23.45"
                            className="w-full px-3.5 py-2 rounded-lg bg-[#0D1321] border border-white/5 text-xs font-mono text-white/80 placeholder:text-white/25 focus:outline-none focus:border-[#3B82F6]/50 transition-all"
                          />
                        </div>
                        <div>
                          <label className="text-[10px] text-white/40 block mb-1.5 uppercase font-bold tracking-wider">Forzar Transporte de Red</label>
                          <div className="grid grid-cols-2 gap-2 mt-1">
                            <button
                              onClick={() => forceTransport('lan_ws')}
                              className={`px-3 py-2 text-[11px] rounded-lg border transition-all ${adaptiveState.transport === 'lan_ws' ? 'bg-[#3B82F6]/10 border-[#3B82F6]/30 text-[#3B82F6] font-bold' : 'bg-[#0D1321] border-white/5 text-white/50 hover:bg-[#131C31]'}`}
                            >
                              ⚡ LAN WS
                            </button>
                            <button
                              onClick={() => forceTransport('webrtc_p2p')}
                              className={`px-3 py-2 text-[11px] rounded-lg border transition-all ${adaptiveState.transport === 'webrtc_p2p' ? 'bg-[#3B82F6]/10 border-[#3B82F6]/30 text-[#3B82F6] font-bold' : 'bg-[#0D1321] border-white/5 text-white/50 hover:bg-[#131C31]'}`}
                            >
                              🌐 WebRTC
                            </button>
                            <button
                              onClick={() => forceTransport('tailscale_ws')}
                              className={`px-3 py-2 text-[11px] rounded-lg border transition-all ${adaptiveState.transport === 'tailscale_ws' ? 'bg-[#3B82F6]/10 border-[#3B82F6]/30 text-[#3B82F6] font-bold' : 'bg-[#0D1321] border-white/5 text-white/50 hover:bg-[#131C31]'}`}
                            >
                              🔒 Tailscale
                            </button>
                            <button
                              onClick={() => forceTransport('turn_relay')}
                              className={`px-3 py-2 text-[11px] rounded-lg border transition-all ${adaptiveState.transport === 'turn_relay' ? 'bg-[#3B82F6]/10 border-[#3B82F6]/30 text-[#3B82F6] font-bold' : 'bg-[#0D1321] border-white/5 text-white/50 hover:bg-[#131C31]'}`}
                            >
                              ☁️ TURN Relay
                            </button>
                          </div>
                        </div>
                      </div>
                    </Step>

                    {/* Step 3: Desktop integration (Electron preferences) */}
                    {typeof window !== 'undefined' && (window as any).api && (
                      <Step num={3} title="Preferencias del Sistema (Electron)" done={true}>
                        <div className="space-y-3">
                          <label className="flex items-center gap-3 text-xs text-white/75 cursor-pointer">
                            <input
                              type="checkbox"
                              checked={autoStart}
                              onChange={handleAutoStartChange}
                              className="rounded border-white/10 bg-[#0D1321] text-[#3B82F6] focus:ring-0 focus:ring-offset-0"
                            />
                            <span>Iniciar automáticamente con Windows</span>
                          </label>

                          <label className="flex items-center gap-3 text-xs text-white/75 cursor-pointer">
                            <input
                              type="checkbox"
                              checked={minToTray}
                              onChange={handleMinToTrayChange}
                              className="rounded border-white/10 bg-[#0D1321] text-[#3B82F6] focus:ring-0 focus:ring-offset-0"
                            />
                            <span>Minimizar a la bandeja al cerrar la ventana</span>
                          </label>
                        </div>
                      </Step>
                    )}
                  </div>
                </div>
              </div>
            )}

          </div>

          {/* Premium Connection Status Footer */}
          <footer className="flex-shrink-0 h-10 px-5 flex items-center justify-between border-t border-white/5 bg-[#0D1321] text-[10px] text-white/40">
            <div className="flex items-center gap-2">
              <span className={`w-1.5 h-1.5 rounded-full ${streamActive ? 'bg-[#10B981] animate-pulse' : 'bg-[#EF4444]'}`} />
              <span>
                {streamActive 
                  ? `Conectado mediante ${adaptiveState.transport === 'webrtc_p2p' ? 'WebRTC P2P' : adaptiveState.transport === 'tailscale_ws' ? 'Tailscale' : 'LAN WS'}` 
                  : 'Desconectado'}
              </span>
            </div>
            {streamActive && (
              <div>
                <span>Resolución: 1080p60 | Latencia: {streamLatency}ms</span>
              </div>
            )}
          </footer>
        </main>
      </div>

      {/* Bottom Log Panel - Always Visible (like in Electron renderer) */}
      <footer className="h-32 border-t border-white/5 bg-[#090d16] flex flex-col overflow-hidden shrink-0 z-40 select-none">
        <div className="h-8 px-4 border-b border-white/5 bg-[#0D1321] flex items-center justify-between text-[10px] text-white/40 font-semibold">
          <div className="flex items-center gap-2 uppercase tracking-wider">
            <Terminal size={12} className="text-[#3B82F6]" />
            <span>Registro de Eventos y Depuración</span>
          </div>
          <div className="flex items-center gap-3">
            <button 
              onClick={() => copyLogsToClipboard(log.join('\n'))}
              className="hover:text-white transition-colors"
              title="Copiar logs al portapapeles"
            >
              Copiar
            </button>
            <span>|</span>
            <button 
              onClick={() => setLog([])}
              className="hover:text-red-400 transition-colors"
              title="Limpiar historial de logs"
            >
              Limpiar
            </button>
          </div>
        </div>
        <div className="flex-1 p-3 overflow-y-auto font-mono text-[10px] text-white/60 space-y-1 bg-[#070b12] custom-scrollbar select-text">
          {log.map((l, i) => {
            let colorCls = 'text-white/50';
            if (l.includes('✓')) colorCls = 'text-emerald-400';
            else if (l.includes('✗') || l.includes('ERROR:')) colorCls = 'text-red-400';
            else if (l.includes('WARN:')) colorCls = 'text-amber-400';
            return (
              <div key={i} className={`whitespace-pre-wrap leading-relaxed ${colorCls}`}>{l}</div>
            );
          })}
          {log.length === 0 && (
            <div className="text-white/20 text-center py-4">No hay eventos registrados en esta sesión</div>
          )}
        </div>
      </footer>

      {/* Dev/Normal Keyboard Integration */}
      <OptimizedKeyboard
        isOpen={kbOpen}
        keyboardMode={kbMode}
        keyboardShift={kbShift}
        keyboardInput={kbInput}
        lastKeyFlash={kbFlash}
        activeModifiers={kbMods}
        onKeyPress={handleKbKey}
        onBackspace={handleKbBspc}
        onSpace={handleKbSpace}
        onEnter={handleKbEnter}
        onShiftToggle={() => setKbShift(!kbShift)}
        onModeChange={setKbMode}
        onShortcut={handleNavKey}
        onClose={() => setKbOpen(false)}
        dark={dark}
      />
    </div>
  );
}

// ─────────────────────────────────────────────────────────────────────────────
// Sub-components
// ─────────────────────────────────────────────────────────────────────────────

function NavItem({ icon, label, active, badge, badgeColor, onClick }: {
  icon: React.ReactNode;
  label: string;
  active: boolean;
  badge?: string;
  badgeColor?: 'cyan'|'emerald'|'amber';
  onClick: () => void;
}) {
  const badgeCls = {
    cyan:    'bg-cyan-500/20 text-cyan-400 border-cyan-500/30',
    emerald: 'bg-emerald-500/20 text-emerald-400 border-emerald-500/30',
    amber:   'bg-amber-500/20 text-amber-400 border-amber-500/30',
  }[badgeColor ?? 'cyan'] ?? '';

  return (
    <button
      onClick={onClick}
      className={`w-full flex items-center gap-3 px-3 py-2.5 rounded-xl text-sm font-medium transition-all text-left duration-200 active-scale
        ${active
          ? 'bg-gradient-to-r from-blue-500/10 to-blue-500/0 border-l-4 border-blue-500 text-blue-400 pl-2 rounded-l-none'
          : 'text-white/50 hover:text-white/80 hover:bg-white/[0.03] border border-transparent'}`}
    >
      <span className={active ? 'text-blue-400' : 'text-white/30'}>{icon}</span>
      <span className="flex-1 leading-none truncate opacity-0 group-hover:opacity-100 transition-opacity duration-200">{label}</span>
      {badge && (
        <span className="text-[10px] font-bold px-1.5 py-0.5 rounded-md border opacity-0 group-hover:opacity-100 transition-opacity duration-200">
          {badge}
        </span>
      )}
    </button>
  );
}

function Step({ num, title, done, children }: {
  num: number;
  title: string;
  done: boolean;
  children: React.ReactNode;
}) {
  return (
    <div className={`rounded-2xl border p-4 transition-all
      ${done ? 'border-emerald-500/25 bg-emerald-500/5' : 'border-white/[0.07] bg-white/[0.02]'}`}>
      <div className="flex items-center gap-3 mb-3">
        <div className={`w-6 h-6 rounded-full flex items-center justify-center text-xs font-bold flex-shrink-0
          ${done ? 'bg-emerald-500 text-white' : 'bg-white/10 text-white/50'}`}>
          {done ? '✓' : num}
        </div>
        <h3 className={`text-sm font-semibold ${done ? 'text-emerald-300' : 'text-white/80'}`}>{title}</h3>
      </div>
      <div className="ml-9">
        {children}
      </div>
    </div>
  );
}
