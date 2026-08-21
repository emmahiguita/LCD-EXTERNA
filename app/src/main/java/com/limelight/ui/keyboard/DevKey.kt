package com.limelight.ui.keyboard

/**
 * Tecla del tab DEV ya resuelta a códigos de tecla concretos.
 *
 * La construye [com.limelight.ui.LogicalKeyboardOverlay] a partir del perfil
 * activo de [KeyboardProfileEngine] y la consume
 * [KeyboardLayoutEngine.buildProfiledDevKeyboard].
 *
 * [vkCodes] con más de un elemento se despacha como macro (combinación).
 */
data class DevKey(
    val label: String,
    val vkCodes: List<Int>,
    val tooltip: String
) {
    val isMacro: Boolean get() = vkCodes.size > 1
}
