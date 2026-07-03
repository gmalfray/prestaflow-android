package com.rebuildit.prestaflow.core.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OCR d'étiquette via ML Kit Text Recognition — modèle latin **bundled**
 * (`com.google.mlkit:text-recognition`), PAS le variant Google Play Services
 * (`com.google.android.gms:play-services-mlkit-text-recognition`).
 *
 * Choix tranché (contrainte vitesse du secours réappro, cf. KDoc
 * [com.rebuildit.prestaflow.ui.products.StockReplenishViewModel]) : le variant Play Services est
 * beaucoup plus léger côté APK (le modèle est téléchargé à la demande), MAIS peut devoir le
 * télécharger au tout premier scan — plusieurs secondes de latence réseau, inacceptable pour un
 * secours qui doit répondre quasi instantanément ou s'effacer silencieusement (timeout dur côté
 * appelant). Le modèle bundled est embarqué dans l'APK et disponible hors-ligne dès l'installation :
 * aucune latence de premier usage. Contrepartie assumée : **~+4 Mo sur l'APK release** (modèle latin
 * embarqué, non compressible par R8/minify qui n'agit que sur le bytecode/ressources Java, pas sur
 * ce modèle binaire ML Kit).
 *
 * [TextRecognizer] créé UNE SEULE FOIS ([Singleton], réutilisé pour tous les scans du processus,
 * jamais recréé à chaque tentative de secours) : tout warm-up interne du moteur ML Kit est amorti à
 * la première utilisation du processus plutôt que de retarder le premier secours de la session.
 */
@Singleton
class MlKitLabelTextRecognizer
    @Inject
    constructor() : LabelTextRecognizer {
        private val recognizer: TextRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        override suspend fun recognize(bitmap: Bitmap): String =
            runCatching { recognizer.process(InputImage.fromBitmap(bitmap, 0)).await().text }
                .onFailure { Timber.w(it, "Label OCR failed") }
                .getOrDefault("")
    }
