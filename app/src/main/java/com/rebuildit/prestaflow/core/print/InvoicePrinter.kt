package com.rebuildit.prestaflow.core.print

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

/**
 * Mode d'impression des factures.
 *
 * - [TWO_UP] : montage A4 paysage — 2 factures côte à côte (moitié gauche 0→421 pt, moitié droite
 *   421→842 pt), chacune dans une zone 421×595 pt ≈ A5 portrait. Ratio respecté, pas de rotation.
 *
 * - [ONE_PER_PAGE] : chaque facture en pleine page A4 portrait (210×297 mm).
 */
enum class PrintMode {
    TWO_UP,
    ONE_PER_PAGE,
}

/**
 * Assemble un ou plusieurs PDFs de factures en un PDF imprimable, puis déclenche l'impression
 * via [PrintManager].
 *
 * Mode [PrintMode.TWO_UP] (défaut économique) :
 *  - Page de sortie : A4 paysage (842 × 595 pt).
 *  - Moitié gauche : x 0→421 pt, y 0→595 pt.
 *  - Moitié droite : x 421→842 pt, y 0→595 pt.
 *  - Chaque demi-largeur (421×595 pt ≈ A5 portrait 148×210 mm) accueille une page source en
 *    portrait sans la tourner — le ratio A4 source (1:√2 ≈ 0,707) est conservé dans la zone
 *    A5 (même ratio) → réduction sans déformation.
 *  - Nombre impair de pages : la dernière page paysage n'a qu'une facture à gauche.
 *
 * Mode [PrintMode.ONE_PER_PAGE] :
 *  - Page de sortie : A4 portrait (595 × 842 pt).
 *  - Chaque facture occupe la pleine page.
 *
 * Limites connues :
 *  - La rasterisation via [PdfRenderer] produit des bitmaps à [RENDER_DPI] points/pouce.
 *    Une valeur trop élevée consomme beaucoup de RAM ; 300 dpi est un bon compromis pour
 *    l'impression thermique A4.
 *  - [PdfRenderer] nécessite un fichier physique (pas un InputStream) : les PDFs sont
 *    écrits dans le répertoire cache avant traitement, puis supprimés.
 *  - Le [PrintDocumentAdapter] fourni est synchrone (pas de pagination interactive) :
 *    le PDF composé est intégralement produit dans [onWrite], ce qui peut être lent pour
 *    un grand nombre de factures.
 */
object InvoicePrinter {
    /** Résolution de rasterisation des pages PDF sources. */
    private const val RENDER_DPI = 300

    /** Points par pouce en coordonnées PDF (PostScript). */
    private const val PDF_POINTS_PER_INCH = 72f

    // ── Dimensions A4 portrait ──────────────────────────────────────────────

    /** Largeur A4 portrait en points (595 pt = 210 mm × 72/25.4). */
    private const val A4_PORTRAIT_WIDTH_PT = 595

    /** Hauteur A4 portrait en points (842 pt = 297 mm × 72/25.4). */
    private const val A4_PORTRAIT_HEIGHT_PT = 842

    // ── Dimensions A4 paysage ───────────────────────────────────────────────

    /** Largeur A4 paysage en points (= hauteur A4 portrait). */
    private const val A4_LANDSCAPE_WIDTH_PT = 842

    /** Hauteur A4 paysage en points (= largeur A4 portrait). */
    private const val A4_LANDSCAPE_HEIGHT_PT = 595

    /**
     * Demi-largeur paysage en points : zone allouée à chaque facture en mode [PrintMode.TWO_UP].
     * 842 / 2 = 421 pt ≈ 148 mm → format A5 portrait en largeur.
     */
    private const val HALF_LANDSCAPE_WIDTH_PT = A4_LANDSCAPE_WIDTH_PT / 2 // 421

    /**
     * Déclenche l'impression des factures.
     *
     * @param context Contexte Android (Activity ou Application).
     * @param pdfBytesList Liste ordonnée des PDFs (un par commande, plusieurs pages possibles).
     * @param jobName Libellé du travail d'impression affiché dans le sélecteur.
     * @param mode Mode d'impression (2-up paysage ou 1 par page portrait).
     */
    fun print(
        context: Context,
        pdfBytesList: List<ByteArray>,
        jobName: String,
        mode: PrintMode = PrintMode.TWO_UP,
    ) {
        if (pdfBytesList.isEmpty()) return

        val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        val adapter = InvoicePrintDocumentAdapter(context, pdfBytesList, jobName, mode)
        val mediaSize =
            when (mode) {
                PrintMode.TWO_UP -> PrintAttributes.MediaSize.ISO_A4.asLandscape()
                PrintMode.ONE_PER_PAGE -> PrintAttributes.MediaSize.ISO_A4
            }
        val attributes =
            PrintAttributes.Builder()
                .setMediaSize(mediaSize)
                .setResolution(PrintAttributes.Resolution("default", "default", RENDER_DPI, RENDER_DPI))
                .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                .build()
        printManager.print(jobName, adapter, attributes)
    }

