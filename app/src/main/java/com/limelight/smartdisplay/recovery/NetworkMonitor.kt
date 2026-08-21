package com.limelight.smartdisplay.recovery

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Handler
import android.os.Looper
import com.limelight.LimeLog

/**
 * SmartDisplay AI – Capa 2: Network Monitor
 *
 * Observa cambios en la conectividad de Android usando ConnectivityManager
 * y NetworkCallback (API 21+) sin interferir con el pipeline de Moonlight.
 *
 * Notifica al {@link AutoReconnectManager} cuando:
 *   - Se pierde la red activa         → onNetworkLost()
 *   - Hay una nueva red disponible    → onNetworkAvailable()
 */
class NetworkMonitor(context: Context, private val listener: NetworkChangeListener) {
    companion object {
        private const val TAG = "SD_NetworkMonitor"
    }

    /** Callback que recibe los eventos de red. */
    interface NetworkChangeListener {
        /** La red activa se perdió (WiFi caída, datos cortados, etc.). */
        fun onNetworkLost()
        /** Hay una nueva red utilizable (WiFi nueva, datos móviles activos, etc.). */
        fun onNetworkAvailable()
    }

    private val connectivityManager: ConnectivityManager = 
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var registered = false

    // ────────────────────────────────────────────────────────────────────────
    // Ciclo de vida
    // ────────────────────────────────────────────────────────────────────────

    /** Inicia la escucha de cambios de red. Seguro de llamar múltiples veces. */
    fun start() {
        if (registered) return

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                LimeLog.info("$TAG: Red disponible → $network")
                mainHandler.post { listener.onNetworkAvailable() }
            }

            override fun onLost(network: Network) {
                LimeLog.info("$TAG: Red perdida → $network")
                mainHandler.post { listener.onNetworkLost() }
            }
        }

        connectivityManager.registerNetworkCallback(request, networkCallback!!)
        registered = true
        LimeLog.info("$TAG: Monitor iniciado")
    }

    /** Detiene la escucha. Llamar en onDestroy(). */
    fun stop() {
        if (registered && networkCallback != null) {
            try {
                connectivityManager.unregisterNetworkCallback(networkCallback!!)
            } catch (e: IllegalArgumentException) {
                // Puede ocurrir si ya fue desregistrado
                LimeLog.warning("$TAG: unregister ignorado – ${e.message}")
            }
            networkCallback = null
            registered = false
            LimeLog.info("$TAG: Monitor detenido")
        }
    }

    /** Devuelve true si hay conectividad de Internet activa ahora mismo. */
    fun isNetworkAvailable(): Boolean {
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val caps = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
