package com.limelight.ui;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;
import android.widget.Toast;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.limelight.R;

/**
 * FABController — Responsabilidad Única: gestión visual del FAB principal.
 *
 * Controla:
 *  - Icono, colores del tema y estado visual del FAB (reposado, expandido, inactivo).
 *  - Efecto de encogimiento/despertar automático por inactividad.
 *  - Toggle de efectos visuales.
 */
public class FABController {

    private static final String PREF_NAME = "fab_overlay_v5";

    private static final float SHRINK_SCALE    = 0.72f;
    private static final float SHRINK_ALPHA    = 0.65f;
    private static final int   SHRINK_DELAY_MS = 3000;

    private static final PathInterpolator EASING_EMPHASIZED = new PathInterpolator(0.2f, 0.0f, 0.0f, 1.0f);
    private static final PathInterpolator EASING_EMPHASIZED_DECELERATE = new PathInterpolator(0.05f, 0.7f, 0.1f, 1.0f);
    private static final PathInterpolator EASING_EMPHASIZED_ACCELERATE = new PathInterpolator(0.3f, 0.0f, 0.8f, 0.15f);

    private static final int DUR_FAB_ROTATE = 250;

    private final FloatingActionButton fabMain;
    private final View fabMainWrapperView;
    private final Context context;
    private final float density;

    private boolean effectsEnabled;
    private boolean shrunk = false;

    private final Runnable shrinkRunnable = this::shrinkFab;

    public FABController(FloatingActionButton fabMain, View fabMainWrapperView) {
        this.fabMain = fabMain;
        this.fabMainWrapperView = fabMainWrapperView;
        this.context = fabMain.getContext();
        this.density = context.getResources().getDisplayMetrics().density;

        this.effectsEnabled = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getBoolean("fab_effects_enabled", true);
    }

    // ── Estado visual ─────────────────────────────────────────────────────────

    /** Transición animada hacia el estado "Expandido": icono expandido sin rotación de pulpo. */
    public void transitionToExpanded() {
        if (fabMain == null) return;
        if (effectsEnabled) {
            fabMain.animate()
                    .scaleX(0.88f).scaleY(0.88f).alpha(0.75f)
                    .setDuration(120)
                    .setInterpolator(EASING_EMPHASIZED_ACCELERATE)
                    .withEndAction(() -> {
                        fabMain.setImageResource(R.drawable.ic_close);
                        fabMain.animate()
                                .scaleX(1f).scaleY(1f).alpha(1f)
                                .rotation(0f)
                                .setDuration(DUR_FAB_ROTATE - 120)
                                .setInterpolator(EASING_EMPHASIZED)
                                .start();
                    }).start();
        } else {
            fabMain.setImageResource(R.drawable.ic_close);
            fabMain.setRotation(0f);
        }
    }

    /** Transición animada hacia el estado "Cerrado": icono selector normal (ic_menu) sin rotación. */
    public void transitionToCollapsed() {
        if (fabMain == null) return;
        if (effectsEnabled) {
            fabMain.animate()
                    .scaleX(0.88f).scaleY(0.88f).alpha(0.75f)
                    .setDuration(100)
                    .setInterpolator(EASING_EMPHASIZED_ACCELERATE)
                    .withEndAction(() -> {
                        fabMain.setImageResource(R.drawable.ic_menu);
                        fabMain.animate()
                                .scaleX(1f).scaleY(1f).alpha(1f)
                                .rotation(0f)
                                .setDuration(DUR_FAB_ROTATE - 100)
                                .setInterpolator(EASING_EMPHASIZED_ACCELERATE)
                                .start();
                    }).start();
        } else {
            fabMain.setImageResource(R.drawable.ic_menu);
            fabMain.setRotation(0f);
        }
    }

    /** Pone el icono de flecha de retorno (cuando el sub-menú "Herramientas" está activo). */
    public void showBackArrow() {
        if (fabMain == null) return;
        fabMain.setImageResource(R.drawable.ic_back_arrow);
        fabMain.setRotation(0f);
    }

    /** Restablece el estado visual del FAB a su estado de reposo. */
    public void resetInstant() {
        if (fabMain == null) return;
        fabMain.setImageResource(R.drawable.ic_menu);
        fabMain.setRotation(0f);
        fabMain.setScaleX(1f);
        fabMain.setScaleY(1f);
        fabMain.setAlpha(1f);
    }

    // ── Efectos (toggle) ─────────────────────────────────────────────────────

    public boolean isEffectsEnabled() {
        return effectsEnabled;
    }

    public void toggleEffects() {
        effectsEnabled = !effectsEnabled;
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
               .edit()
               .putBoolean("fab_effects_enabled", effectsEnabled)
               .apply();

        Toast.makeText(context,
                effectsEnabled ? "Efectos FAB: ACTIVADOS" : "Efectos FAB: DESACTIVADOS",
                Toast.LENGTH_SHORT).show();
    }

    // ── Encogimiento por inactividad (Desactivado para mantener visible y manejable en todo momento) ─────────────────────────────────────────

    public void scheduleShrink() {
        // Desactivado: el FAB no se encoge por inactividad
    }

    public void wake() {
        if (fabMainWrapperView == null) return;
        fabMainWrapperView.removeCallbacks(shrinkRunnable);
        if (shrunk) {
            shrunk = false;
            fabMainWrapperView.animate()
                    .scaleX(1f).scaleY(1f).alpha(1f)
                    .setDuration(200)
                    .setInterpolator(EASING_EMPHASIZED_DECELERATE)
                    .start();
        }
    }

    private void shrinkFab() {
        // Desactivado: mantener escala y opacidad al 100%
    }



    // ── Ciclo de vida ────────────────────────────────────────────────────────

    public void release() {
        if (fabMainWrapperView != null) {
            fabMainWrapperView.animate().cancel();
            fabMainWrapperView.removeCallbacks(shrinkRunnable);
        }
        if (fabMain != null) {
            fabMain.animate().cancel();
        }
    }
}
