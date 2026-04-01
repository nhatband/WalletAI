package com.wallet.manager.data.repository

import com.wallet.manager.data.local.db.CreditCardDao
import com.wallet.manager.data.local.entity.CreditCard
import com.wallet.manager.data.remote.supabase.SupabaseService
import kotlinx.coroutines.flow.Flow

interface CreditCardRepository {
    fun getAllCards(): Flow<List<CreditCard>>
    suspend fun getAllCardsList(): List<CreditCard>
    suspend fun saveCard(card: CreditCard): Long
    suspend fun deleteCard(card: CreditCard)
}

class CreditCardRepositoryImpl(
    private val dao: CreditCardDao,
    private val supabaseService: SupabaseService = SupabaseService()
) : CreditCardRepository {
    override fun getAllCards(): Flow<List<CreditCard>> = dao.getAllCards()

    override suspend fun getAllCardsList(): List<CreditCard> = dao.getAllCardsList()

    override suspend fun saveCard(card: CreditCard): Long {
        val id = dao.insert(card)
        try {
            supabaseService.syncCreditCard(card.copy(id = id))
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return id
    }

    override suspend fun deleteCard(card: CreditCard) {
        dao.delete(card)
        try {
            supabaseService.deleteCreditCard(card.id)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
