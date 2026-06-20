// src/components/keyboard/OptimizedKeyboard.tsx
// 🎹 TECLADO OPTIMIZADO - SIN REDUNDANCIA - ARQUITECTURA CORRECTA
//
// ✅ CARACTERÍSTICAS:
// • Single instance - Renderizado una sola vez
// • Fixed overlay - Completamente independiente del stream
// • No afecta posición de pantalla remota
// • Validación automática de integridad de posición
// • Código muerto eliminado - Solo lógica necesaria

'use client';

import React, { useCallback, memo, useRef, useState, useEffect } from 'react';
import { Send, X } from 'lucide-react';
import type { ModifierState } from './types';
import './OptimizedKeyboard.css';

export type KeyboardMode = 'text' | 'numbers' | 'symbols' | 'dev' | 'functions' | 'navigation';

export interface OptimizedKeyboardProps {
  isOpen: boolean;
  keyboardMode: KeyboardMode;
  keyboardShift: boolean;
  keyboardInput: string;
  lastKeyFlash: string | null;
  activeModifiers: ModifierState;
  onKeyPress: (char: string) => void;
  onBackspace: () => void;
  onSpace: () => void;
  onEnter: () => void;
  onShiftToggle: () => void;
  onModeChange: (mode: KeyboardMode) => void;
  onShortcut: (keycode: string, description: string) => void;
  onClose: () => void;
  dark: boolean;
}

// ────────────────────────────────────────────────────────────────────────────
// LAYOUT DEFINITIONS (SIN REDUNDANCIA)
// ────────────────────────────────────────────────────────────────────────────

const CHAR_ROWS = {
  text: [
    ['Q','W','E','R','T','Y','U','I','O','P'],
    ['A','S','D','F','G','H','J','K','L','Ñ'],
    ['Z','X','C','V','B','N','M',',','.'],
  ],
  numbers: [
    ['1','2','3','4','5','6','7','8','9','0'],
    ['+','-','*','/','=','%','(',')','.',','],
    ['!','@','#','$','^','&','|','~','`','\\'],
  ],
  symbols: [
    ['<','>','{','}','[',']',':',';',"'",'"'],
    ['€','£','¥','©','®','™','°','±','×','÷'],
    ['¡','¿','µ','·','•','…','–','—','«','»'],
  ],
};

const FUNCTION_KEYS = [
  { label: 'F1', code: 'KEYCODE_F1' },
  { label: 'F2', code: 'KEYCODE_F2' },
  { label: 'F3', code: 'KEYCODE_F3' },
  { label: 'F4', code: 'KEYCODE_F4' },
  { label: 'F5', code: 'KEYCODE_F5' },
  { label: 'F6', code: 'KEYCODE_F6' },
  { label: 'F7', code: 'KEYCODE_F7' },
  { label: 'F8', code: 'KEYCODE_F8' },
  { label: 'F9', code: 'KEYCODE_F9' },
  { label: 'F10', code: 'KEYCODE_F10' },
  { label: 'F11', code: 'KEYCODE_F11' },
  { label: 'F12', code: 'KEYCODE_F12' },
];

const NAVIGATION_KEYS = [
  { label: 'Esc', code: 'KEYCODE_ESCAPE' },
  { label: 'Tab', code: 'KEYCODE_TAB' },
  { label: 'Ctrl', code: 'KEYCODE_CTRL_LEFT' },
  { label: 'Alt', code: 'KEYCODE_ALT_LEFT' },
  { label: 'Win', code: 'KEYCODE_META_LEFT' },
  { label: '←', code: 'KEYCODE_DPAD_LEFT' },
  { label: '↑', code: 'KEYCODE_DPAD_UP' },
  { label: '↓', code: 'KEYCODE_DPAD_DOWN' },
  { label: '→', code: 'KEYCODE_DPAD_RIGHT' },
  { label: '⌦', code: 'KEYCODE_FORWARD_DEL' },
];

