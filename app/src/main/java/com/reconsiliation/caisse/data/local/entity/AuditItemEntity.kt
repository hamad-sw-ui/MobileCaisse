package com.reconsiliation.caisse.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "audit_items",
    foreignKeys = [
        ForeignKey(
            entity = AuditEntity::class,
            parentColumns = ["id"],
            childColumns = ["auditId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["auditId"])]
)
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
