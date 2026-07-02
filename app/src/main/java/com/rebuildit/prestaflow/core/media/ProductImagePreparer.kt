package com.rebuildit.prestaflow.core.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

/**
 * Prépare une image (galerie ou appareil photo) pour l'upload vers la fiche produit :
 * redimensionnement raisonnable (max [MAX_DIMENSION] px sur le plus grand côté), correction de
 * la rotation EXIF (photos prises en portrait/paysage) et compression JPEG (~[JPEG_QUALITY]).
 * Les fichiers temporaires sont écrits dans le cache de l'app (nettoyés via [cleanup]).
 */
class ProductImagePreparer(
    private val context: Context,
) {
    data class CameraCaptureTarget(val file: File, val uri: Uri)

    /**
     * Crée un fichier temporaire dans le cache et son URI [FileProvider], à passer à
     * `ActivityResultContracts.TakePicture()` comme destination de la capture.
     */
    fun createCameraCaptureTarget(): CameraCaptureTarget {
        val file = File(imageCacheDir(), "capture_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return CameraCaptureTarget(file, uri)
    }

    /** Prépare une image sélectionnée dans la galerie (URI de contenu). @return null en cas d'échec. */
    suspend fun prepareFromContentUri(uri: Uri): File? =
        withContext(Dispatchers.IO) {
            runCatching {
                val rawFile = File(imageCacheDir(), "raw_${System.currentTimeMillis()}")
                val copied =
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(rawFile).use { output -> input.copyTo(output) }
                        true
                    } ?: false
                if (!copied) return@runCatching null
                try {
                    compressAndRotate(rawFile)
                } finally {
                    rawFile.delete()
                }
            }.onFailure { Timber.w(it, "Failed to prepare gallery image for upload") }.getOrNull()
        }

    /** Prépare une photo fraîchement capturée (fichier déjà sur disque). @return null en cas d'échec. */
    suspend fun prepareFromFile(file: File): File? =
        withContext(Dispatchers.IO) {
            runCatching { compressAndRotate(file) }
                .onFailure { Timber.w(it, "Failed to prepare captured photo for upload") }
                .getOrNull()
        }

    /** Supprime un fichier temporaire (capture annulée, fichier préparé déjà uploadé, etc.). */
    fun cleanup(file: File) {
        runCatching { file.delete() }
    }

    private fun imageCacheDir(): File = File(context.cacheDir, IMAGE_CACHE_DIR).apply { mkdirs() }

    private fun compressAndRotate(sourceFile: File): File {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(sourceFile.path, bounds)
        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = computeInSampleSize(bounds.outWidth, bounds.outHeight) }
        val decoded = BitmapFactory.decodeFile(sourceFile.path, decodeOptions) ?: error("Image illisible : ${sourceFile.name}")
        val sampled = downscaleIfNeeded(decoded)
        val orientation = readOrientation(sourceFile)
        val rotated = applyRotation(sampled, orientation)

        val outFile = File(imageCacheDir(), "upload_${System.currentTimeMillis()}.jpg")
        FileOutputStream(outFile).use { out -> rotated.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out) }
        rotated.recycle()
        if (rotated !== sampled) sampled.recycle()
        return outFile
    }

    private fun readOrientation(file: File): Int =
        runCatching {
            ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

    private fun computeInSampleSize(
        width: Int,
        height: Int,
    ): Int {
        var sampleSize = 1
        var w = width
        var h = height
        while (w / 2 >= MAX_DIMENSION || h / 2 >= MAX_DIMENSION) {
            w /= 2
            h /= 2
            sampleSize *= 2
        }
        return sampleSize
    }

    private fun downscaleIfNeeded(bitmap: Bitmap): Bitmap {
        val largestSide = maxOf(bitmap.width, bitmap.height)
        if (largestSide <= MAX_DIMENSION) return bitmap
        val scale = MAX_DIMENSION.toFloat() / largestSide
        val targetWidth = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
        val targetHeight = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
        if (scaled !== bitmap) bitmap.recycle()
        return scaled
    }

    private fun applyRotation(
        bitmap: Bitmap,
        orientation: Int,
    ): Bitmap {
        val degrees =
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> ROTATE_90
                ExifInterface.ORIENTATION_ROTATE_180 -> ROTATE_180
                ExifInterface.ORIENTATION_ROTATE_270 -> ROTATE_270
                else -> 0f
            }
        if (degrees == 0f) return bitmap
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private companion object {
        const val IMAGE_CACHE_DIR = "product_images"
        const val MAX_DIMENSION = 1600
        const val JPEG_QUALITY = 85
        const val ROTATE_90 = 90f
        const val ROTATE_180 = 180f
        const val ROTATE_270 = 270f
    }
}
