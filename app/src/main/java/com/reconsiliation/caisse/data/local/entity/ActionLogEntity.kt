package com.reconsiliation.caisse.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "action_logs")
data class ActionLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: Date,
    val actionType: String, // STOCK_ADJUST, PRICE_CHANGE, DELETE_SALE, etc.
    val details: String,
    val userRole: String = "ADMIN",
    val severity: String = "INFO" // INFO, WARNING, CRITICAL
)
