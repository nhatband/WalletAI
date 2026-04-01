package com.wallet.manager.data.repository

import com.wallet.manager.data.local.db.ExpenseDao
import com.wallet.manager.data.local.db.ExpenseWithFriends
import com.wallet.manager.data.local.db.TypeTotalProjection
import com.wallet.manager.data.local.entity.Expense
import com.wallet.manager.data.remote.supabase.SupabaseService
import kotlinx.coroutines.flow.Flow

interface ExpenseRepository {
    fun getAllExpenses(): Flow<List<Expense>>
    fun getAllExpensesWithFriends(): Flow<List<ExpenseWithFriends>>
    fun getExpensesInRange(from: Long, to: Long): Flow<List<Expense>>
    fun getExpensesWithFriendsInRange(from: Long, to: Long): Flow<List<ExpenseWithFriends>>
    fun getTotalByType(from: Long, to: Long): Flow<List<TypeTotalProjection>>
    suspend fun addExpense(expense: Expense)
    suspend fun addExpenseWithFriends(expense: Expense, friendShares: Map<Long, Int>, isSettled: Boolean)
    suspend fun deleteExpense(expense: Expense)
    suspend fun settleExpenseForFriend(expenseId: Long, friendId: Long, isSettled: Boolean)
    suspend fun settleAllForFriend(friendId: Long)
}

class ExpenseRepositoryImpl(
    private val dao: ExpenseDao,
    private val supabaseService: SupabaseService = SupabaseService()
) : ExpenseRepository {
    override fun getAllExpenses(): Flow<List<Expense>> = dao.getAllExpenses()
    override fun getAllExpensesWithFriends(): Flow<List<ExpenseWithFriends>> = dao.getAllExpensesWithFriends()
    
    override fun getExpensesInRange(from: Long, to: Long): Flow<List<Expense>> =
        dao.getExpensesInRange(from, to)

    override fun getExpensesWithFriendsInRange(from: Long, to: Long): Flow<List<ExpenseWithFriends>> =
        dao.getExpensesWithFriendsInRange(from, to)

    override fun getTotalByType(from: Long, to: Long): Flow<List<TypeTotalProjection>> =
        dao.getTotalByType(from, to)

    override suspend fun addExpense(expense: Expense) {
        val id = dao.insert(expense)
        try {
            supabaseService.syncExpense(expense.copy(id = id))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override suspend fun addExpenseWithFriends(expense: Expense, friendShares: Map<Long, Int>, isSettled: Boolean) {
        val expenseId = dao.upsertExpenseWithFriends(expense, friendShares, isSettled)
        try {
            supabaseService.syncExpense(expense.copy(id = expenseId))
            friendShares.forEach { (friendId, shareCount) ->
                supabaseService.syncExpenseFriendCrossRef(expenseId, friendId, shareCount, isSettled)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override suspend fun deleteExpense(expense: Expense) {
        dao.delete(expense)
        try {
            supabaseService.deleteExpense(expense.id)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override suspend fun settleExpenseForFriend(expenseId: Long, friendId: Long, isSettled: Boolean) {
        dao.updateSettlementStatus(expenseId, friendId, isSettled)
        // Note: You might need a more complex sync flow for settlement status if it's stored in a cross-ref table with extra columns
    }

    override suspend fun settleAllForFriend(friendId: Long) {
        dao.settleAllForFriend(friendId)
    }
}
