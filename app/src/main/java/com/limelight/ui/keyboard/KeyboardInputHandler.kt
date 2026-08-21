package com.limelight.ui.keyboard

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.widget.TextView
import android.widget.Toast
import com.limelight.R
import com.limelight.nvstream.NvConnection

/**
 * Procesa la entrada del teclado: envía caracteres, modificadores y macros
 * al PC vía [NvConnection]. No tiene conocimiento de layouts ni vistas
 * excepto los botones modificadores que actualiza visualmente.
 */
class KeyboardInputHandler(
    private val context: Context,
    private val state: KeyboardState,
    private val layout: KeyboardLayoutEngine
) {
    private var conn: NvConnection? = null
    private var btnShiftL: TextView? = null
    private var btnShiftR: TextView? = null
    private var btnCtrl: TextView? = null
    private var btnAlt: TextView? = null

    fun setConnection(c: NvConnection?) { conn = c }
    fun setModifierButtons(sL: TextView?, sR: TextView?, c: TextView?, a: TextView?) {
        btnShiftL = sL; btnShiftR = sR; btnCtrl = c; btnAlt = a
    }

    // ── Pulsaciones de tecla ──────────────────────────────────────────

    fun onCharKeyPressed(kd: KeyboardLayoutEngine.KeyData) {
        val c = conn ?: run { noConnection(); return }
        if (state.ctrlActive || state.altActive) {
            sendKeyWithModifier(kd.vkCode, state.buildModifierMask())
            state.releaseAllModifiers()
            updateAllModBtns()
        } else if (state.shiftActive) {
            c.sendUtf8Text(kd.shiftedLabel)
            releaseShift()
        } else {
            var toSend = kd.normalLabel
            if (toSend.length == 1) {
                val ch = toSend[0]
                if (ch in 'A'..'Z' || ch == 'Ñ') toSend = toSend.lowercase()
            }
            c.sendUtf8Text(toSend)
        }
    }

    fun onSpecialKeyPressed(vkCode: Int) {
        val c = conn ?: run { noConnection(); return }
        sendKeyWithModifier(vkCode, state.buildModifierMask())
        if (vkCode != KeyboardLayoutEngine.VK_LEFT && vkCode != KeyboardLayoutEngine.VK_UP
            && vkCode != KeyboardLayoutEngine.VK_RIGHT && vkCode != KeyboardLayoutEngine.VK_DOWN) {
            state.releaseAllModifiers()
            updateAllModBtns()
        }
    }

    fun onSpacePressed() {
        val c = conn ?: run { noConnection(); return }
        val mod = state.buildModifierMask()
        if (mod.toInt() != 0) {
            sendKeyWithModifier(KeyboardLayoutEngine.VK_SPACE, mod)
            state.releaseAllModifiers()
            updateAllModBtns()
        } else {
            c.sendUtf8Text(" ")
        }
    }

    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    // ── Macros ─────────────────────────────────────────────────────────

    fun sendMacro(vararg vkCodes: Int) {
        val c = conn ?: return
        if (vkCodes.isEmpty()) return

        if (vkCodes.size == 1) {
            val code = toGfeCode(vkCodes[0])
            c.sendKeyboardInput(code, 3.toByte(), 0.toByte(), 0.toByte())
            mainHandler.postDelayed({
                try {
                    c.sendKeyboardInput(code, 4.toByte(), 0.toByte(), 0.toByte())
                } catch (ignored: Exception) {}
            }, 50)
            return
        }

        // Si son 2 o más teclas (ej. Ctrl + Ñ, Ctrl + Shift + P, Ctrl + J):
        // Los modificadores son los primeros y la tecla de acción es la última.
        val modifiers = vkCodes.dropLast(1)
        val mainKey = vkCodes.last()

        // 1. Presionar los modificadores (Ctrl, Shift, Alt)
        for (m in modifiers) {
            c.sendKeyboardInput(toGfeCode(m), 3.toByte(), 0.toByte(), 0.toByte())
        }

        // 2. Retardo de 40ms para que Windows registre el modificador como activo
        mainHandler.postDelayed({
            try {
                // Presionar tecla principal (ej. Ñ / 192 / J)
                c.sendKeyboardInput(toGfeCode(mainKey), 3.toByte(), 0.toByte(), 0.toByte())

                // 3. Retardo de 40ms y soltar tecla principal
                mainHandler.postDelayed({
                    try {
                        c.sendKeyboardInput(toGfeCode(mainKey), 4.toByte(), 0.toByte(), 0.toByte())

                        // 4. Retardo de 30ms y soltar modificadores
                        mainHandler.postDelayed({
                            for (m in modifiers.reversed()) {
                                try {
                                    c.sendKeyboardInput(toGfeCode(m), 4.toByte(), 0.toByte(), 0.toByte())
                                } catch (ignored: Exception) {}
                            }
                        }, 30)
                    } catch (ignored: Exception) {}
                }, 40)
            } catch (ignored: Exception) {}
        }, 40)
    }

    fun sendKeyWithModifier(vkCode: Int, modifier: Byte) {
        val c = conn ?: return
        val gfe = toGfeCode(vkCode)
        c.sendKeyboardInput(gfe, 3.toByte(), modifier, 0.toByte())
        mainHandler.postDelayed({
            try {
                c.sendKeyboardInput(gfe, 4.toByte(), modifier, 0.toByte())
            } catch (ignored: Exception) {}
        }, 50)
    }

    private fun toGfeCode(vkCode: Int): Short = (0x8000 or vkCode).toShort()

    // ── Modificadores ──────────────────────────────────────────────────

    fun toggleModifier(type: String, btn: TextView) {
        when (type) {
            "shift" -> {
                state.shiftActive = !state.shiftActive
                updateModBtn(btn, state.shiftActive)
                if (btnShiftL != null && btn !== btnShiftL) updateModBtn(btnShiftL, state.shiftActive)
                if (btnShiftR != null && btn !== btnShiftR) updateModBtn(btnShiftR, state.shiftActive)
                updateKeyLabels()
            }
            "ctrl" -> {
                state.ctrlActive = !state.ctrlActive
                updateModBtn(btn, state.ctrlActive)
            }
            "alt" -> {
                state.altActive = !state.altActive
                updateModBtn(btn, state.altActive)
            }
        }
    }

    fun releaseShift() {
        state.shiftActive = false
        if (btnShiftL != null) updateModBtn(btnShiftL, false)
        if (btnShiftR != null) updateModBtn(btnShiftR, false)
        updateKeyLabels()
    }

    fun releaseAllModifiers() {
        state.releaseAllModifiers()
        updateAllModBtns()
    }

    fun updateKeyLabels() {
        for (kd in layout.allKeys) {
            kd.view.text = if (state.shiftActive) kd.shiftedLabel else kd.normalLabel
        }
    }

    // ── Visual de botones modificadores ────────────────────────────────

    private fun updateModBtn(btn: TextView?, active: Boolean) {
        if (btn == null) return
        if (active) {
            btn.setBackgroundResource(R.drawable.key_active_bg)
        } else {
            val index = layout.allKeyViews.indexOf(btn)
            if (index in 0 until layout.keyBackgrounds.size) {
                btn.background = RippleDrawable(
                    ColorStateList.valueOf(
                        androidx.core.content.ContextCompat.getColor(btn.context, R.color.kbd_key_ripple)),
                    layout.keyBackgrounds[index],
                    layout.keyBackgrounds[index])
            } else {
                btn.setBackgroundResource(R.drawable.key_button_bg)
            }
        }
        btn.setTextColor(androidx.core.content.ContextCompat.getColor(btn.context, R.color.kbd_key_text))
    }

    private fun updateAllModBtns() {
        if (btnShiftL != null) updateModBtn(btnShiftL, state.shiftActive)
        if (btnShiftR != null) updateModBtn(btnShiftR, state.shiftActive)
        if (btnCtrl != null) updateModBtn(btnCtrl, state.ctrlActive)
        if (btnAlt != null) updateModBtn(btnAlt, state.altActive)
    }

    // ── Utilidad ───────────────────────────────────────────────────────

    private fun noConnection() {
        Toast.makeText(context, R.string.keyboard_no_connection, Toast.LENGTH_SHORT).show()
    }

    /** Tooltip descriptivo de cada macro. */
    fun macroDesc(label: String): String = when (label) {
        "Alt+Tab"     -> "Cambiar de ventana"
        "Alt+F4"      -> "Cerrar ventana"
        "Win+D"       -> "Mostrar escritorio"
        "Win+E"       -> "Abrir Explorador de archivos"
        "Ctrl+C"      -> "Copiar"
        "Ctrl+V"      -> "Pegar"
        "Ctrl+X"      -> "Cortar"
        "Ctrl+Z"      -> "Deshacer"
        "Ctrl+Y"      -> "Rehacer"
        "Ctrl+S"      -> "Guardar"
        "Ctrl+F"      -> "Buscar"
        "Ctrl+A"      -> "Seleccionar todo"
        "Ctrl+Sh+P"   -> "Paleta de comandos (VS Code)"
        "Ctrl+Alt+L"  -> "Formatear código (Android Studio)"
        "Shift+F10"   -> "Menú contextual (clic derecho)"
        "Ctrl+Sh+Esc" -> "Administrador de tareas"
        else          -> "Atajo"
    }
}
