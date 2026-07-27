package com.reconsiliation.caisse.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "audit_items")
data class AuditItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val auditId: Long,
    val productId: Long,
    val productName: String,
    val systemQuantity: Double,
    val physicalQuantity: Double,
    val unitPrice: Double,
    val purchasePrice: Double
)
