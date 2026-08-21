package com.limelight.ui.keyboard

import android.os.SystemClock
import com.limelight.nvstream.input.KeyboardPacket

enum class ModifierMode {
    OFF,
    /**
     * Se aplica a la próxima acción y después se libera.
     */
    ONE_SHOT,
    /**
     * Permanece activo hasta que el usuario vuelva a pulsarlo.
     */
    LOCKED,
    /**
     * Reservado para press-and-hold/multitouch físico.
     */
    PRESSED
}

/**
 * Estado observable del teclado SmartDisplay.
 */
class KeyboardState {

    fun interface ModifierListener {
        fun onModifiersChanged(
            ctrl: Boolean,
            alt: Boolean,
            shift: Boolean
        )
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

    var modifierListener: ModifierListener? = null
    var tabListener: TabListener? = null
    var scaleListener: ScaleListener? = null
    var alphaListener: AlphaListener? = null

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
            val clamped = value.coerceIn(
                0,
                ALPHA_VALS.size - 1
            )
            if (field != clamped) {
                field = clamped
                alphaListener?.onAlphaChanged(ALPHA_VALS[clamped])
            }
        }

    private var _shiftMode = ModifierMode.OFF
    private var _ctrlMode = ModifierMode.OFF
    private var _altMode = ModifierMode.OFF
    private var _metaMode = ModifierMode.OFF

    val shiftMode: ModifierMode get() = _shiftMode
    val ctrlMode: ModifierMode get() = _ctrlMode
    val altMode: ModifierMode get() = _altMode
    val metaMode: ModifierMode get() = _metaMode

    /**
     * Compatibilidad con código existente.
     */
    var shiftActive: Boolean
        get() = _shiftMode != ModifierMode.OFF
        set(value) {
            setModifierMode(
                "shift",
                if (value) ModifierMode.ONE_SHOT else ModifierMode.OFF
            )
        }

    var ctrlActive: Boolean
        get() = _ctrlMode != ModifierMode.OFF
        set(value) {
            setModifierMode(
                "ctrl",
                if (value) ModifierMode.ONE_SHOT else ModifierMode.OFF
            )
        }

    var altActive: Boolean
        get() = _altMode != ModifierMode.OFF
        set(value) {
            setModifierMode(
                "alt",
                if (value) ModifierMode.ONE_SHOT else ModifierMode.OFF
            )
        }

    var metaActive: Boolean
        get() = _metaMode != ModifierMode.OFF
        set(value) {
            setModifierMode(
                "meta",
                if (value) ModifierMode.ONE_SHOT else ModifierMode.OFF
            )
        }

    private val lastModifierTap = HashMap<String, Long>()

    /**
     * Comportamiento:
     *
     * OFF -> ONE_SHOT
     *
     * Segundo tap rápido:
     * ONE_SHOT -> LOCKED
     *
     * Tap tardío sobre ONE_SHOT:
     * ONE_SHOT -> OFF
     *
     * LOCKED -> OFF
     */
    fun toggleModifier(
        type: String,
        nowMs: Long = SystemClock.uptimeMillis()
    ) {
        val current = getModifierMode(type)
        val previousTap = lastModifierTap[type] ?: 0L
        val next = when (current) {
            ModifierMode.OFF -> {
                lastModifierTap[type] = nowMs
                ModifierMode.ONE_SHOT
            }
            ModifierMode.ONE_SHOT -> {
                if (nowMs - previousTap <= DOUBLE_TAP_LOCK_MS) {
                    lastModifierTap[type] = 0L
                    ModifierMode.LOCKED
                } else {
                    lastModifierTap[type] = 0L
                    ModifierMode.OFF
                }
            }
            ModifierMode.LOCKED -> {
                lastModifierTap[type] = 0L
                ModifierMode.OFF
            }
            ModifierMode.PRESSED -> {
                lastModifierTap[type] = 0L
                ModifierMode.OFF
            }
        }
        setModifierMode(type, next)
    }

