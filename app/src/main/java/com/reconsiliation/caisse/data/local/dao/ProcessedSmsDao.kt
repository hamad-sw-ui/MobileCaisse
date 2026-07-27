package com.reconsiliation.caisse.data.local.dao

import androidx.room.*
import com.reconsiliation.caisse.data.local.entity.ProcessedSmsEntity

@Dao
interface ProcessedSmsDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(sms: ProcessedSmsEntity)

    @Query("SELECT EXISTS(SELECT 1 FROM processed_sms WHERE transactionId = :id)")
    suspend fun exists(id: String): Boolean
}
