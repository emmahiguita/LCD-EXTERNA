package com.limelight.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PathEffect;
import android.graphics.DashPathEffect;
import android.util.AttributeSet;
import android.view.View;

/**
 * RadialRingView — Vista transparente que dibuja sobre el Canvas:
 *  1. Dos anillos concéntricos azul-cian alrededor del FAB (diseño de referencia).
 *  2. Líneas "spoke" tenues desde el centro hacia cada botón radial.
 *
 * No interfiere con eventos táctiles (importantForAccessibility="no", clickable=false).
 */
public class RadialRingView extends View {

    // ── Pintura anillos ───────────────────────────────────────────────────────
    private final Paint paintRingOuter = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintRingInner = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintSpoke     = new Paint(Paint.ANTI_ALIAS_FLAG);

    // ── Estado ────────────────────────────────────────────────────────────────
    private float cx, cy;                 // Centro del FAB en coordenadas de esta vista
    private float radiusOuter;            // Radio anillo exterior
    private float radiusInner;            // Radio anillo interior (≈ botones radiales)
    private double[] spokeAngles;         // Ángulos de las líneas radiales (grados)
    private boolean visible = false;      // Solo dibuja cuando el menú está abierto

    public RadialRingView(Context context) {
        super(context);
        init();
    }

    public RadialRingView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        float density = getResources().getDisplayMetrics().density;

        // Anillo exterior: borde fino azul eléctrico semitransparente
        paintRingOuter.setStyle(Paint.Style.STROKE);
        paintRingOuter.setStrokeWidth(1.2f * density);
        paintRingOuter.setColor(0x4000C6FF);   // cian eléctrico ~25% opacidad

        // Anillo interior: ligeramente más brillante
        paintRingInner.setStyle(Paint.Style.STROKE);
        paintRingInner.setStrokeWidth(0.8f * density);
        paintRingInner.setColor(0x2A0088CC);   // azul oscuro ~16% opacidad

        // Spokes: líneas punteadas muy tenues
        paintSpoke.setStyle(Paint.Style.STROKE);
        paintSpoke.setStrokeWidth(0.6f * density);
        paintSpoke.setColor(0x1800AAEE);       // azul sutil ~10% opacidad
        paintSpoke.setPathEffect(new DashPathEffect(new float[]{4 * density, 6 * density}, 0));

        // Esta vista NO consume eventos táctiles
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    /**
     * Actualiza los parámetros de dibujado. Llamar desde {@link OverlayAnimationController}
     * cuando el menú se abre/cierra o el FAB cambia de posición.
     *
     * @param cx           Centro X del FAB en coordenadas del padre (FrameLayout overlay)
     * @param cy           Centro Y del FAB en coordenadas del padre
     * @param innerRadius  Radio donde se ubican los botones radiales (px)
     * @param spokeAngles  Array de ángulos (grados) para las líneas — null para no dibujar spokes
     * @param show         true = menú abierto; false = ocultar todo
     */
    public void update(float cx, float cy, float innerRadius, double[] spokeAngles, boolean show) {
        this.cx           = cx;
        this.cy           = cy;
        this.radiusInner  = innerRadius;
        this.radiusOuter  = innerRadius * 1.18f;   // anillo exterior ≈ 18% más grande
        this.spokeAngles  = (spokeAngles != null) ? spokeAngles.clone() : null;
        this.visible      = show;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (!visible) return;

        // ── Anillo exterior ───────────────────────────────────────────────────
        canvas.drawCircle(cx, cy, radiusOuter, paintRingOuter);

        // ── Anillo interior (radio de los botones) ────────────────────────────
        canvas.drawCircle(cx, cy, radiusInner, paintRingInner);

        // ── Spokes (líneas radiales) ──────────────────────────────────────────
        if (spokeAngles != null) {
            float fabR = radiusInner * 0.28f;   // deja hueco alrededor del FAB
            for (double angleDeg : spokeAngles) {
                double rad = Math.toRadians(angleDeg);
                float cos  = (float) Math.cos(rad);
                float sin  = (float) Math.sin(rad);
                canvas.drawLine(
                    cx + cos * fabR,
                    cy + sin * fabR,
                    cx + cos * (radiusInner - 24 * getResources().getDisplayMetrics().density),
                    cy + sin * (radiusInner - 24 * getResources().getDisplayMetrics().density),
                    paintSpoke
                );
            }
        }
    }
}
