package com.rebuildit.prestaflow.ui.orders

/**
 * Extraction du numéro de suivi à partir d'un contenu brut scanné.
 *
 * Deux cas gérés :
 *
 * 1. **DataMatrix La Poste « Lettre suivie » / « Courrier suivi »** — payload structuré
 *    de la forme `%<zéros de padding><14 chiffres = n° de suivi><données date/lot>^<signature>`.
 *    Exemples réels (décodés via Google Lens) :
 *      `%000000087001335318721601250A18^edb7b43` → `87001335318721`
 *      `%000000087001244100285601250A18^6b3a602` → `87001244100285`
 *    Le n° de suivi La Poste = les **14 chiffres** situés après les zéros de tête. La « lettre »
 *    imprimée en plus (1H / 5T / 8N) est une clé de contrôle FACULTATIVE (modulo 23) que
 *    laposte.fr recalcule lui-même : elle n'est pas dans le code et n'est PAS nécessaire au suivi
 *    (le suivi fonctionne avec les 14 chiffres seuls — vérifié en réel sur laposte.fr).
 *
 * 2. **Code-barres 1D (Colissimo colis, etc.) ou numéro simple** — le contenu EST le numéro :
 *    on retire un préfixe « SD : » éventuel et les espaces, puis on garde le token.
 *
 * Ne lève jamais d'exception ; en dernier recours renvoie le contenu nettoyé (champ éditable).
 */

/** 14 chiffres consécutifs = n° de suivi La Poste (après retrait des zéros de padding). */
private val LAPOSTE_14_DIGITS = Regex("""\d{14}""")

/** Repli : token alphanumérique de 11 à 18 caractères (Colissimo colis, numéro complet collé, etc.). */
private val GENERIC_TRACKING_REGEX = Regex("""([A-Za-z0-9]{11,18})""")

fun extractTrackingNumber(raw: String): String {
    if (raw.isBlank()) return raw.trim()

    val trimmed = raw.trim()

    // Cas 1 — payload DataMatrix La Poste : "%<zéros><14 chiffres>...^<signature>".
    // On isole la partie avant la signature '^', on retire les zéros de tête, puis on lit
    // les 14 premiers chiffres = le numéro de suivi.
    if (trimmed.startsWith("%") || trimmed.contains("^")) {
        val core = trimmed.removePrefix("%").substringBefore("^").trimStart('0')
        LAPOSTE_14_DIGITS.find(core)?.let { return it.value }
    }

    // Normalisation : retire un préfixe "SD :"/"SD:" (insensible à la casse) et les espaces.
    val compact = trimmed
        .replace(Regex("""^SD\s*:\s*""", RegexOption.IGNORE_CASE), "")
        .replace(" ", "")

    // Cas 2 — token générique (code-barres 1D Colissimo, numéro simple).
    GENERIC_TRACKING_REGEX.find(compact)?.let { return it.value.uppercase() }

    // Repli — contenu nettoyé, éditable par l'utilisateur.
    return compact.ifBlank { trimmed }
}