const DEV_SHORTCUTS = [
  { label: 'C+C', code: 'KEYCODE_COPY' },
  { label: 'C+V', code: 'KEYCODE_PASTE' },
  { label: 'C+X', code: 'KEYCODE_CUT' },
  { label: 'C+Z', code: 'KEYCODE_UNDO' },
  { label: 'C+S', code: 'KEYCODE_SAVE' },
  { label: 'C+A', code: 'KEYCODE_SELECT_ALL' },
  { label: 'C+F', code: 'KEYCODE_FIND' },
  { label: 'C⇧P', code: 'KEYCODE_COMMAND_PALETTE' },
  { label: 'A+Tab', code: 'KEYCODE_ALT_TAB' },
  { label: 'W+D', code: 'KEYCODE_SHOW_DESKTOP' },
  { label: 'Term', code: 'KEYCODE_TERMINAL' },
  { label: 'DevTools', code: 'KEYCODE_DEVTOOLS' },
];

// ────────────────────────────────────────────────────────────────────────────
// STYLE CONSTANTS
// ────────────────────────────────────────────────────────────────────────────

const KEY_BASE = 'flex-1 min-w-0 h-[30px] flex items-center justify-center rounded-md text-[11px] font-semibold select-none touch-none focus:outline-none transition-colors duration-75';
const KEY_NORMAL = 'bg-white/[0.08] active:bg-white/25 text-white/85';
const KEY_SPECIAL = 'bg-white/[0.05] active:bg-white/20 text-white/55';
const KEY_ACCENT = 'bg-cyan-500/35 text-white';

interface DragOrigin { cx: number; cy: number; px: number; py: number; }

// ────────────────────────────────────────────────────────────────────────────
// COMPONENT
// ────────────────────────────────────────────────────────────────────────────

