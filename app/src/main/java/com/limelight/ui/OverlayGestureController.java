package com.limelight.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.view.animation.PathInterpolator;

/**
 * OverlayGestureController — Responsabilidad Única: gestión de gestos táctiles sobre el FAB.
 *
 * Controla:
 *  - Arrastre libre (drag) del FAB.
 *  - Snap magnético al borde lateral más cercano (con OvershootInterpolator).
 *  - Detección de tap, doble tap y long press vía GestureDetector.
 *  - Clamping dentro de los límites de pantalla.
 */
public class OverlayGestureController {

    private static final String PREF_NAME = "fab_overlay_v5";
    private static final String KEY_FAB_X = "fab_x";
    private static final String KEY_FAB_Y = "fab_y";

    private static final float DRAG_THRESHOLD_PX = 12f;
    private static final float TOP_MARGIN_DP     = 24f;
    private static final float EDGE_MARGIN_DP    = 16f;

    private static final PathInterpolator EASING_EMPHASIZED_DECELERATE = new PathInterpolator(0.05f, 0.7f, 0.1f, 1.0f);

    public interface GestureListener {
        void onSingleTap();
        void onDoubleTap();
        void onLongPress(boolean isDragging);
        void onDragStart();
        void onDragEnd();
    }

    private final View              fabMain;
    private final View              fabMainWrapper;
    private final View              overlayRoot;
    private final Context           context;
    private final float             density;
    private final GestureListener   listener;
    private final GestureDetector   gestureDetector;

    private float   dragStartRawX, dragStartRawY;
    private float   wrapperStartX, wrapperStartY;
    private boolean dragging = false;

    // Callback para notificar fin del snap (para invalidar caché de geometría)
    private Runnable onSnapFinished;

