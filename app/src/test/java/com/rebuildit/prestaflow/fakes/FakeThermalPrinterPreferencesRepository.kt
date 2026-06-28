package com.rebuildit.prestaflow.fakes

import com.rebuildit.prestaflow.domain.printer.ThermalPrinterPreferencesRepository
import com.rebuildit.prestaflow.domain.printer.model.SavedPrinterDevice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Implémentation en mémoire de [ThermalPrinterPreferencesRepository] pour les tests JVM. */
class FakeThermalPrinterPreferencesRepository : ThermalPrinterPreferencesRepository {
    private val _device = MutableStateFlow<SavedPrinterDevice?>(null)

    override val savedDevice: Flow<SavedPrinterDevice?> = _device.asStateFlow()

    override suspend fun saveDevice(device: SavedPrinterDevice) {
        _device.value = device
    }

    override suspend fun clearDevice() {
        _device.value = null
    }

    /** Permet d'initialiser l'état depuis un test sans passer par [saveDevice]. */
    fun setDevice(device: SavedPrinterDevice?) {
        _device.value = device
    }
}
