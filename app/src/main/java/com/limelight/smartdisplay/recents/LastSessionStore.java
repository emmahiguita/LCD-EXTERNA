package com.limelight.smartdisplay.recents;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * SmartDisplay AI – UX Layer (FASE 2): Última sesión
 *
 * Almacén ligero y persistente del último stream lanzado, usado para mostrar
 * la tarjeta "Última sesión" en la pantalla principal y permitir relanzarlo
 * con un toque.
 *
 * A diferencia de {@code SessionRecoveryManager} (que se limpia al cerrar
 * voluntariamente), este registro persiste hasta que se lanza otra sesión,
 * de modo que el acceso rápido siga disponible tras un cierre normal.
 *
 * No interactúa con Moonlight: solo lee/escribe SharedPreferences.
 */
public class LastSessionStore {

    private static final String PREFS_NAME = "sd_last_session";
    private static final String KEY_PC_UUID  = "pc_uuid";
    private static final String KEY_PC_NAME  = "pc_name";
    private static final String KEY_APP_NAME = "app_name";
    private static final String KEY_APP_ID   = "app_id";
    private static final String KEY_APP_HDR  = "app_hdr";
    private static final String KEY_SAVED_AT = "saved_at";
    private static final String KEY_HAS      = "has_last";

    private final SharedPreferences prefs;

    public LastSessionStore(Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void record(String pcUuid, String pcName,
                       String appName, int appId, boolean appHdr) {
        if (pcUuid == null || pcUuid.isEmpty()) {
            return;
        }
        prefs.edit()
                .putString (KEY_PC_UUID,  pcUuid)
                .putString (KEY_PC_NAME,  pcName != null ? pcName : "")
                .putString (KEY_APP_NAME, appName != null ? appName : "")
                .putInt    (KEY_APP_ID,   appId)
                .putBoolean(KEY_APP_HDR,  appHdr)
                .putLong   (KEY_SAVED_AT, System.currentTimeMillis())
                .putBoolean(KEY_HAS,      true)
                .apply();
    }

    public boolean has()        { return prefs.getBoolean(KEY_HAS, false); }
    public String  getPcUuid()  { return prefs.getString(KEY_PC_UUID, ""); }
    public String  getPcName()  { return prefs.getString(KEY_PC_NAME, ""); }
    public String  getAppName() { return prefs.getString(KEY_APP_NAME, ""); }
    public int     getAppId()   { return prefs.getInt(KEY_APP_ID, -1); }
    public boolean getAppHdr()  { return prefs.getBoolean(KEY_APP_HDR, false); }

    public void clear() {
        prefs.edit().putBoolean(KEY_HAS, false).apply();
    }
}
