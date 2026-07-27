package com.reconsiliation.caisse.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.reconsiliation.caisse.data.local.entity.ActionLogEntity
import kotlinx.coroutines.flow.Flow
import java.util.Date

@Dao
interface ActionLogDao {
    @Insert
    suspend fun insert(log: ActionLogEntity)

    @Query("SELECT * FROM action_logs ORDER BY date DESC LIMIT 100")
    fun getRecentLogs(): Flow<List<ActionLogEntity>>

    @Query("DELETE FROM action_logs WHERE date < :beforeDate AND severity = 'INFO'")
    suspend fun deleteOldInfoLogs(beforeDate: Date): Int

    @Query("DELETE FROM action_logs")
    suspend fun clearLogs()
}
