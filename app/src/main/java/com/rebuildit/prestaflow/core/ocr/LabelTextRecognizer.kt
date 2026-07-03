package com.rebuildit.prestaflow.core.ocr

import android.graphics.Bitmap

/**
 * Abstraction OCR de texte d'étiquette produit — implémentation ML Kit on-device dans
 * [MlKitLabelTextRecognizer]. Utilisée en secours du réappro stock quand un code-barres scanné est
 * introuvable (cf. [com.rebuildit.prestaflow.ui.products.StockReplenishViewModel]) : le texte reconnu
 * alimente [com.rebuildit.prestaflow.core.ocr.LabelReferenceParser] pour en extraire des jetons de
 * référence, puis une recherche produit existante.
 */
interface LabelTextRecognizer {
    /**
     * Reconnaît le texte visible sur [bitmap] (concaténation de tous les blocs détectés par le
     * moteur OCR, une ligne source par ligne de résultat).
     * @return le texte reconnu, ou une chaîne vide si rien n'est détecté / en cas d'échec —
     * n'expose JAMAIS d'exception (secours best-effort, cf. appelant).
     */
    suspend fun recognize(bitmap: Bitmap): String
}
