package com.limelight.ui.keyboard

import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets

/**
 * Posicionamiento del panel del teclado en pantalla.
 *
 * Responsabilidad única: calcular dónde poner el CardView del teclado
 * según la orientación, el tamaño del contenido, los insets del sistema
 * y la escala del usuario. También se encarga del clamp durante el arrastre.
 */
class KeyboardPositionManager {

    /** Ajusta LayoutParams y posición del panel en la orientación actual. */
    fun constrainAndPositionInitial(
        panelRoot: View,
        currentScale: Float,
        onScaleChanged: (Float) -> Unit
    ) {
        val parent = panelRoot.parent as? View
        val parentW: Float
        val parentH: Float
        if (parent == null || parent.width == 0) {
            val metrics = panelRoot.context.resources.displayMetrics
            parentW = metrics.widthPixels.toFloat()
            parentH = metrics.heightPixels.toFloat()
        } else {
            parentW = parent.width.toFloat()
            parentH = parent.height.toFloat()
        }
        val isLandscape = parentW > parentH
        var scale = currentScale
        val bottomInset = getBottomInset(parent)
        val topInset = getTopInset(parent)
        val usableH = parentH - bottomInset - topInset

        val lp = panelRoot.layoutParams as? ViewGroup.LayoutParams

        if (isLandscape) {
            val maxW = (parentW * 0.75f).toInt()
            if (lp != null) { lp.width = maxW; panelRoot.layoutParams = lp }
            panelRoot.measure(
                View.MeasureSpec.makeMeasureSpec(maxW, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))

            val availableH = usableH - dpToPx(panelRoot, 12f)
            if (panelRoot.measuredHeight > 0 && panelRoot.measuredHeight * scale > availableH) {
                scale = Math.max(0.5f, availableH / panelRoot.measuredHeight.toFloat())
            }
            val scaledW = panelRoot.measuredWidth * scale
            val scaledH = panelRoot.measuredHeight * scale
            panelRoot.x = Math.max(0f, (parentW - scaledW) / 2f)
            panelRoot.y = parentH - bottomInset - scaledH - dpToPx(panelRoot, 6f)
        } else {
            if (lp != null) { lp.width = ViewGroup.LayoutParams.MATCH_PARENT; panelRoot.layoutParams = lp }
            panelRoot.measure(
                View.MeasureSpec.makeMeasureSpec(parentW.toInt(), View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))

            val panelW = panelRoot.measuredWidth * scale
            val maxW = parentW - dpToPx(panelRoot, 8f)
            if (panelW > maxW && scale > 0.60f) {
                scale = Math.max(0.60f, scale * maxW / panelW)
            }
            val availableH = usableH - dpToPx(panelRoot, 12f)
            if (panelRoot.measuredHeight > 0 && panelRoot.measuredHeight * scale > availableH) {
                scale = Math.min(scale, availableH / panelRoot.measuredHeight.toFloat())
            }
            val scaledW = panelRoot.measuredWidth * scale
            val scaledH = panelRoot.measuredHeight * scale
            panelRoot.x = Math.max(dpToPx(panelRoot, 4f).toFloat(), (parentW - scaledW) / 2f)
            panelRoot.y = parentH - bottomInset - scaledH - dpToPx(panelRoot, 8f)
        }

        onScaleChanged(scale)
        panelRoot.scaleX = scale
        panelRoot.scaleY = scale
    }

    fun clampX(x: Float, parent: View, panelW: Float): Float {
        val minX = 0f
        val maxX = Math.max(0f, parent.width - panelW)
        return if (maxX >= minX) Math.max(minX, Math.min(maxX, x)) else 0f
    }

    fun clampY(y: Float, parent: View, panelH: Float): Float {
        val topInset = getTopInset(parent)
        val bottomInset = getBottomInset(parent)
        val maxY = parent.height - bottomInset - panelH
        return Math.max(topInset, Math.min(maxY, y))
    }

    // ── Insets ─────────────────────────────────────────────────────────

    fun getBottomInset(parent: View?): Float {
        if (parent == null) return 0f
        if (Build.VERSION.SDK_INT >= 30) {
            val insets = parent.rootWindowInsets ?: return 0f
            return insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()).bottom.toFloat()
        }
        if (Build.VERSION.SDK_INT >= 23) {
            @Suppress("DEPRECATION")
            return parent.rootWindowInsets?.systemWindowInsetBottom?.toFloat() ?: 0f
        }
        return 0f
    }

    fun getTopInset(parent: View?): Float {
        if (parent == null) return 0f
        if (Build.VERSION.SDK_INT >= 30) {
            val insets = parent.rootWindowInsets ?: return 0f
            return insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()).top.toFloat()
        }
        if (Build.VERSION.SDK_INT >= 23) {
            @Suppress("DEPRECATION")
            return parent.rootWindowInsets?.systemWindowInsetTop?.toFloat() ?: 0f
        }
        return 0f
    }

    private fun dpToPx(v: View, dp: Float): Int =
        Math.round(dp * v.context.resources.displayMetrics.density)
}
