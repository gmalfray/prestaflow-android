package com.rebuildit.prestaflow.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rebuildit.prestaflow.domain.printer.ThermalPrinterPreferencesRepository
import com.rebuildit.prestaflow.domain.printer.model.SavedPrinterDevice
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel gérant la sélection et la persistance de l'imprimante thermique Bluetooth.
 *
 * Utilisé dans [com.rebuildit.prestaflow.ui.settings.SettingsRoute] (réglages) et
 * dans [com.rebuildit.prestaflow.ui.orders.OrderDetailRoute] (accès en lecture seule
 * pour savoir si une imprimante est configurée avant de lancer l'impression).
 */
@HiltViewModel
class ThermalPrinterViewModel
    @Inject
    constructor(
        private val thermalPrinterPreferencesRepository: ThermalPrinterPreferencesRepository,
    ) : ViewModel() {
        /** Imprimante thermique actuellement sélectionnée, ou null si aucune. */
        val savedDevice: StateFlow<SavedPrinterDevice?> =
            thermalPrinterPreferencesRepository.savedDevice
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5_000),
                    initialValue = null,
                )

        /** Persiste le device sélectionné par l'utilisateur dans les réglages. */
        fun selectDevice(device: SavedPrinterDevice) {
            viewModelScope.launch {
                thermalPrinterPreferencesRepository.saveDevice(device)
            }
        }

        /** Supprime la sélection (l'utilisateur a appuyé sur "Supprimer l'imprimante"). */
        fun clearDevice() {
            viewModelScope.launch {
                thermalPrinterPreferencesRepository.clearDevice()
            }
        }
    }
