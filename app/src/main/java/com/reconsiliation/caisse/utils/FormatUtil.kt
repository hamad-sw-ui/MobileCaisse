package com.reconsiliation.caisse.utils

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

object FormatUtil {
    fun formatCurrency(amount: Double, currency: String = "FCFA"): String {
        val format = NumberFormat.getInstance(Locale.FRANCE)
        return "${format.format(amount)} $currency"
    }

    fun formatDate(date: Date): String {
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE)
        return sdf.format(date)
    }

    fun formatShortDate(date: Date): String {
        val sdf = SimpleDateFormat("dd/MM", Locale.FRANCE)
        return sdf.format(date)
    }

    fun generateReceiptText(
        boutique: com.reconsiliation.caisse.data.local.entity.BoutiqueEntity, 
        vente: com.reconsiliation.caisse.data.local.entity.VenteEntity, 
        items: List<com.reconsiliation.caisse.data.local.entity.VenteItemEntity>, 
        currency: String = "FCFA"
    ): String {
        val sb = StringBuilder()
        sb.append("🧾 *REÇU - ${boutique.name}*\n")
        if (!boutique.address.isNullOrBlank()) {
            sb.append("📍 ${boutique.address}\n")
        }
        sb.append("----------------------------\n")
        sb.append("📅 Date: ${formatDate(vente.date)}\n")
        sb.append("🆔 Facture N°: ${vente.invoiceNumber ?: vente.id}\n")
        
        if (boutique.showCustomerPhoneOnReceipt && !vente.customerPhone.isNullOrBlank()) {
            sb.append("👤 Client: ${vente.customerPhone}\n")
        }
        
        sb.append("----------------------------\n")
        items.forEach { item ->
            sb.append("• ${item.productName} x${item.quantity.toInt()} : ${formatCurrency(item.quantity * item.unitPrice, currency)}\n")
        }
        sb.append("----------------------------\n")
        
        if (vente.discount > 0) {
            sb.append("Remise: -${formatCurrency(vente.discount, currency)}\n")
        }
        
        if (boutique.showTaxesOnReceipt && vente.taxAmount > 0) {
            sb.append("${boutique.taxName}: ${formatCurrency(vente.taxAmount, currency)}\n")
        }
        
        if (vente.fees > 0) {
            sb.append("Frais: ${formatCurrency(vente.fees, currency)}\n")
        }
        
        sb.append("💰 *TOTAL: ${formatCurrency(vente.amount, currency)}*\n")
        
        if (boutique.showTotalQuantityOnReceipt) {
            sb.append("📦 Articles: ${items.sumOf { it.quantity }.toInt()}\n")
        }
        
        sb.append("Mode: ${vente.paymentMethod}\n")
        sb.append("----------------------------\n")
        sb.append(boutique.receiptFooter ?: "Merci de votre fidélité ! 🙏")
        return sb.toString()
    }

    fun generateDailySummary(
        boutiqueName: String,
        date: Date,
        totalSales: Double,
        cashSales: Double,
        momoSales: Double,
        debtsCreated: Double,
        expenses: Double,
        netProfit: Double,
        currency: String = "FCFA"
    ): String {
        val sb = StringBuilder()
        sb.append("📊 *RÉSUMÉ JOURNALIER - $boutiqueName*\n")
        sb.append("📅 Journée du: ${SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE).format(date)}\n")
        sb.append("----------------------------\n")
        sb.append("💰 *Chiffre d'Affaires : ${formatCurrency(totalSales, currency)}*\n")
        sb.append("💵 Espèces (Cash) : ${formatCurrency(cashSales, currency)}\n")
        sb.append("📱 MoMo / Orange : ${formatCurrency(momoSales, currency)}\n")
        sb.append("🤝 Crédits Accordés : ${formatCurrency(debtsCreated, currency)}\n")
        sb.append("----------------------------\n")
        sb.append("💸 Dépenses : ${formatCurrency(expenses, currency)}\n")
        sb.append("📈 *Bénéfice Net Est. : ${formatCurrency(netProfit, currency)}*\n")
        sb.append("----------------------------\n")
        sb.append("Généré par Mobile Caisse 🚀")
        return sb.toString()
    }

    fun generateDebtReminder(boutiqueName: String, customerName: String, amount: Double): String {
        return "Bonjour $customerName, c'est la boutique $boutiqueName. Un petit rappel concernant votre reste à payer de ${formatCurrency(amount)}. Merci de nous contacter pour le règlement. Cordialement."
    }
}
