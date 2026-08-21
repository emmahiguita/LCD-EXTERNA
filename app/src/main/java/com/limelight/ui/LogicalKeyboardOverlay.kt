package com.limelight.ui

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.limelight.Game
import com.limelight.R
import com.limelight.nvstream.NvConnection
import com.limelight.ui.keyboard.*

class LogicalKeyboardOverlay(private val panelRoot: View, private val context: Context) {

    fun interface OnVisibilityChangedListener {
        fun onVisibilityChanged(visible: Boolean, topY: Int)
    }

    // ── Sub-controladores ────────────────────────────────────────────────
    private val state = KeyboardState()
    private val persistence = KeyboardPersistence(context)
    private val layout = KeyboardLayoutEngine(context)
    private val input = KeyboardInputHandler(context, state, layout)
    private val animEngine = KeyboardAnimationEngine(context)
    private val position = KeyboardPositionManager()
    private val profileEngine = KeyboardProfileEngine { rebuildIfDevTabVisible() }

    private var visibilityListener: OnVisibilityChangedListener? = null
    private var keyboardOffsetAnim: ValueAnimator? = null
    private var keyboardContainer = panelRoot.findViewById<LinearLayout>(R.id.keyboardContainer)
    private var stickyKeysStatus: TextView? = null
    private var btnShiftL: TextView? = null; private var btnShiftR: TextView? = null
    private var btnCtrl: TextView? = null; private var btnAlt: TextView? = null
    private lateinit var scaleDetector: ScaleGestureDetector

    // Efecto RGB en las teclas (botón de paleta en el header). Se re-aplica
    // tras cada reconstrucción (cambio de tab) porque clearAll() cambia las
    // vistas/backgrounds sobre los que anima.
    private var rgbActive = false

    init {
        panelRoot.pivotX = 0f
        panelRoot.pivotY = 0f
        layout.handler = object : KeyboardLayoutEngine.ActionHandler {
            override fun onCharKeyPressed(kd: KeyboardLayoutEngine.KeyData) = input.onCharKeyPressed(kd)
            override fun onSpecialKeyPressed(vk: Int) = input.onSpecialKeyPressed(vk)
            override fun onModifierToggled(type: String, btn: TextView) = input.toggleModifier(type, btn)
            override fun onSpacePressed() = input.onSpacePressed()
            override fun onMacro(vararg vk: Int) = input.sendMacro(*vk)
            override fun animateKeyPress(v: View) = animEngine.animateKeyPress(v)
            override fun getConnectionStatus() = true
            override fun getCurrentTab() = state.currentTab
            override fun getTooltip(label: String) = input.macroDesc(label)
        }
        setupHeaderControls(); persistence.restoreState(state)
        state.tabListener = KeyboardState.TabListener { rebuildKeyboard() }
        state.modifierListener = KeyboardState.ModifierListener { _, _, _ -> updateStickyKeysStatus() }
        panelRoot.post { rebuildKeyboard() }
    }

    fun setConnection(conn: NvConnection?) { input.setConnection(conn) }
    fun isVisible(): Boolean = panelRoot.visibility == View.VISIBLE
    fun setOnVisibilityChangedListener(l: OnVisibilityChangedListener?) { visibilityListener = l }

    // ── Mostrar / Ocultar ────────────────────────────────────────────────
    fun show() {
        if ((keyboardContainer?.childCount ?: 0) == 0) rebuildKeyboard()
        else applyRgbState() // reanuda el efecto RGB si estaba activo (hide() lo pausó)
        panelRoot.pivotX = 0f
        panelRoot.pivotY = 0f
        position.constrainAndPositionInitial(panelRoot, state.currentScale) { state.currentScale = it }
        val targetY = panelRoot.y
        visibilityListener?.onVisibilityChanged(true, targetY.toInt())
        panelRoot.alpha = 0f; panelRoot.y = targetY + 150f
        panelRoot.scaleX = state.currentScale; panelRoot.scaleY = state.currentScale
        panelRoot.visibility = View.VISIBLE
        panelRoot.animate().alpha(KeyboardState.ALPHA_VALS[state.alphaIndex]).y(targetY)
            .scaleX(state.currentScale).scaleY(state.currentScale)
            .setDuration(300).setInterpolator(KeyboardAnimationEngine.EASING_DECELERATE).start()
        
        // Evitar solapamiento con el stream remoto desplazándolo hacia arriba
        panelRoot.post {
            val panelH = (panelRoot.measuredHeight * state.currentScale).coerceAtLeast(1f)
            setScreenOffset(-panelH * 0.85f)
        }
    }

