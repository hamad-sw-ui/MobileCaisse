package com.reconsiliation.caisse.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "sms_errors")
data class SmsErrorEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val body: String,
    val date: Date,
    val sender: String? = null,
    val isResolved: Boolean = false
)
