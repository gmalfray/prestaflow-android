package com.rebuildit.prestaflow.core.util

import javax.inject.Inject

/**
 * Source d'horloge injectable pour le code métier (ViewModels...) qui a besoin de mesurer un délai
 * écoulé (ex. throttle d'un rafraîchissement automatique). Indirection nécessaire pour rendre ce
 * genre de logique testable avec un temps virtuel/contrôlé — `System.currentTimeMillis()` en dur
 * ne peut pas être piloté depuis un test JVM utilisant `StandardTestDispatcher` (temps virtuel des
 * coroutines, indépendant de l'horloge système).
 */
interface TimeProvider {
    /** Horodatage courant en millisecondes (référentiel identique à [System.currentTimeMillis]). */
    fun nowMillis(): Long
}

/** Implémentation par défaut adossée à l'horloge système. */
class SystemTimeProvider
    @Inject
    constructor() : TimeProvider {
        override fun nowMillis(): Long = System.currentTimeMillis()
    }
