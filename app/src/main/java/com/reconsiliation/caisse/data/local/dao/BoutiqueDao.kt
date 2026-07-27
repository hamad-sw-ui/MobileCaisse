package com.reconsiliation.caisse.data.local.dao

import androidx.room.*
import com.reconsiliation.caisse.data.local.entity.BoutiqueEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BoutiqueDao {
    @Query("SELECT * FROM boutique WHERE id = 1")
    fun getBoutique(): Flow<BoutiqueEntity?>

    @Query("SELECT * FROM boutique WHERE id = 1")
    suspend fun getBoutiqueOnce(): BoutiqueEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(boutique: BoutiqueEntity)

    @Query("UPDATE boutique SET pinHash = :hash WHERE id = 1")
    suspend fun updatePin(hash: String): Int
}
