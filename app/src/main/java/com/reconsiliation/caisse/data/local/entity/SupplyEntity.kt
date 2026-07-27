package com.reconsiliation.caisse.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "supplies")
data class SupplyEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: Long,
    val productName: String,
    val quantity: Double,
    val purchasePrice: Double,
    val date: Date,
    val supplierName: String? = null,
    val isPaid: Boolean = true, // Point 2: Track supplier debt
    val expiryDate: Date? = null, // Point 2: Expiry tracking
    val supplierId: Long? = null // Point 3: Link to CRM
)
