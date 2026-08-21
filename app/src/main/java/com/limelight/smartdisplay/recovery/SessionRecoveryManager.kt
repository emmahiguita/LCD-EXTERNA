package com.limelight.smartdisplay.recovery

import android.content.Context
import android.content.SharedPreferences

/**
 * SmartDisplay AI – Capa 2: Session Recovery
 *
 * Guarda el estado mínimo de la sesión activa (host, puerto, uniqueId, appId,
 * appName, httpsPort, serverCert) para poder relanzarla de forma silenciosa
 * tras una desconexión inesperada.
 *
 * NO modifica ni sustituye ninguna lógica interna de Moonlight/NvConnection.
 * Actúa únicamente como almacén auxiliar de Intent extras.
 */
class SessionRecoveryManager(private val context: Context) {
    companion object {
        private const val PREFS_NAME = "sd_session_recovery"
        private const val KEY_HOST = "host"
        private const val KEY_PORT = "port"
        private const val KEY_HTTPS_PORT = "https_port"
        private const val KEY_APP_NAME = "app_name"
        private const val KEY_APP_ID = "app_id"
        private const val KEY_UNIQUE_ID = "unique_id"
        private const val KEY_PC_UUID = "pc_uuid"
        private const val KEY_PC_NAME = "pc_name"
        private const val KEY_APP_HDR = "app_hdr"
        private const val KEY_MAC_ADDRESS = "mac_address"
        private const val KEY_HAS_SESSION = "has_session"
        private const val KEY_SAVED_AT = "saved_at"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ────────────────────────────────────────────────────────────────────────
    // Guardar sesión
    // ────────────────────────────────────────────────────────────────────────

    fun saveSession(
        host: String?, port: Int, httpsPort: Int,
        appName: String?, appId: Int,
        uniqueId: String?, pcUuid: String?,
        pcName: String?, appHdr: Boolean,
        macAddress: String = ""
    ) {
        prefs.edit()
            .putString(KEY_HOST, host ?: "")
            .putInt(KEY_PORT, port)
            .putInt(KEY_HTTPS_PORT, httpsPort)
            .putString(KEY_APP_NAME, appName ?: "")
            .putInt(KEY_APP_ID, appId)
            .putString(KEY_UNIQUE_ID, uniqueId ?: "")
            .putString(KEY_PC_UUID, pcUuid ?: "")
            .putString(KEY_PC_NAME, pcName ?: "")
            .putBoolean(KEY_APP_HDR, appHdr)
            .putString(KEY_MAC_ADDRESS, macAddress)
            .putBoolean(KEY_HAS_SESSION, true)
            .putLong(KEY_SAVED_AT, System.currentTimeMillis())
            .apply()
    }

    // ────────────────────────────────────────────────────────────────────────
    // Leer sesión
    // ────────────────────────────────────────────────────────────────────────

    fun hasSession(): Boolean = prefs.getBoolean(KEY_HAS_SESSION, false)

    fun getHost(): String? = prefs.getString(KEY_HOST, null)
    fun getPort(): Int = prefs.getInt(KEY_PORT, 47989)
    fun getHttpsPort(): Int = prefs.getInt(KEY_HTTPS_PORT, 0)
    fun getAppName(): String = prefs.getString(KEY_APP_NAME, "") ?: ""
    fun getAppId(): Int = prefs.getInt(KEY_APP_ID, -1)
    fun getUniqueId(): String = prefs.getString(KEY_UNIQUE_ID, "") ?: ""
    fun getPcUuid(): String = prefs.getString(KEY_PC_UUID, "") ?: ""
    fun getPcName(): String = prefs.getString(KEY_PC_NAME, "") ?: ""
    fun getAppHdr(): Boolean = prefs.getBoolean(KEY_APP_HDR, false)
    fun getMacAddress(): String = prefs.getString(KEY_MAC_ADDRESS, "") ?: ""
    fun getSavedAtMillis(): Long = prefs.getLong(KEY_SAVED_AT, 0L)

    /**
     * @return true si la sesión guardada es reciente (dentro de {@code maxAgeMs}).
     *         Evita ofrecer reanudar sesiones obsoletas tras un cierre lejano.
     */
    fun isRecent(maxAgeMs: Long): Boolean {
        val savedAt = getSavedAtMillis()
        if (savedAt <= 0L) return false
        val age = System.currentTimeMillis() - savedAt
        return age >= 0 && age <= maxAgeMs
    }

    // ────────────────────────────────────────────────────────────────────────
    // Limpiar sesión (al cerrar voluntariamente)
    // ────────────────────────────────────────────────────────────────────────

    fun clearSession() {
        prefs.edit().putBoolean(KEY_HAS_SESSION, false).apply()
    }
}
