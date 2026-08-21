package com.limelight.ui;

import android.annotation.SuppressLint;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnLayoutChangeListener;
import android.widget.TextView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.limelight.R;

/**
 * OverlayFabController v6.0 — Fachada SOLID.
 *
 * Esta clase actúa únicamente como coordinadora de ciclo de vida y punto de
 * entrada para Game.java. Toda la lógica reside en los sub-controladores:
 *
 *   ├── FABController              → visual del FAB principal
 *   ├── RadialMenuController       → pool de ítems y caché de geometría
 *   ├── OverlayAnimationController → todas las animaciones
 *   ├── OverlayGestureController   → drag, snap, gestos táctiles
 *   ├── OverlayKeyboardController  → ajuste de posición con teclado
 *   └── TooltipController          → etiquetas flotantes on-demand
 *
 * La interfaz pública (OnActionListener + métodos) es 100% compatible con
 * la versión anterior, de modo que Game.java no necesita ningún cambio.
 */
public class OverlayFabController {

    // ── API pública (sin cambios respecto a versión anterior) ─────────────────
    public interface OnActionListener {
        void onKeyboardToggle();
        void onMoveToggle();
        default void onItemAction(int itemIndex) {}
        default void onItemLongAction(int itemIndex) {}
        default void onMouseModeToggle() {}
    }

    private static final int[] ITEM_IDS = {
        R.id.fabItem0, R.id.fabItem2, R.id.fabItem3,
        R.id.fabItem6, R.id.fabItem4, R.id.fabItem5,
        R.id.fabItem7, R.id.fabItem8
    };
    private static final int[] ICON_IDS = {
        R.id.fabIcon0, R.id.fabIcon2, R.id.fabIcon3,
        R.id.fabIcon6, R.id.fabIcon4, R.id.fabIcon5,
        R.id.fabIcon7, R.id.fabIcon8
    };

    // Códigos de acción estables (no posicionales) — preservados para Game.java
    public static final int A_KEYBOARD = RadialMenuController.A_KEYBOARD;
    public static final int A_ZOOM     = RadialMenuController.A_ZOOM;
    public static final int A_MOUSE    = RadialMenuController.A_MOUSE;
    public static final int A_FILES    = RadialMenuController.A_FILES;
    public static final int A_VOICE    = RadialMenuController.A_VOICE;
    public static final int A_PIP      = RadialMenuController.A_PIP;
    public static final int A_SCREEN   = RadialMenuController.A_SCREEN;
    public static final int A_EXIT     = RadialMenuController.A_EXIT;

    // Definición estática de los menús (sin TextView: las etiquetas van al tooltip)
    // 5 items en sentido horario: Zoom (top), Herramientas (top-der),
    // Archivos (bot-der), Voz (bot-izq), Teclado (bot).
    private static final RadialMenuController.Entry[] MAIN_ENTRIES = {
        new RadialMenuController.Entry(R.drawable.ic_move_overlay,    "Zoom",          RadialMenuController.A_ZOOM),
        new RadialMenuController.Entry(R.drawable.ic_tools_overlay,   "Herramientas",  RadialMenuController.A_TOOLS_OPEN),
        new RadialMenuController.Entry(R.drawable.ic_files_overlay,   "Archivos",      RadialMenuController.A_FILES),
        new RadialMenuController.Entry(R.drawable.ic_mic_overlay,     "Voz",           RadialMenuController.A_VOICE),
        new RadialMenuController.Entry(R.drawable.ic_keyboard_overlay,"Teclado",       RadialMenuController.A_KEYBOARD),
    };
    private static final RadialMenuController.Entry[] TOOLS_ENTRIES = {
        new RadialMenuController.Entry(R.drawable.ic_mouse_overlay,   "Modo Mouse",    RadialMenuController.A_MOUSE),
        new RadialMenuController.Entry(R.drawable.ic_pip_overlay,     "PiP",           RadialMenuController.A_PIP),
    };

    // ── Referencias de vistas ─────────────────────────────────────────────────
    private final View                   overlayRoot;
    private final FloatingActionButton   fabMain;
    private final View                   fabMainWrapper;
    private final View[]                 radialItems;
    private final android.widget.ImageView[] iconViews;

