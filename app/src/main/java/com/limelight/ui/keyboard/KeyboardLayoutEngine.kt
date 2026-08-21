package com.limelight.ui.keyboard

import android.content.Context
import android.content.res.Configuration
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.limelight.R

/**
 * Constructor del layout de teclas.
 *
 * Responsabilidad única: crear filas (LinearLayout) y teclas (TextView)
 * con su estilo, disposición y listeners. No decide qué hacer cuando se
 * pulsa una tecla — delega en [ActionHandler] para eso.
 */
class KeyboardLayoutEngine(private val context: Context) {

    private enum class KeyStyle { STANDARD, PROFILE }

    private val isPortrait: Boolean
        get() = context.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT

    private val keyHeightDp: Float
        get() = if (isPortrait) 44f else 38f

    private val keyHorizontalMarginDp: Float
        get() = if (isPortrait) 1.5f else 2.0f

    /** Interfaz que define qué hacer cuando se pulsa cada tipo de tecla. */
    interface ActionHandler {
        fun onCharKeyPressed(kd: KeyData)
        fun onSpecialKeyPressed(vkCode: Int)
        fun onModifierToggled(type: String, btn: TextView)
        fun onSpacePressed()
        fun onMacro(vararg vkCodes: Int)
        fun animateKeyPress(v: View)
        fun getConnectionStatus(): Boolean
        fun getCurrentTab(): Int
        fun getTooltip(label: String): String
    }

    data class KeyData(
        val view: TextView,
        val normalLabel: String,
        val shiftedLabel: String,
        val vkCode: Int
    )

    // ── Colecciones compartidas ─────────────────────────────────────────

    val allKeys = ArrayList<KeyData>()
    val allKeyViews = ArrayList<TextView>()
    val keyBackgrounds = ArrayList<android.graphics.drawable.GradientDrawable>()

    var handler: ActionHandler? = null

    // ── Constantes de tecla ─────────────────────────────────────────────

    companion object {
        const val VK_BACK = 8
        const val VK_TAB = 9
        const val VK_RETURN = 13
        const val VK_SHIFT = 16
        const val VK_CONTROL = 17
        const val VK_MENU = 18
        const val VK_CAPITAL = 20
        const val VK_ESCAPE = 27
        const val VK_SPACE = 32
        const val VK_LEFT = 37
        const val VK_UP = 38
        const val VK_RIGHT = 39
        const val VK_DOWN = 40
        const val VK_INSERT = 45
        const val VK_DELETE = 46
        const val VK_SNAPSHOT = 44
        const val VK_APPS = 93
        const val VK_F1 = 112
        const val VK_F2 = 113
        const val VK_F3 = 114
        const val VK_F4 = 115
        const val VK_F5 = 116
        const val VK_F6 = 117
        const val VK_F7 = 118
        const val VK_F8 = 119
        const val VK_F9 = 120
        const val VK_F10 = 121
        const val VK_F11 = 122
        const val VK_F12 = 123
        const val VK_LWIN = 91
        const val VK_HOME = 36
        const val VK_END = 35
        const val VK_PRIOR = 33
        const val VK_NEXT = 34
    }

    // ── Construcción pública ────────────────────────────────────────────

    fun clearAll() {
        allKeys.clear()
        allKeyViews.clear()
        keyBackgrounds.clear()
    }

    // ── Tecla individual ───────────────────────────────────────────────

