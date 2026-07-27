package com.reconsiliation.caisse.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "stock_movements")
data class StockMovementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: Long,
    val date: Date,
    val type: String, // IN, OUT, LOSS, AUDIT
    val quantity: Double,
    val reason: String,
    val balanceAfter: Double
)
