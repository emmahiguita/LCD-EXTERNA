package com.limelight.ui

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.limelight.Game
import com.limelight.R
import com.limelight.nvstream.NvConnection
import com.limelight.ui.keyboard.*

class LogicalKeyboardOverlay(
    private val panelRoot: View,
    private val context: Context
) {
    fun interface OnVisibilityChangedListener {
        fun onVisibilityChanged(
            visible: Boolean,
            topY: Int
        )
    }

    private val state = KeyboardState()
    private val persistence = KeyboardPersistence(context)
    private val layout = KeyboardLayoutEngine(context)
    private val input = KeyboardInputHandler(
        context,
        state,
        layout
    )
    private val animEngine = KeyboardAnimationEngine(context)
    private val position = KeyboardPositionManager()
    private val profileEngine = KeyboardProfileEngine {
        rebuildIfDevTabVisible()
    }

    private var visibilityListener: OnVisibilityChangedListener? = null
    private var keyboardOffsetAnim: ValueAnimator? = null
    private var keyboardContainer = panelRoot.findViewById<LinearLayout>(
        R.id.keyboardContainer
    )
    private var stickyKeysStatus: TextView? = null
    private var btnShiftL: TextView? = null
    private var btnShiftR: TextView? = null
    private var btnCtrl: TextView? = null
    private var btnAlt: TextView? = null

    private lateinit var scaleDetector: ScaleGestureDetector
    private var rgbActive = false
    private var dx = 0f
    private var dy = 0f
    private var sx = 0f
    private var sy = 0f

    init {
        panelRoot.pivotX = 0f
        panelRoot.pivotY = 0f

        layout.handler = object : KeyboardLayoutEngine.ActionHandler {
            override fun onCharKeyPressed(kd: KeyboardLayoutEngine.KeyData) {
                input.onCharKeyPressed(kd)
            }

            override fun onSpecialKeyPressed(vkCode: Int) {
                input.onSpecialKeyPressed(vkCode)
            }

            override fun onModifierToggled(type: String, btn: TextView) {
                input.toggleModifier(type, btn)
            }

            override fun onSpacePressed() {
                input.onSpacePressed()
            }

            override fun onRemoteAction(action: RemoteKeyAction) {
                input.sendAction(action)
            }

            override fun animateKeyPress(v: View) {
                animEngine.animateKeyPress(v)
            }

            override fun getConnectionStatus(): Boolean {
                return input.hasConnection()
            }

            override fun getCurrentTab(): Int {
                return state.currentTab
            }
        }

        setupHeaderControls()
        persistence.restoreState(state)

        state.tabListener = KeyboardState.TabListener {
            rebuildKeyboard()
        }
        state.modifierListener = KeyboardState.ModifierListener { _, _, _ ->
            updateStickyKeysStatus()
        }

        panelRoot.post {
            rebuildKeyboard()
        }
    }

    fun setConnection(conn: NvConnection?) {
        input.setConnection(conn)
    }

    fun isVisible(): Boolean {
        return panelRoot.visibility == View.VISIBLE
    }

    fun setOnVisibilityChangedListener(listener: OnVisibilityChangedListener?) {
        visibilityListener = listener
    }

    // ------------------------------------------------------------
    // SEGURIDAD DE INPUT
    // ------------------------------------------------------------

    /**
     * Llamar desde Game.onPause(), connectionTerminated(), etc.
     */
    fun onInputSuspended() {
        input.releaseAllPressedKeys()
    }

    // ------------------------------------------------------------
    // SHOW / HIDE
    // ------------------------------------------------------------

    fun show() {
        if ((keyboardContainer?.childCount ?: 0) == 0) {
            rebuildKeyboard()
        } else {
            applyRgbState()
        }

        panelRoot.pivotX = 0f
        panelRoot.pivotY = 0f
        position.constrainAndPositionInitial(
            panelRoot,
            state.currentScale
        ) { state.currentScale = it }

        val targetY = panelRoot.y
        visibilityListener?.onVisibilityChanged(true, targetY.toInt())

        panelRoot.alpha = 0f
        panelRoot.y = targetY + 150f
        panelRoot.scaleX = state.currentScale
        panelRoot.scaleY = state.currentScale
        panelRoot.visibility = View.VISIBLE

        panelRoot.animate()
            .alpha(KeyboardState.ALPHA_VALS[state.alphaIndex])
            .y(targetY)
            .scaleX(state.currentScale)
            .scaleY(state.currentScale)
            .setDuration(300)
            .setInterpolator(KeyboardAnimationEngine.EASING_DECELERATE)
            .start()

        panelRoot.post {
            val panelHeight = (panelRoot.measuredHeight * state.currentScale).coerceAtLeast(1f)
            setScreenOffset(-panelHeight * 0.85f)
        }
    }

    fun hide() {
        persistence.saveState(state)
        /*
         * IMPORTANTE:
         * No solo apagar flags UI. Liberar cualquier DOWN pendiente.
         */
        input.releaseAllPressedKeys()
        animEngine.stopRGBAnimation(
            layout.allKeyViews,
            layout.keyBackgrounds
        )

        val targetY = panelRoot.y
        visibilityListener?.onVisibilityChanged(false, targetY.toInt())
        setScreenOffset(0f)

        panelRoot.animate()
            .alpha(0f)
            .y(targetY + 150f)
            .scaleX(state.currentScale * 0.95f)
            .scaleY(state.currentScale * 0.95f)
            .setDuration(250)
            .setInterpolator(KeyboardAnimationEngine.EASING_ACCELERATE)
            .withEndAction {
                panelRoot.visibility = View.GONE
                panelRoot.y = targetY
                panelRoot.scaleX = state.currentScale
                panelRoot.scaleY = state.currentScale
            }
            .start()
    }

    fun onOrientationChanged() {
        if (panelRoot.visibility != View.VISIBLE) {
            return
        }
        panelRoot.post {
            val parent = panelRoot.parent as? View ?: return@post
            if (parent.width == 0 || parent.height == 0) {
                return@post
            }
            panelRoot.pivotX = 0f
            panelRoot.pivotY = 0f
            position.constrainAndPositionInitial(
                panelRoot,
                state.currentScale
            ) { state.currentScale = it }

            visibilityListener?.onVisibilityChanged(true, panelRoot.y.toInt())
            val panelHeight = (panelRoot.measuredHeight * state.currentScale).coerceAtLeast(1f)
            setScreenOffset(-panelHeight * 0.85f)
        }
    }

    fun pauseAnimations() {
        animEngine.stopRGBAnimation(
            layout.allKeyViews,
            layout.keyBackgrounds
        )
    }

    fun resumeAnimations() {
        if (isVisible()) {
            applyRgbState()
        }
    }

    fun release() {
        /*
         * Cierra primero TX para no dejar DOWN vivos al destruir Activity.
         */
        input.close()
        keyboardOffsetAnim?.cancel()
        keyboardOffsetAnim = null
        panelRoot.animate().cancel()
        visibilityListener = null
        animEngine.stopRGBAnimation(
            layout.allKeyViews,
            layout.keyBackgrounds
        )
    }

    // ------------------------------------------------------------
    // HEADER
    // ------------------------------------------------------------

    @SuppressLint("ClickableViewAccessibility")
    private fun setupHeaderControls() {
        val isLandscape = panelRoot.context
            .resources
            .configuration
            .orientation == Configuration.ORIENTATION_LANDSCAPE
        if (isLandscape) {
            val density = panelRoot.context.resources.displayMetrics.density
            val smallHandleHeight = Math.round(40f * density)
            panelRoot.findViewById<View>(R.id.keyboardDragHandle)?.layoutParams?.height = smallHandleHeight
        }

        panelRoot.findViewById<TextView>(R.id.tabShortcuts)?.setOnClickListener {
            switchTab(0)
        }
        panelRoot.findViewById<TextView>(R.id.tabNormal)?.setOnClickListener {
            switchTab(1)
        }
        panelRoot.findViewById<TextView>(R.id.tabDev)?.setOnClickListener {
            if (state.currentTab == 2) {
                profileEngine.cycleProfile()
            } else {
                switchTab(2)
            }
        }
        panelRoot.findViewById<TextView>(R.id.tabDev)?.setOnLongClickListener {
            profileEngine.cycleProfile()
            true
        }

        panelRoot.findViewById<TextView>(R.id.btnAlphaDown)?.setOnClickListener {
            decAlpha()
        }
        panelRoot.findViewById<TextView>(R.id.btnAlphaUp)?.setOnClickListener {
            incAlpha()
        }
        panelRoot.findViewById<View>(R.id.btnCloseKeyboard)?.setOnClickListener {
            hide()
        }
        panelRoot.findViewById<MaterialButton>(R.id.btnRGB)?.setOnClickListener {
            rgbActive = !rgbActive
            applyRgbState()
        }

        stickyKeysStatus = panelRoot.findViewById(R.id.stickyKeysStatus)

        scaleDetector = ScaleGestureDetector(
            context,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    state.currentScale = (state.currentScale * detector.scaleFactor).coerceIn(0.78f, 1.15f)
                    panelRoot.scaleX = state.currentScale
                    panelRoot.scaleY = state.currentScale
                    val parent = panelRoot.parent as? View ?: return true
                    panelRoot.x = position.clampX(
                        panelRoot.x,
                        parent,
                        panelRoot.width * state.currentScale
                    )
                    panelRoot.y = position.clampY(
                        panelRoot.y,
                        parent,
                        panelRoot.height * state.currentScale
                    )
                    return true
                }

                override fun onScaleEnd(detector: ScaleGestureDetector) {
                    persistence.saveState(state)
                }
            }
        )

        val dragHandle = panelRoot.findViewById<View>(R.id.keyboardDragHandle) ?: return
        dragHandle.setOnTouchListener { view, event ->
            scaleDetector.onTouchEvent(event)
            if (!scaleDetector.isInProgress && event.pointerCount == 1) {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        dx = event.rawX
                        dy = event.rawY
                        sx = panelRoot.x
                        sy = panelRoot.y
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val parent = panelRoot.parent as? View ?: return@setOnTouchListener true
                        panelRoot.x = position.clampX(
                            sx + event.rawX - dx,
                            parent,
                            panelRoot.width * state.currentScale
                        )
                        panelRoot.y = position.clampY(
                            sy + event.rawY - dy,
                            parent,
                            panelRoot.height * state.currentScale
                        )
                    }
                    MotionEvent.ACTION_UP -> {
                        persistence.saveState(state)
                        view.performClick()
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        input.releaseAllPressedKeys()
                    }
                }
            }
            true
        }
    }

    // ------------------------------------------------------------
    // TABS
    // ------------------------------------------------------------

    private fun switchTab(tab: Int) {
        if (state.currentTab == tab) {
            return
        }
        /*
         * Una macro pendiente nunca debe sobrevivir a un cambio de tab.
         */
        input.releaseAllPressedKeys()
        state.currentTab = tab
        updateTabUI()
        rebuildKeyboard()
    }

    private fun updateTabUI() {
        val tabs = listOf(
            R.id.tabShortcuts to 0,
            R.id.tabNormal to 1,
            R.id.tabDev to 2
        )
        for ((id, index) in tabs) {
            val text = panelRoot.findViewById<TextView>(id)
            text?.setBackgroundResource(
                if (state.currentTab == index) {
                    R.drawable.key_active_bg
                } else {
                    R.drawable.key_button_bg
                }
            )
            text?.setTextColor(android.graphics.Color.WHITE)
        }
        panelRoot.findViewById<TextView>(R.id.tabDev)?.text = profileEngine.profileLabel()
    }

    private fun rebuildKeyboard() {
        keyboardContainer?.let { container ->
            layout.clearAll()
            container.removeAllViews()
            when (state.currentTab) {
                0 -> layout.buildShortcutsKeyboard(container)
                1 -> layout.buildNormalKeyboard(
                    container,
                    btnShiftL,
                    btnShiftR,
                    btnCtrl,
                    btnAlt
                )
                2 -> layout.buildProfiledDevKeyboard(
                    container,
                    profileEngine.getDevKeys()
                )
                else -> layout.buildNormalKeyboard(
                    container,
                    btnShiftL,
                    btnShiftR,
                    btnCtrl,
                    btnAlt
                )
            }
            btnCtrl = findMods("Ctrl").firstOrNull()
            btnAlt = findMods("Alt").firstOrNull()
            val shifts = findMods("⇧")
            btnShiftL = shifts.getOrNull(0)
            btnShiftR = shifts.getOrNull(1)
            input.setModifierButtons(
                btnShiftL,
                btnShiftR,
                btnCtrl,
                btnAlt
            )
        }
        updateTabUI()
        updateStickyKeysStatus()
        applyRgbState()
    }

    private fun findMods(text: String): List<TextView> {
        val result = ArrayList<TextView>()
        for (i in 0 until (keyboardContainer?.childCount ?: 0)) {
            val row = keyboardContainer?.getChildAt(i) as? ViewGroup ?: continue
            for (j in 0 until row.childCount) {
                val child = row.getChildAt(j) as? TextView ?: continue
                if (child.text.toString() == text) {
                    result += child
                }
            }
        }
        return result
    }

    private fun rebuildIfDevTabVisible() {
        if (panelRoot.visibility != View.VISIBLE) {
            return
        }
        panelRoot.post {
            updateTabUI()
            if (state.currentTab == 2) {
                input.releaseAllPressedKeys()
                rebuildKeyboard()
            }
        }
    }

    // ------------------------------------------------------------
    // RGB
    // ------------------------------------------------------------

    private fun applyRgbState() {
        if (rgbActive) {
            animEngine.startRGBAnimation(
                layout.allKeyViews,
                layout.keyBackgrounds
            )
        } else {
            animEngine.stopRGBAnimation(
                layout.allKeyViews,
                layout.keyBackgrounds
            )
        }
        val btn = panelRoot.findViewById<MaterialButton>(R.id.btnRGB)
        btn?.iconTint = android.content.res.ColorStateList.valueOf(
            if (rgbActive) {
                androidx.core.content.ContextCompat.getColor(context, R.color.neon_cyan)
            } else {
                androidx.core.content.ContextCompat.getColor(context, R.color.hud_text)
            }
        )
    }

    // ------------------------------------------------------------
    // STICKY STATUS
    // ------------------------------------------------------------

    private fun updateStickyKeysStatus() {
        val status = stickyKeysStatus ?: return
        val pieces = ArrayList<String>()
        when (state.ctrlMode) {
            ModifierMode.ONE_SHOT -> pieces += "CTRL"
            ModifierMode.LOCKED -> pieces += "CTRL LOCK"
            ModifierMode.PRESSED -> pieces += "CTRL HOLD"
            ModifierMode.OFF -> Unit
        }
        when (state.altMode) {
            ModifierMode.ONE_SHOT -> pieces += "ALT"
            ModifierMode.LOCKED -> pieces += "ALT LOCK"
            ModifierMode.PRESSED -> pieces += "ALT HOLD"
            ModifierMode.OFF -> Unit
        }
        when (state.shiftMode) {
            ModifierMode.ONE_SHOT -> pieces += "SHIFT"
            ModifierMode.LOCKED -> pieces += "SHIFT LOCK"
            ModifierMode.PRESSED -> pieces += "SHIFT HOLD"
            ModifierMode.OFF -> Unit
        }
        if (pieces.isEmpty()) {
            status.visibility = View.GONE
        } else {
            status.text = pieces.joinToString(" · ")
            status.visibility = View.VISIBLE
        }
    }

    // ------------------------------------------------------------
    // ALPHA
    // ------------------------------------------------------------

    private fun decAlpha() {
        state.alphaIndex = state.alphaIndex - 1
        applyAlpha()
    }

    private fun incAlpha() {
        state.alphaIndex = state.alphaIndex + 1
        applyAlpha()
    }

    private fun applyAlpha() {
        panelRoot.animate()
            .alpha(KeyboardState.ALPHA_VALS[state.alphaIndex])
            .setDuration(160)
            .setInterpolator(KeyboardAnimationEngine.EASING_DECELERATE)
            .start()
        persistence.saveState(state)
    }

    // ------------------------------------------------------------
    // STREAM OFFSET
    // ------------------------------------------------------------

    private fun setScreenOffset(target: Float) {
        if (context !is Game) {
            return
        }
        val controller = context.streamTransformCtrl ?: return
        keyboardOffsetAnim?.cancel()
        val animator = ValueAnimator.ofFloat(
            controller.keyboardOffsetY,
            target
        )
        animator.duration = 250
        animator.interpolator = KeyboardAnimationEngine.EASING_DECELERATE
        animator.addUpdateListener { valueAnimator ->
            controller.setKeyboardOffset(valueAnimator.animatedValue as Float)
        }
        keyboardOffsetAnim = animator
        animator.start()
    }
}
