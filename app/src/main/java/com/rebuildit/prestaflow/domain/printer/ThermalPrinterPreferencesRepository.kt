package com.rebuildit.prestaflow.domain.printer

import com.rebuildit.prestaflow.domain.printer.model.SavedPrinterDevice
import kotlinx.coroutines.flow.Flow

/** Persistance de la sélection d'imprimante thermique Bluetooth de l'utilisateur. */
interface ThermalPrinterPreferencesRepository {
    /** Flux de l'imprimante choisie, ou null si aucune n'est configurée. */
    val savedDevice: Flow<SavedPrinterDevice?>

    /** Persiste l'adresse MAC et le nom de l'imprimante sélectionnée. */
    suspend fun saveDevice(device: SavedPrinterDevice)

    /** Supprime la sélection (retour à l'état "aucune imprimante"). */
    suspend fun clearDevice()
}
