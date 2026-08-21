package com.limelight.ui.keyboard

import com.limelight.ui.keyboard.KeyboardLayoutEngine.Companion.VK_CONTROL
import com.limelight.ui.keyboard.KeyboardLayoutEngine.Companion.VK_SHIFT
import com.limelight.ui.keyboard.KeyboardLayoutEngine.Companion.VK_MENU
import com.limelight.ui.keyboard.KeyboardLayoutEngine.Companion.VK_ESCAPE
import com.limelight.ui.keyboard.KeyboardLayoutEngine.Companion.VK_F5
import com.limelight.ui.keyboard.KeyboardLayoutEngine.Companion.VK_F6
import com.limelight.ui.keyboard.KeyboardLayoutEngine.Companion.VK_F9
import com.limelight.ui.keyboard.KeyboardLayoutEngine.Companion.VK_F10
import com.limelight.ui.keyboard.KeyboardLayoutEngine.Companion.VK_F12

/**
 * Motor de perfiles del tab DEV.
 *
 * Cada perfil devuelve directamente las teclas ya resueltas a códigos de tecla
 * REALES ([DevKey.vkCodes]). No hay re-mapeo por label en la capa de UI: el
 * perfil es la única fuente de verdad de sus atajos (SOLID / SRP).
 *
 * Atajos verificados contra la documentación oficial de cada IDE (keymap
 * Windows por defecto):
 *   VS Code / Antigravity / Open Code (Cursor) → code.visualstudio.com/docs/getstarted/keybindings
 *   IntelliJ IDEA / Android Studio             → jetbrains.com/help/idea/reference-keymap-win-default.html
 *
 * Windows Virtual-Key codes: Ctrl=17, Shift=16, Alt=18. Letras A..Z = 65..90.
 * Dígitos 0..9 = 48..57. VK_OEM_2 (/ ?) = 191. VK_OEM_3 (` ~) = 192.
 */
