package com.limelight.smartdisplay.context

import android.os.Handler
import android.os.Looper
import java.util.concurrent.CopyOnWriteArrayList

/**
 * SmartDisplay AI – Capa 2: registry de la aplicación activa en el PC.
 *
 * El companion (Python) emite mensajes `foreground_app` por el SmartDisplayBus
 * cada vez que cambia la ventana en primer plano del PC. Game.java enruta esos
 * mensajes hasta aquí y los consumidores (FAB, SmartBar contextual, perfiles
 * de cursor por app…) se suscriben a este registry para reaccionar.
 *
 * Object (singleton) deliberado: el estado de "qué app está activa en el PC"
 * es global a la sesión y muchos componentes lo necesitan; pasarlo por
 * constructor a todos sería ruido. Las suscripciones son débiles en el sentido
 * de que cada consumidor debe llamar a [removeListener] en su onDestroy().
 */
object ForegroundAppRegistry {

    data class ForegroundApp(val process: String, val title: String) {
        /** "chrome.exe" → "chrome" para matching ligero. */
        val processBase: String
            get() = process.substringBeforeLast('.', process).lowercase()
    }

    fun interface Listener {
        fun onForegroundAppChanged(app: ForegroundApp)
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArrayList<Listener>()

    @Volatile
    private var current: ForegroundApp? = null

    /** Snapshot del estado actual o null si aún no llegó ninguno. */
    val currentApp: ForegroundApp?
        get() = current

    fun addListener(listener: Listener) {
        listeners.addIfAbsent(listener)
        // Replay del estado actual para que el suscriptor no se pierda el último
        // cambio si llega tarde a la fiesta.
        current?.let { snapshot ->
            mainHandler.post { listener.onForegroundAppChanged(snapshot) }
        }
    }

    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }

    /**
     * Lo llama Game.java al recibir un mensaje JSON `foreground_app` del bus.
     * Deduplica para evitar notificaciones espurias si llega el mismo estado
     * (ocurre en el snapshot inicial post-reconexión).
     */
    fun update(process: String, title: String) {
        val next = ForegroundApp(process, title)
        if (next == current) return
        current = next
        // Notificar siempre en el main thread; los consumidores tocan UI.
        if (Looper.myLooper() == Looper.getMainLooper()) {
            listeners.forEach { it.onForegroundAppChanged(next) }
        } else {
            mainHandler.post {
                listeners.forEach { it.onForegroundAppChanged(next) }
            }
        }
    }

    /** Limpia el estado al cerrar la sesión (onDestroy de Game). */
    fun reset() {
        current = null
        listeners.clear()
    }
}
