package com.limelight.ui.effects.glass

import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.view.View

/**
 * GlassCapabilities — Detección inteligente de capacidades de renderizado GPU.
 *
 * Determina el nivel de fidelidad óptica (Tier) adecuado para cada dispositivo:
 *  • LIQUID_FULL  (API 33+): AGSL RuntimeShader + Refracción física + Fresnel + Dispersión
 *  • GLASS_BLUR   (API 31-32): RenderEffect Blur acelerado por GPU + Tinting tonal M3
 *  • FALLBACK     (API <31 o Modo Ahorro / Accesibilidad): Scrim translúcido de cero coste GPU
 */
object GlassCapabilities {

    enum class GlassTier {
        LIQUID_FULL,
        GLASS_BLUR,
        FALLBACK
    }

    /**
     * Evalúa el nivel de fidelidad óptico óptimo para el contexto y vista actuales.
     */
    fun resolveTier(view: View): GlassTier {
        val context = view.context

        // 1. Verificar si la vista tiene aceleración por hardware
        if (!view.isHardwareAccelerated && view.layerType != View.LAYER_TYPE_HARDWARE) {
            return GlassTier.FALLBACK
        }

        // 2. Verificar si el usuario tiene activo el modo de Ahorro de Batería
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        if (powerManager?.isPowerSaveMode == true) {
            return GlassTier.FALLBACK
        }

        // 3. Verificar si el usuario ha solicitado reducción de transparencia por accesibilidad
        if (isReduceTransparencyEnabled(context)) {
            return GlassTier.FALLBACK
        }

        // 4. Selección por nivel de API de Android
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> GlassTier.LIQUID_FULL
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> GlassTier.GLASS_BLUR
            else -> GlassTier.FALLBACK
        }
    }

    private fun isReduceTransparencyEnabled(context: Context): Boolean {
        return try {
            // Android 14+ setting o fallback seguro
            Settings.Secure.getInt(
                context.contentResolver,
                "reduce_transparency",
                0
            ) == 1
        } catch (_: Exception) {
            false
        }
    }
}