    /**
     * Compose en mémoire le PDF 2-up A4 paysage à partir des [pdfBytesList].
     *
     * Montage : page paysage 842×595 pt, facture gauche (x=0→421), facture droite (x=421→842).
     * Exposé en interne pour tests et aperçu.
     */
    @Suppress("NestedBlockDepth") // inévitable : boucle pages + demi-pages + rendu bitmap
    internal fun buildTwoUpPdf(context: Context, pdfBytesList: List<ByteArray>): ByteArray {
        val allPages = mutableListOf<Pair<PdfRenderer, Int>>()
        val tempFiles = mutableListOf<File>()
        val renderers = mutableListOf<PdfRenderer>()

        try {
            for ((idx, pdfBytes) in pdfBytesList.withIndex()) {
                val tmp = File(context.cacheDir, "invoice_tmp_$idx.pdf")
                tmp.writeBytes(pdfBytes)
                tempFiles.add(tmp)
                val pfd = ParcelFileDescriptor.open(tmp, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(pfd)
                renderers.add(renderer)
                for (pageIdx in 0 until renderer.pageCount) {
                    allPages.add(Pair(renderer, pageIdx))
                }
            }

            val doc = PdfDocument()

            // Pixels cibles pour rasterisation de chaque demi-page (421×595 pt → px à RENDER_DPI)
            val halfWidthPx = (HALF_LANDSCAPE_WIDTH_PT / PDF_POINTS_PER_INCH * RENDER_DPI).toInt()
            val pageHeightPx = (A4_LANDSCAPE_HEIGHT_PT / PDF_POINTS_PER_INCH * RENDER_DPI).toInt()

            var pageIdx = 0
            while (pageIdx < allPages.size) {
                // Page de sortie : A4 paysage (842×595 pt)
                val pageInfo =
                    PdfDocument.PageInfo.Builder(
                        A4_LANDSCAPE_WIDTH_PT,
                        A4_LANDSCAPE_HEIGHT_PT,
                        pageIdx / 2 + 1,
                    ).create()
                val docPage = doc.startPage(pageInfo)
                val canvas: Canvas = docPage.canvas

                // Moitié gauche (x 0→421, y 0→595)
                renderHalf(
                    canvas = canvas,
                    pageRef = allPages[pageIdx],
                    halfWidthPx = halfWidthPx,
                    pageHeightPx = pageHeightPx,
                    leftHalf = true,
                )
                pageIdx++

                // Moitié droite (x 421→842, y 0→595) — si une page suivante existe
                if (pageIdx < allPages.size) {
                    renderHalf(
                        canvas = canvas,
                        pageRef = allPages[pageIdx],
                        halfWidthPx = halfWidthPx,
                        pageHeightPx = pageHeightPx,
                        leftHalf = false,
                    )
                    pageIdx++
                }

                doc.finishPage(docPage)
            }

            val out = java.io.ByteArrayOutputStream()
            doc.writeTo(out)
            doc.close()
            return out.toByteArray()
        } finally {
            renderers.forEach { r ->
                runCatching { r.close() }.onFailure { Timber.w(it, "Fermeture PdfRenderer") }
            }
            tempFiles.forEach { f -> runCatching { f.delete() } }
        }
    }

    /**
     * Compose en mémoire le PDF 1-par-page A4 portrait à partir des [pdfBytesList].
     * Exposé en interne pour tests et aperçu.
     */
    @Suppress("NestedBlockDepth")
    internal fun buildOnePerPagePdf(
        context: Context,
        pdfBytesList: List<ByteArray>,
    ): ByteArray {
        val allPages = mutableListOf<Pair<PdfRenderer, Int>>()
        val tempFiles = mutableListOf<File>()
        val renderers = mutableListOf<PdfRenderer>()

        try {
            for ((idx, pdfBytes) in pdfBytesList.withIndex()) {
                val tmp = File(context.cacheDir, "invoice_tmp_$idx.pdf")
                tmp.writeBytes(pdfBytes)
                tempFiles.add(tmp)
                val pfd = ParcelFileDescriptor.open(tmp, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(pfd)
                renderers.add(renderer)
                for (pageIdx in 0 until renderer.pageCount) {
                    allPages.add(Pair(renderer, pageIdx))
                }
            }

            val doc = PdfDocument()
            val widthPx = (A4_PORTRAIT_WIDTH_PT / PDF_POINTS_PER_INCH * RENDER_DPI).toInt()
            val heightPx = (A4_PORTRAIT_HEIGHT_PT / PDF_POINTS_PER_INCH * RENDER_DPI).toInt()

            for ((outIdx, pageRef) in allPages.withIndex()) {
                val pageInfo =
                    PdfDocument.PageInfo.Builder(
                        A4_PORTRAIT_WIDTH_PT,
                        A4_PORTRAIT_HEIGHT_PT,
                        outIdx + 1,
                    ).create()
                val docPage = doc.startPage(pageInfo)
                val canvas: Canvas = docPage.canvas

                val (renderer, pdfPageIdx) = pageRef
                val pdfPage = renderer.openPage(pdfPageIdx)
                try {
                    val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(android.graphics.Color.WHITE)
                    pdfPage.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)

                    val paint = Paint(Paint.FILTER_BITMAP_FLAG)
                    val src = android.graphics.RectF(0f, 0f, widthPx.toFloat(), heightPx.toFloat())
                    val dst =
                        android.graphics.RectF(
                            0f,
                            0f,
                            A4_PORTRAIT_WIDTH_PT.toFloat(),
                            A4_PORTRAIT_HEIGHT_PT.toFloat(),
                        )
                    canvas.drawBitmap(bitmap, null, dst, paint)
                    bitmap.recycle()
                } finally {
                    pdfPage.close()
                }

                doc.finishPage(docPage)
            }

            val out = java.io.ByteArrayOutputStream()
            doc.writeTo(out)
            doc.close()
            return out.toByteArray()
        } finally {
            renderers.forEach { r ->
                runCatching { r.close() }.onFailure { Timber.w(it, "Fermeture PdfRenderer") }
            }
            tempFiles.forEach { f -> runCatching { f.delete() } }
        }
    }

    /**
     * Rasterise une page PDF source [pageRef] et la dessine dans une demi-page [canvas] paysage.
     *
     * @param leftHalf `true` = moitié gauche (x=0→421 pt), `false` = moitié droite (x=421→842 pt).
     */
    private fun renderHalf(
        canvas: Canvas,
        pageRef: Pair<PdfRenderer, Int>,
        halfWidthPx: Int,
        pageHeightPx: Int,
        leftHalf: Boolean,
    ) {
        val (renderer, pdfPageIdx) = pageRef
        val pdfPage = renderer.openPage(pdfPageIdx)
        try {
            val bitmap = Bitmap.createBitmap(halfWidthPx, pageHeightPx, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(android.graphics.Color.WHITE)
            pdfPage.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)

            val paint = Paint(Paint.FILTER_BITMAP_FLAG)
            val destLeft = if (leftHalf) 0f else HALF_LANDSCAPE_WIDTH_PT.toFloat()
            val destRight = if (leftHalf) HALF_LANDSCAPE_WIDTH_PT.toFloat() else A4_LANDSCAPE_WIDTH_PT.toFloat()

            val src = android.graphics.RectF(0f, 0f, halfWidthPx.toFloat(), pageHeightPx.toFloat())
            val dst = android.graphics.RectF(destLeft, 0f, destRight, A4_LANDSCAPE_HEIGHT_PT.toFloat())
            canvas.drawBitmap(bitmap, null, dst, paint)
            bitmap.recycle()
        } finally {
            pdfPage.close()
        }
    }

    // ─── PrintDocumentAdapter ───────────────────────────────────────────────

    private class InvoicePrintDocumentAdapter(
        private val context: Context,
        private val pdfBytesList: List<ByteArray>,
        private val docName: String,
        private val mode: PrintMode,
    ) : PrintDocumentAdapter() {
        private var builtPdf: ByteArray? = null

        override fun onLayout(
            oldAttributes: PrintAttributes?,
            newAttributes: PrintAttributes,
            cancellationSignal: android.os.CancellationSignal,
            callback: LayoutResultCallback,
            extras: android.os.Bundle?,
        ) {
            if (cancellationSignal.isCanceled) {
                callback.onLayoutCancelled()
                return
            }
            try {
                builtPdf =
                    when (mode) {
                        PrintMode.TWO_UP -> buildTwoUpPdf(context, pdfBytesList)
                        PrintMode.ONE_PER_PAGE -> buildOnePerPagePdf(context, pdfBytesList)
                    }
                val info =
                    PrintDocumentInfo.Builder(docName)
                        .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                        .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
                        .build()
                callback.onLayoutFinished(info, true)
            } catch (e: Exception) {
                Timber.e(e, "Erreur lors de la composition du PDF d'impression")
                callback.onLayoutFailed(e.message)
            }
        }

        override fun onWrite(
            pages: Array<out android.print.PageRange>?,
            destination: ParcelFileDescriptor,
            cancellationSignal: android.os.CancellationSignal,
            callback: WriteResultCallback,
        ) {
            if (cancellationSignal.isCanceled) {
                callback.onWriteCancelled()
                return
            }
            val pdf = builtPdf
            if (pdf == null) {
                callback.onWriteFailed("PDF non disponible")
                return
            }
            try {
                FileOutputStream(destination.fileDescriptor).use { out: OutputStream ->
                    out.write(pdf)
                }
                callback.onWriteFinished(arrayOf(android.print.PageRange.ALL_PAGES))
            } catch (e: Exception) {
                Timber.e(e, "Erreur lors de l'écriture du PDF d'impression")
                callback.onWriteFailed(e.message)
            }
        }
    }
}
