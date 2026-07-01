package com.rebuildit.prestaflow.ui.orders

/**
 * Utilitaires pour les chips de filtre statut : libellés courts et accessibilité.
 *
 * Le mapping couvre les noms standards PrestaShop FR. La correspondance est insensible
 * à la casse et aux accents (via [normalizeForMatch] déjà défini dans [OrdersViewModel]).
 * Pour un statut non mappé, le fallback retourne le premier mot tronqué à 12 caractères
 * max avec une ellipse propre (pas de troncature violente).
 */

/**
 * Table des correspondances normalisée → libellé court.
 *
 * Ordonnée du plus spécifique au plus générique pour éviter les faux positifs.
 * La recherche utilise [String.contains] sur la chaîne normalisée (sans accents, minuscules).
 */
private val STATUS_LABEL_MAP: List<Pair<String, String>> = listOf(
    "paiement accepte" to "Payé",
    "paiement erreur" to "Erreur",
    "paiement refuse" to "Erreur",
    "cheque" to "Chèque",
    "virement" to "Virement",
    "preparation" to "Prépa",
    "expedi" to "Expédié",
    "livre" to "Livré",
    "termin" to "Terminé",
    "annul" to "Annulé",
    "rembours" to "Remboursé",
    "erreur" to "Erreur",
    "refus" to "Erreur",
)

/** Longueur maximale du libellé court (premier mot, fallback). */
private const val SHORT_LABEL_MAX_LEN = 12

/**
 * Retourne le libellé court à afficher sur un chip de filtre statut.
 *
 * Algorithme :
 * 1. Normalise [name] (minuscules, sans accents) via [normalizeForMatch].
 * 2. Cherche la première entrée de [STATUS_LABEL_MAP] dont la clé est une sous-chaîne du nom normalisé.
 * 3. Si aucune correspondance : prend le premier mot du nom original, tronqué à [SHORT_LABEL_MAX_LEN]
 *    caractères avec une ellipse propre si nécessaire.
 *
 * @param name Nom complet du statut tel que renvoyé par l'API (ex. « En cours de préparation »).
 * @return Libellé court adapté au chip (ex. « Prépa »).
 */
internal fun statusShortLabel(name: String): String {
    if (name.isBlank()) return name
    val normalized = name.normalizeForMatch()
    STATUS_LABEL_MAP.forEach { (key, label) ->
        if (normalized.contains(key)) return label
    }
    // Fallback : premier mot du nom original
    val firstWord = name.trim().split(Regex("\\s+")).firstOrNull() ?: name.trim()
    return if (firstWord.length > SHORT_LABEL_MAX_LEN) {
        "${firstWord.take(SHORT_LABEL_MAX_LEN - 1)}…"
    } else {
        firstWord
    }
}
