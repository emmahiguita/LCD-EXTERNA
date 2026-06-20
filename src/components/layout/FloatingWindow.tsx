// src/components/layout/FloatingWindow.tsx
// Floating, draggable, resizable window container.
// Full window controls: move, resize (8 directions), minimize, maximize, close.
// Uses a portal to render outside normal flow.

'use client';

import React, { useCallback, useEffect, useRef, useState, memo } from 'react';
import { createPortal } from 'react-dom';
import { Minus, Maximize2, Minimize2, X, GripHorizontal } from 'lucide-react';

export type WindowState = 'normal' | 'minimized' | 'maximized';

interface FloatingWindowProps {
  id: string;
  title: string;
  children: React.ReactNode;
  defaultPosition?: { x: number; y: number };
  defaultSize?: { width: number; height: number };
  minWidth?: number;
  minHeight?: number;
  onClose?: () => void;
  dark?: boolean;
  /** If true, always renders inside parent (no portal) */
  inline?: boolean;
}

const RESIZE_HANDLES = ['n', 's', 'e', 'w', 'ne', 'nw', 'se', 'sw'] as const;
type ResizeDir = typeof RESIZE_HANDLES[number];

function FloatingWindowInner({
  id, title, children,
  defaultPosition = { x: 100, y: 80 },
  defaultSize = { width: 640, height: 480 },
  minWidth = 320,
  minHeight = 240,
  onClose,
  dark = true,
  inline = false,
}: FloatingWindowProps) {
  const [windowState, setWindowState] = useState<WindowState>('normal');
  const [position, setPosition] = useState(defaultPosition);
  const [size, setSize] = useState(defaultSize);
  const [isDragging, setIsDragging] = useState(false);
  const [isResizing, setIsResizing] = useState<ResizeDir | null>(null);

  const dragRef = useRef({ startX: 0, startY: 0, startPosX: 0, startPosY: 0 });
  const resizeRef = useRef({ startX: 0, startY: 0, startW: 0, startH: 0, startPosX: 0, startPosY: 0 });
  const windowRef = useRef<HTMLDivElement>(null);
  const prevStateRef = useRef<{ position: { x: number; y: number }; size: { width: number; height: number } } | null>(null);

  // ─── Clamp to viewport ───────────────────────────────────────────────
  const clampPosition = useCallback((x: number, y: number, w: number, h: number) => {
    const vw = window.innerWidth;
    const vh = window.innerHeight;
    const margin = 8;
    return {
      x: Math.max(-w + margin, Math.min(vw - margin, x)),
      y: Math.max(-h + margin, Math.min(vh - margin, y)),
    };
  }, []);

  // ─── Drag Logic ──────────────────────────────────────────────────────
  const handleDragStart = useCallback((e: React.PointerEvent) => {
    if (windowState !== 'normal') return;
    setIsDragging(true);
    dragRef.current = {
      startX: e.clientX,
      startY: e.clientY,
      startPosX: position.x,
      startPosY: position.y,
    };
    (e.target as HTMLElement).setPointerCapture(e.pointerId);
  }, [position, windowState]);

  const handleDragMove = useCallback((e: React.PointerEvent) => {
    if (!isDragging) return;
    const dx = e.clientX - dragRef.current.startX;
    const dy = e.clientY - dragRef.current.startY;
    const clamped = clampPosition(
      dragRef.current.startPosX + dx,
      dragRef.current.startPosY + dy,
      size.width,
      size.height,
    );
    setPosition(clamped);
  }, [isDragging, size, clampPosition]);

  const handleDragEnd = useCallback(() => {
    setIsDragging(false);
  }, []);

  // ─── Resize Logic ───────────────────────────────────────────────────
  const handleResizeStart = useCallback((dir: ResizeDir, e: React.PointerEvent) => {
    if (windowState !== 'normal') return;
    e.stopPropagation();
    setIsResizing(dir);
    resizeRef.current = {
      startX: e.clientX,
      startY: e.clientY,
      startW: size.width,
      startH: size.height,
      startPosX: position.x,
      startPosY: position.y,
    };
    (e.target as HTMLElement).setPointerCapture(e.pointerId);
  }, [size, position, windowState]);

  const handleResizeMove = useCallback((e: React.PointerEvent) => {
    if (!isResizing) return;
    const dx = e.clientX - resizeRef.current.startX;
    const dy = e.clientY - resizeRef.current.startY;
    const r = resizeRef.current;
    let newW = r.startW;
    let newH = r.startH;
    let newX = r.startPosX;
    let newY = r.startPosY;

    if (isResizing.includes('e')) newW = Math.max(minWidth, r.startW + dx);
    if (isResizing.includes('w')) {
      const delta = Math.min(dx, r.startW - minWidth);
      newW = r.startW - delta;
      newX = r.startPosX + delta;
    }
    if (isResizing.includes('s')) newH = Math.max(minHeight, r.startH + dy);
    if (isResizing.includes('n')) {
      const delta = Math.min(dy, r.startH - minHeight);
      newH = r.startH - delta;
      newY = r.startPosY + delta;
    }

    const clamped = clampPosition(newX, newY, newW, newH);
    setPosition(clamped);
    setSize({ width: newW, height: newH });
  }, [isResizing, minWidth, minHeight, clampPosition]);

  const handleResizeEnd = useCallback(() => {
    setIsResizing(null);
  }, []);

  // ─── Window State Controls ───────────────────────────────────────────
  const handleMinimize = useCallback(() => {
    if (windowState === 'minimized') {
      // Restore
      if (prevStateRef.current) {
        setPosition(prevStateRef.current.position);
        setSize(prevStateRef.current.size);
      }
      setWindowState('normal');
    } else {
      prevStateRef.current = { position, size };
      setWindowState('minimized');
    }
  }, [windowState, position, size]);

  const handleMaximize = useCallback(() => {
    if (windowState === 'maximized') {
      // Restore
      if (prevStateRef.current) {
        setPosition(prevStateRef.current.position);
        setSize(prevStateRef.current.size);
      }
      setWindowState('normal');
    } else {
      prevStateRef.current = { position, size };
      setPosition({ x: 8, y: 8 });
      setSize({ width: window.innerWidth - 16, height: window.innerHeight - 16 });
      setWindowState('maximized');
    }
  }, [windowState, position, size]);

  // ─── Pointer events for drag + resize ───────────────────────────────
  useEffect(() => {
    if (!isDragging && !isResizing) return;
    const handleMove = isDragging ? handleDragMove : handleResizeMove;
    const handleUp = isDragging ? handleDragEnd : handleResizeEnd;

    const onPointerMove = (e: PointerEvent) => {
      handleMove(e as unknown as React.PointerEvent);
    };
    const onPointerUp = () => {
      handleUp();
    };

    window.addEventListener('pointermove', onPointerMove);
    window.addEventListener('pointerup', onPointerUp);
    return () => {
      window.removeEventListener('pointermove', onPointerMove);
      window.removeEventListener('pointerup', onPointerUp);
    };
  }, [isDragging, isResizing, handleDragMove, handleResizeMove, handleDragEnd, handleResizeEnd]);

  // ─── Keyboard shortcuts ──────────────────────────────────────────────
  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && windowState === 'maximized') {
        handleMaximize();
      }
    };
    window.addEventListener('keydown', handler);
    return () => window.removeEventListener('keydown', handler);
  }, [windowState, handleMaximize]);

  // ─── Content ─────────────────────────────────────────────────────────
  const isMinimized = windowState === 'minimized';
  const isMaximized = windowState === 'maximized';

  const resizeHandle = (dir: ResizeDir) => (
    <div
      key={dir}
      onPointerDown={(e) => handleResizeStart(dir, e)}
      className={`absolute z-30 ${getResizeCursor(dir)} ${
        dir.length === 1
          ? dir === 'n' || dir === 's'
            ? 'left-1 right-1 h-2 cursor-ns-resize'
            : 'top-1 bottom-1 w-2 cursor-ew-resize'
          : 'w-4 h-4'
      } ${
        dir === 'n' ? 'top-0' : ''
      } ${
        dir === 's' ? 'bottom-0' : ''
      } ${
        dir === 'e' ? 'right-0' : ''
      } ${
        dir === 'w' ? 'left-0' : ''
      } ${
        dir === 'ne' ? 'top-0 right-0 cursor-ne-resize' : ''
      } ${
        dir === 'nw' ? 'top-0 left-0 cursor-nw-resize' : ''
      } ${
        dir === 'se' ? 'bottom-0 right-0 cursor-se-resize' : ''
      } ${
        dir === 'sw' ? 'bottom-0 left-0 cursor-sw-resize' : ''
      }`}
    />
  );

  const windowContent = (
    <div
      ref={windowRef}
      id={`floating-window-${id}`}
      className={`fixed z-50 flex flex-col overflow-hidden rounded-2xl border shadow-2xl transition-shadow duration-200 ${
        dark
          ? 'bg-[#0f1120]/95 border-slate-700/50 backdrop-blur-xl'
          : 'bg-white/95 border-slate-200 backdrop-blur-xl'
      } ${isDragging || isResizing ? 'shadow-cyan-500/20' : ''} ${
        isMinimized ? 'h-auto min-h-0' : ''
      }`}
      style={{
        left: isMaximized ? 0 : position.x,
        top: isMaximized ? 0 : position.y,
        width: isMaximized ? '100vw' : size.width,
        height: isMinimized ? 'auto' : isMaximized ? '100vh' : size.height,
        transform: `translate(${isMinimized ? 0 : 0}px, ${isMinimized ? 0 : 0}px)`,
        pointerEvents: 'auto',
      }}
    >
      {/* ── Title Bar (drag handle) ───────────────────────────── */}
      <div
        onPointerDown={handleDragStart}
        onPointerMove={handleDragMove}
        onPointerUp={handleDragEnd}
        onPointerCancel={handleDragEnd}
        className={`flex items-center justify-between px-3 py-2 shrink-0 select-none ${
          dark ? 'bg-slate-800/60 border-b border-slate-700/40' : 'bg-slate-100 border-b border-slate-200'
        } ${windowState === 'normal' ? 'cursor-grab active:cursor-grabbing' : 'cursor-default'}`}
      >
        <div className="flex items-center gap-2 min-w-0">
          <GripHorizontal size={14} className={`shrink-0 ${dark ? 'text-white/30' : 'text-slate-400'}`} />
          <span className={`text-[12px] font-bold truncate ${dark ? 'text-white/80' : 'text-slate-700'}`}>
            {title}
          </span>
        </div>

        <div className="flex items-center gap-1 shrink-0">
          {/* Minimize */}
          <button
            onClick={handleMinimize}
            className={`p-1.5 rounded-lg transition-all ${
              dark ? 'hover:bg-white/10 text-white/40 hover:text-white/70' : 'hover:bg-slate-200 text-slate-500'
            }`}
            aria-label={isMinimized ? 'Restaurar' : 'Minimizar'}
            title={isMinimized ? 'Restaurar' : 'Minimizar'}
          >
            <Minus size={14} />
          </button>

          {/* Maximize / Restore */}
          <button
            onClick={handleMaximize}
            className={`p-1.5 rounded-lg transition-all ${
              dark ? 'hover:bg-white/10 text-white/40 hover:text-white/70' : 'hover:bg-slate-200 text-slate-500'
            }`}
            aria-label={isMaximized ? 'Restaurar' : 'Maximizar'}
            title={isMaximized ? 'Restaurar' : 'Maximizar'}
          >
            {isMaximized ? <Minimize2 size={14} /> : <Maximize2 size={14} />}
          </button>

          {/* Close */}
          {onClose && (
            <button
              onClick={onClose}
              className={`p-1.5 rounded-lg transition-all ${
                dark ? 'hover:bg-red-500/20 text-white/40 hover:text-red-400' : 'hover:bg-red-100 text-slate-500 hover:text-red-500'
              }`}
              aria-label="Cerrar"
              title="Cerrar"
            >
              <X size={14} />
            </button>
          )}
        </div>
      </div>

      {/* ── Content ─────────────────────────────────────────────── */}
      <div className={`flex-1 overflow-auto ${isMinimized ? 'hidden' : ''}`}>
        {children}
      </div>

      {/* ── Resize Handles (only in normal state) ──────────────── */}
      {windowState === 'normal' && !isMinimized && RESIZE_HANDLES.map(resizeHandle)}
    </div>
  );

  // Portal or inline rendering
  if (inline) return windowContent;
  if (typeof document === 'undefined') return null; // SSR guard
  return createPortal(windowContent, document.body);
}

function getResizeCursor(dir: ResizeDir): string {
  switch (dir) {
    case 'n': case 's': return 'cursor-ns-resize';
    case 'e': case 'w': return 'cursor-ew-resize';
    case 'ne': case 'sw': return 'cursor-nesw-resize';
    case 'nw': case 'se': return 'cursor-nwse-resize';
  }
}

export const FloatingWindow = memo(FloatingWindowInner);
