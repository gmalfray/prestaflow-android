package com.rebuildit.prestaflow.ui.orders

/**
 * Extraction du numéro de suivi à partir d'un contenu brut scanné.
 *
 * Trois cas gérés :
 *
 * 1. **Colissimo / Lettre Max (code-barres 1D)** — le Code 128 d'une étiquette colis encode un payload
 *    de routage qui contient `<préfixe = 1 chiffre + 1 lettre><10 chiffres = serial>` MAIS **sans la clé
 *    de contrôle finale** (noyée dans des données de tri/service/poids).
 *    Ex (brut) : `%0055170115Y0052689628801250` → préfixe `5Y` + serial `0052689628`.
 *    La clé de contrôle est un **check digit type EAN/GS1** (poids 3,1 alternés depuis la droite, mod 10)
 *    calculé sur les 10 chiffres du serial — vérifié sur ~300 vrais numéros de la boutique (5Y/6A/1L).
 *    On la recalcule et on l'ajoute → `5Y00526896286` (13 caractères, le numéro complet trackable).
 *
 * 2. **La Poste « Lettre suivie » / « Courrier suivi » (DataMatrix 2D)** — payload
 *    `%<zéros de padding><14 chiffres = n° de suivi><données date/lot>^<signature>`.
 *    Ex : `%000000087001335318721601250A18^edb7b43` → `87001335318721` (les 14 chiffres ; la lettre de
 *    contrôle imprimée est facultative, La Poste la recalcule).
 *
 * 3. **Code simple / numéro déjà complet** — on nettoie un préfixe « SD : » et on garde le token.
 *
 * Ne lève jamais d'exception ; en dernier recours renvoie le contenu nettoyé (champ éditable).
 */

/** Colissimo/Lettre Max dans un code-barres : 1 chiffre + 1 lettre (préfixe) + 10 chiffres (serial). */
private val COLISSIMO_REGEX = Regex("""(\d[A-Z])(\d{10})""")

/** 14 chiffres consécutifs = n° de suivi La Poste Lettre suivie (après retrait des zéros de padding). */
private val LAPOSTE_14_DIGITS = Regex("""\d{14}""")

/** Repli : token alphanumérique de 11 à 18 caractères (numéro complet collé, etc.). */
private val GENERIC_TRACKING_REGEX = Regex("""([A-Za-z0-9]{11,18})""")

/**
 * Clé de contrôle Colissimo / La Poste colis (check digit style EAN/GS1) :
 * poids 3,1 alternés depuis la droite du serial, somme mod 10, clé = (10 − somme%10) % 10.
 */
internal fun colissimoCheckDigit(serial10: String): Int {
    var sum = 0
    val reversed = serial10.reversed()
    for (i in reversed.indices) {
        val digit = reversed[i] - '0'
        sum += digit * if (i % 2 == 0) 3 else 1
    }
    return (10 - (sum % 10)) % 10
}

fun extractTrackingNumber(raw: String): String {
    if (raw.isBlank()) return raw.trim()

    val trimmed = raw.trim()
    val upper = trimmed.uppercase()

    // Cas 1 — Colissimo / Lettre Max : <chiffre><lettre><10 chiffres> trouvé dans le contenu.
    // Le code-barres 1D omet la clé de contrôle → on la recalcule et on l'ajoute (numéro complet 13 car.).
    // (Doit passer AVANT le cas La Poste : un code Colissimo commence aussi par '%'.)
    COLISSIMO_REGEX.find(upper)?.let { match ->
        val prefix = match.groupValues[1] // ex "5Y"
        val serial = match.groupValues[2] // 10 chiffres
        return prefix + serial + colissimoCheckDigit(serial)
    }

    // Cas 2 — DataMatrix La Poste Lettre suivie : "%<zéros><14 chiffres>...^<signature>".
    if (trimmed.startsWith("%") || trimmed.contains("^")) {
        val core = trimmed.removePrefix("%").substringBefore("^").trimStart('0')
        LAPOSTE_14_DIGITS.find(core)?.let { return it.value }
    }

    // Cas 3 — token générique (numéro simple, numéro complet imprimé, S10 international, etc.).
    val compact = trimmed
        .replace(Regex("""^SD\s*:\s*""", RegexOption.IGNORE_CASE), "")
        .replace(" ", "")
    GENERIC_TRACKING_REGEX.find(compact)?.let { return it.value.uppercase() }

    // Repli — contenu nettoyé, éditable par l'utilisateur.
    return compact.ifBlank { trimmed }
}
