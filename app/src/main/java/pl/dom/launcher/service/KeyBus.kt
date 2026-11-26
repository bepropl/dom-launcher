package pl.dom.launcher.service

import android.view.KeyEvent
import kotlinx.coroutines.flow.MutableStateFlow

data class KeyPress(val keyCode: Int, val atMillis: Long)

/** Łączy usługę przycisków z ekranem ustawień (nauka nowego przycisku pilota). */
object KeyBus {
    /** Ostatni puszczony przycisk pilota, widziany przez usługę ułatwień dostępu. */
    val lastKey = MutableStateFlow<KeyPress?>(null)

    /** Gdy true, usługa łapie następny „specjalny” przycisk zamiast go wykonywać. */
    val learning = MutableStateFlow(false)

    /** Przyciski nawigacji, których nie da się przypisać (bez nich nie dałoby się sterować). */
    private val navigation = setOf(
        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_HOME,
        KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE,
        KeyEvent.KEYCODE_POWER,
    )

    fun isAssignable(keyCode: Int) = keyCode !in navigation
}

/** Kiedy ostatnio ktoś użył pilota – do trybu spoczynku (ochrona OLED). */
object Interaction {
    val lastAt = MutableStateFlow(System.currentTimeMillis())
    val idle = MutableStateFlow(false)

    fun touch() {
        lastAt.value = System.currentTimeMillis()
    }
}
