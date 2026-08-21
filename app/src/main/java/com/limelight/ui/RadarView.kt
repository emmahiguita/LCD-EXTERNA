package com.limelight.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.cos
import kotlin.math.sin

class RadarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(
    context,
    attrs,
    defStyleAttr
) {

    private val gridPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.2f
            color = Color.argb(70, 0, 230, 118)
        }

    private val sweepPaint =
        Paint(Paint.ANTI_ALIAS_FLAG)

    private val pointPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.rgb(0, 230, 118)
        }

    private val sweepMatrix = Matrix()
    private var sweepShader: SweepGradient? = null
    private var rotationAngle = 0f

    private val points =
        listOf(
            0.28f to 45f,
            0.52f to 120f,
            0.72f to 215f,
            0.63f to 310f
        )

    private val animator =
        ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 2600
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                rotationAngle = it.animatedValue as Float
                invalidate()
            }
        }

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    fun startScanning() {
        if (!animator.isRunning) {
            animator.start()
        }
    }

    fun stopScanning() {
        if (animator.isRunning) {
            animator.cancel()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (visibility == VISIBLE) {
            startScanning()
        }
    }

    override fun onDetachedFromWindow() {
        stopScanning()
        super.onDetachedFromWindow()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == VISIBLE && isAttachedToWindow) {
            startScanning()
        } else {
            stopScanning()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            val cx = w / 2f
            val cy = h / 2f
            sweepShader = SweepGradient(
                cx,
                cy,
                intArrayOf(
                    Color.TRANSPARENT,
                    Color.argb(15, 0, 230, 118),
                    Color.argb(160, 0, 230, 118),
                    Color.TRANSPARENT
                ),
                floatArrayOf(
                    0f,
                    0.75f,
                    0.96f,
                    1f
                )
            )
            sweepPaint.shader = sweepShader
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cx = width / 2f
        val cy = height / 2f
        val maxRadius = minOf(width, height) * 0.46f

        if (maxRadius <= 0) return

        // Círculos concéntricos
        for (i in 1..4) {
            canvas.drawCircle(
                cx,
                cy,
                maxRadius * i / 4f,
                gridPaint
            )
        }

        // Líneas de retícula
        canvas.drawLine(
            cx - maxRadius,
            cy,
            cx + maxRadius,
            cy,
            gridPaint
        )

        canvas.drawLine(
            cx,
            cy - maxRadius,
            cx,
            cy + maxRadius,
            gridPaint
        )

        // Sweep giratorio sin asignación en onDraw
        sweepShader?.let { shader ->
            sweepMatrix.setRotate(rotationAngle, cx, cy)
            shader.setLocalMatrix(sweepMatrix)
            canvas.drawCircle(cx, cy, maxRadius, sweepPaint)
        }

        // Objetivo central
        pointPaint.color = Color.argb(220, 0, 230, 118)
        canvas.drawCircle(
            cx,
            cy,
            5.5f,
            pointPaint
        )

        // Equipos detectados con brillo pulsante al paso del radar
        points.forEach {
            val distance = it.first * maxRadius
            val angleDeg = it.second
            val angleRad = Math.toRadians(angleDeg.toDouble())
            val x = cx + cos(angleRad).toFloat() * distance
            val y = cy + sin(angleRad).toFloat() * distance

            val diff = (rotationAngle - angleDeg + 360f) % 360f
            val pingAlpha = if (diff < 90f) {
                ((1f - diff / 90f) * 200 + 55).toInt().coerceIn(55, 255)
            } else {
                55
            }

            pointPaint.color = Color.argb(pingAlpha, 0, 230, 118)
            val radius = if (diff < 30f) 5.5f else 3.8f
            canvas.drawCircle(
                x,
                y,
                radius,
                pointPaint
            )
        }
    }
}