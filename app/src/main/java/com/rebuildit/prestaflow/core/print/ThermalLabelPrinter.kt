package com.rebuildit.prestaflow.core.print

import android.bluetooth.BluetoothManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.dantsu.escposprinter.connection.bluetooth.BluetoothConnection
import com.dantsu.escposprinter.EscPosPrinter
import com.dantsu.escposprinter.textparser.PrinterTextParserImg
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

/**
 * Impression d'une étiquette d'expédition (PDF 10×15 cm) sur une imprimante thermique
 * Bluetooth ESC/POS via la bibliothèque DantSu ESCPOS-ThermalPrinter-Android.
 *
 * ─── Paramètres matériel MUNBYN ITPP941B ───────────────────────────────────────────
 * - Résolution : 203 dpi
 * - Largeur papier : 4 pouces = 101,6 mm → zone d'impression effective ~104 mm
 *   (les drivers MUNBYN exposent 104 mm comme largeur maximale utilisable)
 * - Vitesse : 150 mm/s (non configurable ici, réglé dans l'imprimante)
 *
 * ─── Dimensions bitmap ──────────────────────────────────────────────────────────────
 * - Largeur : [BITMAP_WIDTH_PX]  = 4 " × 203 dpi = 812 px
 * - Hauteur : [BITMAP_HEIGHT_PX] = 6 " × 203 dpi = 1218 px  (étiquette 10×15 cm)
 *
 * ─── Ajustement pour le test réel ──────────────────────────────────────────────────
 * Si l'étiquette est tronquée en largeur → réduire [PRINTER_WIDTH_MM] (ex. 102f).
 * Si l'image est étirée/compressée → ajuster [PRINTER_CHARS_PER_LINE] ou [BITMAP_WIDTH_PX].
 * Si le rendu est flou → vérifier que [PdfRenderer.Page.RENDER_MODE_FOR_PRINT] est bien utilisé.
 */
object ThermalLabelPrinter {
    /** Résolution de l'imprimante thermique en points par pouce. */
    const val PRINTER_DPI = 203

    /**
     * Largeur effective de la zone d'impression en millimètres.
     * Pour MUNBYN ITPP941B 4" : 4 × 25,4 = 101,6 mm — on utilise 104 mm pour inclure
     * la marge interne déclarée par le driver de la lib ESC/POS.
     * Ajuster à 101f si les bords sont coupés lors du test réel.
     */
    const val PRINTER_WIDTH_MM = 104f

    /**
     * Nombre de caractères par ligne à 8×16 pt (utilisé par la lib pour calculer
     * le facteur d'échelle de l'image). Valeur standard pour 104 mm / 203 dpi.
     * Ajuster si l'image est mal proportionnée (généralement 33 ou 42).
     */
    const val PRINTER_CHARS_PER_LINE = 33

    /** Largeur cible du bitmap généré (4 " × 203 dpi). */
    const val BITMAP_WIDTH_PX = 812

    /** Hauteur cible du bitmap généré (6 " × 203 dpi — étiquette 10×15 cm / 4×6"). */
    const val BITMAP_HEIGHT_PX = 1218

    /**
     * Rend la **page 1** du PDF [pdfBytes] en [Bitmap] puis l'envoie à l'imprimante
     * thermique identifiée par son adresse MAC [macAddress].
     *
     * Toutes les opérations I/O (PDF rendering, socket Bluetooth) s'exécutent sur
     * [Dispatchers.IO]. En cas d'échec, une exception est propagée à l'appelant.
     *
     * @param context    Contexte Android (pour écriture fichier temporaire + BluetoothManager).
     * @param pdfBytes   Contenu binaire du PDF de l'étiquette.
     * @param macAddress Adresse MAC de l'imprimante cible (ex. "AA:BB:CC:DD:EE:FF").
     */
    suspend fun print(
        context: Context,
        pdfBytes: ByteArray,
        macAddress: String,
    ) {
        withContext(Dispatchers.IO) {
            val bitmap = renderFirstPage(context, pdfBytes)
            try {
                printBitmap(context, bitmap, macAddress)
            } finally {
                bitmap.recycle()
            }
        }
    }

