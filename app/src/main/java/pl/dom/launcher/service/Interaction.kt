package pl.dom.launcher.service

import kotlinx.coroutines.flow.MutableStateFlow

/** Kiedy ostatnio ktoś użył pilota – do trybu spoczynku (ochrona OLED). */
object Interaction {
    val lastAt = MutableStateFlow(System.currentTimeMillis())
    val idle = MutableStateFlow(false)

    fun touch() {
        lastAt.value = System.currentTimeMillis()
    }
}
