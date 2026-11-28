package pl.dom.launcher.service

import kotlinx.coroutines.flow.MutableStateFlow

/** Czy launcher jest teraz na ekranie (powiadomienia: dymek w launcherze albo nad inną aplikacją). */
object AppVisibility {
    @Volatile var launcherVisible = false
}

/**
 * Przytrzymanie OK, które coś otwiera (np. podgląd serialu): dalsze powtórzenia i puszczenie
 * tego samego OK nie mogą „kliknąć” w to, co właśnie dostało fokus.
 */
object OkGuard {
    @Volatile var held = false
    @Volatile var swallow = false

    /** Wołać, gdy przytrzymanie OK otworzyło nowy ekran. */
    fun consumeRest() {
        if (held) swallow = true
    }
}

/** Kiedy ostatnio ktoś użył pilota – do trybu spoczynku (ochrona OLED). */
object Interaction {
    val lastAt = MutableStateFlow(System.currentTimeMillis())
    val idle = MutableStateFlow(false)

    fun touch() {
        lastAt.value = System.currentTimeMillis()
    }
}
