package com.reconsiliation.caisse.platform

import android.content.Context
import com.reconsiliation.caisse.data.local.entity.BoutiqueEntity
import com.reconsiliation.caisse.data.local.entity.VenteEntity
import com.reconsiliation.caisse.data.local.entity.VenteItemEntity
import com.reconsiliation.caisse.utils.FormatUtil
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PrinterManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * Imprime un ticket de caisse pour une vente.
     * Isole la connexion Bluetooth et les commandes ESC/POS du repository.
     */
    suspend fun printReceipt(
        boutique: BoutiqueEntity,
        vente: VenteEntity,
        items: List<VenteItemEntity>,
        printerAddress: String
    ) {
        if (printerAddress.isBlank() || printerAddress == "NONE") return

        val printer = EscPosPrinter(context)
        
        if (printer.connect(printerAddress)) {
            val text = FormatUtil.generateReceiptText(boutique, vente, items, boutique.currency)
            printer.printText(text)
            printer.feed(3)
            printer.disconnect()
        }
    }

    /**
     * Teste la connexion à une imprimante.
     */
    suspend fun testConnection(printerAddress: String) {
        if (printerAddress.isBlank() || printerAddress == "NONE") return
        val printer = EscPosPrinter(context)
        if (printer.connect(printerAddress)) {
            printer.testPrint()
            printer.disconnect()
        }
    }
}
