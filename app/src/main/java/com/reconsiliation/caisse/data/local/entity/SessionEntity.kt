package com.reconsiliation.caisse.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val staffId: Long? = null,
    val sellerName: String,
    val startTime: Date,
    val endTime: Date? = null,
    val openingBalance: Double,
    val closingBalance: Double? = null,
    val expectedBalance: Double? = null,
    val totalCashSales: Double = 0.0,
    val totalMomoSales: Double = 0.0,
    val totalExpenses: Double = 0.0,
    val isActive: Boolean = true
)
