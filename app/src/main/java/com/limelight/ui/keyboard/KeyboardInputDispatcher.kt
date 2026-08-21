package com.limelight.ui.keyboard

import android.util.Log
import com.limelight.nvstream.NvConnection
import com.limelight.nvstream.input.KeyboardPacket
import java.util.LinkedHashSet
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicLong

/**
 * Dispatcher SERIAL de input remoto.
 *
 * Garantías:
 * 1. Una sola macro se ejecuta a la vez.
 * 2. No pueden mezclarse DOWN/UP de dos macros.
 * 3. Soporta Chord real.
 * 4. Soporta Sequence real.
 * 5. Soporta DoubleTap real.
 * 6. Mantiene registro de las teclas actualmente DOWN.
 * 7. releaseAllPressedKeys() invalida macros pendientes.
 * 8. Al cambiar NvConnection libera primero la conexión anterior.
 */
class KeyboardInputDispatcher {

    @Volatile
    private var connection: NvConnection? = null

    @Volatile
    private var closed = false

    /**
     * Cada cancelación incrementa generation.
     * Las acciones viejas dejan automáticamente de ejecutarse.
     */
    private val generation = AtomicLong(0L)

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "SmartDisplay-KeyboardTX").apply {
            priority = Thread.NORM_PRIORITY
            isDaemon = true
        }
    }

    /**
     * Solo se modifica desde el executor serial.
     */
    private val pressedKeys = LinkedHashSet<Int>()

    fun hasConnection(): Boolean {
        return connection != null
    }

    @Synchronized
    fun setConnection(newConnection: NvConnection?) {
        if (closed) return
        val oldConnection = connection
        if (oldConnection === newConnection) {
            return
        }
        // Invalida cualquier acción asociada a la conexión anterior.
        generation.incrementAndGet()
        connection = newConnection
        try {
            executor.execute {
                if (oldConnection != null) {
                    releaseAllInternal(oldConnection)
                } else {
                    pressedKeys.clear()
                }
            }
        } catch (_: RejectedExecutionException) {
            pressedKeys.clear()
        }
    }

    /**
     * Encola una acción.
     * @return false si no existe conexión.
     */
    fun dispatch(action: RemoteKeyAction): Boolean {
        if (closed) return false
        val conn = connection ?: return false
        val ticket = generation.get()
        try {
            executor.execute {
                if (!isCurrent(ticket)) {
                    return@execute
                }
                try {
                    executeAction(
                        action = action,
                        conn = conn,
                        ticket = ticket
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error despachando acción $action", e)
                    releaseAllInternal(conn)
                }
            }
        } catch (_: RejectedExecutionException) {
            return false
        }
        return true
    }

    /**
     * Cancela macros pendientes y garantiza KEY_UP de todo lo que
     * todavía esté registrado como DOWN.
     */
    fun releaseAllPressedKeys() {
        if (closed) return
        val conn = connection
        generation.incrementAndGet()
        try {
            executor.execute {
                if (conn != null) {
                    releaseAllInternal(conn)
                } else {
                    pressedKeys.clear()
                }
            }
        } catch (_: RejectedExecutionException) {
            pressedKeys.clear()
        }
    }

    /**
     * Cierre definitivo del dispatcher.
     */
    @Synchronized
    fun close() {
        if (closed) return
        closed = true
        val conn = connection
        generation.incrementAndGet()
        try {
            executor.execute {
                if (conn != null) {
                    releaseAllInternal(conn)
                } else {
                    pressedKeys.clear()
                }
            }
        } catch (_: RejectedExecutionException) {
            pressedKeys.clear()
        } finally {
            connection = null
            executor.shutdown()
        }
    }

    private fun executeAction(
        action: RemoteKeyAction,
        conn: NvConnection,
        ticket: Long
    ): Boolean {
        if (!isCurrent(ticket)) {
            return false
        }
        return when (action) {
            is RemoteKeyAction.Key -> {
                tapKey(
                    conn = conn,
                    vkCode = action.vkCode,
                    ticket = ticket
                )
            }
            is RemoteKeyAction.Chord -> {
                sendChord(
                    conn = conn,
                    vkCode = action.vkCode,
                    modifiers = action.modifiers,
                    ticket = ticket
                )
            }
            is RemoteKeyAction.Sequence -> {
                executeSequence(
                    action = action,
                    conn = conn,
                    ticket = ticket
                )
            }
            is RemoteKeyAction.DoubleTap -> {
                executeDoubleTap(
                    action = action,
                    conn = conn,
                    ticket = ticket
                )
            }
            is RemoteKeyAction.Text -> {
                if (!isCurrent(ticket)) {
                    false
                } else {
                    conn.sendUtf8Text(action.text)
                    true
                }
            }
        }
    }

    private fun executeSequence(
        action: RemoteKeyAction.Sequence,
        conn: NvConnection,
        ticket: Long
    ): Boolean {
        for (index in action.actions.indices) {
            if (!executeAction(
                    action = action.actions[index],
                    conn = conn,
                    ticket = ticket
                )
            ) {
                return false
            }
            if (index != action.actions.lastIndex) {
                if (!pause(
                        action.gapMs,
                        ticket
                    )
                ) {
                    return false
                }
            }
        }
        return true
    }

    private fun executeDoubleTap(
        action: RemoteKeyAction.DoubleTap,
        conn: NvConnection,
        ticket: Long
    ): Boolean {
        if (!tapKey(conn, action.vkCode, ticket)) {
            return false
        }
        if (!pause(action.gapMs, ticket)) {
            return false
        }
        return tapKey(conn, action.vkCode, ticket)
    }

    /**
     * Implementación de combinación simultánea REAL.
     */
    private fun sendChord(
        conn: NvConnection,
        vkCode: Int,
        modifiers: List<RemoteModifier>,
        ticket: Long
    ): Boolean {
        val uniqueModifiers = modifiers.distinct()
        for (modifier in uniqueModifiers) {
            if (!isCurrent(ticket)) {
                return false
            }
            pressKey(
                conn = conn,
                vkCode = modifier.vkCode
            )
            if (!pause(MODIFIER_GAP_MS, ticket)) {
                return false
            }
        }
        if (!tapKey(conn = conn, vkCode = vkCode, ticket = ticket)) {
            return false
        }
        for (modifier in uniqueModifiers.asReversed()) {
            if (!isCurrent(ticket)) {
                return false
            }
            releaseKey(
                conn = conn,
                vkCode = modifier.vkCode
            )
            if (!pause(MODIFIER_RELEASE_GAP_MS, ticket)) {
                return false
            }
        }
        return true
    }

    private fun tapKey(
        conn: NvConnection,
        vkCode: Int,
        ticket: Long
    ): Boolean {
        if (!isCurrent(ticket)) {
            return false
        }
        pressKey(conn = conn, vkCode = vkCode)
        if (!pause(KEY_HOLD_MS, ticket)) {
            return false
        }
        if (!isCurrent(ticket)) {
            return false
        }
        releaseKey(conn = conn, vkCode = vkCode)
        return true
    }

    private fun pressKey(
        conn: NvConnection,
        vkCode: Int
    ) {
        // Evita DOWN duplicado accidental.
        if (!pressedKeys.add(vkCode)) {
            return
        }
        val modifierMask = currentModifierMask()
        sendPacket(
            conn = conn,
            vkCode = vkCode,
            direction = KeyboardPacket.KEY_DOWN,
            modifierMask = modifierMask
        )
    }

    private fun releaseKey(
        conn: NvConnection,
        vkCode: Int
    ) {
        if (!pressedKeys.contains(vkCode)) {
            return
        }
        /*
         * Igual que los KeyEvent físicos de Game.java:
         * el paquete lleva el estado actual de modificadores.
         */
        val modifierMask = currentModifierMask()
        sendPacket(
            conn = conn,
            vkCode = vkCode,
            direction = KeyboardPacket.KEY_UP,
            modifierMask = modifierMask
        )
        pressedKeys.remove(vkCode)
    }

    /**
     * Seguridad contra stuck keys.
     * Primero libera teclas normales.
     * Después modificadores, en orden inverso.
     */
    private fun releaseAllInternal(conn: NvConnection) {
        try {
            val normalKeys = pressedKeys
                .filterNot(Win32VirtualKey::isModifier)
                .asReversed()
            for (vkCode in normalKeys) {
                try {
                    sendPacket(
                        conn = conn,
                        vkCode = vkCode,
                        direction = KeyboardPacket.KEY_UP,
                        modifierMask = currentModifierMask()
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "No se pudo liberar VK=$vkCode", e)
                }
                pressedKeys.remove(vkCode)
            }
            val modifiers = pressedKeys
                .toList()
                .asReversed()
            for (vkCode in modifiers) {
                try {
                    sendPacket(
                        conn = conn,
                        vkCode = vkCode,
                        direction = KeyboardPacket.KEY_UP,
                        modifierMask = currentModifierMask()
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "No se pudo liberar modificador VK=$vkCode", e)
                }
                pressedKeys.remove(vkCode)
            }
        } finally {
            pressedKeys.clear()
        }
    }

    private fun currentModifierMask(): Byte {
        var mask = 0
        if (pressedKeys.contains(Win32VirtualKey.VK_SHIFT) ||
            pressedKeys.contains(Win32VirtualKey.VK_LSHIFT) ||
            pressedKeys.contains(Win32VirtualKey.VK_RSHIFT)
        ) {
            mask = mask or KeyboardPacket.MODIFIER_SHIFT.toInt()
        }
        if (pressedKeys.contains(Win32VirtualKey.VK_CONTROL) ||
            pressedKeys.contains(Win32VirtualKey.VK_LCONTROL) ||
            pressedKeys.contains(Win32VirtualKey.VK_RCONTROL)
        ) {
            mask = mask or KeyboardPacket.MODIFIER_CTRL.toInt()
        }
        if (pressedKeys.contains(Win32VirtualKey.VK_MENU) ||
            pressedKeys.contains(Win32VirtualKey.VK_LMENU) ||
            pressedKeys.contains(Win32VirtualKey.VK_RMENU)
        ) {
            mask = mask or KeyboardPacket.MODIFIER_ALT.toInt()
        }
        if (pressedKeys.contains(Win32VirtualKey.VK_LWIN) ||
            pressedKeys.contains(Win32VirtualKey.VK_RWIN)
        ) {
            mask = mask or KeyboardPacket.MODIFIER_META.toInt()
        }
        return mask.toByte()
    }

    private fun sendPacket(
        conn: NvConnection,
        vkCode: Int,
        direction: Byte,
        modifierMask: Byte
    ) {
        val moonlightCode = Win32VirtualKey.toMoonlightCode(vkCode)
        conn.sendKeyboardInput(
            moonlightCode,
            direction,
            modifierMask,
            0.toByte()
        )
        if (DEBUG_LOGS) {
            Log.d(
                TAG,
                "VK=0x${vkCode.toString(16)} dir=${direction.toInt()} mods=0x${modifierMask.toInt().toString(16)}"
            )
        }
    }

    private fun pause(
        durationMs: Long,
        ticket: Long
    ): Boolean {
        if (durationMs <= 0L) {
            return isCurrent(ticket)
        }
        try {
            Thread.sleep(durationMs)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            return false
        }
        return isCurrent(ticket)
    }

    private fun isCurrent(ticket: Long): Boolean {
        return !closed && ticket == generation.get()
    }

    companion object {
        private const val TAG = "KeyboardDispatcher"
        private const val DEBUG_LOGS = false

        /*
         * Retardos suficientemente cortos para streaming,
         * pero conservan orden observable por Windows.
         */
        private const val KEY_HOLD_MS = 32L
        private const val MODIFIER_GAP_MS = 18L
        private const val MODIFIER_RELEASE_GAP_MS = 12L
    }
}
