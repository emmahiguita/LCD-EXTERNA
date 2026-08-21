package com.limelight.smartdisplay.recovery

import android.os.Handler
import android.os.Looper
import com.limelight.LimeLog
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.wol.WakeOnLanSender
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

/**
 * SmartDisplay AI – Capa 2: Wake-on-LAN Manager
 *
 * Cuando el PC está suspendido y no responde, este manager:
 *   1. Envía el paquete mágico WoL por todos los puertos disponibles
 *   2. Espera activamente (polling TCP al puerto HTTPS del PC) hasta que despierte
 *   3. Notifica al callback cuando el PC está listo para streaming
 *
 * Integración con AutoReconnectManager:
 *   - Antes de iniciar el ciclo de reconexión, se consulta si el PC está
 *     alcanzable vía TCP. Si no responde, se dispara WoL.
 *   - Mientras WoL está en progreso, la UI muestra "Despertando PC…"
 *   - Cuando el PC responde, se procede con la reconexión normal.
 *
 * NO modifica el pipeline de streaming, NvConnection ni MoonBridge.
 */
class WakeOnLanManager(
    private val host: String?,
    private val httpsPort: Int,
    private val macAddress: String?
) {
    companion object {
        private const val TAG = "SD_WakeOnLan"

        /** Puertos TCP que intentamos para detectar que el PC ya despertó. */
        private val PROBE_PORTS = intArrayOf(47984, 47989, 48010)

        /** Intervalo entre intentos de sondeo mientras el PC arranca. */
        private const val PROBE_INTERVAL_MS = 2_000L

        /** Tiempo máximo de espera para que el PC despierte (2 minutos). */
        private const val MAX_WAKE_WAIT_MS = 120_000L

        /** Tiempo mínimo de espera tras enviar WoL antes de empezar a sondear. */
        private const val MIN_BOOT_DELAY_MS = 4_000L
    }

    interface WakeOnLanCallback {
        /** Se llama periódicamente con el progreso: segundos transcurridos, máximo. */
        fun onWaitingForPc(elapsedSeconds: Int, maxSeconds: Int)

        /** El PC respondió: listo para conectar. */
        fun onPcReady()

        /** El PC no despertó en el tiempo máximo. */
        fun onPcUnreachable()
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var callback: WakeOnLanCallback? = null
    @Volatile private var isWaking = false
    private var wolThread: Thread? = null

    /**
     * Intenta despertar el PC vía WoL y espera hasta que responda.
     *
     * @param cb Callback para notificar progreso y resultado.
     * @return true si se inició el proceso WoL (teníamos MAC), false si no se puede.
     */
    fun wakeAndWait(cb: WakeOnLanCallback): Boolean {
        if (isWaking) {
            LimeLog.info("$TAG: Ya hay un proceso WoL en curso – ignorando")
            return false
        }

        val mac = macAddress ?: ""
        if (mac.isEmpty() || mac == "00:00:00:00:00:00") {
            LimeLog.warning("$TAG: Sin dirección MAC – WoL imposible")
            return false
        }

        isWaking = true
        callback = cb

        wolThread = Thread {
            try {
                // 1. Enviar paquete WoL
                val hostName = host ?: ""
                LimeLog.info("$TAG: Enviando paquete WoL a $mac hacia $hostName")
                sendWol()
                mainHandler.post { cb.onWaitingForPc(0, (MAX_WAKE_WAIT_MS / 1000).toInt()) }

                // 2. Esperar tiempo mínimo de arranque
                Thread.sleep(MIN_BOOT_DELAY_MS)

                // 3. Sondear hasta que el PC responda o se agote el tiempo
                val startTime = System.currentTimeMillis()

                while (System.currentTimeMillis() - startTime < MAX_WAKE_WAIT_MS && isWaking) {
                    val elapsedSec = ((System.currentTimeMillis() - startTime) / 1000).toInt()
                    val maxSec = (MAX_WAKE_WAIT_MS / 1000).toInt()

                    mainHandler.post { cb.onWaitingForPc(elapsedSec, maxSec) }

                    if (isPcReachable()) {
                        LimeLog.info("$TAG: PC responde tras ${elapsedSec}s – ¡despierto!")
                        mainHandler.post { cb.onPcReady() }
                        return@Thread
                    }

                    Thread.sleep(PROBE_INTERVAL_MS)
                }

                // 4. Timeout: el PC no despertó
                LimeLog.warning("$TAG: PC no respondió en ${MAX_WAKE_WAIT_MS / 1000}s")
                mainHandler.post { cb.onPcUnreachable() }

            } catch (e: InterruptedException) {
                LimeLog.info("$TAG: Proceso WoL cancelado")
                mainHandler.post { cb.onPcUnreachable() }
            } catch (e: Exception) {
                LimeLog.warning("$TAG: Error en WoL: ${e.message}")
                mainHandler.post { cb.onPcUnreachable() }
            } finally {
                isWaking = false
                wolThread = null
            }
        }.also { wolThread = it }
        wolThread!!.start()

        return true
    }

    /** Cancela el proceso WoL en curso (interrumpe el hilo de sondeo). */
    fun cancel() {
        isWaking = false
        wolThread?.interrupt()
    }

    /** ¿Hay un proceso WoL activo ahora mismo? */
    fun isWakingUp(): Boolean = isWaking

    /** Host/IP del PC (para sondeo TCP desde AutoReconnectManager). */
    fun getHost(): String = host ?: ""

    /** Puerto HTTPS del PC. */
    fun getHttpsPort(): Int = httpsPort

    // ────────────────────────────────────────────────────────────────────────
    // Internos
    // ────────────────────────────────────────────────────────────────────────

    /** Envía el paquete WoL usando la infraestructura existente de Moonlight. */
    private fun sendWol() {
        val computer = ComputerDetails().apply {
            this.macAddress = this@WakeOnLanManager.macAddress ?: ""
            // Usamos localAddress porque WoL va por LAN/subred local
            this.localAddress = ComputerDetails.AddressTuple(host ?: "", httpsPort)
        }

        try {
            WakeOnLanSender.sendWolPacket(computer)
            LimeLog.info("$TAG: Paquete WoL enviado correctamente")
        } catch (e: IOException) {
            // No lanzamos: incluso si algún puerto falla, otros pueden haber funcionado.
            // WakeOnLanSender intenta múltiples puertos. Si CERO paquetes salieron,
            // la excepción vendrá aquí pero el usuario al menos verá que se intentó.
            LimeLog.warning("$TAG: Algunos puertos WoL fallaron: ${e.message}")
        }
    }

    /** Intenta abrir una conexión TCP a cualquiera de los puertos del PC. */
    private fun isPcReachable(): Boolean {
        val hostName = host ?: return false
        if (hostName.isEmpty()) return false
        for (port in PROBE_PORTS) {
            try {
                val sock = Socket()
                sock.connect(InetSocketAddress(hostName, port), 1500)
                sock.close()
                return true
            } catch (_: Exception) {
                // Este puerto no responde — probar el siguiente
            }
        }
        return false
    }
}
