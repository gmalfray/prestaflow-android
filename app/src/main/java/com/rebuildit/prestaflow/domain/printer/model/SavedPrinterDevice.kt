package com.rebuildit.prestaflow.domain.printer.model

/**
 * Imprimante thermique Bluetooth persistée dans les préférences utilisateur.
 *
 * @param address Adresse MAC Bluetooth (ex. "AA:BB:CC:DD:EE:FF").
 * @param name    Nom affiché par l'OS Bluetooth (ex. "ITPP941B").
 */
data class SavedPrinterDevice(
    val address: String,
    val name: String,
)
