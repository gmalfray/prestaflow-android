package com.rebuildit.prestaflow.core.util

import java.text.Normalizer

/**
 * Normalise une chaîne pour la comparaison insensible à la casse et aux accents.
 * `"Paiement accepté"` → `"paiement accepte"`.
 */
fun String.normalizeForMatch(): String =
    Normalizer.normalize(this, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}"), "")
        .lowercase()
        .trim()
