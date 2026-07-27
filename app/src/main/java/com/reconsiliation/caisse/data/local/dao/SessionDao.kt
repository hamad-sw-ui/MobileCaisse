package com.reconsiliation.caisse.data.local.dao

import androidx.room.*
import com.reconsiliation.caisse.data.local.entity.SessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions WHERE isActive = 1 LIMIT 1")
    fun getActiveSession(): Flow<SessionEntity?>

    @Insert
    suspend fun openSession(session: SessionEntity): Long

    @Update
    suspend fun closeSession(session: SessionEntity)

    @Query("SELECT * FROM sessions ORDER BY startTime DESC")
    fun getAllSessions(): Flow<List<SessionEntity>>
}