    /**
     * Rend la première page du PDF en [Bitmap] aux dimensions [BITMAP_WIDTH_PX] × [BITMAP_HEIGHT_PX].
     * Fond blanc, rendu qualité impression ([PdfRenderer.Page.RENDER_MODE_FOR_PRINT]).
     */
    internal fun renderFirstPage(
        context: Context,
        pdfBytes: ByteArray,
    ): Bitmap {
        val tmp = File(context.cacheDir, "thermal_label_tmp.pdf")
        tmp.writeBytes(pdfBytes)
        val pfd = ParcelFileDescriptor.open(tmp, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(pfd)
        try {
            val page = renderer.openPage(0)
            val bitmap = Bitmap.createBitmap(BITMAP_WIDTH_PX, BITMAP_HEIGHT_PX, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)
            // Matrice null → rendu pleine page (page mise à l'échelle dans les dimensions bitmap)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
            page.close()
            return bitmap
        } finally {
            renderer.close()
            runCatching { tmp.delete() }
        }
    }

    /**
     * Envoie [bitmap] à l'imprimante Bluetooth identifiée par [macAddress] via ESC/POS raster.
     *
     * La lib DantSu convertit le bitmap en séquences ESC/POS `GS v 0` (raster bitimage)
     * via [PrinterTextParserImg.bitmapToHexadecimalString], puis les envoie sur le socket BT.
     *
     * @throws android.bluetooth.BluetoothAdapter.LeScanCallback Si l'imprimante est introuvable.
     * @throws com.dantsu.escposprinter.exceptions.EscPosConnectionException Si la connexion échoue.
     */
    @Suppress("TooGenericExceptionCaught") // Bibliothèque tierce — exceptions non typées rethrown
    private fun printBitmap(
        context: Context,
        bitmap: Bitmap,
        macAddress: String,
    ) {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            ?: throw IllegalStateException("BluetoothManager indisponible")
        val adapter = bluetoothManager.adapter
            ?: throw IllegalStateException("Bluetooth non supporté sur cet appareil")

        @Suppress("MissingPermission") // Permission vérifiée en amont dans le composable Route
        val device = adapter.bondedDevices.firstOrNull { it.address.equals(macAddress, ignoreCase = true) }
            ?: throw IllegalArgumentException("Imprimante non trouvée parmi les appareils appairés : $macAddress")

        @Suppress("MissingPermission")
        Timber.d("Connexion à l'imprimante thermique : ${device.name} ($macAddress)")

        val connection = BluetoothConnection(device)
        val printer = EscPosPrinter(connection, PRINTER_DPI, PRINTER_WIDTH_MM, PRINTER_CHARS_PER_LINE)
        try {
            val imageHex = PrinterTextParserImg.bitmapToHexadecimalString(printer, bitmap)
            printer.printFormattedTextAndCut("[C]<img>$imageHex</img>\n")
        } catch (e: Exception) {
            Timber.e(e, "Erreur lors de l'impression thermique")
            throw e
        } finally {
            runCatching { printer.disconnectPrinter() }
                .onFailure { Timber.w(it, "Erreur fermeture connexion imprimante") }
        }
    }

    /**
     * Convertit un [Bitmap] ARGB en bitmap noir & blanc (1bpp simulé en ARGB_8888)
     * pour réduire la taille des données ESC/POS envoyées.
     * Actuellement non utilisé — [PrinterTextParserImg] gère lui-même la binarisation.
     * Conservé comme utilitaire si l'optimisation devient nécessaire.
     */
    internal fun toBinaryBitmap(src: Bitmap): Bitmap {
        val dst = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(dst)
        val paint = Paint().apply {
            colorFilter = android.graphics.ColorMatrixColorFilter(
                android.graphics.ColorMatrix().also { it.setSaturation(0f) },
            )
        }
        canvas.drawBitmap(src, 0f, 0f, paint)
        return dst
    }
}
