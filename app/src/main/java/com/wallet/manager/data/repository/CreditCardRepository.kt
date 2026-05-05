package com.wallet.manager.data.repository

import android.content.Context
import com.wallet.manager.data.local.db.CreditCardDao
import com.wallet.manager.data.local.db.ExpenseDao
import com.wallet.manager.data.local.entity.CreditCard
import com.wallet.manager.data.remote.supabase.CloudSyncManager
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
    private val appContext: Context? = null,
    private val expenseDao: ExpenseDao? = null,
    private val supabaseService: SupabaseService = SupabaseService()
) : CreditCardRepository {
    override fun getAllCards(): Flow<List<CreditCard>> = dao.getAllCards()

    override suspend fun getAllCardsList(): List<CreditCard> = dao.getAllCardsList()

    override suspend fun saveCard(card: CreditCard): Long {
        val id = dao.insert(card)
        val ctx = appContext
        if (ctx != null) {
            CloudSyncManager.markPending(ctx)
            CloudSyncManager.requestSync(ctx)
        } else {
            try {
                supabaseService.syncCreditCard(card.copy(id = id))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return id
    }

    override suspend fun deleteCard(card: CreditCard) {
        expenseDao?.clearCreditCardForExpenses(card.id)
        dao.delete(card)
        val ctx = appContext
        if (ctx != null) {
            CloudSyncManager.markCreditCardDeleted(ctx, card.id)
            CloudSyncManager.requestSync(ctx)
        } else {
            try {
                supabaseService.deleteCreditCard(card.id)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
