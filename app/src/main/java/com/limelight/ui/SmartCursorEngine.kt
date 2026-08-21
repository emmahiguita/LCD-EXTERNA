package com.limelight.ui

/**
 * SmartCursorEngine
 * ─────────────────
 * Fase 3: Motor de cursor contextual escrito en Kotlin.
 * Recibe metadatos REALES del PC (el companion envía `focus_changed` con el tipo
 * de elemento bajo el foco, vía TextFocusWatcher) y orquesta los estados de
 * `AdaptiveCursorView`. No usa datos simulados.
 */
class SmartCursorEngine(private val cursorView: AdaptiveCursorView) {

    enum class ElementType {
        NONE, TEXT_INPUT, BUTTON, LINK, IDE_WORKSPACE
    }

    private var currentHoverElement = ElementType.NONE

    /**
     * Actualiza el tipo de elemento sobre el que está el cursor.
     * En el futuro, esto se llamará desde un modelo de IA local (MediaPipe) 
     * o desde metadatos de red del PC anfitrión.
     */
    fun onHoverElementChanged(type: ElementType) {
        if (currentHoverElement == type) return
        currentHoverElement = type
        
        when (type) {
            ElementType.NONE -> cursorView.setCursorState(AdaptiveCursorView.CursorState.NORMAL)
            ElementType.TEXT_INPUT -> cursorView.setCursorState(AdaptiveCursorView.CursorState.TEXT)
            ElementType.BUTTON -> cursorView.setCursorState(AdaptiveCursorView.CursorState.BUTTON)
            ElementType.LINK -> cursorView.setCursorState(AdaptiveCursorView.CursorState.BUTTON) // Para links usamos estilo botón
            ElementType.IDE_WORKSPACE -> cursorView.setCursorState(AdaptiveCursorView.CursorState.NORMAL) // Manejado vía popup en Fase 3
        }
    }

    private var lastCursorX = -1f
    private var lastCursorY = -1f
    private var lastTime = 0L
    private var isSnapped = false

    /**
     * Evalúa si es necesario aplicar Smart Snap a un botón cercano
     * @param cursorX Coordenada X actual
     * @param cursorY Coordenada Y actual
     * @param targetX Coordenada X del botón detectado por IA
     * @param targetY Coordenada Y del botón detectado por IA
     */
    fun evaluateSmartSnap(cursorX: Float, cursorY: Float, targetX: Float, targetY: Float) {
        val now = System.currentTimeMillis()
        var velocity = 0f
        
        if (lastCursorX >= 0 && lastTime > 0) {
            val dt = (now - lastTime) / 1000f
            if (dt >= 0.01f) {
                val mx = cursorX - lastCursorX
                val my = cursorY - lastCursorY
                velocity = Math.sqrt((mx * mx + my * my).toDouble()).toFloat() / dt
            }
        }
        
        lastCursorX = cursorX
        lastCursorY = cursorY
        lastTime = now

        val dx = targetX - cursorX
        val dy = targetY - cursorY
        val distance = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()

        // Velocity Escapement: Si el usuario mueve el ratón muy rápido (> 600px/s),
        // rompemos la fuerza magnética para que pueda escapar del botón.
        if (velocity > 600f) {
            isSnapped = false
            return
        }

        // Si el cursor está a menos de 100px del botón, aplicamos fuerza magnética
        if (distance < 100f && distance > 5f) {
            // Usamos el factor dinámico de magnetismo (configurable por app)
            val force = cursorView.magnetismFactor
            if (force > 0f) {
                cursorView.smartSnapTo(targetX, targetY, force)
                isSnapped = true
            }
        } else if (distance >= 100f) {
            isSnapped = false
        }
    }
}
