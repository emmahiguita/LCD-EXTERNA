package com.limelight.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.PathInterpolator
import com.limelight.nvstream.NvConnection
import com.limelight.nvstream.input.MouseButtonPacket
import kotlin.math.hypot

/**
 * MouseModeCircle ÔÇö "Modo mouse" de SmartDisplay (Cyber Neon Productivity).
 *
 * Trackpad circular flotante con feedback visual y h├íptico:
 *  ÔÇó Candado: Permite fijarlo o moverlo por la pantalla.
 *  ÔÇó Arrastrar: mueve el cursor del PC (relativo).
 *  ÔÇó Tap mitad izquierda: click izquierdo.
 *  ÔÇó Tap mitad derecha: click derecho.
 *  ÔÇó Arrastrar 2 dedos: scroll vertical.
 */
class MouseModeCircle @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        private const val COLOR_PURPLE = 0xFF8B5CF6.toInt()
        private const val COLOR_CYAN   = 0xFF22D3EE.toInt()
        private const val MOVE_SENSITIVITY = 1.6f
        private const val TAP_SLOP_DP      = 8f
        private const val TAP_TIMEOUT_MS   = 220L
        private const val SCROLL_STEP_DP   = 6f

        // Easing Material 3 (motion emphasized) para las transiciones.
        private val EASING_EMPHASIZED_DECELERATE = PathInterpolator(0.05f, 0.7f, 0.1f, 1f)
        private val EASING_EMPHASIZED_ACCELERATE = PathInterpolator(0.3f, 0f, 0.8f, 0.15f)
    }

    private var conn: NvConnection? = null
    fun setConnection(c: NvConnection?) { conn = c }

    private val density   = context.resources.displayMetrics.density
    private val tapSlopPx = TAP_SLOP_DP * density
    private val scrollStepPx = SCROLL_STEP_DP * density

    // ÔöÇÔöÇ Paints cacheados ÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇ
    private val glowPaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val ringGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 5f * density; color = COLOR_PURPLE
    }
    private val ringPaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2f * density; color = COLOR_PURPLE
    }
    private val ringPaint2 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 1.5f * density; color = COLOR_CYAN
    }
    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2.5f * density; color = COLOR_CYAN
        strokeCap = Paint.Cap.ROUND
    }
    private val arcRect = RectF()
    private val dotGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL; color = COLOR_CYAN
    }
    private val dotPaint   = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = 0xFFFFFFFF.toInt() }
    private val pulsePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2f * density; color = COLOR_CYAN
    }
    private val lockPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2f * density; color = COLOR_CYAN
    }
    private val lockFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL; color = COLOR_CYAN
    }

    // ÔöÇÔöÇ Animaci├│n de "respiraci├│n" + ├│rbita ÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇ
    private var phase = 0f
    private var ambientAnimator: ValueAnimator? = null

    // ÔöÇÔöÇ Estado de toque y arrastre ÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇ
    private var lastX = 0f;  private var lastY = 0f
    private var downX = 0f;  private var downY = 0f
    private var downRawX = 0f; private var downRawY = 0f
    private var initialViewX = 0f; private var initialViewY = 0f
    private var downTime = 0L
    private var moved = false
    private var scrollAccum = 0f

    // Candado
    private var isLocked = true
    // El toque actual empez├│ sobre el icono del candado (para tratarlo como tap,
    // sin secuestrar el arrastre del trackpad).
    private var downInLockZone = false

    // ÔöÇÔöÇ Pulso de feedback ÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇ
    private var pulseRadius = 0f
    private var pulseAlpha  = 0f
    private var pulseAnimator: ValueAnimator? = null

    private val vibrator: Vibrator? =
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

    init {
        isClickable = true
        isFocusable = false
    }

    fun toggle(): Boolean {
        return if (visibility != View.VISIBLE) { showAnimated(); true } else { hideAnimated(); false }
    }

    /**
     * Sube el c├¡rculo por encima del teclado para que no quede tapado.
     * Con keyboardTopY <= 0 (teclado oculto) vuelve a su posici├│n natural.
     * Idempotente: resetea la traslaci├│n antes de recalcular.
     */
    fun adjustForKeyboard(keyboardTopY: Int) {
        if (visibility != View.VISIBLE) { translationY = 0f; return }
        post {
            translationY = 0f
            val loc = IntArray(2)
            getLocationInWindow(loc)
            val naturalBottom = loc[1] + height
            if (keyboardTopY in 1 until naturalBottom) {
                translationY = -(naturalBottom - keyboardTopY + 12f * density)
            }
        }
    }

    private fun showAnimated() {
        animate().cancel()
        scaleX = 0.8f; scaleY = 0.8f; alpha = 0f
        visibility = View.VISIBLE
        animate().scaleX(1f).scaleY(1f).alpha(1f)
            .setDuration(260L).setInterpolator(EASING_EMPHASIZED_DECELERATE)
            .start()
    }

    private fun hideAnimated() {
        animate().cancel()
        animate().scaleX(0.8f).scaleY(0.8f).alpha(0f)
            .setDuration(200L).setInterpolator(EASING_EMPHASIZED_ACCELERATE)
            .withEndAction {
                visibility = View.GONE
                scaleX = 1f; scaleY = 1f; alpha = 1f
            }
            .start()
    }

    private fun startAmbient() {
        if (ambientAnimator != null) return
        ambientAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 4200L
            repeatCount = ValueAnimator.INFINITE
            interpolator = android.view.animation.LinearInterpolator()
            addUpdateListener { phase = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    private fun stopAmbient() {
        ambientAnimator?.cancel()
        ambientAnimator = null
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == View.VISIBLE && isAttachedToWindow) startAmbient() else stopAmbient()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (visibility == View.VISIBLE) startAmbient()
    }

    override fun onDetachedFromWindow() {
        stopAmbient()
        pulseAnimator?.cancel(); pulseAnimator = null
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        pivotX = w / 2f; pivotY = h / 2f
        val cx = w / 2f; val cy = h / 2f
        val r = minOf(w, h) / 2f
        glowPaint.shader = RadialGradient(
            cx, cy, r,
            intArrayOf(0x338B5CF6, 0x2222D3EE, 0x00000000),
            floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f; val cy = height / 2f
        val r = minOf(width, height) / 2f - 8f * density

        val breath = 1f + 0.06f * kotlin.math.sin(phase * 2f * Math.PI.toFloat())
        val orbit  = phase * 360f

        canvas.save()
        canvas.scale(breath, breath, cx, cy)
        canvas.drawCircle(cx, cy, r, glowPaint)
        canvas.restore()

        // Dibuja el glow de los anillos con multi-pass
        val baseAlpha = (120 + 60 * (breath - 1f) / 0.06f).toInt().coerceIn(60, 200)
        
        // Capa 1: Glow amplio sutil (10dp de grosor)
        ringGlowPaint.strokeWidth = 10f * density
        ringGlowPaint.alpha = (baseAlpha * 0.25f).toInt()
        canvas.drawCircle(cx, cy, r, ringGlowPaint)
        
        // Capa 2: Glow medio (5dp de grosor)
        ringGlowPaint.strokeWidth = 5f * density
        ringGlowPaint.alpha = (baseAlpha * 0.6f).toInt()
        canvas.drawCircle(cx, cy, r, ringGlowPaint)

        // Capa 3: Anillo central n├¡tido
        canvas.drawCircle(cx, cy, r, ringPaint)

        // Anillo interior secundario
        canvas.drawCircle(cx, cy, r * 0.62f, ringPaint2)

        arcRect.set(cx - r * 0.82f, cy - r * 0.82f, cx + r * 0.82f, cy + r * 0.82f)
        
        // Capa 1 de arcos: Glow amplio
        arcPaint.strokeWidth = 6f * density
        arcPaint.alpha = 50
        canvas.drawArc(arcRect, orbit, 60f, false, arcPaint)
        canvas.drawArc(arcRect, orbit + 180f, 60f, false, arcPaint)

        // Capa 2 de arcos: Arco n├¡tido
        arcPaint.strokeWidth = 2.5f * density
        arcPaint.alpha = 255
        canvas.drawArc(arcRect, orbit, 60f, false, arcPaint)
        canvas.drawArc(arcRect, orbit + 180f, 60f, false, arcPaint)

        // Glow del punto central
        dotGlowPaint.alpha = 60
        canvas.drawCircle(cx, cy, 12f * density, dotGlowPaint)
        dotGlowPaint.alpha = 150
        canvas.drawCircle(cx, cy, 8f * density, dotGlowPaint)
        canvas.drawCircle(cx, cy, 5f * density, dotPaint)

        if (pulseAlpha > 0f) {
            pulsePaint.alpha = (pulseAlpha * 255).toInt()
            canvas.drawCircle(cx, cy, pulseRadius, pulsePaint)
        }
        
        // Dibujar el candado (Lock Icon)
        val lockSize = 10f * density
        val lockCx = cx
        val lockCy = cy - r + lockSize * 2f
        val lockTop = lockCy - lockSize / 2f
        
        // Cuerpo del candado
        canvas.drawRoundRect(lockCx - lockSize/1.2f, lockTop + lockSize/2f, lockCx + lockSize/1.2f, lockTop + lockSize*1.5f, 4f, 4f, if (isLocked) lockFillPaint else lockPaint)
        
        // Arco del candado
        val lockArcRect = RectF(lockCx - lockSize/2f, lockTop, lockCx + lockSize/2f, lockTop + lockSize)
        if (isLocked) {
            canvas.drawArc(lockArcRect, 180f, 180f, false, lockPaint)
        } else {
            // Abierto
            canvas.drawArc(lockArcRect, 200f, 160f, false, lockPaint)
            // Peque├▒a l├¡nea para que parezca abierto
            canvas.drawLine(lockCx - lockSize/2f, lockTop + lockSize/2f, lockCx - lockSize/2f, lockTop + lockSize/2f - 2f*density, lockPaint)
        }
        
        // L├¡nea divisoria central opcional para indicar click izquierdo/derecho
        canvas.drawLine(cx, cy + r * 0.3f, cx, cy + r * 0.8f, ringPaint2)
    }

    private fun firePulse(isRightClick: Boolean = false) {
        pulseAnimator?.cancel()
        val maxR = minOf(width, height) / 2f
        pulsePaint.color = if (isRightClick) COLOR_PURPLE else COLOR_CYAN
        pulseAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 360
            interpolator = PathInterpolator(0.05f, 0.7f, 0.1f, 1f)
            addUpdateListener {
                val t = it.animatedValue as Float
                pulseRadius = t * maxR; pulseAlpha = 1f - t; invalidate()
            }
            start()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val c = conn
        val cx = width / 2f
        val cy = height / 2f
        val r = minOf(width, height) / 2f - 8f * density
        
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x; downY = event.y; lastX = event.x; lastY = event.y
                downRawX = event.rawX; downRawY = event.rawY
                initialViewX = x; initialViewY = y
                downTime = System.currentTimeMillis(); moved = false; scrollAccum = 0f

                // Zona del candado: hit-target PEQUE├æO y preciso (antes 40dp
                // secuestraba la parte superior del trackpad). Se alterna como TAP
                // en ACTION_UP, no aqu├¡, para no impedir el arrastre.
                val lockCy = cy - r + 20f * density
                downInLockZone = hypot(event.x - cx, event.y - lockCy) < 22f * density
                if (downInLockZone) return true

                firePulse(event.x > cx) // Feedback visual seg├║n de qu├® lado toque
            }
            MotionEvent.ACTION_MOVE -> {
                if (downInLockZone) return true // el toque empez├│ sobre el candado
                if (!isLocked) {
                    // Mover la vista entera
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    if (hypot(dx, dy) > tapSlopPx) moved = true
                    // Clamp dentro de la pantalla para que no salga en vertical ni horizontal
                    val parent = parent as? android.view.View
                    val maxX = (parent?.width  ?: width).toFloat() - width
                    val maxY = (parent?.height ?: height).toFloat() - height
                    x = (initialViewX + dx).coerceIn(0f, maxX.coerceAtLeast(0f))
                    y = (initialViewY + dy).coerceIn(0f, maxY.coerceAtLeast(0f))
                    return true
                }
                
                if (c == null) return true
                if (event.pointerCount >= 2) {
                    scrollAccum += event.y - lastY
                    val steps = (scrollAccum / scrollStepPx).toInt()
                    if (steps != 0) {
                        c.sendMouseHighResScroll((-steps * 20).toShort())
                        scrollAccum -= steps * scrollStepPx
                        moved = true
                    }
                } else {
                    val dx = (event.x - lastX) * MOVE_SENSITIVITY
                    val dy = (event.y - lastY) * MOVE_SENSITIVITY
                    if (hypot((event.x - downX).toDouble(), (event.y - downY).toDouble()) > tapSlopPx) moved = true
                    if (dx.toInt() != 0 || dy.toInt() != 0) {
                        c.sendMouseMove(dx.toInt().toShort(), dy.toInt().toShort())
                    }
                }
                lastX = event.x; lastY = event.y
            }
            MotionEvent.ACTION_POINTER_UP -> {
                // Click derecho si se hace tap con 2 dedos
                if (!moved && event.pointerCount == 2 && c != null && isLocked &&
                    System.currentTimeMillis() - downTime < TAP_TIMEOUT_MS) {
                    c.sendMouseButtonDown(MouseButtonPacket.BUTTON_RIGHT)
                    c.sendMouseButtonUp(MouseButtonPacket.BUTTON_RIGHT)
                    firePulse(isRightClick = true)
                    vibrate(12)
                    moved = true
                }
            }
            MotionEvent.ACTION_UP -> {
                // Tap sobre el candado: fijar / liberar el c├¡rculo.
                if (downInLockZone) {
                    if (!moved && System.currentTimeMillis() - downTime < TAP_TIMEOUT_MS) {
                        isLocked = !isLocked
                        vibrate(15)
                        invalidate()
                    }
                    downInLockZone = false
                    return true
                }
                if (!moved && c != null && isLocked &&
                    System.currentTimeMillis() - downTime < TAP_TIMEOUT_MS) {
                    // Mitad izquierda = Click izquierdo, Mitad derecha = Click derecho
                    if (downX < cx) {
                        c.sendMouseButtonDown(MouseButtonPacket.BUTTON_LEFT)
                        c.sendMouseButtonUp(MouseButtonPacket.BUTTON_LEFT)
                        vibrate(8)
                    } else {
                        c.sendMouseButtonDown(MouseButtonPacket.BUTTON_RIGHT)
                        c.sendMouseButtonUp(MouseButtonPacket.BUTTON_RIGHT)
                        firePulse(isRightClick = true)
                        vibrate(12)
                    }
                }
            }
            MotionEvent.ACTION_CANCEL -> { moved = false; downInLockZone = false }
        }
        return true
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
}
