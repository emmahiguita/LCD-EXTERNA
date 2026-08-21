package com.limelight.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.animation.PathInterpolator;

/**
 * OverlayAnimationController — Responsabilidad Única: gestión de todas las animaciones del menú radial.
 *
 * Controla:
 *  - Expansión (cascade) e implosión (reverse cascade) de los ítems radiales.
 *  - Circular reveal / hide del scrim.
 *  - Pulso del halo (fabHaloPulse).
 *  - Anillo orbital (fabOrbitRing).
 */
public class OverlayAnimationController {

    private static final int DUR_EXPAND_ITEM  = 250;
    private static final int DUR_COLLAPSE_ITEM = 180;
    private static final int STAGGER_MS        = 25;
    private static final int DUR_HALO_PULSE    = 1400;
    private static final int HALO_PULSE_GAP    = 500;
    private static final int DUR_SCRIM_SHOW    = 350;
    private static final int DUR_SCRIM_HIDE    = 220;

    private static final PathInterpolator EASING_EMPHASIZED = new PathInterpolator(0.2f, 0.0f, 0.0f, 1.0f);
    private static final PathInterpolator EASING_EMPHASIZED_DECELERATE = new PathInterpolator(0.05f, 0.7f, 0.1f, 1.0f);
    private static final PathInterpolator EASING_EMPHASIZED_ACCELERATE = new PathInterpolator(0.3f, 0.0f, 0.8f, 0.15f);

    private final View   fabScrim;
    private final View   fabHaloPulse;
    private final View   fabHaloGlow;
    private final View   fabOrbitRing;
    private final View   overlayRoot;
    private final View   fabMainWrapper;

    /** Vista Canvas que dibuja anillos concéntricos y spokes (puede ser null). */
    private RadialRingView radialRingView;

    private boolean pulsing        = false;
    private boolean effectsEnabled = true;
    private boolean performanceMode = false;

    private final Runnable pulseRunnable = this::runPulseCycle;
    private final Runnable glowRunnable  = this::runGlowFlash;

    // Callbacks externos para notificar al FAB cuándo puede reiniciar el pulso
    private Runnable onCollapseAnimationFinished;

    public OverlayAnimationController(
            View overlayRoot,
            View fabMainWrapper,
            View fabScrim,
            View fabHaloPulse,
            View fabHaloGlow,
            View fabOrbitRing,
            boolean effectsEnabled) {

        this.overlayRoot    = overlayRoot;
        this.fabMainWrapper = fabMainWrapper;
        this.fabScrim       = fabScrim;
        this.fabHaloPulse   = fabHaloPulse;
        this.fabHaloGlow    = fabHaloGlow;
        this.fabOrbitRing   = fabOrbitRing;
        this.effectsEnabled = effectsEnabled;
    }

    public void setEffectsEnabled(boolean enabled) { this.effectsEnabled = enabled; }
    public void setPerformanceMode(boolean perf)   { this.performanceMode = perf; }
    public void setOnCollapseFinished(Runnable r)  { this.onCollapseAnimationFinished = r; }
    public void setRadialRingView(RadialRingView v) { this.radialRingView = v; }

    // ── Expansión del menú ────────────────────────────────────────────────────

    /** Anima el anillo orbital al abrirse, expandiéndolo al radio real de los ítems. */
    public void animateOrbitRingExpand(float adaptiveRadius, float density) {
        if (fabOrbitRing == null) return;
        // El anillo orbital en el XML es 116dp de diámetro (58dp radio).
        // Queremos que coincida con el diámetro del círculo de ítems = adaptiveRadius*2 + márgenes.
        float ringBaseDp = 116f; // diámetro real del anillo en overlay_fab_menu.xml
        float targetScale = (adaptiveRadius * 2f + 12f * density) / (ringBaseDp * density);

        if (effectsEnabled) {
            fabOrbitRing.animate()
                    .scaleX(targetScale).scaleY(targetScale)
                    .alpha(0.22f)
                    .setDuration(250)
                    .setInterpolator(EASING_EMPHASIZED)
                    .start();
        } else {
            fabOrbitRing.setScaleX(targetScale);
            fabOrbitRing.setScaleY(targetScale);
            fabOrbitRing.setAlpha(0.22f);
        }
    }

