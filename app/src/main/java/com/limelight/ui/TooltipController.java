package com.limelight.ui;

import android.view.View;
import android.widget.TextView;

/**
 * TooltipController — Gestiona el Tooltip dinámico y flotante para los botones radiales.
 */
public class TooltipController {
    private final TextView tooltipView;
    private final float density;

    public TooltipController(TextView tooltipView) {
        this.tooltipView = tooltipView;
        this.density = tooltipView != null 
                ? tooltipView.getContext().getResources().getDisplayMetrics().density 
                : 1f;
    }

    /**
     * Muestra el tooltip encima del botón objetivo.
     */
    public void showTooltip(View targetView, String labelText) {
        if (tooltipView == null || targetView == null || labelText == null || labelText.isEmpty()) return;

        tooltipView.setText(labelText);
        tooltipView.setVisibility(View.VISIBLE);
        tooltipView.setAlpha(0f);

        // Medir el texto para obtener dimensiones dinámicas
        tooltipView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int tw = tooltipView.getMeasuredWidth();
        int th = tooltipView.getMeasuredHeight();

        // Calcular posición centrada sobre el botón radial
        float targetCenterX = targetView.getX() + targetView.getWidth() / 2f;
        float targetTopY = targetView.getY();

        tooltipView.setX(targetCenterX - tw / 2f);
        tooltipView.setY(targetTopY - th - (6 * density)); // 6dp de separación

        // Animación suave de aparición
        tooltipView.animate().cancel();
        tooltipView.animate()
                .alpha(1f)
                .setDuration(140)
                .start();
    }

    /**
     * Oculta el tooltip con una animación de desvanecimiento rápida.
     */
    public void hideTooltip() {
        if (tooltipView == null || tooltipView.getVisibility() != View.VISIBLE) return;

        tooltipView.animate().cancel();
        tooltipView.animate()
                .alpha(0f)
                .setDuration(100)
                .withEndAction(() -> tooltipView.setVisibility(View.GONE))
                .start();
    }

    /**
     * Libera recursos y cancela animaciones en curso.
     */
    public void release() {
        if (tooltipView != null) {
            tooltipView.animate().cancel();
            tooltipView.setVisibility(View.GONE);
        }
    }
}
