package com.reconsiliation.caisse.printing

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*

class EscPosPrinter(private val context: Context) {
    private val PRINTER_UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb")
    private var socket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null

    // ESC/POS Commands
    private val ESC: Byte = 0x1B
    private val GS: Byte = 0x1D
    private val ALIGN_LEFT = byteArrayOf(ESC, 'a'.toByte(), 0x00)
    private val ALIGN_CENTER = byteArrayOf(ESC, 'a'.toByte(), 0x01)
    private val ALIGN_RIGHT = byteArrayOf(ESC, 'a'.toByte(), 0x02)
    private val BOLD_ON = byteArrayOf(ESC, 'E'.toByte(), 0x01)
    private val BOLD_OFF = byteArrayOf(ESC, 'E'.toByte(), 0x00)
    private val FONT_LARGE = byteArrayOf(GS, '!'.toByte(), 0x11)
    private val FONT_NORMAL = byteArrayOf(GS, '!'.toByte(), 0x00)
    private val FEED_LINE = byteArrayOf(0x0A)

    @SuppressLint("MissingPermission")
    fun connect(address: String): Boolean {
        val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = btManager.adapter
        val device = adapter?.getRemoteDevice(address) ?: return false
        return try {
            socket = device.createRfcommSocketToServiceRecord(PRINTER_UUID)
            socket?.connect()
            outputStream = socket?.outputStream
            true
        } catch (e: Exception) {
            false
        }
    }

    fun printText(text: String, align: Int = 0, bold: Boolean = false, large: Boolean = false) {
        try {
            val alignCmd = when(align) {
                1 -> ALIGN_CENTER
                2 -> ALIGN_RIGHT
                else -> ALIGN_LEFT
            }
            outputStream?.write(alignCmd)
            if (bold) outputStream?.write(BOLD_ON) else outputStream?.write(BOLD_OFF)
            if (large) outputStream?.write(FONT_LARGE) else outputStream?.write(FONT_NORMAL)
            
            // Use a more robust encoding or clean the text
            val cleanText = text.replace("é", "e").replace("è", "e").replace("à", "a").replace("ç", "c")
                .replace("É", "E").replace("È", "E").replace("À", "A").replace("Ç", "C")
                .replace("ù", "u").replace("ô", "o").replace("î", "i").replace("ï", "i").replace("ë", "e")
            outputStream?.write((cleanText + "\n").toByteArray(charset("US-ASCII")))
            outputStream?.flush()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun testPrint() {
        printText("TICKET DE TEST", 1, bold = true, large = true)
        printText("Configuration Reussie", 1)
        printDashLine()
        printText("Date: " + SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date()), 0)
        printText("Caisse Mobile v1.0", 0)
        printDashLine()
        feed(3)
    }

    fun printDashLine() {
        printText("--------------------------------", 1)
    }

    fun printItemLine(name: String, qty: Double, price: Double) {
        val total = qty * price
        val line = String.format("%-16s %3.0f x %5.0f", name.take(16), qty, total)
        printText(line, 0)
    }

    fun feed(lines: Int) {
        try {
            repeat(lines) { outputStream?.write(FEED_LINE) }
            outputStream?.flush()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun disconnect() {
        try {
            outputStream?.close()
            socket?.close()
        } catch (e: Exception) {
            // La fermeture d'un socket déjà rompu est sans conséquence :
            // on trace sans propager, l'appelant n'a rien à décider.
            android.util.Log.w("EscPosPrinter", "Fermeture de la connexion imprimante", e)
        }
    }
}
