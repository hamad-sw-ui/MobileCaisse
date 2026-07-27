package com.reconsiliation.caisse.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "vente_items",
    foreignKeys = [
        ForeignKey(
            entity = VenteEntity::class,
            parentColumns = ["id"],
            childColumns = ["venteId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class VenteItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val venteId: Long,
    val productId: Long,
    val productName: String,
    val quantity: Double,
    val unitPrice: Double,
    val purchasePrice: Double = 0.0 // Captured at time of sale for margin accuracy
)
