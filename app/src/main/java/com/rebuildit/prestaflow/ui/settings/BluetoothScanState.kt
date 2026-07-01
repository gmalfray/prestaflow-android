package com.rebuildit.prestaflow.ui.settings

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import timber.log.Timber

/**
 * État observable d'un scan Bluetooth classique pour découvrir les imprimantes.
 *
 * @property devices    Liste fusionnée appairés + découverts (dédupliquée par adresse MAC).
 * @property isScanning Vrai tant que la découverte est en cours.
 * @property start      Démarre (ou relance) un cycle de découverte.
 */
class BluetoothScanState(
    val devices: List<BluetoothDevice>,
    val isScanning: Boolean,
    val start: () -> Unit,
)

/**
 * Découverte d'imprimantes Bluetooth **sans appairage préalable**, à la manière de l'app
 * constructeur : on lance [BluetoothAdapter.startDiscovery] et on écoute
 * [BluetoothDevice.ACTION_FOUND] via un [BroadcastReceiver] enregistré le temps de la composition.
 *
 * La liste retournée fusionne les appareils déjà appairés ([BluetoothAdapter.getBondedDevices])
 * et ceux trouvés pendant le scan, dédupliqués par adresse MAC.
 *
 * Permissions : la découverte exige `BLUETOOTH_SCAN` (API 31+) — déclarée avec
 * `neverForLocation` pour éviter d'exiger la localisation. L'appelant doit avoir obtenu la
 * permission avant d'appeler [BluetoothScanState.start]. Sur API < 31, la découverte classique
 * requiert en plus la localisation : on se limite alors aux appareils appairés.
 */
@Composable
fun rememberBluetoothDeviceScan(context: Context): BluetoothScanState {
    val adapter =
        remember {
            (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        }
    // Map adresse MAC → device, pour dédupliquer appairés + découverts
    val deviceList = remember { mutableStateListOf<BluetoothDevice>() }
    var isScanning by remember { mutableStateOf(false) }

    @SuppressLint("MissingPermission") // Permissions vérifiées par l'appelant avant start()
    fun addDevice(device: BluetoothDevice?) {
        if (device == null) return
        if (deviceList.none { it.address == device.address }) {
            deviceList.add(device)
        }
    }

    val receiver =
        remember {
            object : BroadcastReceiver() {
                override fun onReceive(ctx: Context, intent: Intent) {
                    when (intent.action) {
                        BluetoothDevice.ACTION_FOUND -> {
                            val device =
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                                } else {
                                    @Suppress("DEPRECATION")
                                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                                }
                            addDevice(device)
                        }
                        BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                            isScanning = false
                        }
                    }
                }
            }
        }

    androidx.compose.runtime.DisposableEffect(receiver) {
        val filter =
            IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_FOUND)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose {
            runCatching { context.unregisterReceiver(receiver) }
            // Tout le bloc Bluetooth est gardé : le getter `isDiscovering` lui-même lève une
            // SecurityException sur API 31+ sans BLUETOOTH_SCAN (crash à la disposition de l'écran
            // quand la permission n'est pas accordée). runCatching englobe donc getter + cancel.
            @SuppressLint("MissingPermission")
            runCatching {
                if (adapter?.isDiscovering == true) {
                    adapter.cancelDiscovery()
                }
            }
        }
    }

    @SuppressLint("MissingPermission") // Permissions vérifiées par l'appelant avant start()
    val start: () -> Unit = start@{
        val bt = adapter ?: return@start
        deviceList.clear()
        // Toujours inclure les appareils déjà appairés
        runCatching { bt.bondedDevices?.forEach { addDevice(it) } }
        // La découverte classique n'est lancée que si BLUETOOTH_SCAN suffit (API 31+) ;
        // sur API < 31 elle exigerait la localisation → on s'en tient aux appairés.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching {
                if (bt.isDiscovering) bt.cancelDiscovery()
                isScanning = bt.startDiscovery()
            }.onFailure { Timber.w(it, "Échec du démarrage de la découverte Bluetooth") }
        }
    }

    return BluetoothScanState(devices = deviceList, isScanning = isScanning, start = start)
}