function OptimizedKeyboardInner({
  isOpen, keyboardMode, keyboardShift, keyboardInput, lastKeyFlash, activeModifiers,
  onKeyPress, onBackspace, onSpace, onEnter, onShiftToggle, onModeChange, onShortcut, onClose,
  dark
}: OptimizedKeyboardProps) {

  // Position state — persists on open/close
  const [pos, setPos] = useState({ x: 0, y: 0 });
  const [dragging, setDragging] = useState(false);
  const posRef = useRef({ x: 0, y: 0 });
  const originRef = useRef<DragOrigin | null>(null);
  const bodyRef = useRef<HTMLDivElement>(null);

  // Store position BEFORE opening for validation
  const savedPosRef = useRef<{ x: number; y: number } | null>(null);

  // Sync posRef
  useEffect(() => { posRef.current = pos; }, [pos]);

   // ── VALIDATE POSITION ON OPEN/CLOSE ────────────────────────────────────
   // Also prevent body scroll when keyboard is open
   useEffect(() => {
     if (isOpen && !savedPosRef.current) {
       // About to open → save current position and prevent scroll
       savedPosRef.current = { ...posRef.current };
       // Prevent body scroll when keyboard is open
       document.documentElement.classList.add('keyboard-open');
       document.body.classList.add('keyboard-open');
       document.body.style.overflow = 'hidden';
       document.body.style.touchAction = 'none';
     } else if (!isOpen && savedPosRef.current) {
       // Closed → validate position didn't change unexpectedly
       const diff = Math.abs(posRef.current.x - savedPosRef.current.x) +
                    Math.abs(posRef.current.y - savedPosRef.current.y);
       if (diff > 10) {
         // Position shifted! Restore it.
         console.warn('⚠️ KEYBOARD BUG: Position changed by', diff, 'px. Restoring...');
         setPos({ ...savedPosRef.current });
       }
       savedPosRef.current = null;
       // Restore body scroll
       document.documentElement.classList.remove('keyboard-open');
       document.body.classList.remove('keyboard-open');
       document.body.style.overflow = '';
       document.body.style.touchAction = '';
     }
   }, [isOpen]);

  // ── DRAG HANDLER (WINDOW-LEVEL) ────────────────────────────────────────
  const startDrag = useCallback((e: React.PointerEvent<HTMLElement>) => {
    e.preventDefault();
    originRef.current = {
      cx: e.clientX, cy: e.clientY,
      px: posRef.current.x, py: posRef.current.y,
    };
    setDragging(true);
  }, []);

  useEffect(() => {
    if (!dragging) return;

    const onMove = (e: PointerEvent) => {
      const o = originRef.current;
      if (!o) return;
      const el = bodyRef.current;
      const rawX = o.px + (e.clientX - o.cx);
      const rawY = o.py + (e.clientY - o.cy);
      if (el) {
        const r = el.getBoundingClientRect();
        const W = window.innerWidth, H = window.innerHeight;
        const natL = r.left - posRef.current.x;
        const natT = r.top - posRef.current.y;
        const vis = 32;
        setPos({
          x: Math.max(vis - r.width - natL, Math.min(W - vis - natL, rawX)),
          y: Math.max(vis - r.height - natT, Math.min(H - vis - natT, rawY)),
        });
      } else {
        setPos({ x: rawX, y: rawY });
      }
    };
    const onEnd = () => setDragging(false);

    window.addEventListener('pointermove', onMove, { passive: true });
    window.addEventListener('pointerup', onEnd);
    window.addEventListener('pointercancel', onEnd);
    return () => {
      window.removeEventListener('pointermove', onMove);
      window.removeEventListener('pointerup', onEnd);
      window.removeEventListener('pointercancel', onEnd);
    };
  }, [dragging]);

  // ── RENDER CHAR ROW ────────────────────────────────────────────────────
  const renderCharRow = (chars: string[]) => (
    <div className="flex gap-0.5">
      {chars.map(k => (
        <button
          key={k}
          onPointerDown={(e) => { e.preventDefault(); onKeyPress(k); }}
          className={`keyboard-key-button ${KEY_BASE} ${lastKeyFlash === k ? KEY_ACCENT : KEY_NORMAL}`}
          type="button"
        >
          {keyboardMode === 'text' && keyboardShift ? k.toUpperCase() : k}
        </button>
      ))}
    </div>
  );

  // ── RENDER SHORTCUT BUTTON ─────────────────────────────────────────────
  const renderShortcut = (item: { label: string; code: string }) => (
    <button
      key={item.code}
      onPointerDown={(e) => { e.preventDefault(); onShortcut(item.code, item.label); }}
      className={`keyboard-key-button ${KEY_BASE} text-[10px] tracking-tight ${KEY_SPECIAL}`}
      type="button"
    >
      {item.label}
    </button>
  );

  // ────────────────────────────────────────────────────────────────────────
  // RENDER CONTENT BASED ON MODE
  // ────────────────────────────────────────────────────────────────────────

  const charRows = keyboardMode === 'numbers' ? CHAR_ROWS.numbers :
                   keyboardMode === 'symbols' ? CHAR_ROWS.symbols :
                   CHAR_ROWS.text;

  const contentView = (() => {
    // Character keyboard
    if (['text', 'numbers', 'symbols'].includes(keyboardMode)) {
      return (
        <>
          {renderCharRow(charRows[0])}
          {renderCharRow(charRows[1])}
          <div className="flex gap-0.5 items-center">
            {keyboardMode === 'text' && (
              <button
                onPointerDown={(e) => { e.preventDefault(); onShiftToggle(); }}
                className={`keyboard-key-button ${KEY_BASE} shrink-0 px-2 ${keyboardShift ? 'bg-cyan-500/25 text-cyan-300 border border-cyan-500/30' : KEY_NORMAL}`}
                type="button"
              >
                ⇧
              </button>
            )}
            {renderCharRow(charRows[2])}
            <button
              onPointerDown={(e) => { e.preventDefault(); onBackspace(); }}
              className={`keyboard-key-button ${KEY_BASE} shrink-0 px-2 ${KEY_SPECIAL}`}
              type="button"
            >
              ⌫
            </button>
          </div>
        </>
      );
    }

    // Function keys
    if (keyboardMode === 'functions') {
      return (
        <div className="grid gap-0.5" style={{ gridTemplateColumns: 'repeat(6, 1fr)' }}>
          {FUNCTION_KEYS.map(k => renderShortcut(k))}
        </div>
      );
    }

    // DEV shortcuts
    if (keyboardMode === 'dev') {
      return (
        <div className="grid gap-0.5" style={{ gridTemplateColumns: 'repeat(4, 1fr)' }}>
          {DEV_SHORTCUTS.map(k => renderShortcut(k))}
        </div>
      );
    }

    return null;
  })();

  // ────────────────────────────────────────────────────────────────────────

   return (
     // CRITICAL: position:fixed + z-50 = true overlay, NOT affecting stream layout
     // Fixed to viewport bottom - completely independent of document scroll
     <div
       className="keyboard-outer-container"
       aria-hidden={!isOpen}
     >
       <div
         ref={bodyRef}
         style={{ transform: `translate(${pos.x}px,${pos.y}px)` }}
         className={[
           'keyboard-inner-container',
           'w-[97vw] max-w-[600px]',
           'max-h-[45svh] landscape:max-h-[30svh] overflow-hidden',
           'transition-opacity duration-150',
           'rounded-xl shadow-[0_8px_32px_rgba(0,0,0,0.7)]',
           isOpen ? 'opacity-100 pointer-events-auto' : 'opacity-0 pointer-events-none',
           dragging ? 'cursor-grabbing' : '',
         ].join(' ')}
         role="toolbar"
         aria-label="Teclado virtual"
       >
        {/* ── DRAG STRIP ────────────────────────────────────────────────── */}
        <div
          onPointerDown={startDrag}
          className={`
            keyboard-drag-strip
            flex items-center gap-2 px-2.5 py-1
            bg-slate-800/90 border border-white/[0.08] rounded-t-xl
            select-none ${dragging ? 'dragging cursor-grabbing' : 'cursor-grab'}
          `}
        >
          {/* Active modifiers display */}
          {(['ctrl','alt','shift','win'] as const).map(k =>
            activeModifiers[k] ? (
              <span key={k} className="text-[8px] font-bold text-cyan-300 bg-cyan-500/20 px-1.5 py-0.5 rounded uppercase leading-none border border-cyan-500/15 shrink-0">
                {k}
              </span>
            ) : null
          )}

          {/* Text preview */}
          <span className="flex-1 min-w-0 font-mono text-[12px] text-white/80 truncate leading-none py-0.5">
            {keyboardInput || <span className="text-white/20 font-sans font-normal text-[11px]">SmartDisplay Keyboard</span>}
          </span>

          {/* Close button */}
          <button
            onPointerDown={(e) => { e.stopPropagation(); e.preventDefault(); onClose(); }}
            className="shrink-0 w-6 h-6 rounded-md bg-white/[0.06] active:bg-red-500/30 text-white/30 active:text-red-400 flex items-center justify-center"
            type="button"
          >
            <X size={12} />
          </button>
        </div>

        {/* ── KEY PANEL ──────────────────────────────────────────────────── */}
        <div className="keyboard-key-panel bg-black/95 border border-white/[0.08] border-t-0 rounded-b-xl p-1.5 space-y-1">

          {/* Navigation row — always visible */}
          <div className="flex gap-0.5">
            {NAVIGATION_KEYS.slice(0, 5).map(k => renderShortcut(k))}
            <div className="flex-1" />
            {NAVIGATION_KEYS.slice(5).map(k => renderShortcut(k))}
          </div>

          {/* Content area */}
          {contentView && <div className="space-y-1">{contentView}</div>}

          {/* ── ACTION BAR ─────────────────────────────────────────────── */}
          <div className="flex gap-1 items-center pt-0.5">
            {/* Mode selector */}
            {(['text', 'numbers', 'symbols', 'functions', 'dev'] as const).map(mode => (
              <button
                key={mode}
                onPointerDown={(e) => { e.preventDefault(); onModeChange(mode); }}
                className={[
                  'keyboard-key-button h-[28px] px-2 rounded-md text-[10px] font-bold touch-none shrink-0 transition-colors',
                  keyboardMode === mode
                    ? mode === 'numbers' ? 'bg-cyan-500 text-black'
                    : mode === 'symbols' ? 'bg-purple-500 text-white'
                    : mode === 'functions' ? 'bg-amber-500 text-black'
                    : mode === 'dev' ? 'bg-red-500 text-white'
                    : 'bg-white/80 text-black'
                    : 'bg-white/[0.07] text-white/40',
                ].join(' ')}
                type="button"
              >
                {mode === 'text' ? 'ABC' :
                 mode === 'numbers' ? '123' :
                 mode === 'symbols' ? '!@#' :
                 mode === 'functions' ? 'F1-12' :
                 'DEV'}
              </button>
            ))}

            {/* Space */}
            <button
              onPointerDown={(e) => { e.preventDefault(); onSpace(); }}
              className={`keyboard-key-button ${KEY_BASE} flex-1 ${KEY_NORMAL} text-white/40 text-[10px]`}
              type="button"
            >
              Espacio
            </button>

            {/* Enter */}
            <button
              onPointerDown={(e) => { e.preventDefault(); onEnter(); }}
              className="keyboard-key-button h-[28px] px-3 bg-emerald-600 active:bg-emerald-500 text-white text-[11px] font-bold rounded-md flex items-center gap-1 touch-none shrink-0"
              type="button"
            >
              <Send size={11} />
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}

export const OptimizedKeyboard = memo(OptimizedKeyboardInner);

