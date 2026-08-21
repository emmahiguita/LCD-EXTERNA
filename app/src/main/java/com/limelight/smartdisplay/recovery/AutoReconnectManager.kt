package com.limelight.smartdisplay.recovery

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.limelight.LimeLog

/**
 * SmartDisplay AI – Capa 2: Auto-Reconnect Manager
 *
 * Orquesta la reconexión automática tras una desconexión inesperada.
 * Opera completamente fuera de Moonlight: nunca modifica NvConnection,
 * MoonBridge ni el pipeline de video/audio.
 *
 * Flujo:
 *   1. Game.java notifica que Moonlight terminó inesperadamente →
 *      {@link #onConnectionTerminated(int)}.
 *   2. Si hay sesión guardada y la causa NO es una terminación voluntaria,
 *      se activa el modo reconexión: muestra la UI "Reconectando…" y espera.
 *   3. Si el PC parece estar suspendido (no responde TCP) Y hay MAC address,
 *      se dispara Wake-on-LAN automáticamente antes de reintentar.
 *      La UI muestra "Despertando PC…" con contador de segundos.
 *   4. Cuando NetworkMonitor reporta red disponible o el PC despierta vía WoL,
 *      se espera un breve delay (para dejar estabilizar la red) y se llama al
 *      callback {@link ReconnectCallback#doReconnect()}, que Game.java implementa
 *      relanzando conn.start() sobre la superficie existente.
 *   5. Cada intento respeta un backoff exponencial con un máximo de
 *      {@value MAX_RETRY_COUNT} intentos.
 *
 * Terminación voluntaria (ML_ERROR_GRACEFUL_TERMINATION) → NO reconecta.
 */
class AutoReconnectManager(context: Context, private val callback: ReconnectCallback) : NetworkMonitor.NetworkChangeListener {
    companion object {
        private const val TAG = "SD_AutoReconnect"

        /** Código de Moonlight para cierre voluntario (no reconectar). */
        const val ML_ERROR_GRACEFUL_TERMINATION = 0

        private const val MAX_RETRY_COUNT = 8
        /** Delay base (ms) entre intentos – se duplica con backoff. */
        private const val BASE_RETRY_DELAY_MS = 3_000
        /** Delay adicional tras detectar red disponible (ms). */
        private const val NETWORK_STABLE_DELAY_MS = 1_500
        private const val MAX_RETRY_DELAY_MS = 30_000
        /**
         * Tras reconectar, el contador de intentos no se resetea hasta que la
         * conexión se haya mantenido estable durante este lapso. Evita que una
         * red intermitente muestre "Reconectando 1/8" repetidas veces: una
         * caída dentro de este periodo se cuenta como continuación del ciclo
         * anterior (verás "2/8", "3/8"…) y respeta el backoff y el límite.
         */
        private const val STABLE_CONNECTION_GRACE_MS = 15_000L
    }

    /**
     * Implementado por Game.java para relanzar la sesión de streaming
     * sin cambiar de actividad ni modificar la lógica de Moonlight.
     */
    interface ReconnectCallback {
        /** Se llama cuando el Manager decide que hay que reconectar. */
        fun doReconnect()
        /** Actualiza la UI de "Reconectando… intento N/M". */
        fun showReconnectingUI(attempt: Int, maxAttempts: Int)
        /** Oculta la UI de reconexión (conexión restaurada). */
        fun hideReconnectingUI()
        /** Abandona completamente (demasiados intentos fallidos). */
        fun onReconnectFailed()
        /**
         * Actualiza la UI mientras se espera que el PC despierte vía WoL.
         * @param elapsedSeconds segundos transcurridos desde que se envió WoL
         * @param maxSeconds tiempo máximo de espera
         */
        fun showWakeOnLanUI(elapsedSeconds: Int, maxSeconds: Int) {}
        /** Oculta la UI de WoL (PC despertó o timeout). */
        fun hideWakeOnLanUI() {}
    }

    // ── Estado ───────────────────────────────────────────────────────────────

    private val networkMonitor: NetworkMonitor = NetworkMonitor(context, this)
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Handler separado para el runnable de confirmación de estabilidad. Lo
     * mantenemos aparte porque cancelPendingReconnect() limpia mainHandler con
     * removeCallbacksAndMessages(null), y no queremos que un onNetworkLost()
     * transitorio durante la ventana de gracia borre el reset legítimo.
     */
    private val stableHandler = Handler(Looper.getMainLooper())

    private var reconnecting = false
    private var networkReady = false
    private var retryCount = 0

    private var pendingReconnectRunnable: Runnable? = null

    // ── Wake-on-LAN ──────────────────────────────────────────────────────────
    /** Manager WoL opcional. Se setea desde Game.java tras construir el manager. */
    private var wolManager: WakeOnLanManager? = null
    /** Si true, el primer intento de reconexión intentará WoL si el PC no responde. */
    private var wolAttempted = false

