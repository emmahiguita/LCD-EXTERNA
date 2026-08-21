package com.limelight.ui.keyboard

import com.limelight.nvstream.input.KeyboardPacket

/**
 * Modificadores remotos reales.
 *
 * Cada modificador contiene:
 * - VK físico Win32
 * - bit de modificador usado por el protocolo Moonlight.
 */
enum class RemoteModifier(
    val vkCode: Int,
    val moonlightMask: Byte
) {
    SHIFT(
        Win32VirtualKey.VK_LSHIFT,
        KeyboardPacket.MODIFIER_SHIFT
    ),
    CTRL(
        Win32VirtualKey.VK_LCONTROL,
        KeyboardPacket.MODIFIER_CTRL
    ),
    ALT(
        Win32VirtualKey.VK_LMENU,
        KeyboardPacket.MODIFIER_ALT
    ),
    META(
        Win32VirtualKey.VK_LWIN,
        KeyboardPacket.MODIFIER_META
    );

    companion object {
        fun fromVk(vkCode: Int): RemoteModifier? {
            return when (vkCode) {
                Win32VirtualKey.VK_SHIFT,
                Win32VirtualKey.VK_LSHIFT,
                Win32VirtualKey.VK_RSHIFT -> SHIFT

                Win32VirtualKey.VK_CONTROL,
                Win32VirtualKey.VK_LCONTROL,
                Win32VirtualKey.VK_RCONTROL -> CTRL

                Win32VirtualKey.VK_MENU,
                Win32VirtualKey.VK_LMENU,
                Win32VirtualKey.VK_RMENU -> ALT

                Win32VirtualKey.VK_LWIN,
                Win32VirtualKey.VK_RWIN -> META

                else -> null
            }
        }

        fun fromMask(mask: Byte): List<RemoteModifier> {
            val value = mask.toInt()
            val result = ArrayList<RemoteModifier>(4)
            if ((value and KeyboardPacket.MODIFIER_CTRL.toInt()) != 0) {
                result += CTRL
            }
            if ((value and KeyboardPacket.MODIFIER_ALT.toInt()) != 0) {
                result += ALT
            }
            if ((value and KeyboardPacket.MODIFIER_SHIFT.toInt()) != 0) {
                result += SHIFT
            }
            if ((value and KeyboardPacket.MODIFIER_META.toInt()) != 0) {
                result += META
            }
            return result
        }
    }
}

/**
 * Una acción ya no es "List<Int>".
 *
 * La diferencia entre estas operaciones es esencial:
 *
 * Chord:
 *   Ctrl DOWN
 *   Shift DOWN
 *   P DOWN
 *   P UP
 *   Shift UP
 *   Ctrl UP
 *
 * Sequence:
 *   Ctrl+K
 *   después
 *   W
 *
 * DoubleTap:
 *   Shift DOWN/UP
 *   Shift DOWN/UP
 */
sealed class RemoteKeyAction {

    /**
     * Pulsación física simple.
     */
    data class Key(
        val vkCode: Int
    ) : RemoteKeyAction()

    /**
     * Combinación simultánea.
     *
     * Ejemplo:
     *   Ctrl+Shift+P
     */
    data class Chord(
        val vkCode: Int,
        val modifiers: List<RemoteModifier>
    ) : RemoteKeyAction()

    /**
     * Secuencia temporal.
     *
     * Ejemplos:
     *   VS Code: Ctrl+K -> W
     *   OpenCode: Ctrl+X -> E
     */
    data class Sequence(
        val actions: List<RemoteKeyAction>,
        val gapMs: Long = DEFAULT_SEQUENCE_GAP_MS
    ) : RemoteKeyAction()

    /**
     * Dos pulsaciones físicas completas.
     *
     * JetBrains Search Everywhere: Shift, Shift
     */
    data class DoubleTap(
        val vkCode: Int,
        val gapMs: Long = DEFAULT_DOUBLE_TAP_GAP_MS
    ) : RemoteKeyAction()

    /**
     * Texto Unicode.
     *
     * Se transmite por NvConnection.sendUtf8Text().
     */
    data class Text(
        val text: String
    ) : RemoteKeyAction()

    companion object {
        const val DEFAULT_SEQUENCE_GAP_MS = 70L
        const val DEFAULT_DOUBLE_TAP_GAP_MS = 90L
    }
}
