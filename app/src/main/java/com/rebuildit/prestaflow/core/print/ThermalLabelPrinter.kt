package com.rebuildit.prestaflow.core.print

import android.bluetooth.BluetoothManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.Deflater
import kotlin.math.roundToInt

/**
 * Impression d'une étiquette d'expédition (PDF) sur imprimante thermique Bluetooth **MUNBYN
 * ITPP941B** (et compatibles) en **langage TSPL** (TSC Printer Language).
 *
 * ─── Pourquoi TSPL et pas ESC/POS ──────────────────────────────────────────────────
 * La capture du trafic Bluetooth de l'app constructeur (btsnoop HCI) a montré que l'ITPP941B
 * n'est PAS une imprimante ESC/POS : elle parle **TSPL** sur un canal RFCOMM/SPP classique.
 * Le job réel observé :
 *   SIZE 102 mm,76 mm / DIRECTION 0,0 / SET GAP ON / SPEED 4.0 / DENSITY 5 / REFERENCE 0,0 / CLS
 *   BITMAP 0,0,<octets/ligne>,<hauteur dots>,3,<taille compressée>,<données 1bpp compressées zlib>
 *   PRINT 1,1
 * Le mode 3 de BITMAP = bitmap monochrome **compressé zlib** (en-tête 0x78 0x9c), reproductible
 * à l'identique via [Deflater]. Résolution : **8 dots/mm** (203 dpi).
 *
 * ─── Géométrie ──────────────────────────────────────────────────────────────────────
 * Le PDF est rendu à la largeur d'impression cible ([PRINT_WIDTH_DOTS] = 816 dots = 102 mm),
 * hauteur calculée pour conserver le ratio. Ajuster [PRINT_WIDTH_DOTS] si l'étiquette déborde.
 */
// Calculs pixel/luminance : coefficients (77/150/29), masques de bits et conversions d'unités
// (25.4 mm/pouce, 72 pt/pouce) sont des constantes standard, explicites dans leur contexte.
@Suppress("MagicNumber")
object ThermalLabelPrinter {
    /** Résolution de l'imprimante : 8 dots par millimètre (≈ 203 dpi). */
    const val DOTS_PER_MM = 8

    /** Largeur d'impression cible en dots (816 = 102 octets/ligne = 102 mm pour une 4"). */
    const val PRINT_WIDTH_DOTS = 816

    /** Seuil de binarisation (0..255) : sous ce niveau de luminance → point noir. */
    private const val LUMINANCE_THRESHOLD = 128

    /**
     * Rend la 1ʳᵉ page de [pdfBytes] et l'imprime sur l'imprimante TSPL [macAddress].
     * Toutes les I/O (rendu PDF, socket Bluetooth) sur [Dispatchers.IO].
     */
    suspend fun print(
        context: Context,
        pdfBytes: ByteArray,
        macAddress: String,
    ) {
        withContext(Dispatchers.IO) {
            val bitmap = renderFirstPage(context, pdfBytes)
            try {
                val tspl = buildTsplJob(bitmap)
                sendToPrinter(context, tspl, macAddress)
            } finally {
                bitmap.recycle()
            }
        }
    }

    /** Largeur du rendu d'analyse (px) servant à détecter la zone de contenu (bbox). */
    private const val ANALYZE_WIDTH = 720

    /** Marge (en px du rendu d'analyse) conservée autour du contenu détecté. */
    private const val CROP_MARGIN = 10

