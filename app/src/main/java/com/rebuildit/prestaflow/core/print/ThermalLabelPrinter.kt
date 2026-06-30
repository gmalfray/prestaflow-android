package com.rebuildit.prestaflow.core.print

import android.bluetooth.BluetoothManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
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

    /** Largeur de rendu maximale (dots) avant recadrage, pour borner la mémoire sur grandes pages A4. */
    private const val MAX_RENDER_WIDTH = 1700

    /** Marge (en dots de l'image de rendu) conservée autour du contenu détecté lors du recadrage. */
    private const val CROP_MARGIN = 12

    /**
     * Rend la première page du PDF, **recadre automatiquement sur la zone imprimée** (utile pour
     * les bordereaux fournis sur une page A4 — ex. Mondial Relay — où l'étiquette n'occupe qu'une
     * partie de la page), puis met à l'échelle à [PRINT_WIDTH_DOTS] de large. Fond blanc.
     */
    internal fun renderFirstPage(
        context: Context,
        pdfBytes: ByteArray,
    ): Bitmap {
        val tmp = File(context.cacheDir, "thermal_label_tmp.pdf")
        tmp.writeBytes(pdfBytes)
        val pfd = ParcelFileDescriptor.open(tmp, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(pfd)
        val rendered: Bitmap
        try {
            val page = renderer.openPage(0)
            // Rendu à ~8 dots/mm (densité imprimante), borné à MAX_RENDER_WIDTH pour l'A4
            val renderWidth = minOf(MAX_RENDER_WIDTH, (page.width * DOTS_PER_MM * 25.4f / 72f).roundToInt())
            val renderHeight = (renderWidth.toFloat() * page.height / page.width).roundToInt()
            rendered = Bitmap.createBitmap(renderWidth, renderHeight, Bitmap.Config.ARGB_8888)
            rendered.eraseColor(Color.WHITE)
            page.render(rendered, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
            page.close()
        } finally {
            renderer.close()
            runCatching { tmp.delete() }
        }

        // Recadrage sur le contenu (bbox des pixels noirs), puis mise à l'échelle à la largeur d'impression
        val cropped = cropToContent(rendered)
        if (cropped !== rendered) rendered.recycle()
        val finalHeight = (PRINT_WIDTH_DOTS.toFloat() * cropped.height / cropped.width).roundToInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(cropped, PRINT_WIDTH_DOTS, finalHeight, true)
        if (scaled !== cropped) cropped.recycle()
        return scaled
    }

    /**
     * Retourne un recadrage de [src] limité à la zone contenant des pixels sombres (+ marge),
     * ou [src] tel quel si la page est vide. Élimine les grandes marges blanches (cas A4).
     */
    @Suppress("NestedBlockDepth")
    private fun cropToContent(src: Bitmap): Bitmap {
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
        if (maxX < minX || maxY < minY) return src // page vide → pas de recadrage
        val x0 = (minX - CROP_MARGIN).coerceAtLeast(0)
        val y0 = (minY - CROP_MARGIN).coerceAtLeast(0)
        val x1 = (maxX + CROP_MARGIN).coerceAtMost(w - 1)
        val y1 = (maxY + CROP_MARGIN).coerceAtMost(h - 1)
        return Bitmap.createBitmap(src, x0, y0, x1 - x0 + 1, y1 - y0 + 1)
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
