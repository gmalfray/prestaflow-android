package com.rebuildit.prestaflow.fakes

import android.graphics.Bitmap
import com.rebuildit.prestaflow.core.ocr.LabelTextRecognizer
import kotlinx.coroutines.delay

/**
 * Fake en mémoire de [LabelTextRecognizer] — utilisé pour tester l'orchestration du secours OCR
 * ([com.rebuildit.prestaflow.ui.products.StockReplenishViewModel]) sans dépendre de ML Kit.
 *
 * [delayMs] simule un OCR lent (ex. > timeout côté ViewModel) sous temps virtuel (`runTest`) : un
 * simple `delay()`, pas un vrai sleep.
 */
class FakeLabelTextRecognizer : LabelTextRecognizer {
    var recognizedText: String = ""
    var delayMs: Long = 0L
    val recognizeCalls = mutableListOf<Bitmap>()

    override suspend fun recognize(bitmap: Bitmap): String {
        recognizeCalls += bitmap
        if (delayMs > 0) delay(delayMs)
        return recognizedText
    }
}
