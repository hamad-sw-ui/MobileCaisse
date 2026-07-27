package com.reconsiliation.caisse.data.local.dao

import androidx.room.*
import com.reconsiliation.caisse.data.local.entity.SmsErrorEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SmsErrorDao {
    @Query("SELECT * FROM sms_errors WHERE isResolved = 0 ORDER BY date DESC")
    fun getUnresolvedErrors(): Flow<List<SmsErrorEntity>>

    @Insert
    suspend fun insert(error: SmsErrorEntity)

    @Update
    suspend fun update(error: SmsErrorEntity)

    @Query("UPDATE sms_errors SET isResolved = 1 WHERE id = :id")
    suspend fun resolve(id: Long): Int

    @Delete
    suspend fun delete(error: SmsErrorEntity)
}
