package com.reconsiliation.caisse.data.local.dao

import androidx.room.*
import com.reconsiliation.caisse.data.local.entity.StaffEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StaffDao {
    @Query("SELECT * FROM staff WHERE isActive = 1")
    fun getAllActiveStaff(): Flow<List<StaffEntity>>

    @Query("SELECT * FROM staff WHERE id = :id")
    suspend fun getStaffById(id: Long): StaffEntity?

    @Query("SELECT * FROM staff WHERE pinHash = :pinHash AND isActive = 1")
    suspend fun getStaffByPin(pinHash: String): StaffEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(staff: StaffEntity): Long

    @Delete
    suspend fun delete(staff: StaffEntity)
}
