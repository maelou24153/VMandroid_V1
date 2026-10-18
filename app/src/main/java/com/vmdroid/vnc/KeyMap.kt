package com.vmdroid.vnc

import android.view.KeyEvent

/** Conversion des touches Android vers les « keysyms » X11 utilisés par VNC. */
object KeyMap {
    const val BACKSPACE = 0xff08
    const val TAB = 0xff09
    const val RETURN = 0xff0d
    const val ESCAPE = 0xff1b
    const val DELETE = 0xffff
    const val LEFT = 0xff51
    const val UP = 0xff52
    const val RIGHT = 0xff53
    const val DOWN = 0xff54
    const val SHIFT = 0xffe1
    const val CTRL = 0xffe3
    const val ALT = 0xffe9
    const val SUPER = 0xffeb

    fun fromChar(cp: Int): Int = when {
        cp == '\n'.code -> RETURN
        cp == '\t'.code -> TAB
        cp in 0x20..0x7e || cp in 0xa0..0xff -> cp
        else -> 0x01000000 + cp
    }

    fun fromEvent(e: KeyEvent): Int = when (val kc = e.keyCode) {
        KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> RETURN
        KeyEvent.KEYCODE_DEL -> BACKSPACE
        KeyEvent.KEYCODE_FORWARD_DEL -> DELETE
        KeyEvent.KEYCODE_TAB -> TAB
        KeyEvent.KEYCODE_ESCAPE -> ESCAPE
        KeyEvent.KEYCODE_DPAD_LEFT -> LEFT
        KeyEvent.KEYCODE_DPAD_UP -> UP
        KeyEvent.KEYCODE_DPAD_RIGHT -> RIGHT
        KeyEvent.KEYCODE_DPAD_DOWN -> DOWN
        KeyEvent.KEYCODE_MOVE_HOME -> 0xff50
        KeyEvent.KEYCODE_MOVE_END -> 0xff57
        KeyEvent.KEYCODE_PAGE_UP -> 0xff55
        KeyEvent.KEYCODE_PAGE_DOWN -> 0xff56
        KeyEvent.KEYCODE_INSERT -> 0xff63
        KeyEvent.KEYCODE_SHIFT_LEFT -> 0xffe1
        KeyEvent.KEYCODE_SHIFT_RIGHT -> 0xffe2
        KeyEvent.KEYCODE_CTRL_LEFT -> 0xffe3
        KeyEvent.KEYCODE_CTRL_RIGHT -> 0xffe4
        KeyEvent.KEYCODE_ALT_LEFT -> 0xffe9
        KeyEvent.KEYCODE_ALT_RIGHT -> 0xffea
        KeyEvent.KEYCODE_META_LEFT -> 0xffeb
        KeyEvent.KEYCODE_META_RIGHT -> 0xffec
        KeyEvent.KEYCODE_CAPS_LOCK -> 0xffe5
        KeyEvent.KEYCODE_SPACE -> 0x20
        in KeyEvent.KEYCODE_F1..KeyEvent.KEYCODE_F12 -> 0xffbe + (kc - KeyEvent.KEYCODE_F1)
        else -> {
            val shiftState = e.metaState and (KeyEvent.META_SHIFT_ON or KeyEvent.META_CAPS_LOCK_ON)
            val u = e.getUnicodeChar(shiftState)
            if (u > 0 && u and KeyCharacterMapCombining == 0) fromChar(u) else 0
        }
    }

    private const val KeyCharacterMapCombining = 0x80000000.toInt()
}
