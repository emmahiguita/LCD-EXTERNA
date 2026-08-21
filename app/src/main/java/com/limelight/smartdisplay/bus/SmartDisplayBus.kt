package com.limelight.smartdisplay.bus

import android.os.Handler
import android.os.Looper
import com.limelight.LimeLog
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * SmartDisplay AI – Capa 2: WebSocket Bidirectional Bus
 *
 * Ofrece un canal de comunicación de baja latencia entre Android y el PC.
 * Permite mandar comandos (ej. IA, portapapeles) y recibir telemetría/acciones.
 */
class SmartDisplayBus(
    private val host: String,
    private val listener: MessageListener,
    private val pin: String = ""
) {
    companion object {
        private const val TAG = "SD_Bus"
        private const val PORT = 47991
    }

    interface MessageListener {
        fun onMessageReceived(json: JSONObject)
        fun onConnected()
        fun onDisconnected()
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val client: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private var isRunning = false
    private var reconnectAttempts = 0

    /** Inicia el bus de comunicación e intenta la primera conexión. */
    @Synchronized
    fun start() {
        if (isRunning) return
        isRunning = true
        reconnectAttempts = 0
        connect()
    }

    /** Detiene el bus de comunicación y cierra la conexión WebSocket. */
    @Synchronized
    fun stop() {
        if (!isRunning) return
        isRunning = false
        // Cancela cualquier reconexión programada para no dejar un Handler huérfano
        // que reviva la conexión tras stop().
        mainHandler.removeCallbacksAndMessages(null)
        webSocket?.close(1000, "Normal closure")
        webSocket = null
    }

    private fun connect() {
        if (!isRunning) return

        val url = "ws://$host:$PORT"
        LimeLog.info("$TAG: Conectando a $url")

        val builder = Request.Builder().url(url)
        if (pin.isNotEmpty()) {
            builder.header("Authorization", "Bearer $pin")
        }
        val request = builder.build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                LimeLog.info("$TAG: WebSocket abierto con éxito")
                reconnectAttempts = 0
                mainHandler.post { listener.onConnected() }
            }

            override fun onMessage(ws: WebSocket, text: String) {
                try {
                    val json = JSONObject(text)
                    mainHandler.post { listener.onMessageReceived(json) }
                } catch (e: Exception) {
                    LimeLog.warning("$TAG: Error al parsear JSON recibido: ${e.message}")
                }
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                LimeLog.info("$TAG: WebSocket cerrándose: $reason")
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                LimeLog.info("$TAG: WebSocket cerrado: $reason")
                mainHandler.post { listener.onDisconnected() }
                triggerReconnect()
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                LimeLog.warning("$TAG: Fallo en WebSocket: ${t.message}")
                mainHandler.post { listener.onDisconnected() }
                triggerReconnect()
            }
        })
    }

    private fun triggerReconnect() {
        if (!isRunning) return
        webSocket = null
        reconnectAttempts++
        // Backoff exponencial: 2s, 4s, 8s, 16s... hasta un cap de 30s
        val delay = Math.min(2000 * Math.pow(2.0, (reconnectAttempts - 1).toDouble()).toLong(), 30000)
        LimeLog.info("$TAG: Programando reconexión en $delay ms (intento $reconnectAttempts)")
        mainHandler.postDelayed({
            synchronized(this) {
                if (isRunning) {
                    connect()
                }
            }
        }, delay)
    }

    /** Envía un objeto JSON al host PC. Retorna true si se envió con éxito. */
    @Synchronized
    fun sendMessage(json: JSONObject): Boolean {
        return webSocket?.send(json.toString()) ?: false
    }
}