    fun hide() {
        persistence.saveState(state); input.releaseAllModifiers()
        // Pausa el efecto RGB mientras el teclado no es visible (ahorra batería);
        // rgbActive se conserva y show() lo reanuda.
        animEngine.stopRGBAnimation(layout.allKeyViews, layout.keyBackgrounds)
        val targetY = panelRoot.y; visibilityListener?.onVisibilityChanged(false, targetY.toInt())
        setScreenOffset(0f); panelRoot.animate().alpha(0f).y(targetY + 150f)
            .scaleX(state.currentScale * 0.95f).scaleY(state.currentScale * 0.95f)
            .setDuration(250).setInterpolator(KeyboardAnimationEngine.EASING_ACCELERATE)
            .withEndAction { panelRoot.visibility = View.GONE; panelRoot.y = targetY
                panelRoot.scaleX = state.currentScale; panelRoot.scaleY = state.currentScale }.start()
    }

    fun onOrientationChanged() {
        if (panelRoot.visibility != View.VISIBLE) return
        panelRoot.post { val p = panelRoot.parent as? View ?: return@post
            if (p.width == 0 || p.height == 0) return@post
            panelRoot.pivotX = 0f
            panelRoot.pivotY = 0f
            position.constrainAndPositionInitial(panelRoot, state.currentScale) { state.currentScale = it }
            visibilityListener?.onVisibilityChanged(true, panelRoot.y.toInt())
            val panelH = (panelRoot.measuredHeight * state.currentScale).coerceAtLeast(1f)
            setScreenOffset(-panelH * 0.85f)
        }
    }

    fun pauseAnimations() {
        animEngine.stopRGBAnimation(layout.allKeyViews, layout.keyBackgrounds)
    }

    fun resumeAnimations() {
        if (isVisible()) applyRgbState()
    }

    fun release() {
        keyboardOffsetAnim?.cancel()
        keyboardOffsetAnim = null
        panelRoot.animate().cancel()
        visibilityListener = null
        animEngine.stopRGBAnimation(layout.allKeyViews, layout.keyBackgrounds)
    }

