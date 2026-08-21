package com.limelight.ui.keyboard

import android.content.Context
import android.content.SharedPreferences

/**
 * Persistencia del teclado en SharedPreferences.
 *
 * Responsabilidad única: serializar/deserializar el estado del teclado
 * (escala, transparencia, pestaña activa, favoritos) en un archivo de
 * preferencias aislado. No decide cuándo guardar ni cuándo restaurar;
 * eso lo hace el dueño del ciclo de vida (LogicalKeyboardOverlay).
 */
class KeyboardPersistence(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    // ── Estado del teclado ─────────────────────────────────────────────

    fun saveState(keyboardState: KeyboardState) {
        prefs.edit()
            .putFloat(KEY_SCALE, keyboardState.currentScale)
            .putInt(KEY_ALPHA_I, keyboardState.alphaIndex)
            .putInt(KEY_TAB, keyboardState.currentTab)
            .apply()
    }

    fun restoreState(keyboardState: KeyboardState) {
        keyboardState.currentScale = prefs.getFloat(KEY_SCALE, 1.02f)
        keyboardState.alphaIndex = prefs.getInt(KEY_ALPHA_I, 1)
        keyboardState.currentTab = prefs.getInt(KEY_TAB, 1)
    }

    // ── Favoritos ──────────────────────────────────────────────────────

    fun saveFavorites(labels: List<String>) {
        prefs.edit().putString(KEY_FAVORITES, labels.joinToString(",")).apply()
    }

    fun loadFavorites(): List<String> {
        val raw = prefs.getString(KEY_FAVORITES, "") ?: ""
        return raw.split(",").filter { it.isNotEmpty() }.take(MAX_FAVORITES)
    }

    fun logKeyPress(label: String): List<String> {
        val list = loadFavorites().toMutableList()
        list.remove(label)
        list.add(0, label)
        val trimmed = list.take(MAX_FAVORITES)
        saveFavorites(trimmed)
        return trimmed
    }

    companion object {
        private const val PREF_NAME = "keyboard_overlay_v6"
        private const val KEY_SCALE = "scale"
        private const val KEY_ALPHA_I = "alpha_index"
        private const val KEY_TAB = "tab"
        private const val KEY_FAVORITES = "favorites"
        private const val MAX_FAVORITES = 10
    }
}