    /**
     * Hace aparecer (brotar) los ítems activos del menú con un cascade escalonado.
     * @param radialItems Pool completo de vistas.
     * @param activeCount Número de ítems activos en el menú actual.
     * @param targetPositions Array de [x, y] precalculados por RadialMenuController.
     * @param angles Ángulos (grados) de cada ítem, para dibujar los spokes.
     */
    public void animateExpansion(View[] radialItems, int activeCount, float[][] targetPositions, double[] angles) {
        // Actualizar RadialRingView con radio y ángulos reales
        if (radialRingView != null && fabMainWrapper != null && activeCount > 0 && targetPositions.length > 0) {
            float cx = fabMainWrapper.getX() + fabMainWrapper.getWidth()  / 2f;
            float cy = fabMainWrapper.getY() + fabMainWrapper.getHeight() / 2f;
            // Radio = distancia del centro del FAB al centro del ítem (FrameLayout 72dp del XML → centro a +36dp)
            float density = overlayRoot.getContext().getResources().getDisplayMetrics().density;
            float itemHalf = 36f * density;
            float dx = (targetPositions[0][0] + itemHalf) - cx;
            float dy = (targetPositions[0][1] + itemHalf) - cy;
            float radius = (float) Math.hypot(dx, dy);
            radialRingView.update(cx, cy, radius, angles, true);
        }

        for (int i = 0; i < activeCount; i++) {
            final View item = radialItems[i];
            if (item == null) continue;
            final float[] pos  = targetPositions[i];
            final long delay   = i * STAGGER_MS;

            if (effectsEnabled) {
                item.setAlpha(0f);
                item.setScaleX(0.7f);
                item.setScaleY(0.7f);
                item.setVisibility(View.VISIBLE);

                item.postDelayed(() -> {
                    item.animate()
                            .x(pos[0]).y(pos[1])
                            .alpha(1f)
                            .scaleX(1f).scaleY(1f)
                            .setDuration(DUR_EXPAND_ITEM)
                            .setInterpolator(EASING_EMPHASIZED)
                            .setListener(null)
                            .start();
                }, delay);
            } else {
                item.setX(pos[0]);
                item.setY(pos[1]);
                item.setAlpha(1f);
                item.setScaleX(1f);
                item.setScaleY(1f);
                item.setVisibility(View.VISIBLE);
            }
        }
    }

    // ── Colapso del menú ─────────────────────────────────────────────────────

    /** Retrae el anillo orbital a su tamaño original. */
    public void animateOrbitRingCollapse() {
        if (fabOrbitRing == null) return;
        if (effectsEnabled) {
            fabOrbitRing.animate()
                    .scaleX(1.0f).scaleY(1.0f)
                    .alpha(0.35f)
                    .setDuration(220)
                    .setInterpolator(EASING_EMPHASIZED_ACCELERATE)
                    .start();
        } else {
            fabOrbitRing.setScaleX(1.0f);
            fabOrbitRing.setScaleY(1.0f);
            fabOrbitRing.setAlpha(0.35f);
        }
    }

    /**
     * Anima el colapso (reverse cascade) de los ítems radiales al centro del FAB.
     * @param radialItems Pool completo de vistas.
     * @param fabCenterX  Coordenada X del centro del FAB.
     * @param fabCenterY  Coordenada Y del centro del FAB.
     */
    public void animateCollapse(View[] radialItems, float fabCenterX, float fabCenterY) {
        int totalItems = radialItems.length;
        for (int i = totalItems - 1; i >= 0; i--) {
            final View item = radialItems[i];
            if (item == null) continue;
            final float cx    = fabCenterX - 36f; // mitad del FrameLayout 72dp del XML
            final float cy    = fabCenterY - 36f;
            final long delay  = (totalItems - 1 - i) * STAGGER_MS;

            if (effectsEnabled) {
                item.animate().cancel();
                item.postDelayed(() -> {
                    item.animate().cancel();
                    item.animate()
                            .x(cx).y(cy)
                            .alpha(0f)
                            .scaleX(0.2f).scaleY(0.2f)
                            .setDuration(DUR_COLLAPSE_ITEM)
                            .setInterpolator(EASING_EMPHASIZED_ACCELERATE)
                            .setListener(new AnimatorListenerAdapter() {
                                @Override
                                public void onAnimationEnd(Animator animation) {
                                    item.setVisibility(View.INVISIBLE);
                                    item.animate().setListener(null);
                                }
                            })
                            .start();
                }, delay);
            } else {
                item.setX(cx);
                item.setY(cy);
                item.setVisibility(View.INVISIBLE);
            }
        }

        // Ocultar RadialRingView al colapsar
        if (radialRingView != null) radialRingView.update(0, 0, 0, null, false);

        // Notificar al FABController cuándo puede iniciar el pulso de halo nuevamente
        if (effectsEnabled && onCollapseAnimationFinished != null && fabScrim != null) {
            long haloDelay = DUR_SCRIM_HIDE + (long)(totalItems * STAGGER_MS) + 80L;
            fabScrim.postDelayed(onCollapseAnimationFinished, haloDelay);
        }
    }

    /** Colapso instantáneo sin animaciones (usado durante drag). */
    public void collapseInstant(View[] radialItems) {
        pulsing = false;
        if (radialRingView != null) radialRingView.update(0, 0, 0, null, false);
        if (fabHaloPulse != null) {
            fabHaloPulse.animate().cancel();
            fabHaloPulse.setVisibility(View.INVISIBLE);
        }
        if (fabOrbitRing != null) {
            fabOrbitRing.animate().cancel();
            fabOrbitRing.setScaleX(1.0f);
            fabOrbitRing.setScaleY(1.0f);
            fabOrbitRing.setAlpha(0.25f);
        }
        for (View item : radialItems) {
            if (item != null) {
                item.animate().cancel();
                item.setAlpha(0f);
                item.setScaleX(1f);
                item.setScaleY(1f);
                item.setVisibility(View.INVISIBLE);
            }
        }
    }

