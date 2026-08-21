package com.limelight.ui.effects

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.Typeface
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.AttributeSet
import android.view.Choreographer
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.PathInterpolator
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.limelight.R
import com.limelight.utils.ThemeManager
import kotlin.math.min

/**
 * Visual states for LiquidStartButton.
 */
enum class ConnectionVisualState {
    IDLE,
    PRESSED,
    CONNECTING,
    CONNECTED,
    STARTING_STREAM,
    ERROR,
    DISABLED
}

/**
 * LiquidStartButton — High-performance 3D Liquid Glass Start Button.
 *
 * Designed for ultra-low latency & zero memory allocations during onDraw().
 * Features:
 *  • Cached gradients and paths computed in onSizeChanged()
 *  • Physics-based SpringAnimation for organic press feedback
 *  • Orbital traveling glow during CONNECTING state
 *  • Lifecycle-aware Choreographer management (zero idle battery consumption)
 *  • Dynamic accessibility descriptions for TalkBack
 *  • Color adaptation matching SmartDisplay themes
 */
class LiquidStartButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        private val EASING_DECELERATE = PathInterpolator(0.05f, 0.7f, 0.1f, 1f)
    }

    private val density = resources.displayMetrics.density

    // ── Paints (pre-allocated) ───────────────────────────────────────────────
    private val ambientGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val glassBodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val specularPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.6f * density
    }
    private val orbitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3.2f * density
        strokeCap = Paint.Cap.ROUND
    }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        textSize = 15f * density
        letterSpacing = 0.14f
    }

    // ── Geometry & Paths (pre-allocated) ─────────────────────────────────────
    private val specularBounds = RectF()
    private val orbitBounds = RectF()
    private val playPath = Path()
    private val checkPath = Path()
    private val errorPath = Path()

    // ── Cached Shaders (rebuilt only on onSizeChanged or theme change) ────────
    private var cachedGlowShader: RadialGradient? = null
    private var cachedGlassShader: RadialGradient? = null
    private var cachedSpecularShader: LinearGradient? = null
    private var cachedRimShader: SweepGradient? = null
    private var cachedOrbitShader: SweepGradient? = null
    private var cachedPlayShader: LinearGradient? = null

    // ── State variables ──────────────────────────────────────────────────────
    private var visualState = ConnectionVisualState.IDLE
    private var pressAmount = 0f
    private var orbitAngle = 0f
    private var targetPcName: String = "PC"

    // ── Frame timing ─────────────────────────────────────────────────────────
    private var frameScheduled = false
    private var lastFrameNanos = 0L

    private val vibrator: Vibrator? by lazy {
        if (isInEditMode) null
        else context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            frameScheduled = false

            if (lastFrameNanos != 0L) {
                val dt = ((frameTimeNanos - lastFrameNanos) / 1_000_000_000f).coerceAtMost(0.033f)
                if (visualState == ConnectionVisualState.CONNECTING) {
                    orbitAngle = (orbitAngle + 160f * dt) % 360f
                }
            }

            lastFrameNanos = frameTimeNanos
            invalidate()

            if (needsContinuousFrames()) {
                scheduleFrame()
            }
        }
    }

    init {
        isClickable = true
        isFocusable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        updateAccessibilityDescription()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val defaultSize = (176 * density).toInt()
        val width = resolveSize(defaultSize, widthMeasureSpec)
        val height = resolveSize(defaultSize, heightMeasureSpec)
        val size = min(width, height)
        setMeasuredDimension(size, size)
    }

    /**
     * Updates the target PC name displayed and used in TalkBack announcements.
     */
    fun setTargetPcName(name: String) {
        targetPcName = if (name.isNotBlank()) name else "PC"
        updateAccessibilityDescription()
    }

    /**
     * Changes the visual state and triggers haptics and frame animations accordingly.
     */
    fun setConnectionState(state: ConnectionVisualState) {
        if (visualState == state) return
        visualState = state

        when (state) {
            ConnectionVisualState.CONNECTING -> {
                scheduleFrame()
            }
            ConnectionVisualState.CONNECTED -> {
                stopFrames()
                vibrate(20L)
                animateSuccess()
            }
            ConnectionVisualState.ERROR -> {
                stopFrames()
                vibrate(40L)
            }
            else -> {
                stopFrames()
            }
        }

        updateAccessibilityDescription()
        invalidate()
    }

    fun getConnectionState(): ConnectionVisualState = visualState

    private fun updateAccessibilityDescription() {
        contentDescription = when (visualState) {
            ConnectionVisualState.CONNECTING -> "Conectando con $targetPcName"
            ConnectionVisualState.CONNECTED -> "Conectado a $targetPcName"
            ConnectionVisualState.STARTING_STREAM -> "Iniciando transmisión con $targetPcName"
            ConnectionVisualState.ERROR -> "Error de conexión con $targetPcName. Toca para reintentar"
            ConnectionVisualState.DISABLED -> "$targetPcName no disponible"
            else -> "Iniciar conexión con $targetPcName"
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return

        val cx = w * 0.5f
        val cy = h * 0.5f
        val radius = min(w, h) * 0.44f

        buildCachedShaders(cx, cy, radius)
        buildCachedPaths(cx, cy, radius)
    }

    private fun buildCachedShaders(cx: Float, cy: Float, radius: Float) {
        val isLight = ThemeManager.isLight(context)
        val accent = ThemeManager.accentColor(context)

        // 1. Ambient Glow Shader
        val glowAlpha1 = if (isLight) 0x22 else 0x38
        val glowAlpha2 = if (isLight) 0x0A else 0x14
        val glowColor1 = (glowAlpha1 shl 24) or (accent and 0x00FFFFFF)
        val glowColor2 = (glowAlpha2 shl 24) or (accent and 0x00FFFFFF)

        cachedGlowShader = RadialGradient(
            cx, cy, radius * 1.30f,
            intArrayOf(glowColor1, glowColor2, Color.TRANSPARENT),
            floatArrayOf(0.60f, 0.82f, 1f),
            Shader.TileMode.CLAMP
        )

        // 2. Glass Body Shader
        if (isLight) {
            cachedGlassShader = RadialGradient(
                cx - radius * 0.35f, cy - radius * 0.45f, radius * 1.55f,
                intArrayOf(
                    Color.argb(220, 240, 248, 255),
                    Color.argb(235, 215, 232, 250),
                    Color.argb(250, 185, 210, 240)
                ),
                floatArrayOf(0f, 0.50f, 1f),
                Shader.TileMode.CLAMP
            )
        } else {
            cachedGlassShader = RadialGradient(
                cx - radius * 0.35f, cy - radius * 0.45f, radius * 1.55f,
                intArrayOf(
                    Color.argb(175, 45, 75, 110),
                    Color.argb(230, 15, 30, 50),
                    Color.argb(250, 6, 14, 25)
                ),
                floatArrayOf(0f, 0.45f, 1f),
                Shader.TileMode.CLAMP
            )
        }

        // 3. Specular Highlight Shader (oval reflection)
        cachedSpecularShader = LinearGradient(
            cx - radius, cy - radius, cx, cy,
            intArrayOf(
                Color.argb(130, 255, 255, 255),
                Color.argb(35, 180, 235, 255),
                Color.TRANSPARENT
            ),
            null,
            Shader.TileMode.CLAMP
        )

        specularBounds.set(
            cx - radius * 0.72f,
            cy - radius * 0.72f,
            cx + radius * 0.22f,
            cy + radius * 0.10f
        )

        // 4. Refractive Rim Shader
        cachedRimShader = SweepGradient(
            cx, cy,
            intArrayOf(
                0xFF5DE1FF.toInt(),
                0xFFFFFFFF.toInt(),
                0xFF008BFF.toInt(),
                0xFF5DE1FF.toInt()
            ),
            null
        )

        // 5. Connecting Orbit Shader
        cachedOrbitShader = SweepGradient(
            cx, cy,
            intArrayOf(
                Color.TRANSPARENT,
                0x0022D3EE.toInt(),
                0xFF55E7FF.toInt(),
                0xFFFFFFFF.toInt(),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.50f, 0.82f, 0.94f, 1f)
        )

        orbitBounds.set(
            cx - radius,
            cy - radius,
            cx + radius,
            cy + radius
        )

        // 6. Play Icon Shader
        cachedPlayShader = LinearGradient(
            cx - radius * 0.2f, cy - radius * 0.3f,
            cx + radius * 0.2f, cy + radius * 0.3f,
            0xFF8CE9FF.toInt(),
            0xFF00A4FF.toInt(),
            Shader.TileMode.CLAMP
        )
    }

    private fun buildCachedPaths(cx: Float, cy: Float, radius: Float) {
        // Play Triangle
        val s = radius * 0.32f
        val playCenterY = cy - radius * 0.16f
        playPath.reset()
        playPath.moveTo(cx - s * 0.50f, playCenterY - s)
        playPath.lineTo(cx + s * 0.85f, playCenterY)
        playPath.lineTo(cx - s * 0.50f, playCenterY + s)
        playPath.close()

        // Check Mark
        val cs = radius * 0.30f
        val checkCenterY = cy - radius * 0.12f
        checkPath.reset()
        checkPath.moveTo(cx - cs * 0.85f, checkCenterY + cs * 0.05f)
        checkPath.lineTo(cx - cs * 0.20f, checkCenterY + cs * 0.65f)
        checkPath.lineTo(cx + cs * 0.90f, checkCenterY - cs * 0.60f)

        // Error Exclamation
        val es = radius * 0.26f
        val errorCenterY = cy - radius * 0.16f
        errorPath.reset()
        errorPath.moveTo(cx, errorCenterY - es)
        errorPath.lineTo(cx, errorCenterY + es * 0.3f)
    }

    // ── Render Pipeline (Zero Allocations in onDraw) ─────────────────────────

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val cx = w * 0.5f
        val cy = h * 0.5f
        val radius = min(w, h) * 0.44f * (1f - pressAmount * 0.04f)

        // 1. Ambient Glow
        ambientGlowPaint.shader = cachedGlowShader
        canvas.drawCircle(cx, cy, radius * 1.28f, ambientGlowPaint)

        // 2. Glass Body
        glassBodyPaint.shader = cachedGlassShader
        canvas.drawCircle(cx, cy, radius, glassBodyPaint)

        // 3. Specular Highlight Oval
        specularPaint.shader = cachedSpecularShader
        canvas.drawOval(specularBounds, specularPaint)

        // 4. Refractive Rim
        rimPaint.shader = cachedRimShader
        canvas.drawCircle(cx, cy, radius, rimPaint)

        // 5. Connecting Orbital Highlight
        if (visualState == ConnectionVisualState.CONNECTING) {
            drawConnectingOrbit(canvas, cx, cy)
        }

        // 6. Center Glyph & Text Label
        drawCenterContent(canvas, cx, cy, radius)
    }

    private fun drawConnectingOrbit(canvas: Canvas, cx: Float, cy: Float) {
        orbitPaint.shader = cachedOrbitShader
        canvas.save()
        canvas.rotate(orbitAngle, cx, cy)
        canvas.drawArc(orbitBounds, 0f, 120f, false, orbitPaint)
        canvas.restore()
    }

    private fun drawCenterContent(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        val isLight = ThemeManager.isLight(context)

        when (visualState) {
            ConnectionVisualState.CONNECTED, ConnectionVisualState.STARTING_STREAM -> {
                iconPaint.style = Paint.Style.STROKE
                iconPaint.strokeWidth = radius * 0.09f
                iconPaint.strokeCap = Paint.Cap.ROUND
                iconPaint.strokeJoin = Paint.Join.ROUND
                iconPaint.shader = null
                iconPaint.color = 0xFF22C55E.toInt() // Green success
                canvas.drawPath(checkPath, iconPaint)
            }
            ConnectionVisualState.ERROR -> {
                iconPaint.style = Paint.Style.STROKE
                iconPaint.strokeWidth = radius * 0.09f
                iconPaint.strokeCap = Paint.Cap.ROUND
                iconPaint.shader = null
                iconPaint.color = 0xFFEF4444.toInt() // Red error
                canvas.drawPath(errorPath, iconPaint)
                iconPaint.style = Paint.Style.FILL
                canvas.drawCircle(cx, cy - radius * 0.16f + radius * 0.20f, radius * 0.045f, iconPaint)
            }
            else -> {
                iconPaint.style = Paint.Style.FILL
                iconPaint.shader = cachedPlayShader
                canvas.drawPath(playPath, iconPaint)
            }
        }

        // Label
        textPaint.color = when {
            visualState == ConnectionVisualState.ERROR -> 0xFFFF6B6B.toInt()
            visualState == ConnectionVisualState.CONNECTED -> 0xFF4ADE80.toInt()
            isLight -> 0xFF0F172A.toInt()
            else -> 0xFFE0F2FE.toInt()
        }

        val label = when (visualState) {
            ConnectionVisualState.CONNECTING -> "CONECTANDO"
            ConnectionVisualState.CONNECTED -> "CONECTADO"
            ConnectionVisualState.STARTING_STREAM -> "INICIANDO"
            ConnectionVisualState.ERROR -> "REINTENTAR"
            ConnectionVisualState.DISABLED -> "OFFLINE"
            else -> "INICIAR"
        }

        canvas.drawText(label, cx, cy + radius * 0.52f, textPaint)
    }

    // ── Touch & Spring Motion ────────────────────────────────────────────────

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled || visualState == ConnectionVisualState.DISABLED) {
            return super.onTouchEvent(event)
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressAmount = 1f
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)

                animate()
                    .scaleX(0.965f)
                    .scaleY(0.965f)
                    .setDuration(90L)
                    .setInterpolator(EASING_DECELERATE)
                    .start()

                invalidate()
                return true
            }

            MotionEvent.ACTION_UP -> {
                pressAmount = 0f

                animate().scaleX(1f).scaleY(1f).setDuration(0).start()
                springBack()

                if (isInside(event.x, event.y)) {
                    performClick()
                }

                invalidate()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                pressAmount = 0f
                springBack()
                invalidate()
                return true
            }
        }

        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun springBack() {
        SpringAnimation(this, DynamicAnimation.SCALE_X, 1f).apply {
            spring = SpringForce(1f).apply {
                dampingRatio = 0.78f
                stiffness = 650f
            }
            start()
        }

        SpringAnimation(this, DynamicAnimation.SCALE_Y, 1f).apply {
            spring = SpringForce(1f).apply {
                dampingRatio = 0.78f
                stiffness = 650f
            }
            start()
        }
    }

    private fun animateSuccess() {
        animate()
            .scaleX(1.025f)
            .scaleY(1.025f)
            .setDuration(140L)
            .withEndAction { springBack() }
            .start()
    }

    private fun isInside(x: Float, y: Float): Boolean {
        val dx = x - width * 0.5f
        val dy = y - height * 0.5f
        val r = min(width, height) * 0.5f
        return dx * dx + dy * dy <= r * r
    }

    private fun vibrate(ms: Long) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION") v.vibrate(ms)
        }
    }

    // ── Frame Scheduling ─────────────────────────────────────────────────────

    private fun needsContinuousFrames(): Boolean =
        visualState == ConnectionVisualState.CONNECTING

    private fun scheduleFrame() {
        if (isInEditMode || frameScheduled || !isAttachedToWindow) return
        frameScheduled = true
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    private fun stopFrames() {
        if (!frameScheduled) return
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        frameScheduled = false
        lastFrameNanos = 0L
    }

    // ── Lifecycle Management ─────────────────────────────────────────────────

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (needsContinuousFrames()) {
            scheduleFrame()
        }
    }

    override fun onDetachedFromWindow() {
        stopFrames()
        animate().cancel()
        super.onDetachedFromWindow()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == VISIBLE && isAttachedToWindow && needsContinuousFrames()) {
            scheduleFrame()
        } else {
            stopFrames()
        }
    }

    fun release() {
        stopFrames()
        animate().cancel()
    }
}
