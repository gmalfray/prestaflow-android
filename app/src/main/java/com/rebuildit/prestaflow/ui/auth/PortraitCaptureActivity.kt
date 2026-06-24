package com.rebuildit.prestaflow.ui.auth

import com.journeyapps.barcodescanner.CaptureActivity

/**
 * Activité de capture QR verrouillée en portrait.
 *
 * La [CaptureActivity] par défaut de ZXing (zxing-android-embedded) force l'orientation paysage
 * via son entrée de manifeste. On la sous-classe pour la redéclarer en portrait dans notre manifeste,
 * afin que le scan reste dans le même sens que le reste de l'app.
 */
class PortraitCaptureActivity : CaptureActivity()
