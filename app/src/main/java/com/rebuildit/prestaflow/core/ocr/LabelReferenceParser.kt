package com.rebuildit.prestaflow.core.ocr

import java.util.Locale

/**
 * Extraction PURE (aucune dépendance Android/ML Kit — testable en JVM simple, cf.
 * `LabelReferenceParserTest`) des jetons d'une étiquette produit qui RESSEMBLENT à une référence, à
 * partir du texte brut reconnu par OCR (cf. [LabelTextRecognizer]). Utilisée en secours du réappro
 * stock quand le code-barres scanné est introuvable (cf.
 * [com.rebuildit.prestaflow.ui.products.StockReplenishViewModel]) : les jetons renvoyés alimentent
 * une recherche produit existante (`GET /products?search=`), best-effort.
 *
 * Heuristique volontairement simple, à ajuster au vu de vraies étiquettes (aucune garantie de
 * précision — c'est un SECOURS, la recherche manuelle reste le filet de sécurité final si rien ne
 * correspond) :
 *  - une LIGNE entière est ignorée si elle contient un mot-bruit connu ([NOISE_KEYWORDS] : lot,
 *    composition, entretien…) ou ressemble à une date ([DATE_REGEX]) — ces lignes ne portent pas la
 *    référence article sur les étiquettes observées (ex. « LOT: BA1234 », « FABRE », « PARTIE 2 »,
 *    « FAB. 07/2026 »).
 *  - dans les lignes restantes, un jeton alphanumérique de [MIN_TOKEN_LENGTH] à [MAX_TOKEN_LENGTH]
 *    caractères est retenu s'il contient AU MOINS UN CHIFFRE (élimine les mots "PELOTE", "LAINE"…
 *    qui n'ont pas de code produit associé, mais garde "049" comme "RICORUMI049").
 *  - dédupliqué en conservant l'ordre d'apparition ; les jetons mêlant lettres ET chiffres (souvent
 *    la vraie référence, ex. "RICORUMI049") sont renvoyés AVANT les jetons tout-chiffres (souvent un
 *    coloris/une taille, plus ambigus, ex. "049" seul) — l'appelant peut ainsi tenter les plus
 *    prometteurs en premier si le nombre de recherches est plafonné.
 */
object LabelReferenceParser {
    private const val MIN_TOKEN_LENGTH = 2
    private const val MAX_TOKEN_LENGTH = 24

    private val TOKEN_REGEX = Regex("[A-Z0-9]{$MIN_TOKEN_LENGTH,$MAX_TOKEN_LENGTH}")

    /** Repère grossier d'une date (jj/mm, jj-mm-aaaa…) — la ligne entière est alors écartée. */
    private val DATE_REGEX = Regex("""\d{1,2}[/.\-]\d{1,2}([/.\-]\d{2,4})?""")

    /** Mots dont la présence sur une ligne exclut TOUTE la ligne (métadonnées, pas une référence). */
    private val NOISE_KEYWORDS =
        listOf(
            "LOT",
            "FABRE",
            "PARTIE",
            "COMPOSITION",
            "COULEUR",
            "COLORIS",
            "LAVAGE",
            "ENTRETIEN",
            "FABRIQUE",
            "ORIGINE",
            "MADE",
            "QUANTITE",
            "POIDS",
            "LONGUEUR",
            "PRIX",
            "CODE BARRE",
        )

    /**
     * @return les jetons candidats extraits de [ocrText], dédupliqués, jetons alphanumériques
     * d'abord puis jetons tout-chiffres — liste vide si rien d'exploitable (OCR illisible/vide).
     */
    fun extractReferenceCandidates(ocrText: String): List<String> {
        if (ocrText.isBlank()) return emptyList()

        val candidateLines =
            ocrText.uppercase(Locale.ROOT).lines().filterNot { line ->
                DATE_REGEX.containsMatchIn(line) || NOISE_KEYWORDS.any { keyword -> line.contains(keyword) }
            }

        val tokens =
            candidateLines
                .flatMap { line -> TOKEN_REGEX.findAll(line).map { match -> match.value } }
                .filter { token -> token.any { it.isDigit() } }
                .distinct()

        val (alphanumeric, pureDigits) = tokens.partition { token -> token.any { it.isLetter() } }
        return alphanumeric + pureDigits
    }
}