    @SuppressLint("ClickableViewAccessibility")
    public OverlayGestureController(View fabMain, View fabMainWrapper, View overlayRoot, GestureListener listener) {
        this.fabMain       = fabMain;
        this.fabMainWrapper = fabMainWrapper;
        this.overlayRoot   = overlayRoot;
        this.context       = fabMain.getContext();
        this.density       = context.getResources().getDisplayMetrics().density;
        this.listener      = listener;

        this.gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                if (listener != null) listener.onSingleTap();
                return true;
            }
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                if (listener != null) listener.onDoubleTap();
                return true;
            }
            @Override
            public void onLongPress(MotionEvent e) {
                if (listener != null) listener.onLongPress(dragging);
            }
        });

        setupTouchListener();
    }

    public void setOnSnapFinished(Runnable r) { this.onSnapFinished = r; }
    public boolean isDragging()               { return dragging; }

    @SuppressLint("ClickableViewAccessibility")
    private void setupTouchListener() {
        fabMain.setOnTouchListener((v, event) -> {
            gestureDetector.onTouchEvent(event);

            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    dragStartRawX = event.getRawX();
                    dragStartRawY = event.getRawY();
                    if (fabMainWrapper != null) {
                        wrapperStartX = fabMainWrapper.getX();
                        wrapperStartY = fabMainWrapper.getY();
                        
                        // Estado: Presionado
                        fabMainWrapper.animate()
                                .scaleX(0.95f).scaleY(0.95f)
                                .translationZ(4f * density) // +4dp a los 12dp base = 16dp
                                .setDuration(90)
                                .setInterpolator(EASING_EMPHASIZED_DECELERATE)
                                .start();
                    }
                    dragging = false;
                    break;

                case MotionEvent.ACTION_MOVE: {
                    float dx = event.getRawX() - dragStartRawX;
                    float dy = event.getRawY() - dragStartRawY;
                    if (!dragging && (Math.abs(dx) > DRAG_THRESHOLD_PX || Math.abs(dy) > DRAG_THRESHOLD_PX)) {
                        dragging = true;
                        if (listener != null) listener.onDragStart();
                        
                        // Estado: Arrastrar
                        if (fabMainWrapper != null) {
                            fabMainWrapper.animate()
                                    .scaleX(1.08f).scaleY(1.08f)
                                    .translationZ(10f * density) // +10dp a los 12dp base = 22dp
                                    .setDuration(150)
                                    .setInterpolator(EASING_EMPHASIZED_DECELERATE)
                                    .start();
                        }
                    }
                    if (dragging && fabMainWrapper != null) {
                        fabMainWrapper.setX(clampX(wrapperStartX + dx));
                        fabMainWrapper.setY(clampY(wrapperStartY + dy));
                    }
                    break;
                }

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (dragging) {
                        clampToScreen();
                        snapToNearestEdge();
                        if (listener != null) listener.onDragEnd();
                    }
                    
                    // Estado: Reposo
                    if (fabMainWrapper != null) {
                        fabMainWrapper.animate()
                                .scaleX(1f).scaleY(1f)
                                .translationZ(0f)
                                .setDuration(200)
                                .setInterpolator(EASING_EMPHASIZED_DECELERATE)
                                .start();
                    }
                    
                    dragging = false;
                    break;
            }
            return true;
        });
    }

    /** Guarda la posición final exactamente donde el usuario la soltó,
     *  manteniendo la posición X e Y y aplicando los límites de pantalla. */
    private void snapToNearestEdge() {
        if (fabMainWrapper == null) return;
        
        float targetX = clampX(fabMainWrapper.getX());
        float targetY = clampY(fabMainWrapper.getY());

        fabMainWrapper.animate()
                .x(targetX)
                .y(targetY)
                .setDuration(120)
                .setInterpolator(EASING_EMPHASIZED_DECELERATE)
                .withEndAction(() -> {
                    saveFabPosition();
                    if (onSnapFinished != null) onSnapFinished.run();
                })
                .start();
    }

    private void clampToScreen() {
        if (fabMainWrapper == null) return;
        fabMainWrapper.setX(clampX(fabMainWrapper.getX()));
        fabMainWrapper.setY(clampY(fabMainWrapper.getY()));
    }

    private float clampX(float x) {
        if (fabMainWrapper == null || overlayRoot.getWidth() == 0) return x;
        float margin = EDGE_MARGIN_DP * density;
        float wrapperWidth = fabMainWrapper.getWidth() > 0 ? fabMainWrapper.getWidth() : 56f * density;
        return Math.max(margin, Math.min(overlayRoot.getWidth() - wrapperWidth - margin, x));
    }

    private float clampY(float y) {
        if (fabMainWrapper == null || overlayRoot.getHeight() == 0) return y;
        float topMargin    = TOP_MARGIN_DP  * density;
        float bottomMargin = EDGE_MARGIN_DP * density;
        float wrapperHeight = fabMainWrapper.getHeight() > 0 ? fabMainWrapper.getHeight() : 56f * density;
        return Math.max(topMargin, Math.min(overlayRoot.getHeight() - wrapperHeight - bottomMargin, y));
    }

    public void restorePosition() {
        if (fabMainWrapper == null) return;
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        float savedX = prefs.getFloat(KEY_FAB_X, -1f);
        float savedY = prefs.getFloat(KEY_FAB_Y, -1f);
        fabMainWrapper.post(() -> {
            float margin = EDGE_MARGIN_DP * density;
            int parentWidth = overlayRoot.getWidth();
            int parentHeight = overlayRoot.getHeight();
            int wrapperWidth = fabMainWrapper.getWidth();
            int wrapperHeight = fabMainWrapper.getHeight();
            
            // Si el layout aún no mide tamaño, posponer hasta el siguiente frame
            if (parentWidth <= 0 || parentHeight <= 0 || wrapperWidth <= 0 || wrapperHeight <= 0) {
                fabMainWrapper.postDelayed(this::restorePosition, 60);
                return;
            }

            if (savedX >= 0 && savedY >= 0) {
                // Aplicar posición guardada y re-clampear dentro de la pantalla física actual
                fabMainWrapper.setX(clampX(savedX));
                fabMainWrapper.setY(clampY(savedY));
            } else {
                // Posición por defecto si no hay guardada (abajo a la derecha)
                fabMainWrapper.setX(parentWidth - wrapperWidth - margin);
                fabMainWrapper.setY(parentHeight - wrapperHeight - margin * 3f);
            }
        });
    }

    public void saveFabPosition() {
        if (fabMainWrapper == null) return;
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
               .edit()
               .putFloat(KEY_FAB_X, fabMainWrapper.getX())
               .putFloat(KEY_FAB_Y, fabMainWrapper.getY())
               .apply();
    }

    /**
     * Re-clampa la posición del FAB dentro de los límites actuales de la pantalla.
     * Útil tras rotación del dispositivo, cuando las dimensiones del overlay cambian
     * y la posición guardada puede quedar fuera del viewport (ej. Y de portrait 2300px
     * cuando la altura en landscape es solo 1080px).
     * No anima: es una corrección silenciosa de layout.
     */
    public void reclampPosition() {
        if (fabMainWrapper == null) return;
        // Asegurar que las dimensiones del padre y del wrapper estén disponibles
        if (overlayRoot.getWidth() <= 0 || overlayRoot.getHeight() <= 0 || fabMainWrapper.getWidth() <= 0 || fabMainWrapper.getHeight() <= 0) {
            // Reintentar en el siguiente frame si el layout aún no midió
            fabMainWrapper.postDelayed(this::reclampPosition, 50);
            return;
        }
        float clampedX = clampX(fabMainWrapper.getX());
        float clampedY = clampY(fabMainWrapper.getY());
        fabMainWrapper.setX(clampedX);
        fabMainWrapper.setY(clampedY);
    }

    public void release() {
        fabMain.setOnTouchListener(null);
    }
}
