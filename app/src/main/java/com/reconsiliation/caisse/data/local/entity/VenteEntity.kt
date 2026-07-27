package com.reconsiliation.caisse.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "ventes")
data class VenteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: Double,
    val description: String,
    val date: Date,
    val paymentMethod: String, // CASH, MOMO, CREDIT
    val status: String, // PENDING, CONFIRMED, FAILED, CREDIT
    val customerPhone: String? = null,
    val transactionId: String? = null, // MoMo Trans ID
    val smsBody: String? = null,
    val reconciliationStatus: String = "PENDING", // OK, DISCREPANCY, ORPHAN
    val customerId: Long? = null, // Link to Customer for debts
    val isLocked: Boolean = false, // Locked after closure
    val amountCash: Double = 0.0,
    val amountMomo: Double = 0.0,
    val fees: Double = 0.0, // Point 2: Transaction fees
    val discount: Double = 0.0, // Point 1: Discount amount
    val taxAmount: Double = 0.0, // Point 4: Tax
    val sessionId: Long? = null, // Point 1: Session tracking
    val invoiceNumber: String? = null // New: Sequential Fiscal Number (ex: 2024-0001)
)
