package com.limelight.ui.keyboard

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Controla la fila de favoritos ⭐ que aparece sobre las pestañas del teclado.
 *
 * Lee/escribe el historial de teclas pulsadas via [KeyboardPersistence] y
 * renderiza chips clicables. Al tocar un chip, busca la tecla en el layout
 * activo y ejecuta la acción mediante [onFavoriteAction].
 */
class KeyboardFavoritesController(
    private val context: Context,
    private val persistence: KeyboardPersistence,
    private val onFavoriteAction: (String) -> Unit
) {
    private var favoritesContainer: LinearLayout? = null
    private var favoritesRow: View? = null

    fun setViews(container: LinearLayout?, row: View?) {
        favoritesContainer = container
        favoritesRow = row
    }

    /** Registra una pulsación y actualiza los chips. */
    fun logKeyPress(label: String) {
        if (label.isNotEmpty()) {
            persistence.logKeyPress(label)
            updateFavoritesRow()
        }
    }

    /** Renderiza los chips con las 5 teclas más recientes. */
    fun updateFavoritesRow() {
        val container = favoritesContainer ?: return
        val row = favoritesRow ?: return
        val labels = persistence.loadFavorites().take(5)
        if (labels.isEmpty()) {
            row.visibility = View.GONE
            return
        }
        row.visibility = View.VISIBLE
        container.removeAllViews()
        for (label in labels) {
            val chip = TextView(context)
            chip.text = "⭐ $label"
            chip.setTextColor(-1)
            chip.setTextSize(2, 10f)
            chip.setSingleLine(true)
            chip.setPadding(dpToPx(8f), 0, dpToPx(8f), 0)
            chip.minimumHeight = dpToPx(28f)
            chip.gravity = 17
            chip.setBackgroundResource(com.limelight.R.drawable.key_active_bg)
            chip.isClickable = true
            chip.isFocusable = true
            val lp = LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT, dpToPx(28f))
            lp.marginEnd = dpToPx(4f)
            chip.layoutParams = lp
            chip.setOnClickListener { v ->
                onFavoriteAction(label)
            }
            container.addView(chip)
        }
    }

    private fun dpToPx(dp: Float): Int =
        Math.round(dp * context.resources.displayMetrics.density)
}
