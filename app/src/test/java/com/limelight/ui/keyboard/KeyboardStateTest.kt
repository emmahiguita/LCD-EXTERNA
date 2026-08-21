package com.limelight.ui.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardStateTest {

    @Test
    fun firstCtrlTapIsOneShot() {
        val state = KeyboardState()
        state.toggleModifier("ctrl", nowMs = 1000)
        assertEquals(ModifierMode.ONE_SHOT, state.ctrlMode)
        assertTrue(state.ctrlActive)
    }

    @Test
    fun doubleCtrlTapLocksModifier() {
        val state = KeyboardState()
        state.toggleModifier("ctrl", nowMs = 1000)
        state.toggleModifier("ctrl", nowMs = 1200)
        assertEquals(ModifierMode.LOCKED, state.ctrlMode)
    }

    @Test
    fun oneShotIsConsumed() {
        val state = KeyboardState()
        state.toggleModifier("shift", nowMs = 1000)
        assertTrue(state.shiftActive)
        state.consumeOneShotModifiers()
        assertFalse(state.shiftActive)
    }

    @Test
    fun lockedModifierIsNotConsumed() {
        val state = KeyboardState()
        state.toggleModifier("shift", nowMs = 1000)
        state.toggleModifier("shift", nowMs = 1100)
        assertEquals(ModifierMode.LOCKED, state.shiftMode)
        state.consumeOneShotModifiers()
        assertEquals(ModifierMode.LOCKED, state.shiftMode)
    }

    @Test
    fun releaseAllAlwaysClearsEverything() {
        val state = KeyboardState()
        state.toggleModifier("ctrl", 1000)
        state.toggleModifier("ctrl", 1100)
        state.toggleModifier("alt", 2000)
        state.toggleModifier("shift", 3000)
        state.releaseAllModifiers()
        assertFalse(state.ctrlActive)
        assertFalse(state.altActive)
        assertFalse(state.shiftActive)
        assertFalse(state.metaActive)
    }
}