    // ── Sub-controladores ─────────────────────────────────────────────────────
    private final FABController              fabController;
    private final RadialMenuController       menuController;
    private final OverlayAnimationController animController;
    private final OverlayGestureController   gestureController;
    private final OverlayKeyboardController  keyboardController;
    private final TooltipController          tooltipController;

    // ── Estado interno ────────────────────────────────────────────────────────
    private boolean expanded = false;
    private OnActionListener listener;
    private OnLayoutChangeListener layoutChangeListener;

    @SuppressLint("ClickableViewAccessibility")
    public OverlayFabController(View rootView) {
        this.overlayRoot   = rootView;

        // ── 1. Inicializar vistas del pool ────────────────────────────────────
        this.fabMain       = rootView.findViewById(R.id.fabMain);
        this.fabMainWrapper = rootView.findViewById(R.id.fabMainWrapper);

        this.radialItems = new View[ITEM_IDS.length];
        this.iconViews   = new android.widget.ImageView[ITEM_IDS.length];
        for (int i = 0; i < ITEM_IDS.length; i++) {
            radialItems[i] = rootView.findViewById(ITEM_IDS[i]);
            iconViews[i]   = rootView.findViewById(ICON_IDS[i]);
        }

        // ── 2. Instanciar sub-controladores ───────────────────────────────────
        fabController = new FABController(fabMain, fabMainWrapper);

        menuController = new RadialMenuController(
                overlayRoot, radialItems, iconViews, MAIN_ENTRIES, TOOLS_ENTRIES);

        View fabScrim    = rootView.findViewById(R.id.fabScrim);
        View fabHaloPulse = rootView.findViewById(R.id.fabHaloPulse);
        View fabHaloGlow  = rootView.findViewById(R.id.fabHaloGlow);
        View fabOrbitRing = rootView.findViewById(R.id.fabOrbitRing);

        animController = new OverlayAnimationController(
                overlayRoot, fabMainWrapper, fabScrim, fabHaloPulse, fabHaloGlow, fabOrbitRing,
                fabController.isEffectsEnabled());

        animController.setOnCollapseFinished(() -> animController.startHaloPulse());

        // Inyectar el RadialRingView para dibujar anillos + spokes en Canvas
        RadialRingView ringView = rootView.findViewById(R.id.fabRadialRings);
        if (ringView != null) animController.setRadialRingView(ringView);

        gestureController = new OverlayGestureController(
                fabMain, fabMainWrapper, overlayRoot,
                new OverlayGestureController.GestureListener() {
                    @Override public void onSingleTap()            { handleSingleTap(); }
                    @Override public void onDoubleTap()            { handleDoubleTap(); }
                    @Override public void onLongPress(boolean drag){ if (!drag && listener != null) listener.onMouseModeToggle(); }
                    @Override public void onDragStart()            { if (expanded) collapseInstant(); fabController.wake(); }
                    @Override public void onDragEnd()              { /* snap inicia aquí */ }
                });

        // Invalidar caché de geometría al finalizar el snap
        gestureController.setOnSnapFinished(() -> menuController.invalidateGeometry());

        keyboardController  = new OverlayKeyboardController(fabMainWrapper);

        TextView tooltipView = rootView.findViewById(R.id.fabTooltip);
        tooltipController   = new TooltipController(tooltipView);

        // ── 3. Setup de clics sobre botones radiales ──────────────────────────
        setupItemInteractions();

        // ── 4. Setup del scrim ────────────────────────────────────────────────
        if (fabScrim != null) {
            fabScrim.setOnClickListener(v -> collapseMenu());
        }

        // ── 5. Restaurar posición y arrancar efectos ──────────────────────────
        gestureController.restorePosition();
        fabController.scheduleShrink();
        fabMain.post(() -> animController.startHaloPulse());

        // ── 6. Listener de cambios de orientación / tamaño ───────────────────
        layoutChangeListener = (v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            int w = right - left;
            int h = bottom - top;
            int oldW = oldRight - oldLeft;
            int oldH = oldBottom - oldTop;
            if (w > 0 && h > 0 && (w != oldW || h != oldH)) {
                // La pantalla rotó o cambió de tamaño: reclampar la posición del FAB
                // dentro de los nuevos límites y recalcular geometría.
                gestureController.reclampPosition();
                // Usar postDelayed para asegurar que el layout esté completamente asentado
                // antes de recalcular la geometría radial.
                fabMainWrapper.postDelayed(() -> {
                    menuController.invalidateGeometry();
                    menuController.syncAllToFabCenter(fabMainWrapper);
                }, 150);
            }
        };
        rootView.addOnLayoutChangeListener(layoutChangeListener);
    }

