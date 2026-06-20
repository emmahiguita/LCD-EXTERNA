// src/components/keyboard/types.ts

export type KeyboardMode = 'text' | 'numbers' | 'symbols' | 'dev';

export interface ModifierState {
  ctrl: boolean;
  alt: boolean;
  shift: boolean;
  win: boolean;
}

export interface VirtualKeyboardState {
  showVirtualKeyboard: boolean;
  keyboardMode: KeyboardMode;
  keyboardShift: boolean;
  keyboardInput: string;
  lastKeyFlash: string | null;
  activeModifiers: ModifierState;
}

export interface KeyboardKey {
  key: string;
  display: string;
  code?: string;
}

export interface MacroItem {
  label: string;
  keys: string;
  combo: string;
  color: 'slate' | 'emerald' | 'blue' | 'cyan' | 'amber' | 'purple' | 'red';
}

export interface DevMacroItem {
  label: string;
  keycode: string;
  icon: string;
  color: 'cyan' | 'emerald' | 'purple' | 'blue';
}

export interface NavigationKey {
  key: string;
  code: string;
}

export interface FunctionKey {
  num: number;
}

export interface KeyboardActions {
  runAction: (action: string, desc: string, payload: Record<string, unknown>) => Promise<void>;
  addLog: (msg: string) => void;
  setKeyboardInput: (value: string | ((prev: string) => string)) => void;
  setKeyboardShift: (value: boolean | ((prev: boolean) => boolean)) => void;
  setDevMode: (value: boolean | ((prev: boolean) => boolean)) => void;
  setLastKeyFlash: (value: string | null) => void;
  setActiveModifiers: (value: ModifierState | ((prev: ModifierState) => ModifierState)) => void;
  setShowVirtualKeyboard: (value: boolean) => void;
}

// Payload types for runAction — MUST MATCH EXISTING FORMAT
export interface KeyEventPayload {
  keycode: string;
  modifiers?: ModifierState;
}

export interface TextEventPayload {
  text: string;
  modifiers?: ModifierState;
}
