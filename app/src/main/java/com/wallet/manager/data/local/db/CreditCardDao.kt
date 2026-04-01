package com.wallet.manager.data.local.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wallet.manager.data.local.entity.CreditCard
import kotlinx.coroutines.flow.Flow

@Dao
interface CreditCardDao {
    @Query("SELECT COUNT(*) FROM credit_cards")
    suspend fun getCardCount(): Int

    @Query("SELECT * FROM credit_cards ORDER BY createdAt DESC")
    fun getAllCards(): Flow<List<CreditCard>>

    @Query("SELECT * FROM credit_cards ORDER BY createdAt DESC")
    suspend fun getAllCardsList(): List<CreditCard>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(card: CreditCard): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(cards: List<CreditCard>)

    @Delete
    suspend fun delete(card: CreditCard)

    @Query("DELETE FROM credit_cards")
    suspend fun deleteAll()
}
