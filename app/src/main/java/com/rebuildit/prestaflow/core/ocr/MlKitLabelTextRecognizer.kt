package com.rebuildit.prestaflow.core.ocr

import android.content.Context
import android.graphics.Bitmap
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OCR d'étiquette via ML Kit Text Recognition — variant **Google Play Services**
 * (`com.google.android.gms:play-services-mlkit-text-recognition`), PAS le modèle bundled
 * (`com.google.mlkit:text-recognition`, embarqué dans l'APK).
 *
 * Choix tranché (décision Greg, v0.39.0 — revient sur le choix initial bundled de v0.38.0) :
 * le modèle latin n'est PAS embarqué dans l'APK mais livré par Play Services (téléchargé/partagé
 * entre apps) → **~8 Mo sur l'APK release au lieu de ~48 Mo** avec le variant bundled. Contrepartie
 * assumée : le modèle peut ne pas être encore présent sur l'appareil au tout premier usage.
 * Compensée par DEUX mécanismes complémentaires :
 *  1. [installLabelModelInAdvance] : téléchargement **best-effort en tâche de fond**, déclenché dès
 *     la construction de ce [Singleton] — donc dès la 1ʳᵉ injection Hilt, c'est-à-dire dès l'entrée
 *     sur l'écran réappro (cf. [com.rebuildit.prestaflow.ui.products.StockReplenishViewModel],
 *     seul point d'injection actuel) — pour que le modèle soit déjà prêt avant le premier VRAI
 *     secours OCR de la session. Fire-and-forget : un échec ici (pas de réseau, Play Services
 *     indisponible…) est journalisé et n'empêche RIEN d'autre.
 *  2. [recognize] reste protégé par `runCatching` (comme avant) : si le modèle n'est toujours pas
 *     disponible au moment d'un secours réel (`MlKitException` — modèle absent/en cours de
 *     téléchargement), l'échec est traité comme un texte vide, PAS une exception qui remonterait —
 *     l'appelant ([com.rebuildit.prestaflow.ui.products.StockReplenishViewModel.attemptLabelFallback])
 *     retombe alors silencieusement sur l'association manuelle. Combiné au timeout dur existant côté
 *     appelant (~1,3 s), l'utilisateur ne peut jamais rester bloqué à attendre un téléchargement.
 *
 * [TextRecognizer] créé UNE SEULE FOIS ([Singleton], réutilisé pour tous les scans du processus).
 */
@Singleton
class MlKitLabelTextRecognizer
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : LabelTextRecognizer {
        private val recognizer: TextRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        init {
            installLabelModelInAdvance()
        }

        override suspend fun recognize(bitmap: Bitmap): String =
            runCatching { recognizer.process(InputImage.fromBitmap(bitmap, 0)).await().text }
                .onFailure { Timber.w(it, "Label OCR failed (model possibly not downloaded yet)") }
                .getOrDefault("")

        /**
         * Déclenche le téléchargement du modèle Play Services EN AMONT, best-effort (cf. KDoc
         * classe) — API `ModuleInstall` officielle pour ce variant (PAS un appel `recognize()` à
         * vide : `installModules` est le mécanisme documenté pour précharger sans dépendre d'une
         * image factice). Fire-and-forget via listeners `Task` (pas de coroutine ici : ce singleton
         * n'a pas de portée de cycle de vie à laquelle rattacher un `CoroutineScope`, et cette
         * tâche n'a de toute façon rien à retourner à l'appelant).
         */
        private fun installLabelModelInAdvance() {
            runCatching {
                ModuleInstall
                    .getClient(context)
                    .installModules(ModuleInstallRequest.newBuilder().addApi(recognizer).build())
                    .addOnFailureListener { Timber.w(it, "Label OCR model pre-download failed") }
            }.onFailure { Timber.w(it, "Label OCR model pre-download could not be scheduled") }
        }
    }
