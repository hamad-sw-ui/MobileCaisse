package com.reconsiliation.caisse.utils

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.reconsiliation.caisse.data.local.dao.VenteWithItems
import java.io.File
import java.io.FileOutputStream

object ExportUtil {
    fun exportSalesToCsv(context: Context, ventes: List<VenteWithItems>): File? {
        val fileName = "Rapport_Ventes_${System.currentTimeMillis()}.csv"
        val file = File(context.cacheDir, fileName)
        
        return try {
            FileOutputStream(file).use { out ->
                // Header with BOM for Excel UTF-8 support
                out.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
                
                val header = "ID;Date;Description;Montant;Mode;Remise;Frais;Taxe;Cout_Achat;Marge_Nette;Statut;Articles\n"
                out.write(header.toByteArray())
                
                ventes.forEach { data ->
                    val itemsString = data.items.joinToString(" | ") { "${it.productName}(${it.quantity})" }
                    val purchaseCost = data.items.sumOf { it.quantity * it.purchasePrice }
                    val netMargin = data.vente.amount - purchaseCost - data.vente.fees
                    
                    val line = "${data.vente.id};" +
                            "${FormatUtil.formatDate(data.vente.date)};" +
                            "${data.vente.description.replace(";", ",")};" +
                            "${data.vente.amount};" +
                            "${data.vente.paymentMethod};" +
                            "${data.vente.discount};" +
                            "${data.vente.fees};" +
                            "${data.vente.taxAmount};" +
                            "$purchaseCost;" +
                            "$netMargin;" +
                            "${data.vente.status};" +
                            "$itemsString\n"
                    out.write(line.toByteArray())
                }
            }
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun shareFile(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Partager le rapport"))
    }
}
