package com.limelight.ui;

import android.view.View;

/**
 * RadialMenuController — Responsabilidad Única: gestión del pool de botones radiales.
 *
 * Centraliza:
 *  - El estado del menú activo (MAIN / TOOLS).
 *  - La asignación de entradas (iconos, etiquetas, acciones) a los contenedores físicos.
 *  - El CACHÉ de geometría (ángulos y radio), recalculado solo cuando el FAB cambia de posición.
 */
public class RadialMenuController {

    public static final int A_KEYBOARD   = 0;
    public static final int A_ZOOM       = 1;
    public static final int A_MOUSE      = 2;
    public static final int A_FILES      = 3;
    public static final int A_VOICE      = 4;
    public static final int A_PIP        = 5;
    public static final int A_SCREEN     = 6;
    public static final int A_EXIT       = 9;
    static final int A_TOOLS_OPEN = 100;

    private static final float RADIAL_RADIUS_DP     = 140f; // radio máximo del anillo orbital
    private static final float RADIAL_RADIUS_MIN_DP = 60f;  // radio mínimo en pantallas pequeñas
    private static final int   POOL_SIZE = 8;

    public enum MenuState { MAIN, TOOLS }

    public static final class Entry {
        public final int    icon;
        public final String label;
        public final int    action;
        public Entry(int icon, String label, int action) {
            this.icon = icon; this.label = label; this.action = action;
        }
    }

    // Definición de los menús
    private final Entry[] mainEntries;
    private final Entry[] toolsEntries;

    private Entry[]   currentEntries;
    private int       activeCount;
    private MenuState menuState = MenuState.MAIN;

    // Pool de vistas físicas (reutilizables)
    private final View[]                      radialItems;
    private final android.widget.ImageView[]  iconViews;

    // ── Caché de geometría ────────────────────────────────────────────────────
    // Solo se recalcula cuando el FAB se mueve o rota la pantalla.
    private double[] cachedAngles;
    private float    cachedRadius;
    private boolean  geometryCached = false;

    private final float density;
    private final View  overlayRoot;

    public RadialMenuController(
            View overlayRoot,
            View[] radialItems,
            android.widget.ImageView[] iconViews,
            Entry[] mainEntries,
            Entry[] toolsEntries) {

        this.overlayRoot  = overlayRoot;
        this.radialItems  = radialItems;
        this.iconViews    = iconViews;
        this.mainEntries  = mainEntries;
        this.toolsEntries = toolsEntries;
        this.density      = overlayRoot.getContext().getResources().getDisplayMetrics().density;

        renderMenu(MenuState.MAIN);
    }

    // ── Estado del menú ──────────────────────────────────────────────────────

    public MenuState getMenuState() { return menuState; }
    public int       getActiveCount() { return activeCount; }
    public Entry[]   getCurrentEntries() { return currentEntries; }

    /** Renderiza (asigna iconos) los botones del menú activo. Oculta los sobrantes. */
    public void renderMenu(MenuState state) {
        menuState      = state;
        currentEntries = (state == MenuState.TOOLS) ? toolsEntries : mainEntries;
        activeCount    = Math.min(currentEntries.length, POOL_SIZE);

        for (int i = 0; i < POOL_SIZE; i++) {
            if (radialItems[i] == null) continue;
            if (i < activeCount) {
                if (iconViews[i] != null) iconViews[i].setImageResource(currentEntries[i].icon);
            } else {
                radialItems[i].animate().cancel();
                radialItems[i].setVisibility(View.INVISIBLE);
                radialItems[i].setAlpha(0f);
            }
        }
    }

    // ── Caché de geometría ────────────────────────────────────────────────────

    /**
     * Invalida el caché de geometría para que se recalcule en el próximo acceso.
     * Se llama cuando el FAB termina de moverse o la pantalla cambia de orientación.
     */
    public void invalidateGeometry() {
        geometryCached = false;
    }

    /** Devuelve el radio adaptativo, recalculando si el caché está inválido. */
    public float getAdaptiveRadius(View fabWrapper) {
        if (!geometryCached) recomputeGeometry(fabWrapper);
        return cachedRadius;
    }

    /** Devuelve los ángulos distribuidos, recalculando si el caché está inválido. */
    public double[] getDynamicAngles(View fabWrapper) {
        if (!geometryCached) recomputeGeometry(fabWrapper);
        return cachedAngles;
    }

