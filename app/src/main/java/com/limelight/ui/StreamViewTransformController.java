package com.limelight.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/**
 * StreamViewTransformController
 *
 * Control táctil avanzado:
 *  - 2 Dedos:
 *      * Pellizco (Pinch): Zoom in / Zoom out centrado dinámicamente en el foco del gesto.
 *      * Desplazamiento (Pan): Mover la pantalla libremente a donde el usuario quiera.
 *      * Aislamiento: Los eventos de 2 dedos son consumidos para la proyección y no envían
 *        eventos de mouse a Windows.
 *  - 1 Dedo:
 *      * Control exclusivo del mouse en Windows (mover puntero, clicks, arrastrar, seleccionar).
 *      * NO consume el evento; se pasa a handleMotionEvent() mapeado con precisión.
 */
public class StreamViewTransformController {

    // ─── Límites ─────────────────────────────────────────────────────────────
    private static final float MIN_SCALE      = 1.0f;
    private static final float MAX_SCALE      = 4.0f;
    /** Tope más suave para el Smart Zoom automático a un campo de texto */
    private static final float AUTO_MAX_ZOOM  = 2.5f;
    /** Si se suelta con scale < SNAP_THRESHOLD → reset animado a 1× */
    private static final float SNAP_THRESHOLD = 1.05f;
    private static final int   DUR_RESET      = 280;  // ms

    // ─── Estado de transformación ────────────────────────────────────────────
    private float scale      = 1.0f;
    private float translateX = 0f;
    private float translateY = 0f;

    // ─── Estado de paneo / multitouch ────────────────────────────────────────
    /** Centroide del último frame MOVE */
    private float lastCentroidX    = 0f;
    private float lastCentroidY    = 0f;
    /** Número de punteros en el último evento procesado */
    private int   lastPointerCount = 0;
    /** true si el paneo con 2 dedos está activo */
    private boolean panActive        = false;
    /** true si la secuencia de toques actual involucró 2 o más dedos */
    private boolean multiTouchActive = false;

    // ─── Modo Mover Manual ───────────────────────────────────────────────────
    private boolean moveModeActive = false;

    /** true cuando el zoom actual lo inició el Smart Zoom (no el usuario). */
    private boolean autoZoomActive = false;

    // ─── Getters para Coordinate Mapper ──────────────────────────────────────
    public float getScale() { return scale; }
    public float getTranslateX() { return translateX; }
    public float getTranslateY() { return translateY; }

    // ─── Vistas y detectors ──────────────────────────────────────────────────
    private final View    targetView;
    private final Context context;
    private final ScaleGestureDetector scaleDetector;
    private final GestureDetector      doubleTapDetector;

    /** Animador de reset (doble-tap / snap) — cancelable si el usuario toca */
    private ValueAnimator resetAnimator;

