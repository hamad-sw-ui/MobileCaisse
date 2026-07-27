package com.reconsiliation.caisse.data.local.dao

import androidx.room.*
import com.reconsiliation.caisse.data.local.entity.SupplierEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SupplierDao {
    @Query("SELECT * FROM suppliers ORDER BY name ASC")
    fun getAllSuppliers(): Flow<List<SupplierEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(supplier: SupplierEntity): Long

    @Query("UPDATE suppliers SET totalDebt = totalDebt + :amount WHERE id = :id")
    suspend fun updateDebt(id: Long, amount: Double)

    @Delete
    suspend fun delete(supplier: SupplierEntity)
}
