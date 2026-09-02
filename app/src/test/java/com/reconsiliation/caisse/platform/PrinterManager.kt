package com.reconsiliation.caisse.platform
import android.content.Context
import com.reconsiliation.caisse.data.local.entity.BoutiqueEntity
import com.reconsiliation.caisse.data.local.entity.VenteEntity
import com.reconsiliation.caisse.data.local.entity.VenteItemEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PrinterManager @Inject constructor(context: Context) {
    var printReceiptCalled = false
    var testConnectionCalled = false
    suspend fun printReceipt(boutique: BoutiqueEntity, vente: VenteEntity, items: List<VenteItemEntity>, printerAddress: String) {
        printReceiptCalled = true
    }
    suspend fun testConnection(printerAddress: String) {
        testConnectionCalled = true
    }
}
