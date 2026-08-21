package com.limelight.ui.keyboard

enum class RemoteKeyPriority {
    PRIMARY,
    SECONDARY,
    ADVANCED
}

enum class RemoteKeySource {
    OFFICIAL,
    INHERITED,
    USER_OVERRIDE,
    CUSTOM,
    UNVERIFIED
}

/**
 * Representación declarativa de una tecla del teclado DEV.
 *
 * La UI muestra [label].
 * La lógica ejecuta [action].
 *
 * Nunca derivar la acción analizando el texto del label.
 */
data class RemoteKeySpec(
    val label: String,
    val action: RemoteKeyAction,
    val tooltip: String,
    val secondaryLabel: String = "",
    val priority: RemoteKeyPriority = RemoteKeyPriority.PRIMARY,
    val source: RemoteKeySource = RemoteKeySource.OFFICIAL
)
