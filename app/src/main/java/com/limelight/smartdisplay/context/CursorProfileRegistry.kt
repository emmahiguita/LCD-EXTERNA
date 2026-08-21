package com.limelight.smartdisplay.context

import android.os.Handler
import android.os.Looper
import java.util.concurrent.CopyOnWriteArrayList

/**
 * SmartDisplay AI – Capa 2: registry del perfil de cursor por aplicación.
 *
 * El companion puede enviar un mensaje `cursor_profile` por el bus cuando
 * cambia la app activa, con parámetros que afinan la sensación del puntero
 * en esa app (ej. menor sensibilidad y mayor magnetismo en un IDE, alta
 * sensibilidad sin magnetismo en un juego).
 *
 * Formato JSON esperado:
 *   {
 *     "type": "cursor_profile",
 *     "app": "Code.exe",
 *     "sensitivity": 0.8,   // 0..1 (factor multiplicativo del touchpad)
 *     "precision":   true,  // fuerza precision mode siempre activo
 *     "magnetism":   0.15   // 0..1 (fuerza del Smart Snap)
 *   }
 *
 * Consumidores típicos: [com.limelight.ui.AdaptiveCursorView] y el controller
 * que aplica deltas del touchpad relativo.
 */
object CursorProfileRegistry {

    data class CursorProfile(
        val app: String,
        val sensitivity: Float,
        val precision: Boolean,
        val magnetism: Float,
    )

    /** Perfil neutro (no toca nada). Usado mientras no llegue un mensaje. */
    val DEFAULT = CursorProfile(app = "", sensitivity = 1f, precision = false, magnetism = 0.15f)

    fun interface Listener {
        fun onCursorProfileChanged(profile: CursorProfile)
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArrayList<Listener>()

    @Volatile
    private var current: CursorProfile = DEFAULT

    val currentProfile: CursorProfile
        get() = current

    fun addListener(listener: Listener) {
        listeners.addIfAbsent(listener)
        // Replay del estado actual: idem ForegroundAppRegistry.
        val snapshot = current
        mainHandler.post { listener.onCursorProfileChanged(snapshot) }
    }

    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }

    /**
     * Lo llama Game.java al recibir un mensaje JSON `cursor_profile` del bus.
     * Sanea los valores: sensibilidad y magnetismo se acotan a [0, 1] para
     * no romper la sensación del cursor con un payload mal formado.
     */
    fun update(
        app: String,
        sensitivity: Float,
        precision: Boolean,
        magnetism: Float,
    ) {
        val next = CursorProfile(
            app = app,
            sensitivity = sensitivity.coerceIn(0f, 1f),
            precision = precision,
            magnetism = magnetism.coerceIn(0f, 1f),
        )
        if (next == current) return
        current = next
        if (Looper.myLooper() == Looper.getMainLooper()) {
            listeners.forEach { it.onCursorProfileChanged(next) }
        } else {
            mainHandler.post {
                listeners.forEach { it.onCursorProfileChanged(next) }
            }
        }
    }

    /** Restaura el perfil neutro y limpia listeners al cerrar la sesión. */
    fun reset() {
        current = DEFAULT
        listeners.clear()
    }
}
