package com.limelight.utils;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

import com.limelight.R;

/**
 * Punto único para aplicar el tema seleccionado por el usuario. Debe llamarse
 * en onCreate() de CADA Activity antes de super.onCreate()/setContentView() para
 * que el tema neón (o el elegido) se aplique en toda la app sin excepción.
 */
public final class ThemeManager {

    private static final String PREFS = "smartdisplay_theme";
    private static final String KEY = "theme";
    private static final String KEY_MODE = "mode";
    private static final String DEFAULT = "universe";
    /** Modo por defecto: oscuro (coherente con la identidad neón del proyecto). */
    private static final String DEFAULT_MODE = "dark";

    public static final String MODE_LIGHT = "light";
    public static final String MODE_DARK = "dark";

    private ThemeManager() {
    }

    public static String getThemeKey(Context context) {
        if (context == null) return DEFAULT;
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            return prefs.getString(KEY, DEFAULT);
        } catch (Exception e) {
            return DEFAULT;
        }
    }

    public static void setThemeKey(Context context, String key) {
        if (context == null) return;
        try {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putString(KEY, key).apply();
        } catch (Exception ignored) {}
    }

    /** Modo actual: "light" u "dark". */
    public static String getMode(Context context) {
        if (context == null) return DEFAULT_MODE;
        try {
            return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getString(KEY_MODE, DEFAULT_MODE);
        } catch (Exception e) {
            return DEFAULT_MODE;
        }
    }

    public static void setMode(Context context, String mode) {
        if (context == null) return;
        try {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putString(KEY_MODE, mode).apply();
        } catch (Exception ignored) {}
        if (MODE_LIGHT.equals(mode)) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        }
    }

    public static boolean isLight(Context context) {
        return MODE_LIGHT.equals(getMode(context));
    }

    /** Resuelve el estilo combinando acento y modo (claro/oscuro). */
    public static int resolveStyle(String key, boolean light) {
        if (key == null) {
            key = DEFAULT;
        }
        switch (key) {
            case "pixel":
                return light ? R.style.Theme_SmartDisplayAI_Pixel_Light : R.style.Theme_SmartDisplayAI_Pixel;
            case "emerald":
                return light ? R.style.Theme_SmartDisplayAI_Emerald_Light : R.style.Theme_SmartDisplayAI_Emerald;
            case "amber":
                return light ? R.style.Theme_SmartDisplayAI_Amber_Light : R.style.Theme_SmartDisplayAI_Amber;
            case "graphite":
                return light ? R.style.Theme_SmartDisplayAI_Graphite_Light : R.style.Theme_SmartDisplayAI_Graphite;
            case "glass":
                return light ? R.style.Theme_SmartDisplayAI_Glass_Light : R.style.Theme_SmartDisplayAI_Glass;
            case "rain":
                return light ? R.style.Theme_SmartDisplayAI_Rain_Light : R.style.Theme_SmartDisplayAI_Rain;
            case "universe":
            default:
                return light ? R.style.Theme_SmartDisplayAI_Universe_Light : R.style.Theme_SmartDisplayAI_Universe;
        }
    }

    /** Aplica el tema (acento + modo) guardado a la Activity. Llamar antes de setContentView(). */
    public static void apply(Activity activity) {
        boolean light = isLight(activity);
        activity.setTheme(resolveStyle(getThemeKey(activity), light));
    }

    /**
     * Color de acento (colorPrimary) del tema elegido por el usuario, resuelto
     * contra su estilo aunque la Activity actual no lo aplique (p. ej. el streaming).
     * Permite que los overlays sigan el tema como el resto de la app.
     */
    public static int accentColor(Context context) {
        String key = getThemeKey(context);
        switch (key) {
            case "pixel":
                return 0xFF4285F4; // Pixel Blue
            case "emerald":
                return 0xFF10B981; // Emerald Green
            case "amber":
                return 0xFFF59E0B; // Amber Orange
            case "graphite":
                return 0xFF94A3B8; // Slate Graphite
            case "glass":
                return 0xFF06B6D4; // Glass Cyan
            case "rain":
                return 0xFF3B82F6; // Electric Rain Blue
            case "universe":
            default:
                return 0xFF8B5CF6; // Universe Purple
        }
    }
}
