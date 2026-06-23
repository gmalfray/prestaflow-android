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
 * Assemble un ou plusieurs PDFs de factures en un PDF A4 avec 2 factures par page
 * (chaque facture occupe une demi-page ≈ format A5), puis déclenche l'impression
 * via [PrintManager].
 *
 * Algorithme de montage :
 *  - Chaque PDF d'entrée peut comporter plusieurs pages.
 *  - Les pages des différentes factures sont enchaînées dans l'ordre d'appel.
 *  - Les demi-pages A4 (haut puis bas) sont remplies dans l'ordre.
 *  - Si le nombre total de pages est impair, la dernière demi-page reste blanche.
 *
 * Limites connues :
 *  - La rasterisation via [PdfRenderer] produit des bitmaps à [RENDER_DPI] points/pouce.
 *    Une valeur trop élevée consomme beaucoup de RAM ; 300 dpi est un bon compromis pour
 *    l'impression thermique A4. Pour de l'impression haute qualité (offset), préférer 600 dpi.
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

    /** Largeur A4 en points (595 pt = 210 mm × 72/25.4). */
    private const val A4_WIDTH_PT = 595

    /** Hauteur A4 en points (842 pt = 297 mm × 72/25.4). */
    private const val A4_HEIGHT_PT = 842

    /**
     * Déclenche l'impression des factures.
     *
     * @param context Contexte Android (Activity ou Application).
     * @param pdfBytesList Liste ordonnée des PDFs (un par commande, plusieurs pages possibles).
     * @param jobName Libellé du travail d'impression affiché dans le sélecteur.
     */
    fun print(
        context: Context,
        pdfBytesList: List<ByteArray>,
        jobName: String,
    ) {
        if (pdfBytesList.isEmpty()) return

        val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        val adapter = InvoicePrintDocumentAdapter(context, pdfBytesList, jobName)
        val attributes =
            PrintAttributes.Builder()
                .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                .setResolution(PrintAttributes.Resolution("default", "default", RENDER_DPI, RENDER_DPI))
                .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                .build()
        printManager.print(jobName, adapter, attributes)
    }

    /**
     * Compose en mémoire le PDF A4 2-up à partir des [pdfBytesList].
     * Exposé en interne pour tests et aperçu.
     */
    @Suppress("NestedBlockDepth") // inévitable : boucle pages + demi-pages + rendu bitmap
    internal fun buildTwoUpPdf(context: Context, pdfBytesList: List<ByteArray>): ByteArray {
        // Collecte toutes les pages de tous les PDFs dans l'ordre
        val allPages = mutableListOf<Pair<PdfRenderer, Int>>() // (renderer, pageIndex)
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
            val halfHeightPt = A4_HEIGHT_PT / 2

            // Dimensions en pixels à la résolution cible
            val widthPx = (A4_WIDTH_PT / PDF_POINTS_PER_INCH * RENDER_DPI).toInt()
            val halfHeightPx = (halfHeightPt / PDF_POINTS_PER_INCH * RENDER_DPI).toInt()

            var pageIdx = 0
            while (pageIdx < allPages.size) {
                val pageInfo =
                    PdfDocument.PageInfo.Builder(A4_WIDTH_PT, A4_HEIGHT_PT, pageIdx / 2 + 1).create()
                val docPage = doc.startPage(pageInfo)
                val canvas: Canvas = docPage.canvas

                // Demi-page du haut
                renderHalf(canvas, allPages[pageIdx], widthPx, halfHeightPx, topHalf = true)
                pageIdx++

                // Demi-page du bas (si une page suivante existe)
                if (pageIdx < allPages.size) {
                    renderHalf(canvas, allPages[pageIdx], widthPx, halfHeightPx, topHalf = false)
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
     * Rasterise une page PDF source [pageRef] et la dessine dans la demi-page [canvas].
     *
     * @param topHalf `true` = moitié haute (y=0), `false` = moitié basse (y=halfHeightPt).
     */
    private fun renderHalf(
        canvas: Canvas,
        pageRef: Pair<PdfRenderer, Int>,
        widthPx: Int,
        halfHeightPx: Int,
        topHalf: Boolean,
    ) {
        val (renderer, pdfPageIdx) = pageRef
        val pdfPage = renderer.openPage(pdfPageIdx)
        try {
            val bitmap = Bitmap.createBitmap(widthPx, halfHeightPx, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(android.graphics.Color.WHITE)
            pdfPage.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)

            val paint = Paint(Paint.FILTER_BITMAP_FLAG)
            val destLeft = 0f
            val destTop = if (topHalf) 0f else (A4_HEIGHT_PT / 2f)
            val destRight = A4_WIDTH_PT.toFloat()
            val destBottom = if (topHalf) (A4_HEIGHT_PT / 2f) else A4_HEIGHT_PT.toFloat()

            val src = android.graphics.RectF(0f, 0f, widthPx.toFloat(), halfHeightPx.toFloat())
            val dst = android.graphics.RectF(destLeft, destTop, destRight, destBottom)
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
                builtPdf = buildTwoUpPdf(context, pdfBytesList)
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
