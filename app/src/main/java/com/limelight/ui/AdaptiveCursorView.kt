package com.limelight.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.AttributeSet
import android.view.Choreographer
import android.view.View
import android.view.animation.PathInterpolator
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * AdaptiveCursorView — SmartDisplay AI Premium (Kotlin)
 * ─────────────────────────────────────────────────────
 * Smart Cursor Engine — IA de interacción adaptativa.
 *
 * CARACTERÍSTICAS (Fase 1 y 2):
 *  • Cursor Adaptativo (Normal, Texto I-Beam, Botón)
 *  • Halo Premium dinámico (20 % opacidad base, expansiones)
 *  • Feedback visual de click (80 ms izquierdo, anillo morado derecho)
 *  • Micro-vibración háptica
 *  • Precision Mode (velocidad baja → cursor 1.2x, halo 35 %)
 *  • Smart Snap (atracción magnética suave)
 *  • Feedback visual de Arrastre y Scroll
 *  • Interpolación a 60/120 FPS vía Choreographer
 */
class AdaptiveCursorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // ── Paleta Premium Futurista ───────────────────────────────────────────
    companion object {
        private const val COLOR_CURSOR_FILL    = 0xFF000000.toInt()
        private const val COLOR_CURSOR_OUTLINE = 0xFFFFFFFF.toInt()
        private const val COLOR_HALO_PURPLE    = 0xFF8B5CF6.toInt()
        private const val COLOR_HALO_CYAN      = 0xFF22D3EE.toInt()
        private const val COLOR_RING_CYAN      = 0xDD22D3EE.toInt()
        private const val COLOR_RING_PURPLE    = 0xDD8B5CF6.toInt()

        // Tamaño del cursor (escala aplicada sobre el path base 14dp).
        const val SIZE_NORMAL = 1.0f   // estándar SmartDisplay

        private const val BASE_CURSOR_SIZE      = 14f   // dp
        private const val HALO_RADIUS_DP        = 32f
        private const val RING_MAX_RADIUS       = 40f
        private const val HIDE_DELAY_MS         = 3000L
        private const val VELOCITY_THRESHOLD_DP = 150f  // dp/s → activa precision mode

        // Easing Material 3 compartido con el resto de la app.
        private val EASING_EMPHASIZED_DECELERATE = PathInterpolator(0.05f, 0.7f, 0.1f, 1.0f)
    }

    // ── Tamaño dinámico del cursor ────────────────────────────────────
    /** Escala actual del cursor (SIZE_SMALL / SIZE_NORMAL / SIZE_LARGE). */

    // ── Estados ─────────────────────────────────────────────────────
    enum class CursorState { NORMAL, TEXT, BUTTON }

    private var currentState  = CursorState.NORMAL
    private var currentScale  = 1.0f
    private var haloBaseAlpha = 0.20f

    // ── Precision Mode & Drag/Scroll ──────────────────────────────────────
    private var precisionModeEnabled  = false
    private var isDragging            = false
    private var isScrolling           = false
    private var lastCursorX           = -1000f
    private var lastCursorY           = -1000f
    private var lastVelocityCheckTime = 0L
    private val velocityThresholdPx: Float

    // ── Posición y visibilidad ─────────────────────────────────────────────
    private var cursorX      = -1000f
    private var cursorY      = -1000f
    private var cursorVisible = false
    private var lastMoveTime  = 0L

    /** true mientras el halo esté activo (default). */
    var haloEnabled: Boolean = true
        private set

    // ── Perfil dinámico por app (CursorProfileRegistry) ────────────────────
    /** Si true, [precisionModeEnabled] se mantiene activo independientemente de la velocidad. */
    private var forcePrecision: Boolean = false
    /** Fuerza del Smart Snap configurable por app. Consultable por los callers de [smartSnapTo]. */
    var magnetismFactor: Float = 0.0f
        private set

    private val cursorProfileListener =
        com.limelight.smartdisplay.context.CursorProfileRegistry.Listener { profile ->
            forcePrecision = profile.precision
            magnetismFactor = profile.magnetism
            if (forcePrecision && haloEnabled) {
                precisionModeEnabled = true
                if (cursorVisible) invalidate()
            }
        }

    // ── Paints ────────────────────────────────────────────────────────────
    private val cursorFillPaint    = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cursorOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textCursorPaint    = Paint(Paint.ANTI_ALIAS_FLAG)
    private val haloPaint          = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint          = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringDotsPaint      = Paint(Paint.ANTI_ALIAS_FLAG)
    private val focusPaint         = Paint(Paint.ANTI_ALIAS_FLAG)
    private val innerGlowPaint     = Paint(Paint.ANTI_ALIAS_FLAG)

    // ── Paths ─────────────────────────────────────────────────────────────
    private val cursorPath: Path
    private val iBeamPath: Path

    // ── Foco de texto ─────────────────────────────────────────────────────
    private var focusX = -1f; private var focusY = -1f
    private var focusW = 0f;  private var focusH = 0f
    private var showFocusHighlight  = false
    private var focusHighlightAlpha = 0

    // ── Anillo de click ───────────────────────────────────────────────────
    private var ringRadius     = 0f
    private var ringAlpha      = 0f
    private var ringColor      = COLOR_RING_CYAN
    private var showRing       = false
    private var showDoubleRing = false
    private var ringAnimator: ValueAnimator? = null

    // ── Caché del halo (evita asignar arrays/RadialGradient cada frame) ──────
    private val haloStops  = floatArrayOf(0f, 0.45f, 1f)
    private val haloColors = IntArray(3)
    private var haloCx     = Float.NaN
    private var haloCy     = Float.NaN
    private var haloRad    = -1f

    // ── Tamaños en px ─────────────────────────────────────────────────────
    private val cursorSizePx: Float
    private val haloRadiusPx: Float
    private val ringMaxPx: Float

    // ── Choreographer ─────────────────────────────────────────────────────
    private val choreographer: Choreographer = Choreographer.getInstance()
    private var choreographerRunning = false
    private val frameCallback: Choreographer.FrameCallback

    // ── Auto-hide ─────────────────────────────────────────────────────────
    private val hideHandler  = Handler(Looper.getMainLooper())
    private val hideRunnable = Runnable { cursorVisible = false; invalidate() }

    // ── Vibrador ──────────────────────────────────────────────────────────
    private val vibrator: Vibrator? =
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    private val hasVibrator: Boolean = vibrator?.hasVibrator() == true

    // ── init ──────────────────────────────────────────────────────────────
    init {
        setWillNotDraw(false)
        isClickable = false
        isFocusable = false

        val d = context.resources.displayMetrics.density
        cursorSizePx        = BASE_CURSOR_SIZE * SIZE_NORMAL * d
        haloRadiusPx        = HALO_RADIUS_DP * d
        ringMaxPx           = RING_MAX_RADIUS * d
        velocityThresholdPx = VELOCITY_THRESHOLD_DP * d

        // Cursor relleno negro
        cursorFillPaint.color = COLOR_CURSOR_FILL
        cursorFillPaint.style = Paint.Style.FILL

        // Borde blanco
        cursorOutlinePaint.color       = COLOR_CURSOR_OUTLINE
        cursorOutlinePaint.style       = Paint.Style.STROKE
        cursorOutlinePaint.strokeWidth = d * 1.5f

        // I-Beam
        textCursorPaint.color       = COLOR_CURSOR_OUTLINE
        textCursorPaint.style       = Paint.Style.STROKE
        textCursorPaint.strokeWidth = d * 2f
        textCursorPaint.setShadowLayer(d * 2f, 0f, 0f, COLOR_CURSOR_FILL)

        // Halo — shader dinámico en onDraw
        haloPaint.style = Paint.Style.FILL

        // Anillo de click
        ringPaint.style       = Paint.Style.STROKE
        ringPaint.strokeWidth = d * 1.5f

        ringDotsPaint.style = Paint.Style.FILL

        innerGlowPaint.style = Paint.Style.FILL

        // Foco de texto
        focusPaint.style       = Paint.Style.STROKE
        focusPaint.strokeWidth = d * 2.5f
        focusPaint.color       = COLOR_HALO_PURPLE

        cursorPath = buildCursorPath()
        iBeamPath  = buildIBeamPath()

        // FrameCallback — se construye al final para que `this` esté listo
        frameCallback = Choreographer.FrameCallback {
            val now = System.currentTimeMillis()
            var stillActive = now - lastMoveTime <= HIDE_DELAY_MS
            if (!stillActive) cursorVisible = false
            if (showRing) stillActive = true

            invalidate()

            if (stillActive && choreographerRunning) {
                choreographer.postFrameCallback(frameCallback)
            } else {
                choreographerRunning = false
            }
        }
    }

    // ── API Pública ────────────────────────────────────────────────────────

    /** Activa/desactiva el halo. Devuelve el nuevo estado. */
    fun toggleHalo(): Boolean {
        haloEnabled = !haloEnabled
        if (!haloEnabled) { cursorVisible = false; invalidate() }
        return haloEnabled
    }


    /** Cambia el estado visual del cursor (NORMAL / TEXT / BUTTON). */
    fun setCursorState(state: CursorState) {
        if (currentState == state) return
        currentState = state
        when (state) {
            CursorState.NORMAL -> { currentScale = 1.0f; haloBaseAlpha = 0.20f }
            CursorState.TEXT   -> { currentScale = 1.0f; haloBaseAlpha = 0.25f }
            CursorState.BUTTON -> { currentScale = 1.1f; haloBaseAlpha = 0.35f }
        }
        if (cursorVisible) invalidate()
    }

    /** Mueve el cursor y calcula velocidad para Precision Mode. */
    fun moveTo(x: Float, y: Float) {
        if (!haloEnabled) return
        val now = System.currentTimeMillis()

        if (lastCursorX >= 0 && lastCursorY >= 0 && lastVelocityCheckTime > 0) {
            val dx = x - lastCursorX
            val dy = y - lastCursorY
            val dt = (now - lastVelocityCheckTime) / 1000f
            if (dt >= 0.016f) {
                val velocity = sqrt((dx * dx + dy * dy).toDouble()).toFloat() / dt
                val byVelocity = velocity < velocityThresholdPx && currentState == CursorState.NORMAL
                precisionModeEnabled = byVelocity || forcePrecision
                lastCursorX = x; lastCursorY = y; lastVelocityCheckTime = now
            }
        } else {
            lastCursorX = x; lastCursorY = y; lastVelocityCheckTime = now
        }

        cursorX = x; cursorY = y
        cursorVisible = true
        lastMoveTime  = now

        hideHandler.removeCallbacks(hideRunnable)
        hideHandler.postDelayed(hideRunnable, HIDE_DELAY_MS)
        invalidate()
    }

    fun setDragging(dragging: Boolean)   { if (isDragging  != dragging)  { isDragging  = dragging;  invalidate() } }
    fun setScrolling(scrolling: Boolean) { if (isScrolling != scrolling) { isScrolling = scrolling; invalidate() } }

    fun smartSnapTo(targetX: Float, targetY: Float, force: Float) {
        moveTo(cursorX + (targetX - cursorX) * force, cursorY + (targetY - cursorY) * force)
    }

    /** Posición actual del halo (última conocida), para cálculos de Smart Snap. */
    fun getCursorX(): Float = cursorX
    fun getCursorY(): Float = cursorY

    fun fireLeftClick(x: Float, y: Float) {
        if (!haloEnabled) return
        cursorX = x; cursorY = y; cursorVisible = true; lastMoveTime = System.currentTimeMillis()
        ringColor = COLOR_RING_CYAN; showRing = true; showDoubleRing = false
        animateRing(80L); vibrate(5L)
        if (!choreographerRunning) startChoreographer()
    }

    fun fireRightClick(x: Float, y: Float) {
        if (!haloEnabled) return
        cursorX = x; cursorY = y; cursorVisible = true; lastMoveTime = System.currentTimeMillis()
        ringColor = COLOR_RING_PURPLE; showRing = true; showDoubleRing = true
        animateRing(150L); vibrate(5L)
        if (!choreographerRunning) startChoreographer()
    }

    fun setFocusHighlight(x: Float, y: Float, w: Float, h: Float) {
        focusX = x; focusY = y; focusW = w; focusH = h
        showFocusHighlight = w > 0 && h > 0

        if (showFocusHighlight) {
            cursorVisible = true; lastMoveTime = System.currentTimeMillis()
            if (!choreographerRunning) startChoreographer()
        } else {
            focusHighlightAlpha = 0; cursorVisible = false; invalidate()
        }
    }

    /** Llamar en onDestroy/onStop para liberar recursos. */
    fun release() {
        stopChoreographer()
        hideHandler.removeCallbacks(hideRunnable)
        ringAnimator?.cancel()
        com.limelight.smartdisplay.context.CursorProfileRegistry.removeListener(cursorProfileListener)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        com.limelight.smartdisplay.context.CursorProfileRegistry.addListener(cursorProfileListener)
    }

    override fun onDetachedFromWindow() {
        com.limelight.smartdisplay.context.CursorProfileRegistry.removeListener(cursorProfileListener)
        super.onDetachedFromWindow()
    }

    // ── Renderizado ────────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!cursorVisible || cursorX < 0) return

        val cx = cursorX; val cy = cursorY
        val ea = if (precisionModeEnabled) 0.35f else haloBaseAlpha
        val es = if (precisionModeEnabled) 1.2f  else currentScale

        // 1. Halo — el shader solo se reconstruye si cambian posición, radio o color.
        val aP = (255 * ea).toInt(); val aC = (255 * ea * 0.5f).toInt()
        val radius = haloRadiusPx * es
        val c0 = (aP shl 24) or (COLOR_HALO_PURPLE and 0x00FFFFFF)
        val c1 = (aC shl 24) or (COLOR_HALO_CYAN   and 0x00FFFFFF)
        if (haloPaint.shader == null || cx != haloCx || cy != haloCy || radius != haloRad ||
            c0 != haloColors[0] || c1 != haloColors[1]) {
            haloColors[0] = c0; haloColors[1] = c1; haloColors[2] = 0x0022D3EE
            haloPaint.shader = RadialGradient(cx, cy, radius, haloColors, haloStops, Shader.TileMode.CLAMP)
            haloCx = cx; haloCy = cy; haloRad = radius
        }
        canvas.drawCircle(cx, cy, radius, haloPaint)

        // 2. Anillo de click
        if (showRing && ringRadius > 0) {
            val ai = (ringAlpha * 255).toInt()
            val cc = (ai shl 24) or (ringColor and 0x00FFFFFF)
            ringPaint.color = cc; ringDotsPaint.color = cc
            canvas.drawCircle(cx, cy, ringRadius, ringPaint)
            for (i in 0 until 8) {
                val angle = i * (Math.PI / 4)
                canvas.drawCircle(cx + (cos(angle) * ringRadius).toFloat(),
                                  cy + (sin(angle) * ringRadius).toFloat(),
                                  cursorSizePx * 0.15f, ringDotsPaint)
            }
            innerGlowPaint.color = ((ai * 0.3f).toInt() shl 24) or (ringColor and 0x00FFFFFF)
            canvas.drawCircle(cx, cy, ringRadius * 0.5f, innerGlowPaint)
            if (showDoubleRing && ringRadius > 12)
                canvas.drawCircle(cx, cy, ringRadius * 0.75f, ringPaint)
        }

        // 3. Cursor adaptativo. cursorSizePx YA incluye SIZE_NORMAL; aquí solo se
        // aplica 'es' (precision/estado). Antes se multiplicaba otra vez por el
        // factor de tamaño → cursor gigante (doble escalado). Corregido.
        canvas.save(); canvas.translate(cx, cy); canvas.scale(es, es)
        if (currentState == CursorState.TEXT) {
            canvas.drawPath(iBeamPath, textCursorPaint)
        } else {
            canvas.drawPath(cursorPath, cursorFillPaint)
            canvas.drawPath(cursorPath, cursorOutlinePaint)
            if (isDragging) {
                val ds = cursorSizePx * 1.5f
                canvas.drawCircle(ds, ds, ds * 0.3f, textCursorPaint)
                canvas.drawLine(0f, 0f, ds, ds, ringPaint)
            }
            if (isScrolling) {
                val ds = cursorSizePx * 1.5f
                canvas.drawLine(ds, -ds * 0.5f, ds, ds * 0.5f, textCursorPaint)
            }
        }
        canvas.restore()

        // 4. Borde de foco de texto
        if (showFocusHighlight && focusW > 0 && focusH > 0) {
            val t = (System.currentTimeMillis() % 1400) / 700f
            val cycle = if (t <= 1f) t else 2f - t
            focusHighlightAlpha = 40 + (200 * cycle).toInt()
            focusPaint.alpha = focusHighlightAlpha
            canvas.drawRect(focusX, focusY, focusX + focusW, focusY + focusH, focusPaint)
            
            // Requerir el próximo frame para continuar la animación del borde si está visible
            if (!choreographerRunning) startChoreographer()
        }
    }

    // ── Paths ─────────────────────────────────────────────────────────────

    private fun buildCursorPath(): Path {
        val s = cursorSizePx
        return Path().apply {
            moveTo(0f,        0f);       lineTo(0f,        s * 0.85f)
            lineTo(s * 0.25f, s * 0.62f); lineTo(s * 0.42f, s * 0.95f)
            lineTo(s * 0.55f, s * 0.88f); lineTo(s * 0.38f, s * 0.56f)
            lineTo(s * 0.6f,  s * 0.53f); close()
        }
    }

    private fun buildIBeamPath(): Path {
        val s = cursorSizePx * 0.9f; val hW = s * 0.25f
        return Path().apply {
            moveTo(-hW, -s * 0.5f); lineTo(hW, -s * 0.5f)
            moveTo(0f,  -s * 0.5f); lineTo(0f,  s * 0.5f)
            moveTo(-hW,  s * 0.5f); lineTo(hW,  s * 0.5f)
        }
    }

    // ── Animación de anillo ────────────────────────────────────────────────

    private fun animateRing(durationMs: Long) {
        ringAnimator?.cancel()
        ringRadius = 0f; ringAlpha = 1f
        ringAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = durationMs; interpolator = EASING_EMPHASIZED_DECELERATE
            addUpdateListener { t -> val v = t.animatedValue as Float; ringRadius = v * ringMaxPx; ringAlpha = 1f - v }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) { showRing = false; showDoubleRing = false }
            })
            start()
        }
    }

    // ── Choreographer ─────────────────────────────────────────────────────

    private fun startChoreographer() { choreographerRunning = true; choreographer.postFrameCallback(frameCallback) }
    private fun stopChoreographer()  { choreographerRunning = false; choreographer.removeFrameCallback(frameCallback) }

    // ── Háptica ───────────────────────────────────────────────────────────

    private fun vibrate(ms: Long) {
        if (!hasVibrator) return
        val v = vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION") v.vibrate(ms)
        }
    }
}
