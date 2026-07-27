package com.reconsiliation.caisse.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.reconsiliation.caisse.data.local.entity.ClosureEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ClosureDao {
    @Insert
    suspend fun insert(closure: ClosureEntity): Long

    @Query("SELECT * FROM closures ORDER BY date DESC")
    fun getAllClosures(): Flow<List<ClosureEntity>>
}
