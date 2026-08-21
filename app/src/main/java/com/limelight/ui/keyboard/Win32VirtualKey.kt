package com.limelight.ui.keyboard

/**
 * Fuente única de verdad para Win32 Virtual-Key Codes.
 *
 * Moonlight espera el VK normalizado:
 * 0x8000 | VK
 *
 * No volver a dispersar números mágicos 16, 17, 18, 186, 192, etc.
 */
object Win32VirtualKey {
    const val VK_BACK = 0x08
    const val VK_TAB = 0x09
    const val VK_CLEAR = 0x0C
    const val VK_RETURN = 0x0D

    // Genéricos Win32
    const val VK_SHIFT = 0x10
    const val VK_CONTROL = 0x11
    const val VK_MENU = 0x12 // Alt
    const val VK_PAUSE = 0x13
    const val VK_CAPITAL = 0x14
    const val VK_ESCAPE = 0x1B
    const val VK_SPACE = 0x20
    const val VK_PRIOR = 0x21 // Page Up
    const val VK_NEXT = 0x22  // Page Down
    const val VK_END = 0x23
    const val VK_HOME = 0x24
    const val VK_LEFT = 0x25
    const val VK_UP = 0x26
    const val VK_RIGHT = 0x27
    const val VK_DOWN = 0x28
    const val VK_SNAPSHOT = 0x2C // Print Screen
    const val VK_INSERT = 0x2D
    const val VK_DELETE = 0x2E

    // 0..9
    const val VK_0 = 0x30
    const val VK_1 = 0x31
    const val VK_2 = 0x32
    const val VK_3 = 0x33
    const val VK_4 = 0x34
    const val VK_5 = 0x35
    const val VK_6 = 0x36
    const val VK_7 = 0x37
    const val VK_8 = 0x38
    const val VK_9 = 0x39

    // A..Z
    const val VK_A = 0x41
    const val VK_B = 0x42
    const val VK_C = 0x43
    const val VK_D = 0x44
    const val VK_E = 0x45
    const val VK_F = 0x46
    const val VK_G = 0x47
    const val VK_H = 0x48
    const val VK_I = 0x49
    const val VK_J = 0x4A
    const val VK_K = 0x4B
    const val VK_L = 0x4C
    const val VK_M = 0x4D
    const val VK_N = 0x4E
    const val VK_O = 0x4F
    const val VK_P = 0x50
    const val VK_Q = 0x51
    const val VK_R = 0x52
    const val VK_S = 0x53
    const val VK_T = 0x54
    const val VK_U = 0x55
    const val VK_V = 0x56
    const val VK_W = 0x57
    const val VK_X = 0x58
    const val VK_Y = 0x59
    const val VK_Z = 0x5A

    const val VK_LWIN = 0x5B
    const val VK_RWIN = 0x5C
    const val VK_APPS = 0x5D

    // F1..F24
    const val VK_F1 = 0x70
    const val VK_F2 = 0x71
    const val VK_F3 = 0x72
    const val VK_F4 = 0x73
    const val VK_F5 = 0x74
    const val VK_F6 = 0x75
    const val VK_F7 = 0x76
    const val VK_F8 = 0x77
    const val VK_F9 = 0x78
    const val VK_F10 = 0x79
    const val VK_F11 = 0x7A
    const val VK_F12 = 0x7B
    const val VK_F13 = 0x7C
    const val VK_F14 = 0x7D
    const val VK_F15 = 0x7E
    const val VK_F16 = 0x7F
    const val VK_F17 = 0x80
    const val VK_F18 = 0x81
    const val VK_F19 = 0x82
    const val VK_F20 = 0x83
    const val VK_F21 = 0x84
    const val VK_F22 = 0x85
    const val VK_F23 = 0x86
    const val VK_F24 = 0x87

    const val VK_NUMLOCK = 0x90
    const val VK_SCROLL = 0x91

    // Left/Right reales
    const val VK_LSHIFT = 0xA0
    const val VK_RSHIFT = 0xA1
    const val VK_LCONTROL = 0xA2
    const val VK_RCONTROL = 0xA3
    const val VK_LMENU = 0xA4
    const val VK_RMENU = 0xA5

    // OEM US keyboard
    const val VK_OEM_1 = 0xBA // ; :
    const val VK_OEM_PLUS = 0xBB // = +
    const val VK_OEM_COMMA = 0xBC // , <
    const val VK_OEM_MINUS = 0xBD // - _
    const val VK_OEM_PERIOD = 0xBE // . >
    const val VK_OEM_2 = 0xBF // / ?
    const val VK_OEM_3 = 0xC0 // ` ~
    const val VK_OEM_4 = 0xDB // [ {
    const val VK_OEM_5 = 0xDC // \ |
    const val VK_OEM_6 = 0xDD // ] }
    const val VK_OEM_7 = 0xDE // ' "

    /**
     * Conversión usada actualmente por SmartDisplay/Moonlight:
     * VK -> 0x8000 | VK
     */
    fun toMoonlightCode(vkCode: Int): Short {
        return (0x8000 or (vkCode and 0x7FFF)).toShort()
    }

    fun isModifier(vkCode: Int): Boolean {
        return when (vkCode) {
            VK_SHIFT, VK_LSHIFT, VK_RSHIFT,
            VK_CONTROL, VK_LCONTROL, VK_RCONTROL,
            VK_MENU, VK_LMENU, VK_RMENU,
            VK_LWIN, VK_RWIN -> true
            else -> false
        }
    }
}
