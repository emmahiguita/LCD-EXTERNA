package com.limelight.ui.glass

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import kotlin.math.min

/**
 * LiquidGlassCircleLayout — Componente circular de Cristal Líquido / Water Glass 3D de última generación (2026).
 *
 * Características ópticas y físicas:
 *  • Renderizado multicapa 100% puro Modo Oscuro Obsidian/Deep Oceanic Glass.
 *  • Cero allocations en onDraw(): todos los Shaders, matrices de gradiente y bounds se construyen en onSizeChanged().
 *  • Refracción cáustica dinámica interactiva: el destello especular y el punto focal se desplazan con el toque táctil.
 *  • Borde cromático refractivo (Fresnel rim) mediante SweepGradient con dispersión espectral cian/zafiro/blanco.
 *  • Halo exterior de resplandor líquido ambiental (Ambient Fluid Halo).
 *  • Físicas de deformación de resorte SpringAnimation orgánica al soltar (dampingRatio = 0.78f, stiffness = 650f).
 *  • Vibración háptica instantánea en ACTION_DOWN.
 *  • Modo Idle Breathing de bajo consumo para orbes centrales (0 FPS / 0 CPU cuando está en pausa o fuera de pantalla).
 *  • 100% compatible con el editor visual (Layout Editor) de Android Studio.
 */
class LiquidGlassCircleLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    companion object {
        private val EASING_PRESS = PathInterpolator(0.1f, 0.9f, 0.2f, 1f)
    }

    private val density = resources.displayMetrics.density

    // ── Pinturas pre-asignadas (Zero Allocation en onDraw) ───────────────────
    private val outerHaloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3.2f * density
    }

    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val innerCausticPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.4f * density
    }

    // ── Shaders y Rectángulos de dibujo ──────────────────────────────────────
    private var bodyShader: Shader? = null
    private var causticShader: Shader? = null
    private var highlightShader: Shader? = null
    private var rimShader: Shader? = null
    private var haloShader: Shader? = null

    private val highlightBounds = RectF()
    private val causticBounds = RectF()

    private var pressAmount = 0f
    private var breathingAnimator: ValueAnimator? = null

    init {
        setWillNotDraw(false)
        isClickable = true
        isFocusable = true
        clipChildren = false
        clipToPadding = false
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return
        rebuildLiquidOptics(w, h, 0f, 0f)
    }

    /**
     * Construye las capas ópticas de cristal líquido y refracción.
     */
    private fun rebuildLiquidOptics(w: Int, h: Int, tiltOffsetX: Float, tiltOffsetY: Float) {
        val cx = w * 0.5f
        val cy = h * 0.5f
        val radius = min(w, h) * 0.47f

        // 1. Cuerpo Principal: Cristal Líquido / Water Glass Translúcido Ultra Hiperrealista
        bodyShader = RadialGradient(
            cx - radius * 0.35f + tiltOffsetX,
            cy - radius * 0.42f + tiltOffsetY,
            radius * 1.75f,
            intArrayOf(
                0x507EB0DB.toInt(), // Cima translúcida con brillo de agua cristalina
                0x7820466E.toInt(), // Tono cristalino oceánico intermedio
                0x9E0D2238.toInt(), // Densidad de refracción líquida profunda
                0xC4050E18.toInt()  // Base abisal cristalina de alto contraste
            ),
            floatArrayOf(0f, 0.26f, 0.62f, 1f),
            Shader.TileMode.CLAMP
        )

        // 2. Refracción Cáustica Inferior (Secondary Caustic Bounce & Rainbow Glow)
        causticShader = RadialGradient(
            cx + radius * 0.28f,
            cy + radius * 0.46f,
            radius * 0.88f,
            intArrayOf(
                0x703CE8FF.toInt(), // Foco cáustico concentrado cian eléctrico
                0x280075FF.toInt(), // Dispersión de luz zafiro en la curva inferior
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.52f, 1f),
            Shader.TileMode.CLAMP
        )

        // 3. Destello Especular Líquido Superior (Ultra-Crisp Specular Crescent)
        highlightShader = LinearGradient(
            cx - radius + tiltOffsetX,
            cy - radius + tiltOffsetY,
            cx + radius * 0.32f,
            cy + radius * 0.42f,
            intArrayOf(
                0xDDFFFFFF.toInt(), // Destello diamante puro en el vértice superior
                0x60AEEBFF.toInt(), // Dispersión celeste translúcida
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.38f, 1f),
            Shader.TileMode.CLAMP
        )

        highlightBounds.set(
            cx - radius * 0.72f + tiltOffsetX * 0.5f,
            cy - radius * 0.74f + tiltOffsetY * 0.5f,
            cx + radius * 0.22f + tiltOffsetX * 0.5f,
            cy + radius * 0.06f + tiltOffsetY * 0.5f
        )

        causticBounds.set(
            cx - radius * 0.62f,
            cy + radius * 0.16f,
            cx + radius * 0.72f,
            cy + radius * 0.84f
        )

        // 4. Borde Refractivo Cromático (Fresnel Prismatic Rim HD)
        rimShader = SweepGradient(
            cx,
            cy,
            intArrayOf(
                0xFF66E5FF.toInt(), // Cian neón cristalino
                0xFFFFFFFF.toInt(), // Destello diamante puro
                0xFF2590FF.toInt(), // Zafiro brillante
                0x666085FF.toInt(), // Prisma de dispersión violeta
                0xFF66E5FF.toInt()  // Cierre de arco continuo
            ),
            null
        )

        // 5. Halo Líquido Exterior Ambiental (Ambient Fluid Glow)
        haloShader = RadialGradient(
            cx,
            cy,
            radius * 1.36f,
            intArrayOf(
                0x403CE8FF.toInt(),
                0x150075FF.toInt(),
                Color.TRANSPARENT
            ),
            floatArrayOf(0.50f, 0.78f, 1f),
            Shader.TileMode.CLAMP
        )

        bodyPaint.shader = bodyShader
        innerCausticPaint.shader = causticShader
        highlightPaint.shader = highlightShader
        rimPaint.shader = rimShader
        outerHaloPaint.shader = haloShader
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width * 0.5f
        val cy = height * 0.5f
        val radius = min(width, height) * (0.47f - pressAmount * 0.012f)

        // Capa 1: Halo Exterior Fluido
        canvas.drawCircle(cx, cy, radius * 1.10f, outerHaloPaint)

        // Capa 2: Cuerpo de Cristal Líquido
        canvas.drawCircle(cx, cy, radius, bodyPaint)

        // Capa 3: Refracción Cáustica Inferior
        canvas.drawOval(causticBounds, innerCausticPaint)

        // Capa 4: Destello Especular Superior
        canvas.drawOval(highlightBounds, highlightPaint)

        // Capa 5: Borde Cromático Refractivo
        canvas.drawCircle(cx, cy, radius, rimPaint)

        super.onDraw(canvas)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) {
            return super.onTouchEvent(event)
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressAmount = 1f
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)

                // Desplazamiento dinámico del foco de luz hacia el dedo
                val dx = (event.x - width * 0.5f) * 0.09f
                val dy = (event.y - height * 0.5f) * 0.09f
                rebuildLiquidOptics(width, height, dx, dy)

                animate()
                    .scaleX(0.962f)
                    .scaleY(0.962f)
                    .setDuration(90L)
                    .setInterpolator(EASING_PRESS)
                    .start()

                invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = (event.x - width * 0.5f) * 0.09f
                val dy = (event.y - height * 0.5f) * 0.09f
                rebuildLiquidOptics(width, height, dx, dy)
                invalidate()
                return true
            }

            MotionEvent.ACTION_UP -> {
                pressAmount = 0f
                rebuildLiquidOptics(width, height, 0f, 0f)
                animate().scaleX(1f).scaleY(1f).setDuration(0).start()
                springToRest()

                if (isPointInsideCircle(event.x, event.y)) {
                    performClick()
                }

                invalidate()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                pressAmount = 0f
                rebuildLiquidOptics(width, height, 0f, 0f)
                springToRest()
                invalidate()
                return true
            }
        }

        return super.onTouchEvent(event)
    }

    private fun isPointInsideCircle(x: Float, y: Float): Boolean {
        val cx = width * 0.5f
        val cy = height * 0.5f
        val dx = x - cx
        val dy = y - cy
        val radius = min(width, height) * 0.5f
        return dx * dx + dy * dy <= radius * radius
    }

    private fun springToRest() {
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

    /**
     * Activa una ondulación orgánica de flotación líquida en reposo.
     */
    fun startBreathing(amplitudeDp: Float = 3.5f, periodMs: Long = 3000L) {
        if (isInEditMode) return
        stopBreathing()

        val maxShift = amplitudeDp * density
        breathingAnimator = ValueAnimator.ofFloat(-maxShift, maxShift).apply {
            duration = periodMs
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { anim ->
                val v = anim.animatedValue as Float
                translationY = v
            }
            start()
        }
    }

    fun stopBreathing() {
        breathingAnimator?.cancel()
        breathingAnimator = null
        translationY = 0f
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility != View.VISIBLE) {
            stopBreathing()
        }
    }

    override fun onDetachedFromWindow() {
        animate().cancel()
        stopBreathing()
        super.onDetachedFromWindow()
    }
}