class KeyboardProfileEngine(
    private val onProfileChanged: () -> Unit
) {
    var currentProfile: String = "default"
        private set

    private val order = listOf("default", "antigravity", "vscode", "opencode", "intellij", "androidstudio")

    fun setProfile(profile: String) {
        if (currentProfile == profile) return
        currentProfile = profile
        onProfileChanged()
    }

    fun cycleProfile() {
        setProfile(order[(order.indexOf(currentProfile) + 1) % order.size])
    }

    fun profileLabel(): String = when (currentProfile) {
        "antigravity"  -> "DEV · Antigravity"
        "vscode"       -> "DEV · VS Code"
        "opencode"     -> "DEV · Open Code"
        "intellij"     -> "DEV · IntelliJ"
        "androidstudio"-> "DEV · Android Studio"
        else           -> "DEV"
    }

    // Helper: crea una tecla con su combo real.
    private fun k(label: String, tooltip: String, vararg codes: Int) =
        DevKey(label, codes.toList(), tooltip)

    fun getDevKeys(profile: String = currentProfile): List<DevKey> = when (profile) {

        // ── Antigravity / Cursor (base VS Code) ──────────────────────────
        "antigravity" -> listOf(
            k("Esc",        "Escape",                            VK_ESCAPE),
            k("F5",         "Ejecutar / Continuar",              VK_F5),
            k("F12",        "Ir a definición",                   VK_F12),
            k("Quick Open", "Ctrl+P — Abrir archivo rápido",     VK_CONTROL, 80),
            k("Terminal",   "Ctrl+Ñ — Alternar terminal",        VK_CONTROL, 192),
            k("Explorer",   "Ctrl+Shift+E — Explorador lateral", VK_CONTROL, VK_SHIFT, 69),
            k("Search",     "Ctrl+Shift+F — Buscar en archivos", VK_CONTROL, VK_SHIFT, 70),
            k("AI Chat",    "Ctrl+Alt+B — Chat IA (Antigravity)",VK_CONTROL, VK_MENU, 66),
            k("AI Edit",    "Ctrl+I — Chat inline IA",           VK_CONTROL, 73),
            k("Format",     "Shift+Alt+F — Formatear documento", VK_SHIFT, VK_MENU, 70),
            k("Save",       "Ctrl+S — Guardar archivo",          VK_CONTROL, 83),
            k("Palette",    "Ctrl+Shift+P — Paleta de comandos", VK_CONTROL, VK_SHIFT, 80),
            k("Git",        "Ctrl+Shift+G — Control de versiones",VK_CONTROL, VK_SHIFT, 71),
            k("Undo",       "Ctrl+Z — Deshacer",                 VK_CONTROL, 90),
            k("Redo",       "Ctrl+Y — Rehacer",                  VK_CONTROL, 89),
            k("Close All",  "Ctrl+K W — Cerrar editores",        VK_CONTROL, 75, 87),
        )

        // ── Visual Studio Code ───────────────────────────────────────────
        "vscode" -> listOf(
            k("Esc",        "Escape",                            VK_ESCAPE),
            k("F5",         "Ejecutar / Continuar",              VK_F5),
            k("F12",        "Ir a definición",                   VK_F12),
            k("Quick Open", "Ctrl+P — Abrir archivo rápido",     VK_CONTROL, 80),
            k("Terminal",   "Ctrl+Ñ — Alternar terminal",        VK_CONTROL, 192),
            k("Explorer",   "Ctrl+Shift+E — Explorador lateral", VK_CONTROL, VK_SHIFT, 69),
            k("Search",     "Ctrl+Shift+F — Buscar en archivos", VK_CONTROL, VK_SHIFT, 70),
            k("AI Chat",    "Ctrl+Alt+B — Copilot Chat",         VK_CONTROL, VK_MENU, 66),
            k("AI Edit",    "Ctrl+I — Chat inline IA",           VK_CONTROL, 73),
            k("Format",     "Shift+Alt+F — Formatear documento", VK_SHIFT, VK_MENU, 70),
            k("Save",       "Ctrl+S — Guardar archivo",          VK_CONTROL, 83),
            k("Palette",    "Ctrl+Shift+P — Paleta de comandos", VK_CONTROL, VK_SHIFT, 80),
            k("Git",        "Ctrl+Shift+G — Control de versiones",VK_CONTROL, VK_SHIFT, 71),
            k("Undo",       "Ctrl+Z — Deshacer",                 VK_CONTROL, 90),
            k("Redo",       "Ctrl+Y — Rehacer",                  VK_CONTROL, 89),
            k("Close All",  "Ctrl+K W — Cerrar editores",        VK_CONTROL, 75, 87),
        )

        // ── Open Code (fork VS Code con IA propia) ───────────────────────
        // Diferencia real vs VS Code/Antigravity: Chat IA usa Ctrl+B (sin Alt).
        "opencode" -> listOf(
            k("Esc",        "Escape",                            VK_ESCAPE),
            k("F5",         "Ejecutar / Continuar",              VK_F5),
            k("F12",        "Ir a definición",                   VK_F12),
            k("Quick Open", "Ctrl+P — Abrir archivo rápido",     VK_CONTROL, 80),
            k("Terminal",   "Ctrl+Ñ — Alternar terminal",        VK_CONTROL, 192),
            k("Explorer",   "Ctrl+Shift+E — Explorador lateral", VK_CONTROL, VK_SHIFT, 69),
            k("Search",     "Ctrl+Shift+F — Buscar en archivos", VK_CONTROL, VK_SHIFT, 70),
            k("AI Chat",    "Ctrl+B — Chat IA (Open Code)",      VK_CONTROL, 66),
            k("AI Edit",    "Ctrl+I — Chat inline IA",           VK_CONTROL, 73),
            k("Format",     "Shift+Alt+F — Formatear documento", VK_SHIFT, VK_MENU, 70),
            k("Save",       "Ctrl+S — Guardar archivo",          VK_CONTROL, 83),
            k("Palette",    "Ctrl+Shift+P — Paleta de comandos", VK_CONTROL, VK_SHIFT, 80),
            k("Git",        "Ctrl+Shift+G — Control de versiones",VK_CONTROL, VK_SHIFT, 71),
            k("Undo",       "Ctrl+Z — Deshacer",                 VK_CONTROL, 90),
            k("Redo",       "Ctrl+Y — Rehacer",                  VK_CONTROL, 89),
            k("Close All",  "Ctrl+K W — Cerrar editores",        VK_CONTROL, 75, 87),
        )

        // ── IntelliJ IDEA (keymap Windows por defecto) ───────────────────
        // Verificado: jetbrains.com/help/idea/reference-keymap-win-default.html
        "intellij" -> listOf(
            k("Esc",        "Escape",                            VK_ESCAPE),
            k("Run",        "Shift+F10 — Ejecutar",              VK_SHIFT, VK_F10),
            k("Debug",      "Shift+F9 — Depurar",                VK_SHIFT, VK_F9),
            k("Go Def",     "Ctrl+B — Ir a declaración",         VK_CONTROL, 66),
            k("Find Act",   "Ctrl+Shift+A — Buscar acción",      VK_CONTROL, VK_SHIFT, 65),
            k("Search",     "Ctrl+Shift+F — Buscar en archivos", VK_CONTROL, VK_SHIFT, 70),
            k("Recent",     "Ctrl+E — Archivos recientes",       VK_CONTROL, 69),
            k("Terminal",   "Alt+F12 — Terminal",                VK_MENU, VK_F12),
            k("Project",    "Alt+1 — Panel de proyecto",         VK_MENU, 49),
            k("Reformat",   "Ctrl+Alt+L — Reformatear código",   VK_CONTROL, VK_MENU, 76),
            k("Rename",     "Shift+F6 — Renombrar",              VK_SHIFT, VK_F6),
            k("Comment",    "Ctrl+/ — Comentar línea",           VK_CONTROL, 191),
            k("Save All",   "Ctrl+S — Guardar todo",             VK_CONTROL, 83),
            k("Git",        "Alt+` — Operaciones VCS",           VK_MENU, 192),
            k("Undo",       "Ctrl+Z — Deshacer",                 VK_CONTROL, 90),
            k("Redo",       "Ctrl+Shift+Z — Rehacer",            VK_CONTROL, VK_SHIFT, 90),
        )

        // ── Android Studio (IntelliJ + herramientas Android) ─────────────
        // Mismo keymap que IntelliJ + Make (Ctrl+F9) y Logcat (Alt+6).
        "androidstudio" -> listOf(
            k("Esc",        "Escape",                            VK_ESCAPE),
            k("Run",        "Shift+F10 — Ejecutar app",          VK_SHIFT, VK_F10),
            k("Debug",      "Shift+F9 — Depurar app",            VK_SHIFT, VK_F9),
            k("Make",       "Ctrl+F9 — Compilar proyecto",       VK_CONTROL, VK_F9),
            k("Logcat",     "Alt+6 — Ventana Logcat",            VK_MENU, 54),
            k("Go Def",     "Ctrl+B — Ir a declaración",         VK_CONTROL, 66),
            k("Find Act",   "Ctrl+Shift+A — Buscar acción",      VK_CONTROL, VK_SHIFT, 65),
            k("Search",     "Ctrl+Shift+F — Buscar en archivos", VK_CONTROL, VK_SHIFT, 70),
            k("Terminal",   "Alt+F12 — Terminal",                VK_MENU, VK_F12),
            k("Reformat",   "Ctrl+Alt+L — Reformatear código",   VK_CONTROL, VK_MENU, 76),
            k("Rename",     "Shift+F6 — Renombrar",              VK_SHIFT, VK_F6),
            k("Comment",    "Ctrl+/ — Comentar línea",           VK_CONTROL, 191),
            k("Save All",   "Ctrl+S — Guardar todo",             VK_CONTROL, 83),
            k("Git",        "Alt+` — Operaciones VCS",           VK_MENU, 192),
            k("Undo",       "Ctrl+Z — Deshacer",                 VK_CONTROL, 90),
            k("Redo",       "Ctrl+Shift+Z — Rehacer",            VK_CONTROL, VK_SHIFT, 90),
        )

        // ── DEV genérico (teclas de función + navegación universal) ─────
        else -> listOf(
            k("Esc",   "Escape",          VK_ESCAPE),
            k("Ins",   "Insert",          KeyboardLayoutEngine.VK_INSERT),
            k("Del",   "Delete",          KeyboardLayoutEngine.VK_DELETE),
            k("Home",  "Inicio de línea", KeyboardLayoutEngine.VK_HOME),
            k("End",   "Fin de línea",    KeyboardLayoutEngine.VK_END),
            k("Win",   "Tecla Windows",   KeyboardLayoutEngine.VK_LWIN),
            k("F1",  "F1",  KeyboardLayoutEngine.VK_F1),
            k("F2",  "F2",  KeyboardLayoutEngine.VK_F2),
            k("F3",  "F3",  KeyboardLayoutEngine.VK_F3),
            k("F4",  "F4",  KeyboardLayoutEngine.VK_F4),
            k("F5",  "F5",  VK_F5),
            k("F6",  "F6",  VK_F6),
            k("F7",  "F7",  KeyboardLayoutEngine.VK_F7),
            k("F8",  "F8",  KeyboardLayoutEngine.VK_F8),
            k("F9",  "F9",  VK_F9),
            k("F10", "F10", VK_F10),
            k("F11", "F11", KeyboardLayoutEngine.VK_F11),
            k("F12", "F12", VK_F12),
            k("PgUp",  "Re Página",       KeyboardLayoutEngine.VK_PRIOR),
            k("PgDn",  "Av Página",       KeyboardLayoutEngine.VK_NEXT),
            k("PrtSc", "Print Screen",    KeyboardLayoutEngine.VK_SNAPSHOT),
            k("Menu",  "Menú contextual", KeyboardLayoutEngine.VK_APPS),
        )
    }
}