    private fun createKey(
        label: String,
        weight: Float,
        isSpecial: Boolean,
        style: KeyStyle = KeyStyle.STANDARD
    ): TextView {
        val key = TextView(context)
        key.text = label
        key.gravity = 17
        key.setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.kbd_key_text))
        val targetTextSize = when (style) {
            KeyStyle.PROFILE -> 11.5f
            KeyStyle.STANDARD -> if (isSpecial) 13f else 15.5f
        }
        key.setTextSize(2, targetTextSize)
        key.isSingleLine = style == KeyStyle.STANDARD
        if (style == KeyStyle.PROFILE) {
            key.maxLines = 2
            key.includeFontPadding = false
            key.setLineSpacing(0f, 0.95f)
            key.setPadding(dpToPx(3f), dpToPx(2f), dpToPx(3f), dpToPx(2f))
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            key.setAutoSizeTextTypeUniformWithConfiguration(
                9, targetTextSize.toInt(), 1, android.util.TypedValue.COMPLEX_UNIT_SP)
        }

        // Layout responsive con pesos y márgenes calibrados
        val params = LinearLayout.LayoutParams(0, dpToPx(keyHeightDp), weight)
        val m = dpToPx(keyHorizontalMarginDp)
        params.marginStart = m
        params.marginEnd = m
        key.layoutParams = params

        // Jerarquía: teclas de función/modificador con fondo más oscuro
        val fillColor = androidx.core.content.ContextCompat.getColor(
            context, if (isSpecial) R.color.kbd_key_special_bg else R.color.kbd_key_bg)
        val strokeColor = androidx.core.content.ContextCompat.getColor(context, R.color.kbd_key_stroke)
        val rippleColor = androidx.core.content.ContextCompat.getColor(context, R.color.kbd_key_ripple)
        val gd = android.graphics.drawable.GradientDrawable()
        gd.setColor(fillColor)
        gd.cornerRadius = dpToPx(if (isPortrait) 6f else 5f).toFloat()
        gd.setStroke(dpToPx(1f), strokeColor)
        val ripple = android.graphics.drawable.RippleDrawable(
            android.content.res.ColorStateList.valueOf(rippleColor), gd, gd)
        key.background = ripple
        keyBackgrounds.add(gd)
        key.isClickable = true
        key.isFocusable = true
        allKeyViews.add(key)
        key.minWidth = 0
        key.minHeight = 0
        return key
    }

    // ── Fábrica de filas ───────────────────────────────────────────────

    fun createRow(weightSum: Float): LinearLayout {
        val row = LinearLayout(context)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = 16
        row.weightSum = weightSum
        val params = LinearLayout.LayoutParams(-1, -2)
        params.bottomMargin = dpToPx(if (isPortrait) 3.5f else 2.5f)
        row.layoutParams = params
        return row
    }

    // ── Constructores de teclas ─────────────────────────────────────────

    fun addCharKey(row: LinearLayout, normal: String, shifted: String, vkCode: Int, weight: Float) {
        val key = createKey(normal, weight, false)
        val kd = KeyData(key, normal, shifted, vkCode)
        allKeys.add(kd)
        key.setOnClickListener { v ->
            handler?.let { h ->
                h.animateKeyPress(v)
                h.onCharKeyPressed(kd)
            }
        }
        row.addView(key)
    }

    fun addSpecialKey(row: LinearLayout, label: String, vkCode: Int, weight: Float, tooltip: String) {
        val key = createKey(label, weight, true)
        val isNormalTab = handler?.getCurrentTab() == 1
        key.setOnClickListener { v ->
            handler?.let { h ->
                h.animateKeyPress(v)
                h.onSpecialKeyPressed(vkCode)
                if (!isNormalTab) {
                    android.widget.Toast.makeText(context, tooltip, android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
        key.setOnLongClickListener {
            android.widget.Toast.makeText(context, tooltip, android.widget.Toast.LENGTH_SHORT).show()
            true
        }
        row.addView(key)
    }

    fun addModifierKey(row: LinearLayout, label: String, weight: Float, type: String): TextView {
        val key = createKey(label, weight, true)
        key.setOnClickListener { v ->
            handler?.let { h ->
                h.animateKeyPress(v)
                h.onModifierToggled(type, key)
            }
        }
        key.setOnLongClickListener {
            val tooltip = when (type) {
                "ctrl" -> "Ctrl — atajos del sistema (copiar/pegar, VS Code, Android Studio, Explorer)"
                "shift" -> "Shift — selección múltiple"
                "alt" -> "Alt — acceso a menús"
                else -> type
            }
            android.widget.Toast.makeText(context, tooltip, android.widget.Toast.LENGTH_SHORT).show()
            true
        }
        row.addView(key)
        return key
    }

    /** Tecla de espacio. [weight] permite ajustar su tamaño relativo. */
    fun addSpaceKey(row: LinearLayout, weight: Float = 8.0f) {
        val key = createKey("␣", weight, false)
        key.setOnClickListener { v ->
            handler?.let { h ->
                h.animateKeyPress(v)
                h.onSpacePressed()
            }
        }
        row.addView(key)
    }

    fun addMacroKey(row: LinearLayout, label: String, weight: Float, vararg vkCodes: Int) {
        val key = createKey(label, weight, true)
        key.setOnClickListener { v ->
            handler?.let { h ->
                h.animateKeyPress(v)
                h.onMacro(*vkCodes)
                val tip = h.getTooltip(label)
                android.widget.Toast.makeText(context,
                    "$label — $tip", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
        key.setOnLongClickListener {
            handler?.let { h ->
                val tip = h.getTooltip(label)
                android.widget.Toast.makeText(context,
                    "$label — $tip", android.widget.Toast.LENGTH_SHORT).show()
            }
            true
        }
        row.addView(key)
    }

    // ── Renders de filas (QWERTY) ──────────────────────────────────────

    fun buildNormalKeyboard(container: LinearLayout, btnShiftL: TextView?, btnShiftR: TextView?,
                            btnCtrl: TextView?, btnAlt: TextView?) {
        container.addView(buildNumberRow())
        container.addView(buildQwertyRow())
        container.addView(buildHomeRow())
        container.addView(buildShiftRow(btnShiftL, btnShiftR))
        container.addView(buildSpaceRow(btnCtrl, btnAlt))
    }

    /**
     * Fila 1: backtick, 1-0, guion, igual y ⌫ ancho.
     * weightSum=15: ` ×1.0 + 12×1.0 + ⌫×2.0
     */
    private fun buildNumberRow(): View {
        val row = createRow(15.0f)
        addCharKey(row, "`", "~", 192, 1.0f)   // VK_OEM_3 — necesario en terminal/regex
        val n = arrayOf("1","2","3","4","5","6","7","8","9","0","-","=")
        val s = arrayOf("!","@","#","$","%","^","&","*","(",")", "_","+")
        val vk = intArrayOf(49,50,51,52,53,54,55,56,57,48,189,187)
        for (i in n.indices) addCharKey(row, n[i], s[i], vk[i], 1.0f)
        addSpecialKey(row, "⌫", VK_BACK, 2.0f, "Borrar")
        return row
    }

    /**
     * Fila 2: Tab ancho + Q..P + [ ] + barra invertida.
     * weightSum=15: Tab×2.0 + 12×1.0 + \×1.0
     */
    private fun buildQwertyRow(): View {
        val row = createRow(15.0f)
        addSpecialKey(row, "⇥", VK_TAB, 2.0f, "Tab")
        val n = arrayOf("Q","W","E","R","T","Y","U","I","O","P","[","]")
        val s = arrayOf("Q","W","E","R","T","Y","U","I","O","P","{","}")
        val vk = intArrayOf(81,87,69,82,84,89,85,73,79,80,219,221)
        for (i in n.indices) addCharKey(row, n[i], s[i], vk[i], 1.0f)
        addCharKey(row, "\\", "|", 220, 1.0f)   // VK_OEM_5 — rutas, pipes
        return row
    }

    /** Fila 3: A..L + ; ' + ⏎. weightSum=15: 11×1.0 + ⏎×4.0 */
    private fun buildHomeRow(): View {
        val row = createRow(15.0f)
        val n = arrayOf("A","S","D","F","G","H","J","K","L",";","'")
        val s = arrayOf("A","S","D","F","G","H","J","K","L",":",'"'.toString())
        val vk = intArrayOf(65,83,68,70,71,72,74,75,76,186,222)
        for (i in n.indices) addCharKey(row, n[i], s[i], vk[i], 1.0f)
        addSpecialKey(row, "⏎", VK_RETURN, 4.0f, "Enter")
        return row
    }

    /**
     * Fila 4: Shift amplio a ambos lados, sin spacer vacío a la derecha.
     * weightSum=15: ⇧×2.5 + 10×1.0 + ⇧×2.5
     */
    private fun buildShiftRow(btnShiftL: TextView?, btnShiftR: TextView?): View {
        val row = createRow(15.0f)
        addModifierKey(row, "⇧", 2.5f, "shift")
        val n = arrayOf("Z","X","C","V","B","N","M",",",".","/")
        val s = arrayOf("Z","X","C","V","B","N","M","<",">","?")
        val vk = intArrayOf(90,88,67,86,66,78,77,188,190,191)
        for (i in n.indices) addCharKey(row, n[i], s[i], vk[i], 1.0f)
        addModifierKey(row, "⇧", 2.5f, "shift")
        return row
    }

    /**
     * Fila 5: Ctrl y Alt más anchos, espacio generoso, 4 flechas iguales.
     * Sin Home/End (ya están en tab DEV).
     * weightSum=15: Ctrl×2.0 + Alt×2.0 + Space×7.0 + 4×1.0
     */
    private fun buildSpaceRow(btnCtrl: TextView?, btnAlt: TextView?): View {
        val row = createRow(15.0f)
        addModifierKey(row, "Ctrl", 2.0f, "ctrl")
        addModifierKey(row, "Alt", 2.0f, "alt")
        addSpaceKey(row, 7.0f)
        addSpecialKey(row, "←", VK_LEFT,  1.0f, "Izquierda")
        addSpecialKey(row, "↑", VK_UP,    1.0f, "Arriba")
        addSpecialKey(row, "↓", VK_DOWN,  1.0f, "Abajo")
        addSpecialKey(row, "→", VK_RIGHT, 1.0f, "Derecha")
        return row
    }

    // ── Renders de pestañas ────────────────────────────────────────────

    fun buildShortcutsKeyboard(container: LinearLayout) {
        val r1 = createRow(4.0f)
        addMacroKey(r1, "Alt+Tab", 1.0f, VK_MENU, VK_TAB)
        addMacroKey(r1, "Alt+F4", 1.0f, VK_MENU, VK_F4)
        addMacroKey(r1, "Win+D", 1.0f, VK_LWIN, 68)
        addMacroKey(r1, "Win+E", 1.0f, VK_LWIN, 69)
        container.addView(r1)
        val r2 = createRow(4.0f)
        addMacroKey(r2, "Ctrl+C", 1.0f, VK_CONTROL, 67)
        addMacroKey(r2, "Ctrl+V", 1.0f, VK_CONTROL, 86)
        addMacroKey(r2, "Ctrl+X", 1.0f, VK_CONTROL, 88)
        addMacroKey(r2, "Ctrl+Z", 1.0f, VK_CONTROL, 90)
        container.addView(r2)
        val r3 = createRow(4.0f)
        addMacroKey(r3, "Ctrl+Y", 1.0f, VK_CONTROL, 89)
        addMacroKey(r3, "Ctrl+S", 1.0f, VK_CONTROL, 83)
        addMacroKey(r3, "Ctrl+F", 1.0f, VK_CONTROL, 70)
        addMacroKey(r3, "Ctrl+A", 1.0f, VK_CONTROL, 65)
        container.addView(r3)
        val r4 = createRow(4.0f)
        addMacroKey(r4, "Ctrl+Sh+P", 1.0f, VK_CONTROL, VK_SHIFT, 80)
        addMacroKey(r4, "Ctrl+Alt+L", 1.0f, VK_CONTROL, VK_MENU, 76)
        addMacroKey(r4, "Shift+F10", 1.0f, VK_SHIFT, VK_F10)
        addMacroKey(r4, "Ctrl+Sh+Esc", 1.0f, VK_CONTROL, VK_SHIFT, VK_ESCAPE)
        container.addView(r4)
    }

    fun buildProfiledDevKeyboard(container: LinearLayout, keys: List<com.limelight.ui.keyboard.DevKey>) {
        val cols = when {
            isPortrait -> 4
            keys.size <= 8 -> 4
            else -> 6
        }
        // Antigravity tiene 18 acciones: en vertical 4/4/4/3/3 evita una
        // última fila de dos teclas desproporcionadamente grandes.
        val rows = if (isPortrait && keys.size % cols == 2 && keys.size > cols) {
            keys.dropLast(6).chunked(cols) + keys.takeLast(6).chunked(3)
        } else {
            keys.chunked(cols)
        }
        for (rowKeys in rows) {
            val row = createRow(rowKeys.size.toFloat())
            for (dk in rowKeys) {
                val isMacro = dk.vkCodes.size > 1
                val key = createKey(profileDisplayLabel(dk.label), 1.0f, true, KeyStyle.PROFILE)
                key.setOnClickListener { v ->
                    handler?.let { h ->
                        h.animateKeyPress(v)
                        if (isMacro) {
                            h.onMacro(*dk.vkCodes.toIntArray())
                        } else if (dk.vkCodes.isNotEmpty()) {
                            h.onSpecialKeyPressed(dk.vkCodes[0])
                        }
                        Toast.makeText(context, dk.tooltip, Toast.LENGTH_SHORT).show()
                    }
                }
                key.setOnLongClickListener {
                    Toast.makeText(context, dk.tooltip, Toast.LENGTH_SHORT).show()
                    true
                }
                setKeyIcon(key, dk.label)
                row.addView(key)
            }
            container.addView(row)
        }
    }

    private fun profileDisplayLabel(label: String): String = when (label) {
        "Quick Open" -> "Quick\nOpen"
        "AI Chat" -> "AI\nChat"
        "AI Edit" -> "AI\nEdit"
        "Close All" -> "Close\nAll"
        else -> label
    }

    private fun setKeyIcon(key: TextView, label: String) {
        val iconRes = when (label) {
            "AI Chat"    -> R.drawable.ic_antigravity_chat
            "Search"     -> R.drawable.ic_search
            "Explorer"   -> R.drawable.ic_files_overlay
            "Palette"    -> R.drawable.ic_palette
            "Close All"  -> R.drawable.ic_win_close
            "Terminal"   -> R.drawable.ic_terminal
            "Git"        -> R.drawable.ic_github
            "Run"        -> R.drawable.ic_play
            else         -> 0
        }
        if (iconRes != 0) {
            val drawable = context.getDrawable(iconRes)
            if (drawable != null) {
                val size = dpToPx(18f)
                drawable.setBounds(0, 0, size, size)
                key.setCompoundDrawables(null, drawable, null, null)
                key.compoundDrawablePadding = dpToPx(1f)
                key.gravity = 17
            }
        }
    }

    // ── Utilidad ───────────────────────────────────────────────────────

    fun dpToPx(dp: Float): Int =
        Math.round(dp * context.resources.displayMetrics.density)
}