    /**
     * Token monótono para invalidar runnables de confirmación de estabilidad
     * pendientes cuando ocurre una nueva desconexión antes de que se cumpla
     * STABLE_CONNECTION_GRACE_MS. El runnable comprueba el token al ejecutarse.
     */
    private var stableConfirmToken = 0

    // ────────────────────────────────────────────────────────────────────────
    // Ciclo de vida
    // ────────────────────────────────────────────────────────────────────────

    /** Inicia el monitor de red. Llamar en onCreate() o surfaceCreated(). */
    fun start() {
        networkMonitor.start()
    }

    /** Libera recursos. Llamar en onDestroy(). */
    fun stop() {
        networkMonitor.stop()
        cancelPendingReconnect()
        stableHandler.removeCallbacksAndMessages(null)
        wolManager?.cancel()
    }

    // ────────────────────────────────────────────────────────────────────────
    // API pública – llamada desde Game.java
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Notifica que Moonlight terminó la sesión.
     *
     * @param errorCode código de error de MoonBridge. Si es
     *                  {@value ML_ERROR_GRACEFUL_TERMINATION} se asume que
     *                  el usuario cerró voluntariamente y NO se reconecta.
     * @return true si el Manager tomará el control (reconectará),
     *         false si Game.java debe seguir su flujo normal (mostrar error/finish).
     */
    fun onConnectionTerminated(errorCode: Int): Boolean {
        // Invalidar cualquier reset de contador pendiente: una nueva caída
        // significa que la conexión no era estable.
        stableConfirmToken++

        if (errorCode == ML_ERROR_GRACEFUL_TERMINATION) {
            LimeLog.info("$TAG: Terminación voluntaria – no reconectar")
            return false
        }

        if (retryCount >= MAX_RETRY_COUNT) {
            LimeLog.warning("$TAG: Máximo de intentos alcanzado")
            return false
        }

        LimeLog.info("$TAG: Desconexión inesperada (code=$errorCode) – iniciando reconexión")
        reconnecting = true
        networkReady = networkMonitor.isNetworkAvailable()

        callback.showReconnectingUI(retryCount + 1, MAX_RETRY_COUNT)

        if (networkReady) {
            scheduleReconnectAttempt()
        } else {
            LimeLog.info("$TAG: Sin red – esperando NetworkMonitor…")
        }

        return true // Tomamos el control: Game.java no debe mostrar el diálogo de error
    }

    /**
     * Notifica que Moonlight restableció la conexión exitosamente.
     * Resetea el contador de intentos.
     */
    fun onConnectionEstablished() {
        LimeLog.info("$TAG: Conexión establecida – overlay oculto, confirmando estabilidad en ${STABLE_CONNECTION_GRACE_MS / 1000}s")
        cancelPendingReconnect()
        callback.hideReconnectingUI()

        // No reseteamos retryCount aún. Si la conexión cae dentro de la ventana
        // de gracia, Game.java verá isReconnecting()==true y la nueva caída se
        // contará como continuación del ciclo (con backoff y límite máximo),
        // evitando ver "Reconectando 1/8" varias veces en redes intermitentes.
        val myToken = ++stableConfirmToken
        stableHandler.postDelayed({
            if (myToken == stableConfirmToken) {
                LimeLog.info("$TAG: Estabilidad confirmada – reset de contador")
                reconnecting = false
                retryCount = 0
            }
        }, STABLE_CONNECTION_GRACE_MS)
    }

    /**
     * Notifica que una etapa de Moonlight falló durante el intento de reconexión.
     * Incrementa el contador e intenta de nuevo con backoff.
     *
     * @param errorCode código del error de stageFailed/connectionTerminated.
     */
    fun onReconnectAttemptFailed(errorCode: Int) {
        if (!reconnecting) return

        // Invalidar cualquier reset de contador pendiente: si veníamos de un
        // onConnectionEstablished() con periodo de gracia y la conexión volvió
        // a caer, NO queremos que el reset programado se ejecute.
        stableConfirmToken++

        retryCount++
        LimeLog.warning("$TAG: Intento $retryCount fallido (code=$errorCode)")

        if (retryCount >= MAX_RETRY_COUNT) {
            reconnecting = false
            callback.onReconnectFailed()
            return
        }

        callback.showReconnectingUI(retryCount + 1, MAX_RETRY_COUNT)

        if (networkMonitor.isNetworkAvailable()) {
            scheduleReconnectAttempt()
        }
        // Si no hay red, esperamos a onNetworkAvailable()
    }