    fun getModifierMode(type: String): ModifierMode {
        return when (type.lowercase()) {
            "shift" -> _shiftMode
            "ctrl" -> _ctrlMode
            "alt" -> _altMode
            "meta", "win" -> _metaMode
            else -> ModifierMode.OFF
        }
    }

    fun setModifierMode(
        type: String,
        mode: ModifierMode
    ) {
        var changed = false
        when (type.lowercase()) {
            "shift" -> {
                if (_shiftMode != mode) {
                    _shiftMode = mode
                    changed = true
                }
            }
            "ctrl" -> {
                if (_ctrlMode != mode) {
                    _ctrlMode = mode
                    changed = true
                }
            }
            "alt" -> {
                if (_altMode != mode) {
                    _altMode = mode
                    changed = true
                }
            }
            "meta", "win" -> {
                if (_metaMode != mode) {
                    _metaMode = mode
                    changed = true
                }
            }
        }
        if (changed) {
            notifyModifierState()
        }
    }

    /**
     * API preparada para futuro press-and-hold multitouch.
     */
    fun pressModifier(type: String) {
        setModifierMode(type, ModifierMode.PRESSED)
    }

    fun releasePressedModifier(type: String) {
        if (getModifierMode(type) == ModifierMode.PRESSED) {
            setModifierMode(type, ModifierMode.OFF)
        }
    }

    /**
     * Libera únicamente los modificadores ONE_SHOT.
     *
     * Los LOCKED permanecen.
     */
    fun consumeOneShotModifiers() {
        var changed = false
        if (_shiftMode == ModifierMode.ONE_SHOT) {
            _shiftMode = ModifierMode.OFF
            changed = true
        }
        if (_ctrlMode == ModifierMode.ONE_SHOT) {
            _ctrlMode = ModifierMode.OFF
            changed = true
        }
        if (_altMode == ModifierMode.ONE_SHOT) {
            _altMode = ModifierMode.OFF
            changed = true
        }
        if (_metaMode == ModifierMode.ONE_SHOT) {
            _metaMode = ModifierMode.OFF
            changed = true
        }
        if (changed) {
            notifyModifierState()
        }
    }

    fun releaseShift() {
        if (_shiftMode != ModifierMode.OFF) {
            _shiftMode = ModifierMode.OFF
            notifyModifierState()
        }
    }

    fun releaseAllModifiers() {
        val changed = _shiftMode != ModifierMode.OFF ||
                _ctrlMode != ModifierMode.OFF ||
                _altMode != ModifierMode.OFF ||
                _metaMode != ModifierMode.OFF
        _shiftMode = ModifierMode.OFF
        _ctrlMode = ModifierMode.OFF
        _altMode = ModifierMode.OFF
        _metaMode = ModifierMode.OFF
        lastModifierTap.clear()
        if (changed) {
            notifyModifierState()
        }
    }

    fun activeModifiers(): List<RemoteModifier> {
        val result = ArrayList<RemoteModifier>(4)
        // Orden estable para DOWN y liberación inversa posterior.
        if (ctrlActive) result += RemoteModifier.CTRL
        if (altActive) result += RemoteModifier.ALT
        if (shiftActive) result += RemoteModifier.SHIFT
        if (metaActive) result += RemoteModifier.META
        return result
    }

    fun buildModifierMask(): Byte {
        var mask = 0
        if (shiftActive) {
            mask = mask or KeyboardPacket.MODIFIER_SHIFT.toInt()
        }
        if (ctrlActive) {
            mask = mask or KeyboardPacket.MODIFIER_CTRL.toInt()
        }
        if (altActive) {
            mask = mask or KeyboardPacket.MODIFIER_ALT.toInt()
        }
        if (metaActive) {
            mask = mask or KeyboardPacket.MODIFIER_META.toInt()
        }
        return mask.toByte()
    }

    private fun notifyModifierState() {
        modifierListener?.onModifiersChanged(
            ctrlActive,
            altActive,
            shiftActive
        )
    }

    companion object {
        private const val DOUBLE_TAP_LOCK_MS = 350L
        val ALPHA_VALS = floatArrayOf(
            1.0f,
            0.85f,
            0.65f,
            0.45f,
            0.25f
        )
    }
}