    // ── Setup ────────────────────────────────────────────────────────────
    @SuppressLint("ClickableViewAccessibility")
    private fun setupHeaderControls() {
        // En horizontal, el drag handle ocupa mucho espacio → reducirlo
        val isLandscape = panelRoot.context.resources.configuration.orientation ==
            android.content.res.Configuration.ORIENTATION_LANDSCAPE
        if (isLandscape) {
            val density = panelRoot.context.resources.displayMetrics.density
            val smallHandleH = Math.round(40f * density)
            panelRoot.findViewById<View>(R.id.keyboardDragHandle)?.layoutParams?.height =
                smallHandleH
        }
        panelRoot.findViewById<TextView>(R.id.tabShortcuts)?.setOnClickListener { switchTab(0) }
        panelRoot.findViewById<TextView>(R.id.tabNormal)?.setOnClickListener { switchTab(1) }
        panelRoot.findViewById<TextView>(R.id.tabDev)?.setOnClickListener {
            if (state.currentTab == 2) {
                // Ya estamos en DEV: ciclar perfil al tocar de nuevo
                profileEngine.cycleProfile()
            } else {
                switchTab(2)
            }
        }
        // Long-press en DEV: también ciclar perfil
        panelRoot.findViewById<TextView>(R.id.tabDev)?.setOnLongClickListener {
            profileEngine.cycleProfile(); true
        }
        panelRoot.findViewById<TextView>(R.id.btnAlphaDown)?.setOnClickListener { decAlpha() }
        panelRoot.findViewById<TextView>(R.id.btnAlphaUp)?.setOnClickListener { incAlpha() }
        panelRoot.findViewById<View>(R.id.btnCloseKeyboard)?.setOnClickListener { hide() }
        panelRoot.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnRGB)
            ?.setOnClickListener { rgbActive = !rgbActive; applyRgbState() }
        stickyKeysStatus = panelRoot.findViewById(R.id.stickyKeysStatus)
        scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                state.currentScale = (state.currentScale * detector.scaleFactor).coerceIn(0.78f, 1.15f)
                panelRoot.scaleX = state.currentScale; panelRoot.scaleY = state.currentScale
                val p = panelRoot.parent as? View ?: return true
                panelRoot.x = position.clampX(panelRoot.x, p, panelRoot.width * state.currentScale)
                panelRoot.y = position.clampY(panelRoot.y, p, panelRoot.height * state.currentScale)
                return true
            }
            override fun onScaleEnd(detector: ScaleGestureDetector) { persistence.saveState(state) }
        })
        val dh = panelRoot.findViewById<View>(R.id.keyboardDragHandle) ?: return
        dh.setOnTouchListener { v, event ->
            scaleDetector.onTouchEvent(event)
            if (!scaleDetector.isInProgress && event.pointerCount == 1) {
                when (event.actionMasked) {
                    android.view.MotionEvent.ACTION_DOWN -> { dx = event.rawX; dy = event.rawY; sx = panelRoot.x; sy = panelRoot.y }
                    android.view.MotionEvent.ACTION_MOVE -> {
                        val p = panelRoot.parent as? View ?: return@setOnTouchListener true
                        panelRoot.x = position.clampX(sx + event.rawX - dx, p, panelRoot.width * state.currentScale)
                        panelRoot.y = position.clampY(sy + event.rawY - dy, p, panelRoot.height * state.currentScale)
                    }
                    android.view.MotionEvent.ACTION_UP -> { persistence.saveState(state); v.performClick() }
                }
            }; true
        }
    }
    private var dx = 0f; private var dy = 0f; private var sx = 0f; private var sy = 0f

    // ── Tabs fijos: 0=Atajos, 1=Normal, 2=DEV (con perfil si detectado) ─
    private fun switchTab(tab: Int) {
        if (state.currentTab == tab) return
        state.currentTab = tab; updateTabUI(); rebuildKeyboard()
    }

    private fun updateTabUI() {
        for ((id, i) in listOf(R.id.tabShortcuts to 0, R.id.tabNormal to 1, R.id.tabDev to 2)) {
            val t = panelRoot.findViewById<TextView>(id)
            t?.setBackgroundResource(if (state.currentTab == i) R.drawable.key_active_bg else R.drawable.key_button_bg)
            t?.setTextColor(-1)
        }
        // Etiqueta DEV muestra el perfil activo (toque para ciclar)
        panelRoot.findViewById<TextView>(R.id.tabDev)?.text = profileEngine.profileLabel()
    }

    private fun rebuildKeyboard() {
        keyboardContainer?.let { kc ->
            layout.clearAll(); kc.removeAllViews()
            when (state.currentTab) {
                0 -> layout.buildShortcutsKeyboard(kc)
                1 -> layout.buildNormalKeyboard(kc, btnShiftL, btnShiftR, btnCtrl, btnAlt)
                2 -> layout.buildProfiledDevKeyboard(kc, profileEngine.getDevKeys())
                else -> layout.buildNormalKeyboard(kc, btnShiftL, btnShiftR, btnCtrl, btnAlt)
            }
            btnCtrl = findMod("Ctrl"); btnAlt = findMod("Alt")
            btnShiftL = findMod("⇧"); btnShiftR = btnShiftL
            input.setModifierButtons(btnShiftL, btnShiftR, btnCtrl, btnAlt)
        }
        updateTabUI(); updateStickyKeysStatus()
        applyRgbState()
    }

    /** Enciende/apaga el efecto RGB sobre las teclas del tab actual y refleja el estado en el botón. */
    private fun applyRgbState() {
        if (rgbActive) {
            animEngine.startRGBAnimation(layout.allKeyViews, layout.keyBackgrounds)
        } else {
            animEngine.stopRGBAnimation(layout.allKeyViews, layout.keyBackgrounds)
        }
        val btn = panelRoot.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnRGB)
        btn?.iconTint = android.content.res.ColorStateList.valueOf(
            if (rgbActive) androidx.core.content.ContextCompat.getColor(context, R.color.neon_cyan)
            else androidx.core.content.ContextCompat.getColor(context, R.color.hud_text)
        )
    }

    private fun findMod(text: String): TextView? {
        for (i in 0 until (keyboardContainer?.childCount ?: 0)) {
            val row = keyboardContainer?.getChildAt(i) as? ViewGroup ?: continue
            for (j in 0 until row.childCount) {
                val c = row.getChildAt(j) as? TextView ?: continue
                if (c.text.toString() == text) return c
            }
        }; return null
    }

    private fun rebuildIfDevTabVisible() {
        if (panelRoot.visibility != View.VISIBLE) return
        panelRoot.post {
            updateTabUI()
            if (state.currentTab == 2) rebuildKeyboard()
        }
    }

    // ── Transparencia ────────────────────────────────────────────────────
    private fun decAlpha() { state.alphaIndex = state.alphaIndex - 1; applyAlpha() }
    private fun incAlpha() { state.alphaIndex = state.alphaIndex + 1; applyAlpha() }
    private fun applyAlpha() {
        panelRoot.animate().alpha(KeyboardState.ALPHA_VALS[state.alphaIndex]).setDuration(160)
            .setInterpolator(KeyboardAnimationEngine.EASING_DECELERATE).start()
        persistence.saveState(state)
    }

    // ── Sticky Keys ──────────────────────────────────────────────────────
    private fun updateStickyKeysStatus() {
        val sv = stickyKeysStatus ?: return; val sb = StringBuilder()
        if (state.ctrlActive) sb.append("CTRL \u25CF  ")
        if (state.altActive) sb.append("ALT \u25CF  ")
        if (state.shiftActive) sb.append("SHIFT \u25CF")
        if (sb.isEmpty()) sv.visibility = View.GONE
        else { sv.text = sb.toString().trimEnd(); sv.visibility = View.VISIBLE }
    }

    // ── Screen offset ────────────────────────────────────────────────────
    private fun setScreenOffset(t: Float) {
        if (context !is Game) return; val ctrl = context.streamTransformCtrl ?: return
        keyboardOffsetAnim?.cancel(); val a = ValueAnimator.ofFloat(ctrl.keyboardOffsetY, t)
        a.duration = 250; a.interpolator = KeyboardAnimationEngine.EASING_DECELERATE
        a.addUpdateListener { a -> ctrl.setKeyboardOffset(a.animatedValue as Float) }
        keyboardOffsetAnim = a; a.start()
    }

}