    /**
     * Recalcula ángulos y radio en un solo paso y los guarda en caché.
     * Este es el único punto donde ocurren los cálculos trigonométricos.
     */
    private void recomputeGeometry(View fabWrapper) {
        float rootW = overlayRoot.getWidth();
        float rootH = overlayRoot.getHeight();

        if (rootW <= 0 || rootH <= 0 || fabWrapper == null) {
            cachedAngles = new double[activeCount];
            cachedRadius = RADIAL_RADIUS_MIN_DP * density;
            geometryCached = true;
            return;
        }

        float cx = fabWrapper.getX() + fabWrapper.getWidth()  / 2f;
        float cy = fabWrapper.getY() + fabWrapper.getHeight() / 2f;

        // ── 1. Calcular ángulo central y arco según posición del FAB ─────────
        float nx = cx / rootW;
        float ny = cy / rootH;

        double centerAngle;
        if      (nx > 0.6f && ny > 0.6f) centerAngle = -135;
        else if (nx < 0.4f && ny > 0.6f) centerAngle = -45;
        else if (nx > 0.6f && ny < 0.4f) centerAngle = 135;
        else if (nx < 0.4f && ny < 0.4f) centerAngle = 45;
        else if (ny > 0.65f)              centerAngle = -90;
        else if (ny < 0.35f)              centerAngle = 90;
        else if (nx > 0.6f)               centerAngle = 180;
        else if (nx < 0.4f)               centerAngle = 0;
        else                              centerAngle = -90;

        float arcSpan;
        if ((nx < 0.2f || nx > 0.8f) && (ny < 0.2f || ny > 0.8f)) arcSpan = 90f;
        else if (nx < 0.2f || nx > 0.8f || ny < 0.2f || ny > 0.8f) arcSpan = 180f;
        else arcSpan = 360f;

        // ── 2. Calcular radio seguro (sin loop iterativo) ────────────────────
        float maxR    = Math.min(RADIAL_RADIUS_DP * density, Math.min(rootW, rootH) * 0.28f);
        float minR    = RADIAL_RADIUS_MIN_DP * density;
        float itemSz  = 72 * density;  // FrameLayout 72dp del XML (contenedor real)
        float pad     = 10 * density;

        // Radio mínimo necesario para que los botones no se superpongan al FAB
        float fabSz   = (fabWrapper.getWidth() > 0 ? fabWrapper.getWidth() : 120 * density);
        float minSafe = (fabSz / 2f) + (itemSz / 2f) + 12 * density;

        // Estimación vectorial directa (mucho más eficiente que el bucle iterativo de 10 pasos)
        float radius = maxR;
        double halfSpan = arcSpan / 2.0;
        int n = Math.max(1, activeCount);
        double step = (arcSpan == 360f) ? (arcSpan / n) : (arcSpan / Math.max(1, n - 1));

        // Una sola pasada de verificación + ajuste
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE;
        float maxX = Float.MIN_VALUE, maxY = Float.MIN_VALUE;
        for (int i = 0; i < activeCount; i++) {
            double a = Math.toRadians(centerAngle - halfSpan + i * step);
            float bx = cx + (float)(radius * Math.cos(a));
            float by = cy + (float)(radius * Math.sin(a));
            minX = Math.min(minX, bx - itemSz / 2f);
            minY = Math.min(minY, by - itemSz / 2f);
            maxX = Math.max(maxX, bx + itemSz / 2f);
            maxY = Math.max(maxY, by + itemSz / 2f);
        }
        // Si algún botón sale de pantalla, escalar el radio proporcionalmente
        if (minX < pad || minY < pad || maxX > rootW - pad || maxY > rootH - pad) {
            float scaleX = rootW / (maxX - minX + 2 * pad);
            float scaleY = rootH / (maxY - minY + 2 * pad);
            radius *= Math.min(scaleX, scaleY) * 0.9f;
        }
        cachedRadius = Math.max(minR, Math.min(maxR, Math.max(radius, minSafe)));

        // ── 3. Computar array de ángulos finales ─────────────────────────────
        cachedAngles = new double[activeCount];
        for (int i = 0; i < activeCount; i++) {
            cachedAngles[i] = centerAngle - halfSpan + i * step;
        }

        geometryCached = true;
    }

    /** Calcula la posición de pantalla [x, y] del ítem de índice dado. */
    public float[] computeExpandedPosition(int index, View fabWrapper) {
        float cx = fabWrapper.getX() + fabWrapper.getWidth()  / 2f;
        float cy = fabWrapper.getY() + fabWrapper.getHeight() / 2f;

        double[] angles = getDynamicAngles(fabWrapper);
        float    radius = getAdaptiveRadius(fabWrapper);

        double rad  = Math.toRadians(angles[index]);
        float itemSz = 72 * density;  // FrameLayout 72dp del XML

        float x = cx + (float)(radius * Math.cos(rad)) - itemSz / 2f;
        float y = cy + (float)(radius * Math.sin(rad)) - itemSz / 2f;
        return new float[]{x, y};
    }

    /** Reposiciona todos los ítems activos en el centro del FAB (estado colapsado). */
    public void syncAllToFabCenter(View fabWrapper) {
        if (fabWrapper == null) return;
        float cx = fabWrapper.getX() + fabWrapper.getWidth()  / 2f;
        float cy = fabWrapper.getY() + fabWrapper.getHeight() / 2f;
        float itemSz = 72 * density;  // FrameLayout 72dp del XML (contenedor real del ítem)
        for (int i = 0; i < radialItems.length; i++) {
            View item = radialItems[i];
            if (item == null) continue;
            item.setX(cx - itemSz / 2f);
            item.setY(cy - itemSz / 2f);
        }
    }

    public View[] getRadialItems() { return radialItems; }
    public int    getPoolSize()     { return POOL_SIZE; }
}
