package com.limelight.ui.keyboard

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.RippleDrawable
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.limelight.R
import com.limelight.nvstream.NvConnection

/**
 * Fachada entre UI y KeyboardInputDispatcher.
 *
 * No transmite directamente paquetes Moonlight.
 */
class KeyboardInputHandler(
    private val context: Context,
    private val state: KeyboardState,
    private val layout: KeyboardLayoutEngine
) {
    private val dispatcher = KeyboardInputDispatcher()
    private var btnShiftL: TextView? = null
    private var btnShiftR: TextView? = null
    private var btnCtrl: TextView? = null
    private var btnAlt: TextView? = null

    fun setConnection(c: NvConnection?) {
        dispatcher.setConnection(c)
    }

    fun hasConnection(): Boolean {
        return dispatcher.hasConnection()
    }

    fun setModifierButtons(
        shiftL: TextView?,
        shiftR: TextView?,
        ctrl: TextView?,
        alt: TextView?
    ) {
        btnShiftL = shiftL
        btnShiftR = shiftR
        btnCtrl = ctrl
        btnAlt = alt
        updateAllModBtns()
    }

    // ------------------------------------------------------------
    // TEXTO
    // ------------------------------------------------------------

    fun onCharKeyPressed(
        kd: KeyboardLayoutEngine.KeyData
    ) {
        val modifiers = state.activeModifiers()
        val action = if (
            modifiers.contains(RemoteModifier.CTRL) ||
            modifiers.contains(RemoteModifier.ALT) ||
            modifiers.contains(RemoteModifier.META)
        ) {
            /*
             * Shortcut físico.
             * Ej: Ctrl+C, Ctrl+Shift+P
             */
            RemoteKeyAction.Chord(
                vkCode = kd.vkCode,
                modifiers = modifiers
            )
        } else if (state.shiftActive) {
            /*
             * Para escritura de caracteres usamos UTF-8.
             * Esto evita que el layout físico del Windows remoto
             * convierta incorrectamente caracteres como: { } | ~ @ etc.
             */
            RemoteKeyAction.Text(kd.shiftedLabel)
        } else {
            RemoteKeyAction.Text(normalizeNormalText(kd.normalLabel))
        }

        if (!dispatcher.dispatch(action)) {
            noConnection()
            return
        }
        consumeOneShotAfterAction()
    }

    fun onSpecialKeyPressed(
        vkCode: Int
    ) {
        val modifiers = state.activeModifiers()
        val action = if (modifiers.isEmpty()) {
            RemoteKeyAction.Key(vkCode)
        } else {
            RemoteKeyAction.Chord(
                vkCode = vkCode,
                modifiers = modifiers
            )
        }
        if (!dispatcher.dispatch(action)) {
            noConnection()
            return
        }
        consumeOneShotAfterAction()
    }

    fun onSpacePressed() {
        val modifiers = state.activeModifiers()
        val action = if (modifiers.isEmpty()) {
            RemoteKeyAction.Text(" ")
        } else {
            RemoteKeyAction.Chord(
                vkCode = Win32VirtualKey.VK_SPACE,
                modifiers = modifiers
            )
        }
        if (!dispatcher.dispatch(action)) {
            noConnection()
            return
        }
        consumeOneShotAfterAction()
    }

    // ------------------------------------------------------------
    // REMOTE ACTION
    // ------------------------------------------------------------

    fun sendAction(
        action: RemoteKeyAction
    ) {
        if (!dispatcher.dispatch(action)) {
            noConnection()
            return
        }
        consumeOneShotAfterAction()
    }

    @Deprecated(message = "Usar RemoteKeyAction directamente")
    fun sendMacro(vararg vkCodes: Int) {
        if (vkCodes.isEmpty()) {
            return
        }
        sendAction(legacyMacroToAction(vkCodes.toList()))
    }

    @Deprecated(message = "Usar RemoteKeyAction.Chord")
    fun sendKeyWithModifier(vkCode: Int, modifier: Byte) {
        val mods = RemoteModifier.fromMask(modifier)
        val action = if (mods.isEmpty()) {
            RemoteKeyAction.Key(vkCode)
        } else {
            RemoteKeyAction.Chord(vkCode, mods)
        }
        sendAction(action)
    }

    private fun legacyMacroToAction(codes: List<Int>): RemoteKeyAction {
        if (codes.size == 1) {
            return RemoteKeyAction.Key(codes.first())
        }
        val actions = ArrayList<RemoteKeyAction>()
        val pendingModifiers = ArrayList<RemoteModifier>()
        for (vkCode in codes) {
            val modifier = RemoteModifier.fromVk(vkCode)
            if (modifier != null) {
                if (!pendingModifiers.contains(modifier)) {
                    pendingModifiers += modifier
                }
                continue
            }
            if (pendingModifiers.isEmpty()) {
                actions += RemoteKeyAction.Key(vkCode)
            } else {
                actions += RemoteKeyAction.Chord(
                    vkCode = vkCode,
                    modifiers = pendingModifiers.toList()
                )
                pendingModifiers.clear()
            }
        }
        for (modifier in pendingModifiers) {
            actions += RemoteKeyAction.Key(modifier.vkCode)
        }
        return when (actions.size) {
            0 -> RemoteKeyAction.Key(codes.last())
            1 -> actions.first()
            else -> RemoteKeyAction.Sequence(actions = actions)
        }
    }

    // ------------------------------------------------------------
    // MODIFICADORES STICKY
    // ------------------------------------------------------------

    fun toggleModifier(type: String, btn: TextView) {
        state.toggleModifier(type)
        when (type.lowercase()) {
            "shift" -> {
                updateModBtn(btn, state.shiftActive)
                updateModBtn(btnShiftL, state.shiftActive)
                updateModBtn(btnShiftR, state.shiftActive)
                updateKeyLabels()
            }
            "ctrl" -> {
                updateModBtn(btn, state.ctrlActive)
            }
            "alt" -> {
                updateModBtn(btn, state.altActive)
            }
            "meta", "win" -> Unit
        }
    }

    fun releaseShift() {
        state.releaseShift()
        updateModBtn(btnShiftL, false)
        updateModBtn(btnShiftR, false)
        updateKeyLabels()
    }

    fun releaseAllModifiers() {
        state.releaseAllModifiers()
        updateAllModBtns()
        updateKeyLabels()
    }

    fun releaseAllPressedKeys() {
        dispatcher.releaseAllPressedKeys()
        state.releaseAllModifiers()
        updateAllModBtns()
        updateKeyLabels()
    }

    fun close() {
        dispatcher.close()
        state.releaseAllModifiers()
        updateAllModBtns()
    }

    private fun consumeOneShotAfterAction() {
        state.consumeOneShotModifiers()
        updateAllModBtns()
        updateKeyLabels()
    }

    fun updateKeyLabels() {
        for (kd in layout.allKeys) {
            kd.view.text = if (state.shiftActive) {
                kd.shiftedLabel
            } else {
                kd.normalLabel
            }
        }
    }

    private fun updateModBtn(btn: TextView?, active: Boolean) {
        if (btn == null) {
            return
        }
        if (active) {
            btn.setBackgroundResource(R.drawable.key_active_bg)
        } else {
            val index = layout.allKeyViews.indexOf(btn)
            if (index >= 0 && index < layout.keyBackgrounds.size) {
                btn.background = RippleDrawable(
                    ColorStateList.valueOf(
                        ContextCompat.getColor(btn.context, R.color.kbd_key_ripple)
                    ),
                    layout.keyBackgrounds[index],
                    layout.keyBackgrounds[index]
                )
            } else {
                btn.setBackgroundResource(R.drawable.key_button_bg)
            }
        }
        btn.setTextColor(ContextCompat.getColor(btn.context, R.color.kbd_key_text))
    }

    private fun updateAllModBtns() {
        updateModBtn(btnShiftL, state.shiftActive)
        updateModBtn(btnShiftR, state.shiftActive)
        updateModBtn(btnCtrl, state.ctrlActive)
        updateModBtn(btnAlt, state.altActive)
    }

    private fun normalizeNormalText(value: String): String {
        if (value.length != 1) {
            return value
        }
        val char = value[0]
        return if (char in 'A'..'Z') {
            char.lowercase()
        } else {
            value
        }
    }

    private fun noConnection() {
        Toast.makeText(context, R.string.keyboard_no_connection, Toast.LENGTH_SHORT).show()
    }
}