    // ── Scrim ─────────────────────────────────────────────────────────────────

    public void showScrim(int fabCenterX, int fabCenterY) {
        if (fabScrim == null) return;
        if (effectsEnabled) {
            float maxRadius = (float) Math.hypot(overlayRoot.getWidth(), overlayRoot.getHeight());
            fabScrim.setVisibility(View.VISIBLE);
            Animator reveal = ViewAnimationUtils.createCircularReveal(fabScrim, fabCenterX, fabCenterY, 0f, maxRadius);
            reveal.setDuration(DUR_SCRIM_SHOW);
            reveal.setInterpolator(EASING_EMPHASIZED_DECELERATE);
            reveal.start();
        } else {
            fabScrim.setVisibility(View.VISIBLE);
            fabScrim.setAlpha(1f);
        }
    }

    public void hideScrim(int fabCenterX, int fabCenterY) {
        if (fabScrim == null || fabScrim.getVisibility() != View.VISIBLE) return;
        if (effectsEnabled) {
            float maxRadius = (float) Math.hypot(overlayRoot.getWidth(), overlayRoot.getHeight());
            Animator reveal = ViewAnimationUtils.createCircularReveal(fabScrim, fabCenterX, fabCenterY, maxRadius, 0f);
            reveal.setDuration(DUR_SCRIM_HIDE);
            reveal.setInterpolator(EASING_EMPHASIZED_ACCELERATE);
            reveal.addListener(new AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(Animator animation) {
                    fabScrim.setVisibility(View.GONE);
                }
            });
            reveal.start();
        } else {
            fabScrim.setVisibility(View.GONE);
        }
    }

    // ── Pulso del Halo ────────────────────────────────────────────────────────

    public void startHaloPulse() {
        if (!effectsEnabled || fabHaloPulse == null || performanceMode) return;
        pulsing = true;
        fabHaloPulse.setVisibility(View.VISIBLE);
        runPulseCycle();
    }

    public void stopHaloPulse() {
        pulsing = false;
        if (fabHaloPulse != null) {
            fabHaloPulse.animate().cancel();
            fabHaloPulse.setVisibility(View.INVISIBLE);
        }
        if (fabHaloGlow != null) {
            fabHaloGlow.animate().cancel();
            fabHaloGlow.setAlpha(0.4f); // Reset a la opacidad base
        }
        // Remover callbacks pendientes del pulse
        if (fabScrim != null) {
            fabScrim.removeCallbacks(pulseRunnable);
            fabScrim.removeCallbacks(glowRunnable);
        }
    }

    private void runPulseCycle() {
        if (!effectsEnabled || !pulsing || fabHaloPulse == null || performanceMode) return;
        fabHaloPulse.setScaleX(1f);
        fabHaloPulse.setScaleY(1f);
        fabHaloPulse.setAlpha(0.7f);
        fabHaloPulse.animate()
                .scaleX(1.8f).scaleY(1.8f).alpha(0f)
                .rotationBy(90f)
                .setDuration(DUR_HALO_PULSE)
                .setInterpolator(EASING_EMPHASIZED_DECELERATE)
                .withEndAction(() -> {
                    if (effectsEnabled && pulsing && fabHaloPulse != null && fabScrim != null) {
                        fabScrim.postDelayed(pulseRunnable, HALO_PULSE_GAP);
                    }
                })
                .start();

        // Pequeño flash en el glow sincronizado con el pulso
        if (fabHaloPulse != null) {
            fabHaloPulse.postDelayed(glowRunnable, DUR_HALO_PULSE / 2);
        }
    }

    private void runGlowFlash() {
        if (!effectsEnabled || !pulsing || fabHaloGlow == null) return;
        fabHaloGlow.animate()
                .alpha(0.7f).setDuration(120)
                .withEndAction(() -> fabHaloGlow.animate().alpha(0.4f).setDuration(350).start())
                .start();
    }

    // ── Ciclo de vida ────────────────────────────────────────────────────────

    public void release() {
        pulsing = false;
        if (fabHaloPulse != null)  { fabHaloPulse.animate().cancel();  fabHaloPulse.setVisibility(View.INVISIBLE); }
        if (fabOrbitRing != null)  { fabOrbitRing.animate().cancel(); }
        if (fabHaloGlow  != null)  { fabHaloGlow.animate().cancel(); }
        if (fabScrim     != null)  {
            fabScrim.animate().cancel();
            fabScrim.removeCallbacks(pulseRunnable);
            fabScrim.removeCallbacks(glowRunnable);
        }
    }
}