    /**
     * Rend la première page du PDF **directement à la résolution d'impression** sur la zone de
     * contenu détectée, sans redimensionnement bilinéaire intermédiaire (qui produisait un moiré
     * de rayures verticales sur les codes-barres). Deux passes sur la même page :
     *  1. rendu d'analyse basse résolution → détection de la bbox du contenu ;
     *  2. rendu net de cette bbox, mis à l'échelle à [PRINT_WIDTH_DOTS] via une matrice.
     *
     * Le recadrage gère les bordereaux fournis en A4 (ex. Mondial Relay) où l'étiquette n'occupe
     * qu'une partie de la page.
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

            // Passe 1 : rendu d'analyse pour trouver la zone de contenu
            val analyzeHeight = (ANALYZE_WIDTH.toFloat() * page.height / page.width).roundToInt().coerceAtLeast(1)
            val analyze = Bitmap.createBitmap(ANALYZE_WIDTH, analyzeHeight, Bitmap.Config.ARGB_8888)
            analyze.eraseColor(Color.WHITE)
            page.render(analyze, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
            val bounds = contentBounds(analyze)
            analyze.recycle()

            // bbox (px analyse) → coordonnées page (points), avec marge
            val pxPerPointX = ANALYZE_WIDTH.toFloat() / page.width
            val pxPerPointY = analyzeHeight.toFloat() / page.height
            val cropXpt = bounds.left / pxPerPointX
            val cropYpt = bounds.top / pxPerPointY
            val cropWpt = (bounds.width()).coerceAtLeast(1) / pxPerPointX
            val cropHpt = (bounds.height()).coerceAtLeast(1) / pxPerPointY

            // Passe 2 : rendu net de la zone recadrée à la largeur d'impression
            val finalWidth = PRINT_WIDTH_DOTS
            val finalHeight = (finalWidth.toFloat() * cropHpt / cropWpt).roundToInt().coerceAtLeast(1)
            val sx = finalWidth / cropWpt
            val sy = finalHeight / cropHpt
            val matrix =
                Matrix().apply {
                    setScale(sx, sy)
                    postTranslate(-cropXpt * sx, -cropYpt * sy)
                }
            val out = Bitmap.createBitmap(finalWidth, finalHeight, Bitmap.Config.ARGB_8888)
            out.eraseColor(Color.WHITE)
            page.render(out, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
            page.close()
            return out
        } finally {
            renderer.close()
            runCatching { tmp.delete() }
        }
    }

    /**
     * Retourne la zone (bbox, en px) contenant des pixels sombres dans [src], élargie de
     * [CROP_MARGIN], ou la page entière si elle est vide. Élimine les grandes marges (cas A4).
     */
    @Suppress("NestedBlockDepth")
    private fun contentBounds(src: Bitmap): Rect {
        val w = src.width
        val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        var minX = w
        var minY = h
        var maxX = -1
        var maxY = -1
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                val p = pixels[row + x]
                val luma = (((p shr 16) and 0xFF) * 77 + ((p shr 8) and 0xFF) * 150 + (p and 0xFF) * 29) shr 8
                if (luma < LUMINANCE_THRESHOLD) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }
        if (maxX < minX || maxY < minY) return Rect(0, 0, w, h) // page vide → pas de recadrage
        return Rect(
            (minX - CROP_MARGIN).coerceAtLeast(0),
            (minY - CROP_MARGIN).coerceAtLeast(0),
            (maxX + CROP_MARGIN).coerceAtMost(w - 1) + 1,
            (maxY + CROP_MARGIN).coerceAtMost(h - 1) + 1,
        )
    }

    /**
     * Construit le job TSPL complet (en-tête + BITMAP compressé zlib + PRINT) pour [bitmap].
     */
    internal fun buildTsplJob(bitmap: Bitmap): ByteArray {
        val widthBytes = (bitmap.width + 7) / 8
        val heightDots = bitmap.height
        val mono = toMonochrome1Bpp(bitmap, widthBytes)
        val compressed = deflateZlib(mono)

        val widthMm = (bitmap.width.toFloat() / DOTS_PER_MM).roundToInt()
        val heightMm = (heightDots.toFloat() / DOTS_PER_MM).roundToInt()

        val out = ByteArrayOutputStream()
        // En-tête TSPL (calqué sur la capture de l'app constructeur)
        out.write(
            (
                "SIZE $widthMm mm,$heightMm mm\r\n" +
                    "DIRECTION 0,0\r\n" +
                    "SET GAP ON\r\n" +
                    "SPEED 4.0\r\n" +
                    "DENSITY 5\r\n" +
                    "REFERENCE 0,0\r\n" +
                    "CLS\r\n" +
                    // mode 3 = bitmap compressé zlib ; suivi de la taille compressée puis des données
                    "BITMAP 0,0,$widthBytes,$heightDots,3,${compressed.size},"
            ).toByteArray(Charsets.US_ASCII),
        )
        out.write(compressed)
        out.write("\r\nPRINT 1,1\r\n".toByteArray(Charsets.US_ASCII))
        return out.toByteArray()
    }

    /**
     * Convertit [bitmap] en données monochrome 1 bit/pixel, paddées à [widthBytes] octets/ligne.
     * Convention TSPL : bit **1 = blanc** (pas de point), bit **0 = noir** (point imprimé), MSB d'abord.
     */
    private fun toMonochrome1Bpp(
        bitmap: Bitmap,
        widthBytes: Int,
    ): ByteArray {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val out = ByteArray(widthBytes * height)
        for (y in 0 until height) {
            val rowOffset = y * widthBytes
            for (x in 0 until width) {
                val p = pixels[y * width + x]
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                // Luminance perceptuelle (entiers) ; pixels transparents déjà fond blanc
                val luma = (r * 77 + g * 150 + b * 29) shr 8
                if (luma >= LUMINANCE_THRESHOLD) {
                    // blanc → bit 1
                    out[rowOffset + (x ushr 3)] =
                        (out[rowOffset + (x ushr 3)].toInt() or (0x80 ushr (x and 7))).toByte()
                }
                // noir → bit 0 (déjà 0 par défaut)
            }
        }
        return out
    }

    /** Compresse [data] en flux zlib/deflate (en-tête 0x78 0x9c), identique au format attendu. */
    internal fun deflateZlib(data: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.DEFAULT_COMPRESSION)
        deflater.setInput(data)
        deflater.finish()
        val out = ByteArrayOutputStream(data.size / 4)
        val buffer = ByteArray(8192)
        while (!deflater.finished()) {
            val n = deflater.deflate(buffer)
            out.write(buffer, 0, n)
        }
        deflater.end()
        return out.toByteArray()
    }

    /** Ouvre la connexion RFCOMM robuste et envoie le job [tspl] brut à l'imprimante. */
    private fun sendToPrinter(
        context: Context,
        tspl: ByteArray,
        macAddress: String,
    ) {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            ?: error("BluetoothManager indisponible")
        val adapter = bluetoothManager.adapter ?: error("Bluetooth non supporté sur cet appareil")
        val device =
            runCatching { adapter.getRemoteDevice(macAddress) }
                .getOrElse { throw IllegalArgumentException("Adresse d'imprimante invalide : $macAddress", it) }

        @Suppress("MissingPermission") // Permissions BLUETOOTH_CONNECT/SCAN vérifiées en amont (UI)
        Timber.d("Impression TSPL vers %s (%d octets)", macAddress, tspl.size)

        val connection = RobustBluetoothConnection(device, adapter)
        try {
            connection.connect()
            connection.writeRaw(tspl)
            // Laisse le temps au tampon d'impression de partir avant la fermeture du socket
            Thread.sleep(WRITE_DRAIN_MS)
        } finally {
            connection.disconnect()
        }
    }

    /** Délai après écriture avant fermeture du socket (évite de tronquer l'envoi). */
    private const val WRITE_DRAIN_MS = 600L
}
