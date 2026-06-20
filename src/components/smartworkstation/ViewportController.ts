/**
 * ViewportController
 *
 * Gestiona zoom, pan, escala y rotación del viewport remoto.
 * El usuario tiene control total - sin automáticos.
 * Principio SOLID: Single Responsibility (solo viewport).
 */

export type ViewportMode = 'view' | 'hand' | 'zoom';
export type ZoomLevel = 100 | 125 | 150 | 175 | 200;

export interface ViewportState {
  // Pan (posición)
  translateX: number;
  translateY: number;

  // Zoom
  zoomLevel: ZoomLevel;

  // Rotación
  rotation: 0 | 90 | 180 | 270;

  // Modo activo
  mode: ViewportMode;
}

export interface ViewportController {
  /**
   * Modo 🤚 (Hand Mode): Permite arrastrar y escalar manualmente
   */
  enableHandMode(): void;
  disableHandMode(): void;

  /**
   * Controles de pan (solo en hand mode)
   */
  pan(deltaX: number, deltaY: number): void;
  setPan(x: number, y: number): void;
  getPan(): { x: number; y: number };

  /**
   * Controles de zoom (con niveles discretos)
   */
  setZoom(level: ZoomLevel): void;
  getZoom(): ZoomLevel;
  zoomIn(): void;
  zoomOut(): void;

  /**
   * Centrado
   */
  center(): void; // Posición 0,0
  fitToScreen(width: number, height: number): void;
  pixelPerfect(): void; // 100% zoom

  /**
   * Rotación (0, 90, 180, 270)
   */
  rotate(degrees: 0 | 90 | 180 | 270): void;
  getRotation(): 0 | 90 | 180 | 270;

  /**
   * Reset a estado inicial
   */
  reset(): void;

  /**
   * Suscriptor de cambios
   */
  subscribe(callback: (state: ViewportState) => void): () => void;
  getState(): ViewportState;
}

/**
 * Implementación base del ViewportController
 */
export class ViewportControllerImpl implements ViewportController {
  private state: ViewportState = {
    translateX: 0,
    translateY: 0,
    zoomLevel: 100,
    rotation: 0,
    mode: 'view',
  };

  private subscribers: Set<(state: ViewportState) => void> = new Set();

  private notifySubscribers() {
    this.subscribers.forEach(cb => cb({ ...this.state }));
  }

  enableHandMode(): void {
    this.state.mode = 'hand';
    this.notifySubscribers();
  }

  disableHandMode(): void {
    this.state.mode = 'view';
    this.notifySubscribers();
  }

  pan(deltaX: number, deltaY: number): void {
    if (this.state.mode !== 'hand') return;
    this.state.translateX += deltaX;
    this.state.translateY += deltaY;
    this.notifySubscribers();
  }

  setPan(x: number, y: number): void {
    this.state.translateX = x;
    this.state.translateY = y;
    this.notifySubscribers();
  }

  getPan() {
    return { x: this.state.translateX, y: this.state.translateY };
  }

  setZoom(level: ZoomLevel): void {
    if (![100, 125, 150, 175, 200].includes(level)) return;
    this.state.zoomLevel = level;
    this.notifySubscribers();
  }

  getZoom(): ZoomLevel {
    return this.state.zoomLevel;
  }

  zoomIn(): void {
    const levels: ZoomLevel[] = [100, 125, 150, 175, 200];
    const idx = levels.indexOf(this.state.zoomLevel);
    if (idx < levels.length - 1) {
      this.setZoom(levels[idx + 1]);
    }
  }

  zoomOut(): void {
    const levels: ZoomLevel[] = [100, 125, 150, 175, 200];
    const idx = levels.indexOf(this.state.zoomLevel);
    if (idx > 0) {
      this.setZoom(levels[idx - 1]);
    }
  }

  center(): void {
    this.state.translateX = 0;
    this.state.translateY = 0;
    this.notifySubscribers();
  }

  fitToScreen(width: number, height: number): void {
    // Aquí se calculará escala automática basada en viewport disponible
    // Por ahora, normalizamos
    this.center();
    this.setZoom(100);
  }

  pixelPerfect(): void {
    this.setZoom(100);
  }

  rotate(degrees: 0 | 90 | 180 | 270): void {
    this.state.rotation = degrees;
    this.notifySubscribers();
  }

  getRotation(): 0 | 90 | 180 | 270 {
    return this.state.rotation;
  }

  reset(): void {
    this.state = {
      translateX: 0,
      translateY: 0,
      zoomLevel: 100,
      rotation: 0,
      mode: 'view',
    };
    this.notifySubscribers();
  }

  subscribe(callback: (state: ViewportState) => void): () => void {
    this.subscribers.add(callback);
    return () => this.subscribers.delete(callback);
  }

  getState(): ViewportState {
    return { ...this.state };
  }
}

