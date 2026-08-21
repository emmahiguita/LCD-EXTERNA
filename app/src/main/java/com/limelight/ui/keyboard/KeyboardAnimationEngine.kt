package com.limelight.ui.keyboard

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.media.AudioManager
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.animation.LinearInterpolator
import android.view.animation.PathInterpolator
import android.widget.TextView
import com.limelight.R

/**
 * Gestión de animaciones del teclado: RGB animado en las teclas y efecto
 * visual de presión de tecla.
 *
 * No conoce el estado ni la persistencia. Solo recibe colecciones de vistas
 * y parámetros para animar.
 */
class KeyboardAnimationEngine(private val context: Context) {

    companion object {
        val EASING_ACCELERATE = PathInterpolator(0.3f, 0.0f, 0.8f, 0.15f)
        val EASING_DECELERATE = PathInterpolator(0.05f, 0.7f, 0.1f, 1.0f)
    }

    private var rgbAnimator: ValueAnimator? = null

    private val audioManager: AudioManager? =
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    /**
     * Feedback físico de tecla + animación de hundirse-luego-rebotar.
     *
     * Haptic + sonido de pulsación como un teclado real. Ambos respetan los
     * ajustes del sistema del usuario (vibración táctil / sonido de teclas),
     * así que no se imponen si el usuario los desactivó.
     */
    fun animateKeyPress(key: View) {
        key.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        audioManager?.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD)
        key.animate().scaleX(0.82f).scaleY(0.82f).setDuration(45L)
            .setInterpolator(EASING_ACCELERATE)
            .withEndAction {
                key.animate().scaleX(1f).scaleY(1f).setDuration(70L)
                    .setInterpolator(EASING_DECELERATE).start()
            }.start()
    }

    // ── RGB Animation ─────────────────────────────────────────────────

    fun isRgbRunning(): Boolean = rgbAnimator != null

    fun startRGBAnimation(views: List<TextView>,
                          backgrounds: List<android.graphics.drawable.GradientDrawable>) {
        stopRGBAnimation(views, backgrounds)
        val anim = ValueAnimator.ofFloat(0f, 360f)
        anim.duration = 6000L
        anim.repeatCount = ValueAnimator.INFINITE
        anim.interpolator = LinearInterpolator()
        anim.addUpdateListener { animation ->
            val hueOffset = animation.animatedValue as Float
            val count = views.size
            for (i in 0 until count) {
                val keyHue = (hueOffset + i * (360f / Math.max(1, count))) % 360f
                val color = Color.HSVToColor(floatArrayOf(keyHue, 0.9f, 0.9f))
                if (i < backgrounds.size) {
                    backgrounds[i].setStroke(dpToPx(1.2f), color)
                }
                if (i < views.size) {
                    views[i].setShadowLayer(dpToPx(5f).toFloat(), 0f, 0f, color)
                }
            }
        }
        rgbAnimator = anim
        anim.start()
    }

    fun stopRGBAnimation(views: List<TextView>,
                         backgrounds: List<android.graphics.drawable.GradientDrawable>) {
        rgbAnimator?.cancel()
        rgbAnimator = null
        for (i in views.indices) {
            views[i].setShadowLayer(0f, 0f, 0f, 0)
            if (i < backgrounds.size) {
                backgrounds[i].setStroke(dpToPx(1f),
                    androidx.core.content.ContextCompat.getColor(context, R.color.kbd_key_stroke))
            }
        }
    }

    private fun dpToPx(dp: Float): Int =
        Math.round(dp * context.resources.displayMetrics.density)
}
