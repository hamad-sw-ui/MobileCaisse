package com.reconsiliation.caisse.data.local.dao

import androidx.room.*
import com.reconsiliation.caisse.data.local.entity.AuditEntity
import com.reconsiliation.caisse.data.local.entity.AuditItemEntity
import kotlinx.coroutines.flow.Flow

data class AuditWithItems(
    @Embedded val audit: AuditEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "auditId"
    )
    val items: List<AuditItemEntity>
)

@Dao
interface AuditDao {
    @Insert
    suspend fun insertAudit(audit: AuditEntity): Long

    @Insert
    suspend fun insertAuditItems(items: List<AuditItemEntity>)

    @Transaction
    @Query("SELECT * FROM audits ORDER BY date DESC")
    fun getAllAudits(): Flow<List<AuditWithItems>>

    @Transaction
    @Query("SELECT * FROM audits WHERE id = :id")
    suspend fun getAuditById(id: Long): AuditWithItems?
}
