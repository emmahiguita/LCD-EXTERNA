package com.limelight.ui.effects.glass

import android.graphics.Color
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.view.View
import androidx.annotation.RequiresApi

/**
 * LiquidGlassEngine — Motor óptico de refracción, Fresnel y dispersión cromática.
 *
 * Utiliza RuntimeShader con AGSL en Android 13+ (API 33) y RenderEffect Blur en Android 12 (API 31-32).
 */
class LiquidGlassEngine {

    companion object {
        // AGSL Shader con cálculo SDF de caja redondeada, refracción normal y Fresnel rim
        private const val AGSL_LIQUID_GLASS_SRC = """
            uniform shader u_backdrop;
            uniform float2 u_bounds;
            uniform float  u_cornerRadius;
            uniform float  u_refractionStrength;
            uniform float  u_rimIntensity;
            uniform float4 u_tintColor;
            uniform float2 u_touchPoint;
            uniform float  u_pressBulge;

            // Función de Distancia Firmada (SDF) para rectángulos con bordes redondeados
            float sdfRoundedRect(vec2 p, vec2 halfSize, float r) {
                vec2 q = abs(p) - halfSize + vec2(r);
                return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
            }

            vec4 main(vec2 fragCoord) {
                vec2 halfSize = u_bounds * 0.5;
                vec2 p = fragCoord - halfSize;
                float r = clamp(u_cornerRadius, 0.0, min(halfSize.x, halfSize.y));
                
                // Distancia a la superficie del cristal
                float d = sdfRoundedRect(p, halfSize, r);
                if (d > 0.0) {
                    return vec4(0.0);
                }

                // Deformación física gel/agua al presionar
                float distToTouch = length(fragCoord - u_touchPoint);
                float wave = exp(-distToTouch * 0.04) * u_pressBulge;

                // Gradiente aproximado de la normal
                float eps = 1.0;
                float dx = sdfRoundedRect(p + vec2(eps, 0.0), halfSize, r) - d;
                float dy = sdfRoundedRect(p + vec2(0.0, eps), halfSize, r) - d;
                vec2 normal = normalize(vec2(dx, dy) + vec2(wave * 0.5));

                // Refracción y dispersión cromática sutil en los bordes
                float edgeFactor = smoothstep(-14.0, 0.0, d);
                float refrStrength = u_refractionStrength * edgeFactor;
                vec2 refrOffset = normal * refrStrength;

                // Muestreo del fondo con micro-dispersión espectral
                vec4 colR = u_backdrop.eval(fragCoord + refrOffset * 1.04);
                vec4 colG = u_backdrop.eval(fragCoord + refrOffset);
                vec4 colB = u_backdrop.eval(fragCoord + refrOffset * 0.96);
                vec3 sampled = vec3(colR.r, colG.g, colB.b);

                // Highlight Fresnel (borde superior e iluminación especular)
                float fresnel = pow(edgeFactor, 2.5) * u_rimIntensity;
                vec3 rimLight = vec3(1.0) * fresnel;

                // Fusión con el tinte tonal oscuro del sistema
                vec3 finalColor = mix(sampled, u_tintColor.rgb, u_tintColor.a) + rimLight;
                return vec4(finalColor, 1.0);
            }
        """
    }

    private var runtimeShader: RuntimeShader? = null
    private var lastWidth = 0
    private var lastHeight = 0

    var cornerRadius: Float = 24f
    var refractionStrength: Float = 8f
    var rimIntensity: Float = 0.35f
    var tintColor: Int = Color.argb(180, 16, 22, 30) // #B410161E Tonal Dark Surface

    var touchPointX: Float = -1000f
    var touchPointY: Float = -1000f
    var pressBulge: Float = 0f

    /**
     * Adapta el tinte y el rim según el modo claro u oscuro del tema actual.
     */
    fun adaptToTheme(context: android.content.Context) {
        val isLight = com.limelight.utils.ThemeManager.isLight(context)
        if (isLight) {
            // Vidrio claro: blanco helado translúcido con menor refracción rim
            tintColor = Color.argb(150, 255, 255, 255)
            rimIntensity = 0.20f
        } else {
            // Vidrio oscuro: superficie tonal profunda de SmartDisplay
            tintColor = Color.argb(180, 16, 22, 30)
            rimIntensity = 0.35f
        }
    }

    /**
     * Aplica el efecto de vidrio sobre la vista según el nivel de soporte de hardware.
     */
    fun applyToView(view: View, tier: GlassCapabilities.GlassTier) {
        val width = view.width.toFloat()
        val height = view.height.toFloat()
        if (width <= 0 || height <= 0) return

        adaptToTheme(view.context)

        when (tier) {
            GlassCapabilities.GlassTier.LIQUID_FULL -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    applyAgslShader(view, width, height)
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    applyRenderEffectBlur(view)
                }
            }
            GlassCapabilities.GlassTier.GLASS_BLUR -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    applyRenderEffectBlur(view)
                }
            }
            GlassCapabilities.GlassTier.FALLBACK -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    view.setRenderEffect(null)
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun applyAgslShader(view: View, width: Float, height: Float) {
        if (runtimeShader == null) {
            try {
                runtimeShader = RuntimeShader(AGSL_LIQUID_GLASS_SRC)
            } catch (_: Exception) {
                // Fallback seguro a RenderEffect si el compilador AGSL del dispositivo falla
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    applyRenderEffectBlur(view)
                }
                return
            }
        }

        runtimeShader?.let { shader ->
            shader.setFloatUniform("u_bounds", width, height)
            shader.setFloatUniform("u_cornerRadius", cornerRadius)
            shader.setFloatUniform("u_refractionStrength", refractionStrength)
            shader.setFloatUniform("u_rimIntensity", rimIntensity)

            val a = Color.alpha(tintColor) / 255f
            val r = Color.red(tintColor) / 255f
            val g = Color.green(tintColor) / 255f
            val b = Color.blue(tintColor) / 255f
            shader.setColorUniform("u_tintColor", Color.valueOf(r, g, b, a))

            shader.setFloatUniform("u_touchPoint", touchPointX, touchPointY)
            shader.setFloatUniform("u_pressBulge", pressBulge)

            val effect = RenderEffect.createRuntimeShaderEffect(shader, "u_backdrop")
            view.setRenderEffect(effect)
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun applyRenderEffectBlur(view: View) {
        val blurEffect = RenderEffect.createBlurEffect(
            22f,
            22f,
            Shader.TileMode.CLAMP
        )
        view.setRenderEffect(blurEffect)
    }
}