    /**
     * Debe llamarse desde la Activity cuando la orientación del dispositivo cambia.
     * Re-clampa la posición del FAB, invalida geometría y guarda la posición corregida.
     */
    public void onOrientationChanged() {
        gestureController.reclampPosition();
        gestureController.saveFabPosition();
        menuController.invalidateGeometry();
        // Re-sincronizar ítems al centro del FAB después de que el layout se asiente
        fabMainWrapper.postDelayed(() -> {
            menuController.syncAllToFabCenter(fabMainWrapper);
        }, 200);
    }

    // ── Acciones internas ─────────────────────────────────────────────────────

    private void handleSingleTap() {
        fabController.wake();
        if (!expanded) {
            menuController.renderMenu(RadialMenuController.MenuState.MAIN);
            expandMenu();
        } else if (menuController.getMenuState() == RadialMenuController.MenuState.TOOLS) {
            switchMenu(RadialMenuController.MenuState.MAIN);
        } else {
            collapseMenu();
        }
    }

    private void handleDoubleTap() {
        fabController.toggleEffects();
        boolean fx = fabController.isEffectsEnabled();
        animController.setEffectsEnabled(fx);
        if (fx) animController.startHaloPulse();
        else    animController.stopHaloPulse();
    }

    private void expandMenu() {
        expanded = true;
        fabController.wake();
        animController.stopHaloPulse();

        // Calcular posiciones de los ítems (usa caché del RadialMenuController)
        int active = menuController.getActiveCount();
        float[][] targets = new float[active][];
        for (int i = 0; i < active; i++) {
            targets[i] = menuController.computeExpandedPosition(i, fabMainWrapper);
        }

        float cx = fabMainWrapper.getX() + fabMainWrapper.getWidth()  / 2f;
        float cy = fabMainWrapper.getY() + fabMainWrapper.getHeight() / 2f;

        menuController.syncAllToFabCenter(fabMainWrapper);
        animController.showScrim((int) cx, (int) cy);
        animController.animateOrbitRingExpand(menuController.getAdaptiveRadius(fabMainWrapper),
                overlayRoot.getContext().getResources().getDisplayMetrics().density);
        animController.animateExpansion(radialItems, active, targets,
                menuController.getDynamicAngles(fabMainWrapper));

        if (menuController.getMenuState() == RadialMenuController.MenuState.TOOLS) {
            fabController.showBackArrow();
        } else {
            fabController.transitionToExpanded();
        }
    }

    private void collapseMenu() {
        expanded = false;
        menuController.renderMenu(RadialMenuController.MenuState.MAIN);

        float cx = fabMainWrapper.getX() + fabMainWrapper.getWidth()  / 2f;
        float cy = fabMainWrapper.getY() + fabMainWrapper.getHeight() / 2f;

        animController.hideScrim((int) cx, (int) cy);
        animController.animateOrbitRingCollapse();
        animController.animateCollapse(radialItems, cx, cy);
        fabController.transitionToCollapsed();
        fabController.scheduleShrink();

        tooltipController.hideTooltip();

        // Restablecer z-order si el teclado está visible
        if (keyboardController.isKeyboardVisible()) {
            fabMainWrapper.bringToFront();
            fabMainWrapper.requestLayout();
        }
    }

    private void collapseInstant() {
        expanded = false;
        animController.collapseInstant(radialItems);
        View fabScrim = overlayRoot.findViewById(R.id.fabScrim);
        if (fabScrim != null) fabScrim.setVisibility(View.GONE);
        fabController.resetInstant();
        tooltipController.hideTooltip();
    }

