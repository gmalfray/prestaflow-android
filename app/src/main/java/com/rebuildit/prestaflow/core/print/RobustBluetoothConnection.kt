package com.rebuildit.prestaflow.core.print

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import com.dantsu.escposprinter.connection.DeviceConnection
import com.dantsu.escposprinter.exceptions.EscPosConnectionException
import timber.log.Timber
import java.util.UUID

/**
 * Connexion Bluetooth RFCOMM robuste pour imprimantes ESC/POS, en remplacement de la
 * [com.dantsu.escposprinter.connection.bluetooth.BluetoothConnection] de la lib DantSu.
 *
 * Motivation : la classe d'origine ne tente qu'un seul mode de socket
 * (`createRfcommSocketToServiceRecord` sécurisé sur l'UUID SPP). Sur de nombreuses imprimantes
 * thermiques bas coût (MUNBYN, GOOJPRT, etc.), ce socket échoue avec
 * « Unable to connect to bluetooth device » alors que l'imprimante est bien appairée et allumée.
 *
 * Cette implémentation essaie successivement plusieurs stratégies jusqu'à ce qu'une réussisse :
 *  1. socket **sécurisé** standard (UUID SPP) ;
 *  2. socket **non sécurisé** (certaines imprimantes refusent l'appairage chiffré) ;
 *  3. **reflection** `createRfcommSocket(int)` sur le canal 1 (contournement Android historique).
 *
 * Les champs [outputStream] et [data] hérités de [DeviceConnection] sont renseignés une fois
 * la connexion établie ; la classe de base gère ensuite l'écriture/envoi des données ESC/POS.
 *
 * @param device  L'imprimante appairée cible.
 * @param adapter L'adaptateur Bluetooth (pour annuler la découverte avant connexion).
 */
class RobustBluetoothConnection(
    private val device: BluetoothDevice,
    private val adapter: BluetoothAdapter,
) : DeviceConnection() {
    private var socket: BluetoothSocket? = null

    override fun isConnected(): Boolean = socket?.isConnected == true && outputStream != null

    @SuppressLint("MissingPermission") // Permissions BLUETOOTH_CONNECT/SCAN vérifiées en amont (UI)
    // Les factories de socket et connect() lèvent des types variés (IOException, SecurityException,
    // ReflectiveOperationException, RuntimeException) — on les traite uniformément pour basculer
    // sur la stratégie suivante.
    @Suppress("TooGenericExceptionCaught")
    override fun connect(): DeviceConnection {
        if (isConnected) return this

        // La découverte en cours sature la radio et fait échouer connect() → toujours l'annuler.
        runCatching { adapter.cancelDiscovery() }

        val strategies: List<Pair<String, () -> BluetoothSocket>> =
            listOf(
                "socket sécurisé (SPP)" to { device.createRfcommSocketToServiceRecord(SPP_UUID) },
                "socket non sécurisé (SPP)" to { device.createInsecureRfcommSocketToServiceRecord(SPP_UUID) },
                "reflection canal 1" to {
                    device.javaClass
                        .getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                        .invoke(device, 1) as BluetoothSocket
                },
            )

        var lastError: Exception? = null
        for ((label, factory) in strategies) {
            val candidate =
                try {
                    factory()
                } catch (e: Exception) {
                    Timber.w(e, "Création du socket échouée (%s)", label)
                    lastError = e
                    continue
                }
            try {
                runCatching { adapter.cancelDiscovery() }
                candidate.connect()
                socket = candidate
                outputStream = candidate.outputStream
                data = ByteArray(0)
                Timber.d("Connexion imprimante établie via %s", label)
                return this
            } catch (e: Exception) {
                Timber.w(e, "Connexion échouée (%s), tentative suivante", label)
                lastError = e
                runCatching { candidate.close() }
            }
        }

        disconnect()
        throw EscPosConnectionException(
            "Unable to connect to bluetooth device." +
                (lastError?.message?.let { " ($it)" } ?: ""),
        )
    }

    /**
     * Écrit [bytes] directement sur le socket, par paquets, puis flush. Utilisé pour envoyer
     * un flux brut (ex. commandes TSPL) sans passer par le tamponnage ESC/POS de [DeviceConnection].
     */
    fun writeRaw(bytes: ByteArray) {
        val os = outputStream ?: throw EscPosConnectionException("Flux de sortie Bluetooth indisponible")
        var offset = 0
        while (offset < bytes.size) {
            val end = minOf(offset + CHUNK_SIZE, bytes.size)
            os.write(bytes, offset, end - offset)
            os.flush()
            offset = end
        }
    }

    override fun disconnect(): DeviceConnection {
        outputStream = null
        runCatching { socket?.close() }
        socket = null
        return this
    }

    private companion object {
        /** UUID du profil Serial Port (SPP) — standard pour les imprimantes ESC/POS et TSPL. */
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

        /** Taille des paquets d'écriture brute (octets). */
        const val CHUNK_SIZE = 2048
    }
}
