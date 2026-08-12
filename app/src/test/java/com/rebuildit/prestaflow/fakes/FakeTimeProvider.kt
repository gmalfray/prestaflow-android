package com.rebuildit.prestaflow.fakes

import com.rebuildit.prestaflow.core.util.TimeProvider

/**
 * Fake de [TimeProvider] pour les tests JVM : l'horodatage est piloté explicitement via [nowMillis]
 * plutôt que lié à l'horloge système ou au temps virtuel des coroutines (indépendants l'un de
 * l'autre), ce qui permet de tester un throttle basé sur des millisecondes réelles de façon
 * déterministe et instantanée.
 */
class FakeTimeProvider(private var current: Long = 0L) : TimeProvider {
    override fun nowMillis(): Long = current

    fun advanceBy(deltaMs: Long) {
        current += deltaMs
    }
}