    private void switchMenu(RadialMenuController.MenuState state) {
        for (View item : radialItems) {
            if (item != null) { item.animate().cancel(); item.setVisibility(View.INVISIBLE); item.setAlpha(0f); }
        }
        menuController.renderMenu(state);
        updateCenterIcon();
        menuController.syncAllToFabCenter(fabMainWrapper);
        menuController.invalidateGeometry();

        int active = menuController.getActiveCount();
        float[][] targets = new float[active][];
        for (int i = 0; i < active; i++) {
            targets[i] = menuController.computeExpandedPosition(i, fabMainWrapper);
        }
        animController.animateExpansion(radialItems, active, targets,
                menuController.getDynamicAngles(fabMainWrapper));
    }

    private void updateCenterIcon() {
        if (menuController.getMenuState() == RadialMenuController.MenuState.TOOLS) {
            fabController.showBackArrow();
        } else if (expanded) {
            fabController.transitionToExpanded();
        } else {
            fabController.transitionToCollapsed();
        }
    }

    // ── Clics sobre ítems radiales ─────────────────────────────────────────

    @SuppressLint("ClickableViewAccessibility")
    private void setupItemInteractions() {
        for (int i = 0; i < radialItems.length; i++) {
            if (radialItems[i] == null) continue;
            final int idx = i;

            radialItems[i].setOnClickListener(v -> handleItemClick(idx));

            radialItems[i].setOnLongClickListener(v -> {
                if (idx < menuController.getActiveCount() && listener != null)
                    listener.onItemLongAction(menuController.getCurrentEntries()[idx].action);
                collapseMenu();
                return true;
            });

            radialItems[i].setOnTouchListener((v, ev) -> {
                switch (ev.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        // Escala de presión premium (0.94 según spec)
                        v.animate().scaleX(0.94f).scaleY(0.94f).setDuration(70).start();
                        // Tooltip on-press
                        if (idx < menuController.getActiveCount()) {
                            tooltipController.showTooltip(v, menuController.getCurrentEntries()[idx].label);
                        }
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        v.animate().scaleX(1f).scaleY(1f).setDuration(90).start();
                        tooltipController.hideTooltip();
                        break;
                }
                return false;
            });
        }
    }

    private void handleItemClick(int index) {
        if (index >= menuController.getActiveCount()) return;
        int action = menuController.getCurrentEntries()[index].action;

        if (action == RadialMenuController.A_TOOLS_OPEN) {
            switchMenu(RadialMenuController.MenuState.TOOLS);
            return;
        }

        if (listener != null) {
            switch (action) {
                case A_KEYBOARD: listener.onKeyboardToggle(); break;
                case A_ZOOM:     listener.onMoveToggle();     break;
                default:         listener.onItemAction(action); break;
            }
        }
        collapseMenu();
    }

    // ── API pública (compatible con Game.java) ────────────────────────────────

    public void setOnActionListener(OnActionListener l) { this.listener = l; }
    public boolean isExpanded()                         { return expanded; }
    public void collapse() { if (expanded) collapseMenu(); }

    public void onKeyboardVisibilityChanged(boolean visible, int keyboardTopY) {
        keyboardController.onKeyboardVisibilityChanged(visible, keyboardTopY, () -> {
            menuController.syncAllToFabCenter(fabMainWrapper);
        });
    }

    public void enterPerformanceMode() {
        animController.setPerformanceMode(true);
        animController.stopHaloPulse();
    }

    public void exitPerformanceMode() {
        animController.setPerformanceMode(false);
        if (!expanded) animController.startHaloPulse();
    }

    public void release() {
        // Cancelar todas las animaciones y callbacks en todos los sub-controladores
        fabController.release();
        animController.release();
        gestureController.release();
        keyboardController.release();
        tooltipController.release();

        if (layoutChangeListener != null) {
            overlayRoot.removeOnLayoutChangeListener(layoutChangeListener);
            layoutChangeListener = null;
        }

        // Remover callbacks pendientes del fabMainWrapper para evitar leaks
        if (fabMainWrapper != null) {
            fabMainWrapper.removeCallbacks(null);
        }
    }
}
