package com.limelight.ui.keyboard

/**
 * Estado observable del teclado.
 *
 * Cada propiedad tiene su propio set de notificación para que los listeners
 * puedan reaccionar solo a lo que les interesa (ej. el StickyKeysIndicator
 * solo escucha cambios en los modificadores, no en la escala).
 */
class KeyboardState {

    // ── Listeners ─────────────────────────────────────────────────────────

    fun interface ModifierListener {
        fun onModifiersChanged(ctrl: Boolean, alt: Boolean, shift: Boolean)
    }

    fun interface TabListener {
        fun onTabChanged(tab: Int)
    }

    fun interface ScaleListener {
        fun onScaleChanged(scale: Float)
    }

    fun interface AlphaListener {
        fun onAlphaChanged(alpha: Float)
    }

    // ── Registro de listeners (1 sola ranura cada uno, no lista) ──────────

    var modifierListener: ModifierListener? = null
    var tabListener: TabListener? = null
    var scaleListener: ScaleListener? = null
    var alphaListener: AlphaListener? = null

    // ── Propiedades observables ──────────────────────────────────────────

    var currentTab: Int = 1
        set(value) {
            if (field != value) {
                field = value
                tabListener?.onTabChanged(value)
            }
        }

    var currentScale: Float = 1.02f
        set(value) {
            val clamped = value.coerceIn(0.50f, 1.8f)
            if (field != clamped) {
                field = clamped
                scaleListener?.onScaleChanged(clamped)
            }
        }

    var alphaIndex: Int = 0
        set(value) {
            val clamped = value.coerceIn(0, ALPHA_VALS.size - 1)
            if (field != clamped) {
                field = clamped
                alphaListener?.onAlphaChanged(ALPHA_VALS[clamped])
            }
        }

    var shiftActive: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                modifierListener?.onModifiersChanged(ctrlActive, altActive, shiftActive)
            }
        }

    var ctrlActive: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                modifierListener?.onModifiersChanged(ctrlActive, altActive, shiftActive)
            }
        }

    var altActive: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                modifierListener?.onModifiersChanged(ctrlActive, altActive, shiftActive)
            }
        }

    fun releaseAllModifiers() {
        shiftActive = false
        ctrlActive = false
        altActive = false
    }

    fun buildModifierMask(): Byte {
        var mod = 0
        if (shiftActive) mod = mod or 1
        if (ctrlActive) mod = mod or 2
        if (altActive) mod = mod or 4
        return mod.toByte()
    }

    companion object {
        val ALPHA_VALS = floatArrayOf(1.0f, 0.85f, 0.65f, 0.45f, 0.25f)
    }
}
