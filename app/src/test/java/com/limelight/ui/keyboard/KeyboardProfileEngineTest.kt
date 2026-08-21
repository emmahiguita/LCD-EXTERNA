package com.limelight.ui.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardProfileEngineTest {

    private fun engine() = KeyboardProfileEngine {}

    private fun action(
        profile: String,
        label: String
    ): RemoteKeyAction {
        return engine()
            .getDevKeys(profile)
            .first { it.label == label }
            .action
    }

    @Test
    fun vscodeCloseAllIsRealSequence() {
        val action = action(
            KeyboardProfileEngine.PROFILE_VSCODE,
            "Close All"
        )
        assertTrue(action is RemoteKeyAction.Sequence)
        val sequence = action as RemoteKeyAction.Sequence
        assertEquals(2, sequence.actions.size)
        val first = sequence.actions[0]
        assertTrue(first is RemoteKeyAction.Chord)
        first as RemoteKeyAction.Chord
        assertEquals(Win32VirtualKey.VK_K, first.vkCode)
        assertEquals(listOf(RemoteModifier.CTRL), first.modifiers)
        val second = sequence.actions[1]
        assertEquals(RemoteKeyAction.Key(Win32VirtualKey.VK_W), second)
    }

    @Test
    fun vscodeKeymapIsTwoCtrlChords() {
        val action = action(
            KeyboardProfileEngine.PROFILE_VSCODE,
            "Keymap"
        ) as RemoteKeyAction.Sequence
        val first = action.actions[0] as RemoteKeyAction.Chord
        val second = action.actions[1] as RemoteKeyAction.Chord
        assertEquals(Win32VirtualKey.VK_K, first.vkCode)
        assertEquals(listOf(RemoteModifier.CTRL), first.modifiers)
        assertEquals(Win32VirtualKey.VK_S, second.vkCode)
        assertEquals(listOf(RemoteModifier.CTRL), second.modifiers)
    }

    @Test
    fun openCodeEditorUsesLeaderSequence() {
        val action = action(
            KeyboardProfileEngine.PROFILE_OPENCODE,
            "Editor"
        )
        assertTrue(action is RemoteKeyAction.Sequence)
        val sequence = action as RemoteKeyAction.Sequence
        assertEquals(2, sequence.actions.size)
        val leader = sequence.actions[0] as RemoteKeyAction.Chord
        assertEquals(Win32VirtualKey.VK_X, leader.vkCode)
        assertEquals(listOf(RemoteModifier.CTRL), leader.modifiers)
        assertEquals(
            RemoteKeyAction.Key(Win32VirtualKey.VK_E),
            sequence.actions[1]
        )
    }

    @Test
    fun androidStudioSearchEverywhereIsDoubleShift() {
        val action = action(
            KeyboardProfileEngine.PROFILE_ANDROID_STUDIO,
            "Search All"
        )
        assertTrue(action is RemoteKeyAction.DoubleTap)
        action as RemoteKeyAction.DoubleTap
        assertEquals(Win32VirtualKey.VK_LSHIFT, action.vkCode)
    }

    @Test
    fun intellijSearchEverywhereIsDoubleShift() {
        val action = action(
            KeyboardProfileEngine.PROFILE_INTELLIJ,
            "Search All"
        )
        assertTrue(action is RemoteKeyAction.DoubleTap)
    }

    @Test
    fun vscodeChatIsCtrlAltI() {
        val action = action(
            KeyboardProfileEngine.PROFILE_VSCODE,
            "Chat"
        )
        assertTrue(action is RemoteKeyAction.Chord)
        action as RemoteKeyAction.Chord
        assertEquals(Win32VirtualKey.VK_I, action.vkCode)
        assertEquals(
            listOf(RemoteModifier.CTRL, RemoteModifier.ALT),
            action.modifiers
        )
    }

    @Test
    fun antigravityTerminalIsCtrlJ() {
        val action = action(
            KeyboardProfileEngine.PROFILE_ANTIGRAVITY,
            "Terminal"
        )
        assertTrue(action is RemoteKeyAction.Chord)
        action as RemoteKeyAction.Chord
        assertEquals(Win32VirtualKey.VK_J, action.vkCode)
        assertEquals(listOf(RemoteModifier.CTRL), action.modifiers)
    }

    @Test
    fun vscodeTerminalIsCtrlJ() {
        val action = action(
            KeyboardProfileEngine.PROFILE_VSCODE,
            "Terminal"
        )
        assertTrue(action is RemoteKeyAction.Chord)
        action as RemoteKeyAction.Chord
        assertEquals(Win32VirtualKey.VK_J, action.vkCode)
        assertEquals(listOf(RemoteModifier.CTRL), action.modifiers)
    }
}
