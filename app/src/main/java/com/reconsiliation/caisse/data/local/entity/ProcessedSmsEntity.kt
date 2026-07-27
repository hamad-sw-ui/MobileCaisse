package com.reconsiliation.caisse.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "processed_sms")
data class ProcessedSmsEntity(
    @PrimaryKey val transactionId: String,
    val processedDate: Date = Date()
)
