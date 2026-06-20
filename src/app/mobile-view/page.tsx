"use client";

import { useEffect, useRef, useState } from 'react';
import { Smartphone, Monitor, MousePointer, Keyboard, Settings, RefreshCw, Command, Play, Power, Volume2, Sparkles } from 'lucide-react';
import { useConnectionSettings } from '@/hooks';

export default function MobileDisplayReceiver() {
  const [connected, setConnected] = useState(false);
  const wsRef = useRef<WebSocket | null>(null);
  const [pin, setPin] = useState('');
  const [pairingStatus, setPairingStatus] = useState('');
  const connSettings = useConnectionSettings();

  const [appNameInput, setAppNameInput] = useState('');
  const [smartClickEnabled, setSmartClickEnabled] = useState(false);
  const [lastTouch, setLastTouch] = useState<{ x: number; y: number } | null>(null);
  const touchpadRef = useRef<HTMLDivElement>(null);

  const submitPin = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!pin.trim()) return;
    setPairingStatus('Enlazando...');
    try {
      const res = await fetch('/api/actions', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ action: 'pair_pin', pin })
      });
      const data = await res.json();
      if (data.success) {
        setPairingStatus('¡Enlazado con éxito!');
        setPin('');
      } else {
        setPairingStatus(`Error: ${data.error || 'Fallo al enlazar'}`);
      }
    } catch {
      setPairingStatus('Error al conectar con la laptop');
    }
  };

  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const token = params.get('token') || '';
    // Use dynamic URL from connection settings
    const baseUrl = connSettings.resolveWsUrl();
    const cleanUrl = baseUrl.replace(/\/+$/, '');
    const wsUrl = token ? `${cleanUrl}/?token=${encodeURIComponent(token)}` : cleanUrl;
    const ws = new WebSocket(wsUrl);
    wsRef.current = ws;

    ws.onopen = () => {
      setConnected(true);
      ws.send(JSON.stringify({ type: 'register', client: 'mobile' }));
    };

    ws.onclose = () => setConnected(false);

    return () => ws.close();
  }, []);

  const sendCommand = (cmd: string) => {
    if (wsRef.current && wsRef.current.readyState === 1) {
      wsRef.current.send(JSON.stringify({ type: 'macro', cmd }));
    }
  };

  const draggedRef = useRef(false);
  const lastTouchRef = useRef<{ x: number; y: number } | null>(null);

  const handleLaunchApp = (e: React.FormEvent) => {
    e.preventDefault();
    if (!appNameInput.trim()) return;
    if (wsRef.current && wsRef.current.readyState === 1) {
      wsRef.current.send(JSON.stringify({ type: 'run_app', name: appNameInput }));
      setAppNameInput('');
    }
  };

  const handleTouchpadTouchStart = (e: React.TouchEvent<HTMLDivElement>) => {
    const touch = e.touches[0];
    const rect = touchpadRef.current?.getBoundingClientRect();
    if (!rect) return;
    lastTouchRef.current = { x: touch.clientX - rect.left, y: touch.clientY - rect.top };
    draggedRef.current = false;
  };

  const handleTouchpadTouchMove = (e: React.TouchEvent<HTMLDivElement>) => {
    const touch = e.touches[0];
    const rect = touchpadRef.current?.getBoundingClientRect();
    if (!rect) return;
    const x = touch.clientX - rect.left;
    const y = touch.clientY - rect.top;

    if (lastTouchRef.current) {
      const dx = Math.round((x - lastTouchRef.current.x) * 1.5);
      const dy = Math.round((y - lastTouchRef.current.y) * 1.5);
      if (dx !== 0 || dy !== 0) {
        draggedRef.current = true;
        if (wsRef.current && wsRef.current.readyState === 1) {
          wsRef.current.send(JSON.stringify({ type: 'relative_move', dx, dy }));
        }
      }
    }
    lastTouchRef.current = { x, y };
  };

  const handleTouchpadTouchEnd = () => {
    lastTouchRef.current = null;
  };

  const handleTouchpadTap = () => {
    if (draggedRef.current) return;
    if (wsRef.current && wsRef.current.readyState === 1) {
      if (smartClickEnabled) {
        wsRef.current.send(JSON.stringify({ type: 'smart_click', x: -1, y: -1 }));
      } else {
        wsRef.current.send(JSON.stringify({ type: 'pc_click', x: -1, y: -1 }));
      }
    }
  };

  return (
    <div className="w-screen min-h-screen bg-slate-950 flex flex-col p-4 text-white font-sans">
      
      {/* Header */}
      <div className="flex items-center justify-between mb-6 border-b border-slate-800 pb-4">
        <div>
          <h1 className="text-xl font-bold bg-gradient-to-r from-blue-400 to-indigo-500 bg-clip-text text-transparent">
            SmartDisplay AI
          </h1>
          <p className="text-slate-400 text-sm">Panel de Control Inteligente</p>
        </div>
        <div className="flex items-center gap-2">
          <div className={`w-3 h-3 rounded-full ${connected ? 'bg-green-500 shadow-[0_0_10px_rgba(34,197,94,0.5)]' : 'bg-red-500'}`} />
          <span className="text-xs font-medium text-slate-300">{connected ? 'PC Enlazado' : 'Desconectado'}</span>
        </div>
      </div>

      {/* Main Actions */}
      <div className="grid grid-cols-2 gap-4 mb-8">
        <button 
          onClick={() => sendCommand('youtube')}
          className="bg-indigo-600 hover:bg-indigo-500 transition-colors p-4 rounded-2xl flex flex-col items-center justify-center gap-3 shadow-lg"
        >
          <Play size={28} className="text-white" />
          <span className="font-semibold text-sm">Abrir YouTube</span>
        </button>
        <button 
          onClick={() => sendCommand('mute')}
          className="bg-slate-800 hover:bg-slate-700 transition-colors p-4 rounded-2xl flex flex-col items-center justify-center gap-3 shadow-lg border border-slate-700"
        >
          <Volume2 size={28} className="text-blue-400" />
          <span className="font-semibold text-sm">Silenciar PC</span>
        </button>
        <button 
          onClick={() => sendCommand('macro_ia')}
          className="bg-slate-800 hover:bg-slate-700 transition-colors p-4 rounded-2xl flex flex-col items-center justify-center gap-3 shadow-lg border border-slate-700"
        >
          <Command size={28} className="text-pink-400" />
          <span className="font-semibold text-sm">Macro IA</span>
        </button>
        <button 
          onClick={() => sendCommand('shutdown')}
          className="bg-red-900/50 hover:bg-red-800/50 transition-colors p-4 rounded-2xl flex flex-col items-center justify-center gap-3 shadow-lg border border-red-900/50"
        >
          <Power size={28} className="text-red-400" />
          <span className="font-semibold text-sm">Apagar PC</span>
        </button>
      </div>

      {/* Wireless Touchpad & Smart Click Control */}
      <div className="bg-slate-900 rounded-2xl p-5 border border-slate-800 shadow-xl mb-6 space-y-4">
        <div className="flex justify-between items-center">
          <h2 className="text-lg font-bold flex items-center gap-2">
            <MousePointer size={20} className="text-blue-400" />
            Touchpad Virtual
          </h2>
          <label className="flex items-center gap-2 cursor-pointer bg-slate-950 px-3 py-1.5 rounded-xl border border-slate-800">
            <input 
              type="checkbox" 
              checked={smartClickEnabled} 
              onChange={() => setSmartClickEnabled(!smartClickEnabled)}
              className="accent-blue-500 rounded cursor-pointer"
            />
            <span className="text-xs font-semibold text-slate-300 flex items-center gap-1">
              <Sparkles size={14} className="text-amber-400" /> Smart Click
            </span>
          </label>
        </div>

        {/* Touchpad Area */}
        <div 
          ref={touchpadRef}
          onTouchStart={handleTouchpadTouchStart}
          onTouchMove={handleTouchpadTouchMove}
          onTouchEnd={handleTouchpadTouchEnd}
          onClick={handleTouchpadTap}
          className="w-full h-36 bg-slate-950 rounded-xl border border-slate-800 hover:border-blue-500/30 active:border-blue-500/50 flex flex-col items-center justify-center transition-all cursor-crosshair shadow-inner"
        >
          <MousePointer size={24} className="text-slate-700 animate-pulse mb-1" />
          <p className="text-slate-500 text-[11px] font-medium uppercase tracking-wider">
            Desliza para mover • Toca para hacer clic
          </p>
        </div>
      </div>

      {/* Launch App Panel */}
      <form onSubmit={handleLaunchApp} className="bg-slate-900 rounded-2xl p-5 border border-slate-800 shadow-xl mb-6 space-y-3">
        <h2 className="text-lg font-bold flex items-center gap-2">
          <Command size={20} className="text-pink-400" />
          Lanzar Aplicación
        </h2>
        <p className="text-slate-400 text-xs leading-relaxed">
          Escribe el nombre de un ejecutable para iniciarlo en tu laptop (ej: notepad, calc, cmd).
        </p>
        <div className="flex gap-2">
          <input 
            type="text" 
            placeholder="Ej: cmd, notepad, calc..." 
            value={appNameInput}
            onChange={(e) => setAppNameInput(e.target.value)}
            className="flex-1 bg-slate-950 border border-slate-800 focus:border-indigo-500 rounded-xl px-3 py-2 text-sm outline-none text-white transition-all"
          />
          <button 
            type="submit" 
            className="bg-pink-600 hover:bg-pink-500 text-white font-bold px-4 py-2 text-xs rounded-xl transition-all"
          >
            Lanzar
          </button>
        </div>
      </form>

      {/* Streaming Instructions */}
      <div className="mt-auto bg-slate-900 rounded-2xl p-5 border border-slate-800 shadow-xl">
        <h2 className="text-lg font-bold mb-2 flex items-center gap-2">
          <Monitor size={20} className="text-indigo-400" />
          Transmisión de Pantalla
        </h2>
        <p className="text-slate-400 text-sm mb-4 leading-relaxed">
          Para ver y controlar la pantalla de tu PC con latencia cero a 60 FPS, abre la aplicación Moonlight en tu dispositivo.
        </p>

        {/* Formulario de Enlace de PIN */}
        <form onSubmit={submitPin} className="mb-4 space-y-2">
          <label className="block text-xs font-bold uppercase tracking-wider text-slate-400">
            Enlace Rápido por PIN
          </label>
          <div className="flex gap-2">
            <input 
              type="text" 
              placeholder="Ingresa el PIN de Moonlight" 
              value={pin}
              onChange={(e) => setPin(e.target.value)}
              className="flex-1 bg-slate-950 border border-slate-800 focus:border-indigo-500 rounded-xl px-3 py-2 text-sm text-center font-mono outline-none text-white transition-all"
            />
            <button 
              type="submit" 
              className="bg-indigo-600 hover:bg-indigo-500 text-white font-bold px-4 py-2 text-xs rounded-xl transition-all"
            >
              Enlazar
            </button>
          </div>
          {pairingStatus && (
            <p className={`text-[11px] font-semibold text-center ${pairingStatus.includes('éxito') ? 'text-emerald-400' : 'text-amber-400'}`}>
              {pairingStatus}
            </p>
          )}
        </form>

        <button 
          onClick={() => window.location.href = 'intent://#Intent;package=com.limelight;end'}
          className="w-full bg-slate-800 hover:bg-slate-700 text-white font-semibold py-3 rounded-xl transition-all border border-slate-700"
        >
          Lanzar Moonlight
        </button>
      </div>

    </div>
  );
}
