package com.rebuildit.prestaflow.ui.orders

/**
 * Extraction heuristique d'un numéro de suivi à partir d'un contenu brut scanné.
 *
 * Contexte : les étiquettes La Poste « Lettre suivie » / « Courrier suivi » portent un
 * DataMatrix 2D dont le payload exact est propriétaire et non documenté publiquement.
 * Les numéros imprimés suivent le schéma « 13 chiffres + 2 caractères alphanumériques »
 * (ex. `8700124410028 5T` → compact `87001244100285T`).
 *
 * ⚠️ **Note** : le format réel du payload DataMatrix La Poste n'a PAS été confirmé sur
 * un exemplaire décodé — cette extraction est **heuristique**. Si un scan réel révèle un
 * contenu structuré différent (paires clé=valeur, GS1, etc.), il faudra affiner la regex.
 * L'utilisateur voit toujours le résultat dans le champ éditable et peut corriger.
 */

/** Regex : 13 chiffres consécutifs suivis de 2 caractères alphanumériques — format La Poste lettre suivie. */
private val LAPOSTE_TRACKING_REGEX = Regex("""(\d{13}[A-Za-z0-9]{2})""")

/** Regex de repli : token alphanumérique de 11 à 16 caractères (Colissimo colis, UPS, etc.). */
private val GENERIC_TRACKING_REGEX = Regex("""([A-Za-z0-9]{11,16})""")

/**
 * Extrait le numéro de suivi le plus probable de [raw].
 *
 * Algorithme :
 * 1. Trim + suppression du préfixe « SD : »/« SD: » (label imprimé sur certaines étiquettes).
 * 2. Suppression des espaces internes (les numéros La Poste sont souvent affichés avec espace).
 * 3. Recherche du motif La Poste 15 chars (13 chiffres + 2 alnum) → retourné en majuscules.
 * 4. Sinon : recherche d'un token générique alphanumérique 11–16 chars → retourné en majuscules.
 * 5. Sinon : retourne la chaîne nettoyée telle quelle (repli éditable par l'utilisateur).
 *
 * Jamais d'exception, même sur entrée vide ou inattendue.
 */
fun extractTrackingNumber(raw: String): String {
    if (raw.isBlank()) return raw.trim()

    // Normalisation 1 : retire le préfixe éventuel "SD : " ou "SD: " (insensible à la casse)
    val withoutPrefix = raw.trim().replace(Regex("""^SD\s*:\s*""", RegexOption.IGNORE_CASE), "")

    // Normalisation 2 : supprime les espaces internes
    // ("8700124410028 5T" → "87001244100285T")
    val compact = withoutPrefix.replace(" ", "")

    // Étape 3 : motif La Poste lettre suivie — 13 chiffres + 2 alnum
    LAPOSTE_TRACKING_REGEX.find(compact)?.let { return it.value.uppercase() }

    // Étape 4 : token générique alphanumérique 11–16 chars (Colissimo, etc.)
    GENERIC_TRACKING_REGEX.find(compact)?.let { return it.value.uppercase() }

    // Étape 5 : repli — brut nettoyé (l'utilisateur peut corriger à la main)
    return compact.ifBlank { raw.trim() }
}
