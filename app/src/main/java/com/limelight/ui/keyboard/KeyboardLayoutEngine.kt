package com.limelight.ui.keyboard

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.util.TypedValue
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.limelight.R

class KeyboardLayoutEngine(
    private val context: Context
) {
    private enum class KeyStyle {
        STANDARD,
        PROFILE
    }

    private val isPortrait: Boolean
        get() = context.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT

    private val keyHeightDp: Float
        get() = if (isPortrait) 44f else 38f

    private val keyHorizontalMarginDp: Float
        get() = if (isPortrait) 1.5f else 2.0f

    interface ActionHandler {
        fun onCharKeyPressed(kd: KeyData)
        fun onSpecialKeyPressed(vkCode: Int)
        fun onModifierToggled(type: String, btn: TextView)
        fun onSpacePressed()
        fun onRemoteAction(action: RemoteKeyAction)
        fun animateKeyPress(v: View)
        fun getConnectionStatus(): Boolean
        fun getCurrentTab(): Int
    }

    data class KeyData(
        val view: TextView,
        val normalLabel: String,
        val shiftedLabel: String,
        val vkCode: Int
    )

    val allKeys = ArrayList<KeyData>()
    val allKeyViews = ArrayList<TextView>()
    val keyBackgrounds = ArrayList<GradientDrawable>()
    var handler: ActionHandler? = null

    /*
     * Aliases para no romper código antiguo que todavía referencia KeyboardLayoutEngine.VK_*
     */
    companion object {
        const val VK_BACK = Win32VirtualKey.VK_BACK
        const val VK_TAB = Win32VirtualKey.VK_TAB
        const val VK_RETURN = Win32VirtualKey.VK_RETURN
        const val VK_SHIFT = Win32VirtualKey.VK_SHIFT
        const val VK_CONTROL = Win32VirtualKey.VK_CONTROL
        const val VK_MENU = Win32VirtualKey.VK_MENU
        const val VK_CAPITAL = Win32VirtualKey.VK_CAPITAL
        const val VK_ESCAPE = Win32VirtualKey.VK_ESCAPE
        const val VK_SPACE = Win32VirtualKey.VK_SPACE
        const val VK_LEFT = Win32VirtualKey.VK_LEFT
        const val VK_UP = Win32VirtualKey.VK_UP
        const val VK_RIGHT = Win32VirtualKey.VK_RIGHT
        const val VK_DOWN = Win32VirtualKey.VK_DOWN
        const val VK_INSERT = Win32VirtualKey.VK_INSERT
        const val VK_DELETE = Win32VirtualKey.VK_DELETE
        const val VK_SNAPSHOT = Win32VirtualKey.VK_SNAPSHOT
        const val VK_APPS = Win32VirtualKey.VK_APPS
        const val VK_F1 = Win32VirtualKey.VK_F1
        const val VK_F2 = Win32VirtualKey.VK_F2
        const val VK_F3 = Win32VirtualKey.VK_F3
        const val VK_F4 = Win32VirtualKey.VK_F4
        const val VK_F5 = Win32VirtualKey.VK_F5
        const val VK_F6 = Win32VirtualKey.VK_F6
        const val VK_F7 = Win32VirtualKey.VK_F7
        const val VK_F8 = Win32VirtualKey.VK_F8
        const val VK_F9 = Win32VirtualKey.VK_F9
        const val VK_F10 = Win32VirtualKey.VK_F10
        const val VK_F11 = Win32VirtualKey.VK_F11
        const val VK_F12 = Win32VirtualKey.VK_F12
        const val VK_LWIN = Win32VirtualKey.VK_LWIN
        const val VK_HOME = Win32VirtualKey.VK_HOME
        const val VK_END = Win32VirtualKey.VK_END
        const val VK_PRIOR = Win32VirtualKey.VK_PRIOR
        const val VK_NEXT = Win32VirtualKey.VK_NEXT
    }

    fun clearAll() {
        allKeys.clear()
        allKeyViews.clear()
        keyBackgrounds.clear()
    }

    // ------------------------------------------------------------
    // VIEW
    // ------------------------------------------------------------

    private fun createKey(
        label: String,
        weight: Float,
        isSpecial: Boolean,
        style: KeyStyle = KeyStyle.STANDARD
    ): TextView {
        val key = TextView(context)
        key.text = label
        key.gravity = 17
        key.setTextColor(ContextCompat.getColor(context, R.color.kbd_key_text))

        val targetTextSize = when (style) {
            KeyStyle.PROFILE -> 11.5f
            KeyStyle.STANDARD -> if (isSpecial) 13f else 15.5f
        }
        key.setTextSize(TypedValue.COMPLEX_UNIT_SP, targetTextSize)
        key.isSingleLine = style == KeyStyle.STANDARD
        if (style == KeyStyle.PROFILE) {
            key.maxLines = 2
            key.includeFontPadding = false
            key.setLineSpacing(0f, 0.95f)
            key.setPadding(dpToPx(3f), dpToPx(2f), dpToPx(3f), dpToPx(2f))
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            key.setAutoSizeTextTypeUniformWithConfiguration(
                9,
                targetTextSize.toInt(),
                1,
                TypedValue.COMPLEX_UNIT_SP
            )
        }

        val params = LinearLayout.LayoutParams(0, dpToPx(keyHeightDp), weight)
        val margin = dpToPx(keyHorizontalMarginDp)
        params.marginStart = margin
        params.marginEnd = margin
        key.layoutParams = params

        val fillColor = ContextCompat.getColor(
            context,
            if (isSpecial) R.color.kbd_key_special_bg else R.color.kbd_key_bg
        )
        val strokeColor = ContextCompat.getColor(context, R.color.kbd_key_stroke)
        val rippleColor = ContextCompat.getColor(context, R.color.kbd_key_ripple)

        val drawable = GradientDrawable()
        drawable.setColor(fillColor)
        drawable.cornerRadius = dpToPx(if (isPortrait) 6f else 5f).toFloat()
        drawable.setStroke(dpToPx(1f), strokeColor)

        key.background = RippleDrawable(ColorStateList.valueOf(rippleColor), drawable, drawable)
        keyBackgrounds += drawable
        allKeyViews += key

        key.isClickable = true
        key.isFocusable = true
        key.minWidth = 0
        key.minHeight = 0
        return key
    }

    fun createRow(weightSum: Float): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = 16
            this.weightSum = weightSum
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(if (isPortrait) 3.5f else 2.5f)
            }
        }
    }

    // ------------------------------------------------------------
    // TECLAS BASE
    // ------------------------------------------------------------

    fun addCharKey(
        row: LinearLayout,
        normal: String,
        shifted: String,
        vkCode: Int,
        weight: Float
    ) {
        val key = createKey(normal, weight, false)
        val data = KeyData(
            view = key,
            normalLabel = normal,
            shiftedLabel = shifted,
            vkCode = vkCode
        )
        allKeys += data
        key.setOnClickListener { view ->
            handler?.let { h ->
                h.animateKeyPress(view)
                h.onCharKeyPressed(data)
            }
        }
        row.addView(key)
    }

    fun addSpecialKey(
        row: LinearLayout,
        label: String,
        vkCode: Int,
        weight: Float,
        tooltip: String
    ) {
        val key = createKey(label, weight, true)
        val isNormalTab = handler?.getCurrentTab() == 1
        key.setOnClickListener { view ->
            handler?.let { h ->
                h.animateKeyPress(view)
                h.onSpecialKeyPressed(vkCode)
                if (!isNormalTab) {
                    Toast.makeText(context, tooltip, Toast.LENGTH_SHORT).show()
                }
            }
        }
        key.setOnLongClickListener {
            Toast.makeText(context, tooltip, Toast.LENGTH_SHORT).show()
            true
        }
        row.addView(key)
    }

    fun addModifierKey(
        row: LinearLayout,
        label: String,
        weight: Float,
        type: String
    ): TextView {
        val key = createKey(label, weight, true)
        key.setOnClickListener { view ->
            handler?.let { h ->
                h.animateKeyPress(view)
                h.onModifierToggled(type, key)
            }
        }
        key.setOnLongClickListener {
            val tooltip = when (type) {
                "ctrl" -> "Ctrl — tap: one-shot; doble tap: lock"
                "shift" -> "Shift — tap: one-shot; doble tap: lock"
                "alt" -> "Alt — tap: one-shot; doble tap: lock"
                else -> type
            }
            Toast.makeText(context, tooltip, Toast.LENGTH_SHORT).show()
            true
        }
        row.addView(key)
        return key
    }

    fun addSpaceKey(row: LinearLayout, weight: Float = 8.0f) {
        val key = createKey("␣", weight, false)
        key.setOnClickListener { view ->
            handler?.let { h ->
                h.animateKeyPress(view)
                h.onSpacePressed()
            }
        }
        row.addView(key)
    }

    private fun addActionKey(
        row: LinearLayout,
        spec: RemoteKeySpec,
        weight: Float = 1f,
        style: KeyStyle = KeyStyle.PROFILE
    ) {
        val key = createKey(profileDisplayLabel(spec.label), weight, true, style)
        key.setOnClickListener { view ->
            handler?.let { h ->
                h.animateKeyPress(view)
                h.onRemoteAction(spec.action)
                Toast.makeText(context, spec.tooltip, Toast.LENGTH_SHORT).show()
            }
        }
        key.setOnLongClickListener {
            Toast.makeText(context, spec.tooltip, Toast.LENGTH_LONG).show()
            true
        }
        setKeyIcon(key, spec.label)
        row.addView(key)
    }

    // ------------------------------------------------------------
    // NORMAL — 59 TECLAS
    // ------------------------------------------------------------

    @Suppress("UNUSED_PARAMETER")
    fun buildNormalKeyboard(
        container: LinearLayout,
        btnShiftL: TextView?,
        btnShiftR: TextView?,
        btnCtrl: TextView?,
        btnAlt: TextView?
    ) {
        container.addView(buildNumberRow())
        container.addView(buildQwertyRow())
        container.addView(buildHomeRow())
        container.addView(buildShiftRow())
        container.addView(buildSpaceRow())
    }

    /**
     * 14 teclas.
     * weightSum = 15
     */
    private fun buildNumberRow(): View {
        val row = createRow(15f)
        addCharKey(row, "`", "~", Win32VirtualKey.VK_OEM_3, 1f)
        val normal = arrayOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0", "-", "=")
        val shifted = arrayOf("!", "@", "#", "$", "%", "^", "&", "*", "(", ")", "_", "+")
        val keys = intArrayOf(
            Win32VirtualKey.VK_1,
            Win32VirtualKey.VK_2,
            Win32VirtualKey.VK_3,
            Win32VirtualKey.VK_4,
            Win32VirtualKey.VK_5,
            Win32VirtualKey.VK_6,
            Win32VirtualKey.VK_7,
            Win32VirtualKey.VK_8,
            Win32VirtualKey.VK_9,
            Win32VirtualKey.VK_0,
            Win32VirtualKey.VK_OEM_MINUS,
            Win32VirtualKey.VK_OEM_PLUS
        )
        for (i in normal.indices) {
            addCharKey(row, normal[i], shifted[i], keys[i], 1f)
        }
        addSpecialKey(row, "⌫", Win32VirtualKey.VK_BACK, 2f, "Backspace")
        return row
    }

    /**
     * 14 teclas.
     */
    private fun buildQwertyRow(): View {
        val row = createRow(15f)
        addSpecialKey(row, "⇥", Win32VirtualKey.VK_TAB, 2f, "Tab")
        val normal = arrayOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P", "[", "]")
        val shifted = arrayOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P", "{", "}")
        val keys = intArrayOf(
            Win32VirtualKey.VK_Q,
            Win32VirtualKey.VK_W,
            Win32VirtualKey.VK_E,
            Win32VirtualKey.VK_R,
            Win32VirtualKey.VK_T,
            Win32VirtualKey.VK_Y,
            Win32VirtualKey.VK_U,
            Win32VirtualKey.VK_I,
            Win32VirtualKey.VK_O,
            Win32VirtualKey.VK_P,
            Win32VirtualKey.VK_OEM_4,
            Win32VirtualKey.VK_OEM_6
        )
        for (i in normal.indices) {
            addCharKey(row, normal[i], shifted[i], keys[i], 1f)
        }
        addCharKey(row, "\\", "|", Win32VirtualKey.VK_OEM_5, 1f)
        return row
    }

    /**
     * 12 teclas.
     */
    private fun buildHomeRow(): View {
        val row = createRow(15f)
        val normal = arrayOf("A", "S", "D", "F", "G", "H", "J", "K", "L", ";", "'")
        val shifted = arrayOf("A", "S", "D", "F", "G", "H", "J", "K", "L", ":", "\"")
        val keys = intArrayOf(
            Win32VirtualKey.VK_A,
            Win32VirtualKey.VK_S,
            Win32VirtualKey.VK_D,
            Win32VirtualKey.VK_F,
            Win32VirtualKey.VK_G,
            Win32VirtualKey.VK_H,
            Win32VirtualKey.VK_J,
            Win32VirtualKey.VK_K,
            Win32VirtualKey.VK_L,
            Win32VirtualKey.VK_OEM_1,
            Win32VirtualKey.VK_OEM_7
        )
        for (i in normal.indices) {
            addCharKey(row, normal[i], shifted[i], keys[i], 1f)
        }
        addSpecialKey(row, "⏎", Win32VirtualKey.VK_RETURN, 4f, "Enter")
        return row
    }

    /**
     * 12 teclas.
     */
    private fun buildShiftRow(): View {
        val row = createRow(15f)
        addModifierKey(row, "⇧", 2.5f, "shift")
        val normal = arrayOf("Z", "X", "C", "V", "B", "N", "M", ",", ".", "/")
        val shifted = arrayOf("Z", "X", "C", "V", "B", "N", "M", "<", ">", "?")
        val keys = intArrayOf(
            Win32VirtualKey.VK_Z,
            Win32VirtualKey.VK_X,
            Win32VirtualKey.VK_C,
            Win32VirtualKey.VK_V,
            Win32VirtualKey.VK_B,
            Win32VirtualKey.VK_N,
            Win32VirtualKey.VK_M,
            Win32VirtualKey.VK_OEM_COMMA,
            Win32VirtualKey.VK_OEM_PERIOD,
            Win32VirtualKey.VK_OEM_2
        )
        for (i in normal.indices) {
            addCharKey(row, normal[i], shifted[i], keys[i], 1f)
        }
        addModifierKey(row, "⇧", 2.5f, "shift")
        return row
    }

    /**
     * 7 teclas.
     */
    private fun buildSpaceRow(): View {
        val row = createRow(15f)
        addModifierKey(row, "Ctrl", 2f, "ctrl")
        addModifierKey(row, "Alt", 2f, "alt")
        addSpaceKey(row, 7f)
        addSpecialKey(row, "←", Win32VirtualKey.VK_LEFT, 1f, "Izquierda")
        addSpecialKey(row, "↑", Win32VirtualKey.VK_UP, 1f, "Arriba")
        addSpecialKey(row, "↓", Win32VirtualKey.VK_DOWN, 1f, "Abajo")
        addSpecialKey(row, "→", Win32VirtualKey.VK_RIGHT, 1f, "Derecha")
        return row
    }

    // ------------------------------------------------------------
    // SHORTCUTS
    // ------------------------------------------------------------

    fun buildShortcutsKeyboard(container: LinearLayout) {
        val shortcuts = listOf(
            RemoteKeySpec("Alt+Tab", RemoteKeyAction.Chord(Win32VirtualKey.VK_TAB, listOf(RemoteModifier.ALT)), "Cambiar ventana"),
            RemoteKeySpec("Alt+F4", RemoteKeyAction.Chord(Win32VirtualKey.VK_F4, listOf(RemoteModifier.ALT)), "Cerrar ventana"),
            RemoteKeySpec("Win+D", RemoteKeyAction.Chord(Win32VirtualKey.VK_D, listOf(RemoteModifier.META)), "Mostrar escritorio"),
            RemoteKeySpec("Win+E", RemoteKeyAction.Chord(Win32VirtualKey.VK_E, listOf(RemoteModifier.META)), "Abrir Explorador"),
            RemoteKeySpec("Ctrl+C", RemoteKeyAction.Chord(Win32VirtualKey.VK_C, listOf(RemoteModifier.CTRL)), "Copiar"),
            RemoteKeySpec("Ctrl+V", RemoteKeyAction.Chord(Win32VirtualKey.VK_V, listOf(RemoteModifier.CTRL)), "Pegar"),
            RemoteKeySpec("Ctrl+X", RemoteKeyAction.Chord(Win32VirtualKey.VK_X, listOf(RemoteModifier.CTRL)), "Cortar"),
            RemoteKeySpec("Ctrl+Z", RemoteKeyAction.Chord(Win32VirtualKey.VK_Z, listOf(RemoteModifier.CTRL)), "Deshacer"),
            RemoteKeySpec("Ctrl+Y", RemoteKeyAction.Chord(Win32VirtualKey.VK_Y, listOf(RemoteModifier.CTRL)), "Rehacer"),
            RemoteKeySpec("Ctrl+S", RemoteKeyAction.Chord(Win32VirtualKey.VK_S, listOf(RemoteModifier.CTRL)), "Guardar"),
            RemoteKeySpec("Ctrl+F", RemoteKeyAction.Chord(Win32VirtualKey.VK_F, listOf(RemoteModifier.CTRL)), "Buscar"),
            RemoteKeySpec("Ctrl+A", RemoteKeyAction.Chord(Win32VirtualKey.VK_A, listOf(RemoteModifier.CTRL)), "Seleccionar todo"),
            RemoteKeySpec("Ctrl+Sh+P", RemoteKeyAction.Chord(Win32VirtualKey.VK_P, listOf(RemoteModifier.CTRL, RemoteModifier.SHIFT)), "Command Palette"),
            RemoteKeySpec("Ctrl+Alt+L", RemoteKeyAction.Chord(Win32VirtualKey.VK_L, listOf(RemoteModifier.CTRL, RemoteModifier.ALT)), "Reformat Code"),
            RemoteKeySpec("Shift+F10", RemoteKeyAction.Chord(Win32VirtualKey.VK_F10, listOf(RemoteModifier.SHIFT)), "Shift+F10"),
            RemoteKeySpec("Ctrl+Sh+Esc", RemoteKeyAction.Chord(Win32VirtualKey.VK_ESCAPE, listOf(RemoteModifier.CTRL, RemoteModifier.SHIFT)), "Administrador de tareas")
        )

        for (chunk in shortcuts.chunked(4)) {
            val row = createRow(4f)
            for (spec in chunk) {
                addActionKey(row = row, spec = spec, weight = 1f)
            }
            container.addView(row)
        }
    }

    // ------------------------------------------------------------
    // DEV PROFILE
    // ------------------------------------------------------------

    fun buildProfiledDevKeyboard(
        container: LinearLayout,
        keys: List<DevKey>
    ) {
        val cols = when {
            isPortrait -> 4
            keys.size <= 8 -> 4
            else -> 6
        }
        val rows = if (isPortrait && keys.size % cols == 2 && keys.size > cols) {
            keys.dropLast(6).chunked(cols) + keys.takeLast(6).chunked(3)
        } else {
            keys.chunked(cols)
        }
        for (rowKeys in rows) {
            val row = createRow(rowKeys.size.toFloat())
            for (spec in rowKeys) {
                addActionKey(
                    row = row,
                    spec = spec,
                    weight = 1f,
                    style = KeyStyle.PROFILE
                )
            }
            container.addView(row)
        }
    }

    private fun profileDisplayLabel(label: String): String {
        return when (label) {
            "Quick Open" -> "Quick\nOpen"
            "Search All" -> "Search\nAll"
            "New Session" -> "New\nSession"
            "New Conv" -> "New\nConv"
            "Prev Conv" -> "Prev\nConv"
            "Next Conv" -> "Next\nConv"
            "Close All" -> "Close\nAll"
            "Find Path" -> "Find\nPath"
            "Smart Comp" -> "Smart\nComp"
            "Quick Fix" -> "Quick\nFix"
            "Line Start" -> "Line\nStart"
            "Line End" -> "Line\nEnd"
            "Del Word" -> "Del\nWord"
            else -> label
        }
    }

    private fun setKeyIcon(key: TextView, label: String) {
        val iconRes = when (label) {
            "Chat" -> R.drawable.ic_antigravity_chat
            "Search", "Search All", "Find Path" -> R.drawable.ic_search
            "Explorer", "Files" -> R.drawable.ic_files_overlay
            "Palette" -> R.drawable.ic_palette
            "Close All" -> R.drawable.ic_win_close
            "Terminal" -> R.drawable.ic_terminal
            "Git", "VCS" -> R.drawable.ic_github
            "Run" -> R.drawable.ic_play
            else -> 0
        }
        if (iconRes == 0) {
            return
        }
        val drawable = context.getDrawable(iconRes) ?: return
        val size = dpToPx(18f)
        drawable.setBounds(0, 0, size, size)
        key.setCompoundDrawables(null, drawable, null, null)
        key.compoundDrawablePadding = dpToPx(1f)
        key.gravity = 17
    }

    fun dpToPx(dp: Float): Int {
        return Math.round(dp * context.resources.displayMetrics.density)
    }
}
