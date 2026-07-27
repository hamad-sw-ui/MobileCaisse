package com.reconsiliation.caisse.data.local.dao

import androidx.room.*
import com.reconsiliation.caisse.data.local.entity.RecipeEntity
import com.reconsiliation.caisse.data.local.entity.StockEntity
import kotlinx.coroutines.flow.Flow

data class RecipeWithIngredient(
    @Embedded val recipe: RecipeEntity,
    @Relation(
        parentColumn = "ingredientProductId",
        entityColumn = "id"
    )
    val ingredient: StockEntity
)

@Dao
interface RecipeDao {
    @Query("SELECT * FROM recipes WHERE parentProductId = :parentId")
    fun getRecipeForProduct(parentId: Long): Flow<List<RecipeWithIngredient>>

    @Query("SELECT * FROM recipes WHERE parentProductId = :parentId")
    suspend fun getRecipeForProductOnce(parentId: Long): List<RecipeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(recipe: RecipeEntity)

    @Delete
    suspend fun delete(recipe: RecipeEntity)

    @Query("DELETE FROM recipes WHERE parentProductId = :parentId")
    suspend fun deleteRecipeForProduct(parentId: Long)
}
