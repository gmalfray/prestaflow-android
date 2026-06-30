package com.rebuildit.prestaflow.ui.settings

import com.rebuildit.prestaflow.domain.printer.model.SavedPrinterDevice
import com.rebuildit.prestaflow.fakes.FakeThermalPrinterPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Tests unitaires de [ThermalPrinterViewModel].
 *
 * Vérifie la gestion de l'état de l'imprimante sélectionnée :
 * - état initial null
 * - persistance via [ThermalPrinterViewModel.selectDevice]
 * - réinitialisation via [ThermalPrinterViewModel.clearDevice]
 * - remplacement d'une sélection existante
 *
 * Les fonctions bas-niveau `hasBtConnectPermission` et `readBondedDevices` (permission
 * Android runtime + accès BluetoothAdapter) ne sont pas testables en JVM sans matériel
 * ni Robolectric — elles sont couvertes ici par leur contrat implicite via le ViewModel :
 * une fois le device sélectionné (après permission accordée côté UI), il est persisté et
 * expose via [ThermalPrinterViewModel.savedDevice].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ThermalPrinterViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepo: FakeThermalPrinterPreferencesRepository
    private lateinit var viewModel: ThermalPrinterViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepo = FakeThermalPrinterPreferencesRepository()
        viewModel = ThermalPrinterViewModel(fakeRepo)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ─── savedDevice : état initial ────────────────────────────────────────────

    @Test
    fun `savedDevice est null initialement`() =
        runTest(testDispatcher) {
            advanceUntilIdle()
            assertNull(viewModel.savedDevice.value)
        }

    // ─── selectDevice ─────────────────────────────────────────────────────────

    @Test
    fun `selectDevice persiste l imprimante et la expose dans savedDevice`() =
        runTest(testDispatcher) {
            // savedDevice est exposé en WhileSubscribed → un collecteur doit être actif
            // pour que le StateFlow reflète l'état du repo (sinon il reste à initialValue).
            backgroundScope.launch { viewModel.savedDevice.collect {} }
            val device = SavedPrinterDevice(address = "AA:BB:CC:DD:EE:FF", name = "ITPP941B")

            viewModel.selectDevice(device)
            advanceUntilIdle()

            assertEquals(device, viewModel.savedDevice.value)
        }

    @Test
    fun `selectDevice met a jour l imprimante quand une autre etait deja selectionnee`() =
        runTest(testDispatcher) {
            backgroundScope.launch { viewModel.savedDevice.collect {} }
            val first = SavedPrinterDevice(address = "11:22:33:44:55:66", name = "Printer A")
            val second = SavedPrinterDevice(address = "AA:BB:CC:DD:EE:FF", name = "ITPP941B")

            viewModel.selectDevice(first)
            advanceUntilIdle()
            viewModel.selectDevice(second)
            advanceUntilIdle()

            assertEquals(second, viewModel.savedDevice.value)
        }

    @Test
    fun `selectDevice conserve l adresse MAC exacte`() =
        runTest(testDispatcher) {
            backgroundScope.launch { viewModel.savedDevice.collect {} }
            val address = "AA:BB:CC:DD:EE:FF"
            viewModel.selectDevice(SavedPrinterDevice(address = address, name = "MUNBYN P941B"))
            advanceUntilIdle()

            assertEquals(address, viewModel.savedDevice.value?.address)
        }

    @Test
    fun `selectDevice conserve le nom exact du device`() =
        runTest(testDispatcher) {
            backgroundScope.launch { viewModel.savedDevice.collect {} }
            val name = "MUNBYN ITPP941B"
            viewModel.selectDevice(SavedPrinterDevice(address = "AA:BB:CC:DD:EE:FF", name = name))
            advanceUntilIdle()

            assertEquals(name, viewModel.savedDevice.value?.name)
        }

    // ─── clearDevice ──────────────────────────────────────────────────────────

    @Test
    fun `clearDevice remet savedDevice a null`() =
        runTest(testDispatcher) {
            backgroundScope.launch { viewModel.savedDevice.collect {} }
            viewModel.selectDevice(SavedPrinterDevice(address = "AA:BB:CC:DD:EE:FF", name = "ITPP941B"))
            advanceUntilIdle()

            viewModel.clearDevice()
            advanceUntilIdle()

            assertNull(viewModel.savedDevice.value)
        }

    @Test
    fun `clearDevice est sans effet si aucun device n est selectionne`() =
        runTest(testDispatcher) {
            viewModel.clearDevice()
            advanceUntilIdle()

            assertNull("clearDevice sur état vide ne doit pas provoquer d'erreur", viewModel.savedDevice.value)
        }

    // ─── Persistance via le repository ────────────────────────────────────────

    @Test
    fun `selectDevice delegue la persistance au repository`() =
        runTest(testDispatcher) {
            val device = SavedPrinterDevice(address = "AA:BB:CC:DD:EE:FF", name = "ITPP941B")

            viewModel.selectDevice(device)
            advanceUntilIdle()

            // Vérification directe via le Flow du repo (pas seulement via le ViewModel)
            assertEquals(device, fakeRepo.savedDevice.first())
        }

    @Test
    fun `clearDevice delegue la suppression au repository`() =
        runTest(testDispatcher) {
            viewModel.selectDevice(SavedPrinterDevice(address = "AA:BB:CC:DD:EE:FF", name = "ITPP941B"))
            advanceUntilIdle()

            viewModel.clearDevice()
            advanceUntilIdle()

            assertNull(fakeRepo.savedDevice.first())
        }
}