    // ─────────────────────────────────────────────────────────────────────────
    //  CONSTRUCTOR
    // ─────────────────────────────────────────────────────────────────────────
    public StreamViewTransformController(Context context, View targetView, View interceptView) {
        this.context    = context;
        this.targetView = targetView;

        // ─── Pinch-to-zoom ───────────────────────────────────────────────────
        scaleDetector = new ScaleGestureDetector(context,
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override
                    public boolean onScaleBegin(ScaleGestureDetector detector) {
                        cancelReset();
                        autoZoomActive = false;
                        multiTouchActive = true;
                        return true;
                    }

                    @Override
                    public boolean onScale(ScaleGestureDetector d) {
                        cancelReset();
                        autoZoomActive = false;
                        multiTouchActive = true;

                        float prevScale = scale;
                        scale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale * d.getScaleFactor()));

                        // Ajuste focal: mantener el punto bajo los dedos estable durante el zoom
                        float focusX = d.getFocusX();
                        float focusY = d.getFocusY();
                        float viewW  = getViewWidth();
                        float viewH  = getViewHeight();
                        float pivotX = viewW / 2f;
                        float pivotY = viewH / 2f;

                        if (prevScale > 0.001f) {
                            float scaleRatio = scale / prevScale;
                            translateX = focusX - pivotX - (focusX - pivotX - translateX) * scaleRatio;
                            translateY = focusY - pivotY - (focusY - pivotY - translateY) * scaleRatio;
                        }

                        applyTransform(true); // clamp al escalar
                        return true;
                    }
                });
        scaleDetector.setQuickScaleEnabled(false);

        // ─── Doble-tap (solo para reset manual en modo mover) ────────────────
        doubleTapDetector = new GestureDetector(context,
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onDoubleTap(MotionEvent e) {
                        if (moveModeActive) {
                            animateReset();
                            return true;
                        }
                        return false;
                    }
                });
        doubleTapDetector.setIsLongpressEnabled(false);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  PUNTO DE ENTRADA — llamado desde Game.onTouch()
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Procesa el MotionEvent.
     * @return true  → evento consumido para manipulación de la pantalla (NO pasar al mouse)
     *         false → evento libre (pasar al mouse de Windows normalmente)
     */
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        int pointerCount = event.getPointerCount();

        if (action == MotionEvent.ACTION_DOWN) {
            cancelReset();
            multiTouchActive = false;
        }

        // Procesar detección de escala (pinch)
        scaleDetector.onTouchEvent(event);

        if (pointerCount >= 2) {
            multiTouchActive = true;
        }

        // Doble tap solo en modo mover explícito
        if (moveModeActive) {
            doubleTapDetector.onTouchEvent(event);
        }

        processPan(event);

        // Si es un solo dedo y NO estamos en modo mover manual ni en medio de un gesto de 2 dedos:
        // NO consumimos el evento para que 1 dedo maneje el mouse al 100%.
        boolean isMultiFinger = (pointerCount >= 2);
        boolean isScaling = scaleDetector.isInProgress();
        boolean isFinishingMultiTouch = multiTouchActive && (action == MotionEvent.ACTION_MOVE || action == MotionEvent.ACTION_POINTER_UP || action == MotionEvent.ACTION_UP);

        boolean consume = isMultiFinger || isScaling || isFinishingMultiTouch || moveModeActive;

        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            multiTouchActive = false;
            panActive = false;
        }

        return consume;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  PANEO CON 2 DEDOS (centroide)
    // ─────────────────────────────────────────────────────────────────────────

    private void processPan(MotionEvent event) {
        int action       = event.getActionMasked();
        int pointerCount = event.getPointerCount();

        switch (action) {

            case MotionEvent.ACTION_DOWN:
                lastCentroidX    = event.getX(0);
                lastCentroidY    = event.getY(0);
                lastPointerCount = 1;
                panActive        = false; // No panear con 1 dedo
                break;

            case MotionEvent.ACTION_POINTER_DOWN:
                lastCentroidX    = centroidX(event, -1);
                lastCentroidY    = centroidY(event, -1);
                lastPointerCount = pointerCount;
                panActive        = true;
                multiTouchActive = true;
                break;

            case MotionEvent.ACTION_MOVE: {
                float cx = centroidX(event, -1);
                float cy = centroidY(event, -1);

                // Mover pantalla SOLO con 2 o más dedos (o moveModeActive explícito)
                if ((pointerCount >= 2 || moveModeActive) && panActive) {
                    if (!scaleDetector.isInProgress() && pointerCount == lastPointerCount) {
                        float dx = cx - lastCentroidX;
                        float dy = cy - lastCentroidY;
                        translateX += dx;
                        translateY += dy;
                        applyTransform(true); // clamp
                    }
                }

                lastCentroidX    = cx;
                lastCentroidY    = cy;
                lastPointerCount = pointerCount;
                break;
            }

            case MotionEvent.ACTION_POINTER_UP: {
                int liftedIdx    = event.getActionIndex();
                lastCentroidX    = centroidX(event, liftedIdx);
                lastCentroidY    = centroidY(event, liftedIdx);
                lastPointerCount = pointerCount - 1;
                if (lastPointerCount < 2 && !moveModeActive) {
                    panActive = false;
                }
                break;
            }

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                panActive        = false;
                multiTouchActive = false;
                lastPointerCount = 0;
                break;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  CENTROIDE
    // ─────────────────────────────────────────────────────────────────────────

    private float centroidX(MotionEvent event, int excludeIndex) {
        float sum = 0f; int count = 0;
        for (int i = 0; i < event.getPointerCount(); i++) {
            if (i == excludeIndex) continue;
            sum += event.getX(i); count++;
        }
        return count > 0 ? sum / count : event.getX(0);
    }

    private float centroidY(MotionEvent event, int excludeIndex) {
        float sum = 0f; int count = 0;
        for (int i = 0; i < event.getPointerCount(); i++) {
            if (i == excludeIndex) continue;
            sum += event.getY(i); count++;
        }
        return count > 0 ? sum / count : event.getY(0);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  APLICAR TRANSFORMACIÓN
    // ─────────────────────────────────────────────────────────────────────────

    private float keyboardOffsetY = 0f;

    public void setKeyboardOffset(float offsetY) {
        this.keyboardOffsetY = offsetY;
        applyTransform(true);
    }
    
    public float getKeyboardOffsetY() {
        return keyboardOffsetY;
    }

    private void applyTransform(boolean clamp) {
        if (clamp) clampTranslation();
        targetView.setScaleX(scale);
        targetView.setScaleY(scale);
        targetView.setTranslationX(translateX);
        targetView.setTranslationY(translateY + keyboardOffsetY);
    }

    private void clampTranslation() {
        int w = getViewWidth();
        int h = getViewHeight();

        // El contenido escalado permanece dentro de los bordes de la pantalla.
        // Fórmula: maxDesplazamiento = tamaño × (scale - 1) / 2
        if (w > 0) {
            float maxTx = w * (scale - 1f) / 2f;
            if (maxTx < 0) maxTx = 0f;
            translateX = Math.max(-maxTx, Math.min(maxTx, translateX));
        }
        if (h > 0) {
            float maxTy = h * (scale - 1f) / 2f;
            if (maxTy < 0) maxTy = 0f;
            translateY = Math.max(-maxTy, Math.min(maxTy, translateY));
        }
    }

    private int getViewWidth() {
        int w = targetView.getWidth();
        if (w == 0) {
            View parent = (View) targetView.getParent();
            if (parent != null) w = parent.getWidth();
        }
        return w;
    }

    private int getViewHeight() {
        int h = targetView.getHeight();
        if (h == 0) {
            View parent = (View) targetView.getParent();
            if (parent != null) h = parent.getHeight();
        }
        return h;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  RESET ANIMADO
    // ─────────────────────────────────────────────────────────────────────────

    public void animateReset() {
        cancelReset();
        autoZoomActive = false;
        moveModeActive = false;

        final float fromScale = scale;
        final float fromTx    = translateX;
        final float fromTy    = translateY;

        resetAnimator = ValueAnimator.ofFloat(0f, 1f);
        resetAnimator.setDuration(DUR_RESET);
        resetAnimator.setInterpolator(new DecelerateInterpolator(1.5f));
        resetAnimator.addUpdateListener(anim -> {
            float t    = (float) anim.getAnimatedValue();
            scale      = fromScale + (MIN_SCALE - fromScale) * t;
            translateX = fromTx    + (0f        - fromTx)    * t;
            translateY = fromTy    + (0f        - fromTy)    * t;
            applyTransform(false);
        });
        resetAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                scale      = MIN_SCALE;
                translateX = 0f;
                translateY = 0f;
                applyTransform(false);
            }
        });
        resetAnimator.start();
    }

    private void cancelReset() {
        if (resetAnimator != null && resetAnimator.isRunning()) {
            resetAnimator.cancel();
            resetAnimator = null;
        }
    }

    public boolean toggleMoveMode() {
        moveModeActive = !moveModeActive;
        return moveModeActive;
    }

    public boolean isMoveModeActive() {
        return moveModeActive;
    }

    // ─── Constantes para Smart Zoom a campos de texto ────────────────────────
    private static final float LEGIBLE_FIELD_DP = 48f;
    private static final float MAX_FILL_W       = 0.90f;
    private static final float MAX_FILL_H       = 0.50f;
    private static final float FIELD_TARGET_Y   = 0.35f;
    private static final float KB_RESERVE_PORT  = 0.45f;
    private static final float KB_RESERVE_LAND  = 0.55f;

    public void zoomToFocusedRect(float localX, float localY, float localW, float localH,
                                  boolean keyboardVisible) {
        if (localW <= 0 || localH <= 0) return;
        if (!autoZoomActive && scale > MIN_SCALE + 0.05f) return;

        int viewW = getViewWidth();
        int viewH = getViewHeight();
        if (viewW <= 0 || viewH <= 0) return;

        float density = context.getResources().getDisplayMetrics().density;

        boolean portrait = context.getResources().getConfiguration().orientation
                == android.content.res.Configuration.ORIENTATION_PORTRAIT;
        float reserve = keyboardVisible ? (portrait ? KB_RESERVE_PORT : KB_RESERVE_LAND) : 0f;
        float availableH = viewH * (1f - reserve);

        float pivotX = viewW / 2f;
        float pivotY = viewH / 2f;
        float fieldCx = localX + localW / 2f;
        float fieldCy = localY + localH / 2f;
        float minLegiblePx = LEGIBLE_FIELD_DP * density;

        float curFieldScreenH = localH * scale;
        float curFieldScreenY = pivotY + (fieldCy - pivotY) * scale + (translateY + keyboardOffsetY);
        boolean alreadyLegible = curFieldScreenH >= minLegiblePx * 0.85f;
        boolean inSafeArea     = curFieldScreenY > minLegiblePx * 0.5f
                              && curFieldScreenY < availableH * 0.95f;
        if (alreadyLegible && inSafeArea) return;

        float legibilityScale = minLegiblePx / localH;
        float fitScale = Math.min((viewW * MAX_FILL_W) / localW,
                                  (availableH * MAX_FILL_H) / localH);
        float targetScale = Math.min(legibilityScale, fitScale);
        targetScale = Math.max(MIN_SCALE, Math.min(AUTO_MAX_ZOOM, targetScale));

        if (targetScale < MIN_SCALE + 0.08f) return;

        float targetScreenX = viewW * 0.5f;
        float targetScreenY = availableH * FIELD_TARGET_Y;

        float targetTx = targetScreenX - pivotX - (fieldCx - pivotX) * targetScale;
        float targetTy = targetScreenY - pivotY - (fieldCy - pivotY) * targetScale;

        autoZoomActive = true;
        animateToViewport(targetScale, targetTx, targetTy);
    }

    public void zoomToRightClick(float localX, float localY) {
        if (!autoZoomActive && scale > MIN_SCALE + 0.05f) return;

        int viewW = getViewWidth();
        int viewH = getViewHeight();
        if (viewW <= 0 || viewH <= 0) return;

        float targetScale = Math.min(AUTO_MAX_ZOOM, MIN_SCALE * 2.5f);

        float targetScreenX = viewW * 0.35f;
        float targetScreenY = viewH * 0.35f;

        float pivotX = viewW / 2f;
        float pivotY = viewH / 2f;

        float targetTx = targetScreenX - pivotX - (localX - pivotX) * targetScale;
        float targetTy = targetScreenY - pivotY - (localY - pivotY) * targetScale;

        autoZoomActive = true;
        animateToViewport(targetScale, targetTx, targetTy);
    }

    public void clearAutoZoom() {
        if (autoZoomActive) {
            animateReset();
        }
    }

    // ─── Viewports Virtuales ─────────────────────────────────────────────────

    public void saveViewport(int slot) {
        SharedPreferences prefs = context.getSharedPreferences("smartdisplay_viewports", Context.MODE_PRIVATE);
        prefs.edit()
             .putFloat("vp_scale_" + slot, scale)
             .putFloat("vp_tx_" + slot, translateX)
             .putFloat("vp_ty_" + slot, translateY)
             .apply();
        android.widget.Toast.makeText(context, "Monitor " + slot + " guardado", android.widget.Toast.LENGTH_SHORT).show();
    }

    public void restoreViewport(int slot) {
        SharedPreferences prefs = context.getSharedPreferences("smartdisplay_viewports", Context.MODE_PRIVATE);
        if (!prefs.contains("vp_scale_" + slot)) {
            android.widget.Toast.makeText(context, "Monitor " + slot + " vacío (Mantén pulsado para guardar)", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }

        final float targetScale = prefs.getFloat("vp_scale_" + slot, MIN_SCALE);
        final float targetTx = prefs.getFloat("vp_tx_" + slot, 0f);
        final float targetTy = prefs.getFloat("vp_ty_" + slot, 0f);

        animateToViewport(targetScale, targetTx, targetTy);
    }

    private void animateToViewport(final float targetScale, final float targetTx, final float targetTy) {
        cancelReset();
        moveModeActive = true; 
        
        final float fromScale = scale;
        final float fromTx    = translateX;
        final float fromTy    = translateY;

        resetAnimator = ValueAnimator.ofFloat(0f, 1f);
        resetAnimator.setDuration(DUR_RESET + 50);
        resetAnimator.setInterpolator(new DecelerateInterpolator(1.8f));
        resetAnimator.addUpdateListener(anim -> {
            float t    = (float) anim.getAnimatedValue();
            scale      = fromScale + (targetScale - fromScale) * t;
            translateX = fromTx    + (targetTx    - fromTx)    * t;
            translateY = fromTy    + (targetTy    - fromTy)    * t;
            applyTransform(true);
        });
        resetAnimator.start();
    }
}