    /** Cancela el proceso de reconexión (usuario decidió salir). */
    fun cancelReconnect() {
        reconnecting = false
        retryCount = 0
        wolAttempted = false
        stableConfirmToken++
        cancelPendingReconnect()
        wolManager?.cancel()
    }

    /** ¿Hay un proceso de reconexión activo? */
    fun isReconnecting(): Boolean = reconnecting

    /** ¿Hay un proceso WoL activo ahora mismo? */
    fun isWakingUp(): Boolean = wolManager?.isWakingUp() == true

    /**
     * Inyecta el manager de Wake-on-LAN. Si el PC no responde durante la
     * reconexión y tenemos MAC address, se intentará despertarlo vía WoL.
     * Llamar desde Game.java después de construir este manager.
     */
    fun setWakeOnLan(manager: WakeOnLanManager?) {
        wolManager = manager
        wolAttempted = false
    }

    // ────────────────────────────────────────────────────────────────────────
    // NetworkMonitor.NetworkChangeListener
    // ────────────────────────────────────────────────────────────────────────

    override fun onNetworkLost() {
        LimeLog.info("$TAG: Red perdida")
        networkReady = false
        cancelPendingReconnect()
    }

    override fun onNetworkAvailable() {
        LimeLog.info("$TAG: Red disponible")
        networkReady = true

        if (reconnecting) {
            // Pequeño delay para dejar que la ruta de red se estabilice
            mainHandler.postDelayed({
                if (reconnecting && networkReady) {
                    scheduleReconnectAttempt()
                }
            }, NETWORK_STABLE_DELAY_MS.toLong())
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // Internos
    // ────────────────────────────────────────────────────────────────────────

    private fun scheduleReconnectAttempt() {
        cancelPendingReconnect()

        // ── Wake-on-LAN: si es el primer intento y el PC podría estar suspendido ──
        if (retryCount == 0 && !wolAttempted && wolManager != null &&
            !isPcReachableForWoL()) {
            LimeLog.info("$TAG: PC no responde – intentando Wake-on-LAN antes de reconectar")
            wolAttempted = true
            val started = wolManager!!.wakeAndWait(object : WakeOnLanManager.WakeOnLanCallback {
                override fun onWaitingForPc(elapsedSeconds: Int, maxSeconds: Int) {
                    callback.showWakeOnLanUI(elapsedSeconds, maxSeconds)
                }

                override fun onPcReady() {
                    LimeLog.info("$TAG: PC despertó vía WoL – procediendo con reconexión")
                    callback.hideWakeOnLanUI()
                    doActualReconnect()
                }

                override fun onPcUnreachable() {
                    LimeLog.warning("$TAG: WoL sin respuesta – reintentando con backoff normal")
                    callback.hideWakeOnLanUI()
                    doActualReconnect()
                }
            })
            if (!started) {
                // No se pudo iniciar WoL (sin MAC, etc.) – continuar normal
                doActualReconnect()
            }
            return
        }
        // ─────────────────────────────────────────────────────────────────────

        doActualReconnect()
    }

    /** Programa el intento real de reconexión con backoff exponencial. */
    private fun doActualReconnect() {
        cancelPendingReconnect()

        // Backoff exponencial: 3s, 6s, 12s … capped en 30s
        val delay = Math.min(BASE_RETRY_DELAY_MS * Math.pow(2.0, retryCount.toDouble()).toLong(), MAX_RETRY_DELAY_MS.toLong())
        LimeLog.info("$TAG: Reconectando en $delay ms (intento ${retryCount + 1})")

        pendingReconnectRunnable = Runnable {
            if (reconnecting && networkReady) {
                LimeLog.info("$TAG: Ejecutando reconexión…")
                callback.doReconnect()
            }
        }

        mainHandler.postDelayed(pendingReconnectRunnable!!, delay)
    }

    /**
     * Comprueba rápidamente si el PC responde en alguno de sus puertos TCP.
     * Se usa para decidir si intentar WoL antes de la reconexión.
     * NO bloquea: si el PC tarda >1.5s en responder, asumimos que no está.
     */
    private fun isPcReachableForWoL(): Boolean {
        val wm = wolManager ?: return true // sin WoL manager = asumir que está OK
        return try {
            val sock = java.net.Socket()
            // Probamos el puerto HTTPS (47984) con un timeout corto
            sock.connect(java.net.InetSocketAddress(
                wm.getHost(), wm.getHttpsPort()), 1500)
            sock.close()
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun cancelPendingReconnect() {
        // Cancela TODO lo pendiente en el handler: tanto el intento de reconexión
        // como el runnable de "red estable" de onNetworkAvailable (que antes
        // quedaba huérfano). Los runnables revalidan flags antes de actuar.
        mainHandler.removeCallbacksAndMessages(null)
        pendingReconnectRunnable = null
    }
}
