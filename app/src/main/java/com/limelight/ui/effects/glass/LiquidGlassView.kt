package com.limelight.ui.effects.glass

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import com.limelight.R

/**
 * LiquidGlassView — Contenedor XML envolvente de Vidrio Líquido.
 *
 * Se puede usar directamente en cualquier layout XML:
 * ```xml
 * <com.limelight.ui.effects.glass.LiquidGlassView
 *     android:layout_width="wrap_content"
 *     android:layout_height="wrap_content">
 *     ...
 * </com.limelight.ui.effects.glass.LiquidGlassView>
 * ```
 */
class LiquidGlassView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val glassEngine = LiquidGlassEngine()
    private var activeTier = GlassCapabilities.GlassTier.FALLBACK

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = context.resources.getDimension(R.dimen.neon_stroke)
        color = Color.argb(45, 255, 255, 255)
    }

    private val boundsRect = RectF()
    private var pressAnimator: ValueAnimator? = null

    init {
        setWillNotDraw(false)
        clipToOutline = true

        val cornerRadius = context.resources.getDimension(R.dimen.sd_corner_lg)
        glassEngine.cornerRadius = cornerRadius

        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, cornerRadius)
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        activeTier = GlassCapabilities.resolveTier(this)
        applyGlassEffect()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        boundsRect.set(0f, 0f, w.toFloat(), h.toFloat())
        applyGlassEffect()
    }

    private fun applyGlassEffect() {
        if (width > 0 && height > 0) {
            glassEngine.applyToView(this, activeTier)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                glassEngine.touchPointX = event.x
                glassEngine.touchPointY = event.y
                animatePressBulge(1.0f)
            }
            MotionEvent.ACTION_MOVE -> {
                glassEngine.touchPointX = event.x
                glassEngine.touchPointY = event.y
                applyGlassEffect()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                animatePressBulge(0.0f)
            }
        }
        return super.onTouchEvent(event)
    }

    private fun animatePressBulge(target: Float) {
        pressAnimator?.cancel()
        pressAnimator = ValueAnimator.ofFloat(glassEngine.pressBulge, target).apply {
            duration = 220
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                glassEngine.pressBulge = it.animatedValue as Float
                applyGlassEffect()
            }
            start()
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        // Dibujar borde especular sutil en la capa superior
        val halfStroke = borderPaint.strokeWidth * 0.5f
        canvas.drawRoundRect(
            halfStroke,
            halfStroke,
            width - halfStroke,
            height - halfStroke,
            glassEngine.cornerRadius,
            glassEngine.cornerRadius,
            borderPaint
        )
    }
}
