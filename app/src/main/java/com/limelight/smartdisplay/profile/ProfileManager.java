package com.limelight.smartdisplay.profile;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * SmartDisplay AI – Touch Engine 2.0 (FASE 3)
 *
 * Persiste el perfil de interacción elegido por el usuario (vía Quick Actions)
 * y lo publica en {@link ActiveProfile} para que la capa táctil lo aplique.
 */
public class ProfileManager {

    private static final String PREFS_NAME = "sd_interaction_profile";
    private static final String KEY_PROFILE = "active_profile";

    private final SharedPreferences prefs;

    public ProfileManager(Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /** Perfil guardado (AUTO por defecto). */
    public InteractionProfile current() {
        return InteractionProfile.fromId(prefs.getString(KEY_PROFILE, InteractionProfile.AUTO.getId()));
    }

    /** Guarda el perfil y lo publica de inmediato en {@link ActiveProfile}. */
    public void select(InteractionProfile profile) {
        if (profile == null) {
            profile = InteractionProfile.AUTO;
        }
        prefs.edit().putString(KEY_PROFILE, profile.getId()).apply();
        ActiveProfile.set(profile);
    }

    /**
     * Carga el perfil persistido en {@link ActiveProfile}. Llamar al iniciar la
     * sesión de streaming para que el perfil elegido se aplique a los toques.
     */
    public void applyToActive() {
        ActiveProfile.set(current());
    }
}
