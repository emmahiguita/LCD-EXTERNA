package com.limelight.ui.keyboard

/**
 * Perfiles DEV declarativos.
 *
 * Windows / Linux default mappings.
 *
 * Los perfiles ya NO están expresados como intArrayOf().
 */
class KeyboardProfileEngine(
    private val onProfileChanged: () -> Unit
) {
    var currentProfile: String = PROFILE_DEFAULT
        private set

    private val order = listOf(
        PROFILE_DEFAULT,
        PROFILE_ANTIGRAVITY,
        PROFILE_VSCODE,
        PROFILE_OPENCODE,
        PROFILE_INTELLIJ,
        PROFILE_ANDROID_STUDIO
    )

    fun setProfile(profile: String) {
        if (profile == currentProfile || !order.contains(profile)) {
            return
        }
        currentProfile = profile
        onProfileChanged()
    }

    fun cycleProfile() {
        val currentIndex = order.indexOf(currentProfile)
        val nextIndex = (currentIndex + 1) % order.size
        setProfile(order[nextIndex])
    }

    fun profileLabel(): String {
        return when (currentProfile) {
            PROFILE_ANTIGRAVITY -> "DEV · Antigravity"
            PROFILE_VSCODE -> "DEV · VS Code"
            PROFILE_OPENCODE -> "DEV · OpenCode"
            PROFILE_INTELLIJ -> "DEV · IntelliJ"
            PROFILE_ANDROID_STUDIO -> "DEV · Android Studio"
            else -> "DEV"
        }
    }

    fun getDevKeys(profile: String = currentProfile): List<DevKey> {
        return when (profile) {
            PROFILE_ANTIGRAVITY -> antigravityKeys()
            PROFILE_VSCODE -> vsCodeKeys()
            PROFILE_OPENCODE -> openCodeKeys()
            PROFILE_INTELLIJ -> intelliJKeys()
            PROFILE_ANDROID_STUDIO -> androidStudioKeys()
            else -> universalKeys()
        }
    }

    // ------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------

    private fun key(
        label: String,
        shortcut: String,
        tooltip: String,
        vkCode: Int,
        source: RemoteKeySource = RemoteKeySource.OFFICIAL
    ): RemoteKeySpec {
        return RemoteKeySpec(
            label = label,
            secondaryLabel = shortcut,
            action = RemoteKeyAction.Key(vkCode),
            tooltip = "$shortcut — $tooltip",
            source = source
        )
    }

    private fun chord(
        label: String,
        shortcut: String,
        tooltip: String,
        vkCode: Int,
        vararg modifiers: RemoteModifier,
        source: RemoteKeySource = RemoteKeySource.OFFICIAL
    ): RemoteKeySpec {
        return RemoteKeySpec(
            label = label,
            secondaryLabel = shortcut,
            action = RemoteKeyAction.Chord(
                vkCode = vkCode,
                modifiers = modifiers.toList()
            ),
            tooltip = "$shortcut — $tooltip",
            source = source
        )
    }

    private fun sequence(
        label: String,
        shortcut: String,
        tooltip: String,
        actions: List<RemoteKeyAction>,
        source: RemoteKeySource = RemoteKeySource.OFFICIAL
    ): RemoteKeySpec {
        return RemoteKeySpec(
            label = label,
            secondaryLabel = shortcut,
            action = RemoteKeyAction.Sequence(actions = actions),
            tooltip = "$shortcut — $tooltip",
            source = source
        )
    }

    private fun doubleTap(
        label: String,
        shortcut: String,
        tooltip: String,
        vkCode: Int
    ): RemoteKeySpec {
        return RemoteKeySpec(
            label = label,
            secondaryLabel = shortcut,
            action = RemoteKeyAction.DoubleTap(vkCode = vkCode),
            tooltip = "$shortcut — $tooltip",
            source = RemoteKeySource.OFFICIAL
        )
    }

    private fun ctrl(vkCode: Int) = RemoteKeyAction.Chord(vkCode, listOf(RemoteModifier.CTRL))
    private fun alt(vkCode: Int) = RemoteKeyAction.Chord(vkCode, listOf(RemoteModifier.ALT))

    // ------------------------------------------------------------
    // ANTIGRAVITY
    // ------------------------------------------------------------

    /**
     * Acciones AI/navigation tomadas de la documentación oficial
     * actual de Google Antigravity.
     * Las acciones estándar del editor se marcan INHERITED.
     */
    private fun antigravityKeys(): List<DevKey> {
        return listOf(
            key("Esc", "Esc", "Cancelar / volver", Win32VirtualKey.VK_ESCAPE),
            chord("Agent", "Ctrl+Alt+B", "Toggle Agent Chat", Win32VirtualKey.VK_B, RemoteModifier.CTRL, RemoteModifier.ALT),
            chord("Focus", "Ctrl+L", "Llevar foco al input del agente", Win32VirtualKey.VK_L, RemoteModifier.CTRL),
            chord("Explorer", "Ctrl+Shift+E", "Abrir / cerrar Explorer", Win32VirtualKey.VK_E, RemoteModifier.CTRL, RemoteModifier.SHIFT),
            chord("Files", "Ctrl+P", "Buscar archivo (Quick Open)", Win32VirtualKey.VK_P, RemoteModifier.CTRL),
            chord("Convs", "Ctrl+K", "Abrir Conversation Picker", Win32VirtualKey.VK_K, RemoteModifier.CTRL),
            chord("New Conv", "Ctrl+N", "Nueva conversación", Win32VirtualKey.VK_N, RemoteModifier.CTRL),
            chord("Model", "Ctrl+/", "Cambiar modelo IA", Win32VirtualKey.VK_OEM_2, RemoteModifier.CTRL),
            chord("Prev Conv", "Alt+↑", "Conversación anterior", Win32VirtualKey.VK_UP, RemoteModifier.ALT),
            chord("Next Conv", "Alt+↓", "Conversación siguiente", Win32VirtualKey.VK_DOWN, RemoteModifier.ALT),
            chord("Palette", "Ctrl+Shift+P", "Command Palette del editor", Win32VirtualKey.VK_P, RemoteModifier.CTRL, RemoteModifier.SHIFT, source = RemoteKeySource.INHERITED),
            chord("Search", "Ctrl+Shift+F", "Buscar en archivos", Win32VirtualKey.VK_F, RemoteModifier.CTRL, RemoteModifier.SHIFT, source = RemoteKeySource.INHERITED),
            chord("Terminal", "Ctrl+J", "Terminal integrado / Panel", Win32VirtualKey.VK_J, RemoteModifier.CTRL, source = RemoteKeySource.INHERITED),
            chord("Git", "Ctrl+Shift+G", "Source Control", Win32VirtualKey.VK_G, RemoteModifier.CTRL, RemoteModifier.SHIFT, source = RemoteKeySource.INHERITED),
            chord("Save", "Ctrl+S", "Guardar", Win32VirtualKey.VK_S, RemoteModifier.CTRL, source = RemoteKeySource.INHERITED),
            chord("Undo", "Ctrl+Z", "Deshacer", Win32VirtualKey.VK_Z, RemoteModifier.CTRL, source = RemoteKeySource.INHERITED)
        )
    }

    // ------------------------------------------------------------
    // VS CODE
    // ------------------------------------------------------------

    private fun vsCodeKeys(): List<DevKey> {
        return listOf(
            key("Esc", "Esc", "Cancelar / cerrar", Win32VirtualKey.VK_ESCAPE),
            chord("Chat", "Ctrl+Alt+I", "Open Chat view", Win32VirtualKey.VK_I, RemoteModifier.CTRL, RemoteModifier.ALT),
            chord("Agent", "Ctrl+Shift+I", "Abrir Chat en Agent Mode", Win32VirtualKey.VK_I, RemoteModifier.CTRL, RemoteModifier.SHIFT),
            chord("Inline", "Ctrl+I", "Inline Chat", Win32VirtualKey.VK_I, RemoteModifier.CTRL),
            chord("Quick Open", "Ctrl+P", "Quick Open", Win32VirtualKey.VK_P, RemoteModifier.CTRL),
            chord("Palette", "Ctrl+Shift+P", "Command Palette", Win32VirtualKey.VK_P, RemoteModifier.CTRL, RemoteModifier.SHIFT),
            chord("Explorer", "Ctrl+Shift+E", "Explorer", Win32VirtualKey.VK_E, RemoteModifier.CTRL, RemoteModifier.SHIFT),
            chord("Search", "Ctrl+Shift+F", "Search", Win32VirtualKey.VK_F, RemoteModifier.CTRL, RemoteModifier.SHIFT),
            chord("Git", "Ctrl+Shift+G", "Source Control", Win32VirtualKey.VK_G, RemoteModifier.CTRL, RemoteModifier.SHIFT),
            chord("Terminal", "Ctrl+J", "Toggle Integrated Terminal / Panel", Win32VirtualKey.VK_J, RemoteModifier.CTRL),
            chord("Format", "Shift+Alt+F", "Format Document", Win32VirtualKey.VK_F, RemoteModifier.SHIFT, RemoteModifier.ALT),
            chord("Save", "Ctrl+S", "Guardar archivo", Win32VirtualKey.VK_S, RemoteModifier.CTRL),
            key("Rename", "F2", "Rename Symbol", Win32VirtualKey.VK_F2),
            key("Go Def", "F12", "Go to Definition", Win32VirtualKey.VK_F12),
            sequence("Close All", "Ctrl+K → W", "Cerrar todos los editores", listOf(ctrl(Win32VirtualKey.VK_K), RemoteKeyAction.Key(Win32VirtualKey.VK_W))),
            sequence("Keymap", "Ctrl+K → Ctrl+S", "Abrir Keyboard Shortcuts", listOf(ctrl(Win32VirtualKey.VK_K), ctrl(Win32VirtualKey.VK_S)))
        )
    }

    // ------------------------------------------------------------
    // OPENCODE TUI
    // ------------------------------------------------------------

    private fun openCodeKeys(): List<DevKey> {
        return listOf(
            key("Esc", "Esc", "Interrumpir sesión / volver", Win32VirtualKey.VK_ESCAPE),
            chord("Commands", "Ctrl+P", "Lista de comandos", Win32VirtualKey.VK_P, RemoteModifier.CTRL),
            sequence("Editor", "Ctrl+X → E", "Abrir editor", listOf(ctrl(Win32VirtualKey.VK_X), RemoteKeyAction.Key(Win32VirtualKey.VK_E))),
            sequence("Theme", "Ctrl+X → T", "Lista de temas", listOf(ctrl(Win32VirtualKey.VK_X), RemoteKeyAction.Key(Win32VirtualKey.VK_T))),
            sequence("Sidebar", "Ctrl+X → B", "Mostrar/ocultar sidebar", listOf(ctrl(Win32VirtualKey.VK_X), RemoteKeyAction.Key(Win32VirtualKey.VK_B))),
            sequence("Status", "Ctrl+X → S", "Status View", listOf(ctrl(Win32VirtualKey.VK_X), RemoteKeyAction.Key(Win32VirtualKey.VK_S))),
            sequence("New Session", "Ctrl+X → N", "Nueva sesión", listOf(ctrl(Win32VirtualKey.VK_X), RemoteKeyAction.Key(Win32VirtualKey.VK_N))),
            sequence("Sessions", "Ctrl+X → L", "Lista de sesiones", listOf(ctrl(Win32VirtualKey.VK_X), RemoteKeyAction.Key(Win32VirtualKey.VK_L))),
            sequence("Timeline", "Ctrl+X → G", "Timeline de sesión", listOf(ctrl(Win32VirtualKey.VK_X), RemoteKeyAction.Key(Win32VirtualKey.VK_G))),
            sequence("Export", "Ctrl+X → X", "Exportar sesión", listOf(ctrl(Win32VirtualKey.VK_X), RemoteKeyAction.Key(Win32VirtualKey.VK_X))),
            sequence("Exit", "Ctrl+X → Q", "Salir de OpenCode", listOf(ctrl(Win32VirtualKey.VK_X), RemoteKeyAction.Key(Win32VirtualKey.VK_Q))),
            chord("Line Start", "Ctrl+A", "Inicio de línea", Win32VirtualKey.VK_A, RemoteModifier.CTRL),
            chord("Line End", "Ctrl+E", "Final de línea", Win32VirtualKey.VK_E, RemoteModifier.CTRL),
            chord("Cursor ←", "Ctrl+B", "Mover cursor hacia atrás", Win32VirtualKey.VK_B, RemoteModifier.CTRL),
            chord("Cursor →", "Ctrl+F", "Mover cursor hacia delante", Win32VirtualKey.VK_F, RemoteModifier.CTRL),
            chord("Del Word", "Ctrl+W", "Eliminar palabra anterior", Win32VirtualKey.VK_W, RemoteModifier.CTRL)
        )
    }

    // ------------------------------------------------------------
    // INTELLIJ IDEA
    // ------------------------------------------------------------

    private fun intelliJKeys(): List<DevKey> {
        return listOf(
            key("Esc", "Esc", "Volver al editor", Win32VirtualKey.VK_ESCAPE),
            doubleTap("Search All", "Shift ×2", "Search Everywhere", Win32VirtualKey.VK_LSHIFT),
            chord("Find Act", "Ctrl+Shift+A", "Find Action", Win32VirtualKey.VK_A, RemoteModifier.CTRL, RemoteModifier.SHIFT),
            chord("Go Def", "Ctrl+B", "Go to Declaration", Win32VirtualKey.VK_B, RemoteModifier.CTRL),
            chord("Usages", "Alt+F7", "Find Usages", Win32VirtualKey.VK_F7, RemoteModifier.ALT),
            chord("Project", "Alt+1", "Project Tool Window", Win32VirtualKey.VK_1, RemoteModifier.ALT),
            chord("Recent", "Ctrl+E", "Recent Files", Win32VirtualKey.VK_E, RemoteModifier.CTRL),
            chord("Terminal", "Alt+F12", "Terminal", Win32VirtualKey.VK_F12, RemoteModifier.ALT),
            chord("Reformat", "Ctrl+Alt+L", "Reformat Code", Win32VirtualKey.VK_L, RemoteModifier.CTRL, RemoteModifier.ALT),
            chord("Rename", "Shift+F6", "Rename", Win32VirtualKey.VK_F6, RemoteModifier.SHIFT),
            chord("Quick Fix", "Alt+Enter", "Intention Actions / Quick Fix", Win32VirtualKey.VK_RETURN, RemoteModifier.ALT),
            chord("Generate", "Alt+Insert", "Generate Code", Win32VirtualKey.VK_INSERT, RemoteModifier.ALT),
            chord("Comment", "Ctrl+/", "Toggle Line Comment", Win32VirtualKey.VK_OEM_2, RemoteModifier.CTRL),
            chord("Run", "Shift+F10", "Run", Win32VirtualKey.VK_F10, RemoteModifier.SHIFT),
            chord("Debug", "Shift+F9", "Debug", Win32VirtualKey.VK_F9, RemoteModifier.SHIFT),
            chord("VCS", "Alt+`", "VCS Operations", Win32VirtualKey.VK_OEM_3, RemoteModifier.ALT),
            chord("Save", "Ctrl+S", "Save All", Win32VirtualKey.VK_S, RemoteModifier.CTRL),
            chord("Redo", "Ctrl+Shift+Z", "Redo", Win32VirtualKey.VK_Z, RemoteModifier.CTRL, RemoteModifier.SHIFT)
        )
    }

    // ------------------------------------------------------------
    // ANDROID STUDIO
    // ------------------------------------------------------------

    private fun androidStudioKeys(): List<DevKey> {
        return listOf(
            key("Esc", "Esc", "Return to Editor", Win32VirtualKey.VK_ESCAPE),
            doubleTap("Search All", "Shift ×2", "Search Everywhere", Win32VirtualKey.VK_LSHIFT),
            chord("Run", "Shift+F10", "Run app", Win32VirtualKey.VK_F10, RemoteModifier.SHIFT),
            chord("Debug", "Shift+F9", "Debug app", Win32VirtualKey.VK_F9, RemoteModifier.SHIFT),
            chord("Make", "Ctrl+F9", "Make Project", Win32VirtualKey.VK_F9, RemoteModifier.CTRL),
            chord("Project", "Alt+1", "Project Tool Window", Win32VirtualKey.VK_1, RemoteModifier.ALT),
            chord("Logcat", "Alt+6", "Logcat", Win32VirtualKey.VK_6, RemoteModifier.ALT),
            chord("VCS", "Alt+9", "Version Control", Win32VirtualKey.VK_9, RemoteModifier.ALT),
            chord("Find Act", "Ctrl+Shift+A", "Find Action", Win32VirtualKey.VK_A, RemoteModifier.CTRL, RemoteModifier.SHIFT),
            chord("Find Path", "Ctrl+Shift+F", "Find in Path", Win32VirtualKey.VK_F, RemoteModifier.CTRL, RemoteModifier.SHIFT),
            chord("Go Def", "Ctrl+B", "Go to Declaration", Win32VirtualKey.VK_B, RemoteModifier.CTRL),
            chord("Complete", "Ctrl+Space", "Basic Completion", Win32VirtualKey.VK_SPACE, RemoteModifier.CTRL),
            chord("Smart Comp", "Ctrl+Shift+Space", "Smart Completion", Win32VirtualKey.VK_SPACE, RemoteModifier.CTRL, RemoteModifier.SHIFT),
            chord("Statement", "Ctrl+Shift+Enter", "Complete Current Statement", Win32VirtualKey.VK_RETURN, RemoteModifier.CTRL, RemoteModifier.SHIFT),
            chord("Quick Fix", "Alt+Enter", "Quick Fix / Intention Action", Win32VirtualKey.VK_RETURN, RemoteModifier.ALT),
            chord("Generate", "Alt+Insert", "Generate Code", Win32VirtualKey.VK_INSERT, RemoteModifier.ALT),
            chord("Reformat", "Ctrl+Alt+L", "Reformat Code", Win32VirtualKey.VK_L, RemoteModifier.CTRL, RemoteModifier.ALT),
            chord("Rename", "Shift+F6", "Rename", Win32VirtualKey.VK_F6, RemoteModifier.SHIFT),
            chord("Terminal", "Alt+F12", "Terminal", Win32VirtualKey.VK_F12, RemoteModifier.ALT),
            chord("Usages", "Alt+F7", "Find Usages", Win32VirtualKey.VK_F7, RemoteModifier.ALT)
        )
    }

    // ------------------------------------------------------------
    // UNIVERSAL
    // ------------------------------------------------------------

    private fun universalKeys(): List<DevKey> {
        return listOf(
            key("Esc", "Esc", "Escape", Win32VirtualKey.VK_ESCAPE),
            key("Ins", "Insert", "Insert", Win32VirtualKey.VK_INSERT),
            key("Del", "Delete", "Delete", Win32VirtualKey.VK_DELETE),
            key("Home", "Home", "Inicio", Win32VirtualKey.VK_HOME),
            key("End", "End", "Final", Win32VirtualKey.VK_END),
            key("Win", "Win", "Windows", Win32VirtualKey.VK_LWIN),
            key("F1", "F1", "F1", Win32VirtualKey.VK_F1),
            key("F2", "F2", "F2", Win32VirtualKey.VK_F2),
            key("F3", "F3", "F3", Win32VirtualKey.VK_F3),
            key("F4", "F4", "F4", Win32VirtualKey.VK_F4),
            key("F5", "F5", "F5", Win32VirtualKey.VK_F5),
            key("F6", "F6", "F6", Win32VirtualKey.VK_F6),
            key("F7", "F7", "F7", Win32VirtualKey.VK_F7),
            key("F8", "F8", "F8", Win32VirtualKey.VK_F8),
            key("F9", "F9", "F9", Win32VirtualKey.VK_F9),
            key("F10", "F10", "F10", Win32VirtualKey.VK_F10),
            key("F11", "F11", "F11", Win32VirtualKey.VK_F11),
            key("F12", "F12", "F12", Win32VirtualKey.VK_F12),
            key("PgUp", "Page Up", "Página anterior", Win32VirtualKey.VK_PRIOR),
            key("PgDn", "Page Down", "Página siguiente", Win32VirtualKey.VK_NEXT),
            key("PrtSc", "Print Screen", "Captura de pantalla", Win32VirtualKey.VK_SNAPSHOT),
            key("Menu", "Menu", "Menú contextual", Win32VirtualKey.VK_APPS)
        )
    }

    companion object {
        const val PROFILE_DEFAULT = "default"
        const val PROFILE_ANTIGRAVITY = "antigravity"
        const val PROFILE_VSCODE = "vscode"
        const val PROFILE_OPENCODE = "opencode"
        const val PROFILE_INTELLIJ = "intellij"
        const val PROFILE_ANDROID_STUDIO = "androidstudio"
    }
}
