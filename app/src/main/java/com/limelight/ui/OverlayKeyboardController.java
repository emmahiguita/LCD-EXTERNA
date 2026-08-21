package com.limelight.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;
import android.view.animation.PathInterpolator;

/**
 * OverlayKeyboardController — Gestiona la elevación y reposicionamiento del FAB para evitar la oclusión del teclado.
 */
public class OverlayKeyboardController {
    private static final String PREF_NAME = "fab_overlay_v5";
    private static final String KEY_FAB_X = "fab_x";
    private static final String KEY_FAB_Y = "fab_y";

    private final View fabMainWrapperView;
    private final Context context;
    private final float density;

    private boolean keyboardVisible = false;
    private int keyboardTopY = 0;

    private static final PathInterpolator EASING_EMPHASIZED_DECELERATE = new PathInterpolator(0.05f, 0.7f, 0.1f, 1.0f);
    private static final PathInterpolator EASING_EMPHASIZED_ACCELERATE = new PathInterpolator(0.3f, 0.0f, 0.8f, 0.15f);

    public OverlayKeyboardController(View fabMainWrapperView) {
        this.fabMainWrapperView = fabMainWrapperView;
        this.context = fabMainWrapperView.getContext();
        this.density = context.getResources().getDisplayMetrics().density;
    }

    public boolean isKeyboardVisible() {
        return keyboardVisible;
    }

    public int getKeyboardTopY() {
        return keyboardTopY;
    }

    /**
     * Reacciona ante los cambios de visibilidad del teclado reubicando y elevando el FAB.
     */
    public void onKeyboardVisibilityChanged(boolean visible, int keyboardTopY, Runnable onPositionSynced) {
        if (fabMainWrapperView == null) return;
        this.keyboardVisible = visible;
        this.keyboardTopY = keyboardTopY;

        if (visible) {
            fabMainWrapperView.bringToFront();
            fabMainWrapperView.requestLayout();
            
            // Elevación adicional de seguridad para quedar encima del teclado (cardElevation = 18dp)
            fabMainWrapperView.setTranslationZ(50f * density);

            int fabH = fabMainWrapperView.getHeight() > 0 ? fabMainWrapperView.getHeight() : (int)(56f * density);
            float fabBottom = fabMainWrapperView.getY() + fabH;
            float safeY = Math.max(10f * density, keyboardTopY - fabH - (16f * density));

            if (fabBottom > keyboardTopY || fabMainWrapperView.getY() > safeY) {
                fabMainWrapperView.animate().cancel();
                fabMainWrapperView.animate()
                    .y(safeY)
                    .setDuration(300)
                    .setInterpolator(EASING_EMPHASIZED_DECELERATE)
                    .withEndAction(onPositionSynced)
                    .start();
            }
        } else {
            // Restaurar elevación y regresar a la posición guardada
            fabMainWrapperView.setTranslationZ(0f);

            SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            float savedX = prefs.getFloat(KEY_FAB_X, fabMainWrapperView.getX());
            float savedY = prefs.getFloat(KEY_FAB_Y, fabMainWrapperView.getY());

            View parent = (View) fabMainWrapperView.getParent();
            if (parent != null && parent.getHeight() > 0) {
                int fabH = fabMainWrapperView.getHeight() > 0 ? fabMainWrapperView.getHeight() : (int)(56f * density);
                savedY = Math.max(10f * density, Math.min(savedY, parent.getHeight() - fabH - (10f * density)));
            }

            fabMainWrapperView.animate().cancel();
            fabMainWrapperView.animate()
                .x(savedX)
                .y(savedY)
                .setDuration(300)
                .setInterpolator(EASING_EMPHASIZED_ACCELERATE)
                .withEndAction(onPositionSynced)
                .start();
        }
    }

    /**
     * Libera recursos del controlador.
     */
    public void release() {
        if (fabMainWrapperView != null) {
            fabMainWrapperView.animate().cancel();
        }
    }
}
