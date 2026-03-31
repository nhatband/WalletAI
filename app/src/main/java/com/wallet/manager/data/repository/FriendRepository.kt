package com.wallet.manager.data.repository

import com.wallet.manager.data.local.db.FriendDao
import com.wallet.manager.data.local.db.FriendWithSpending
import com.wallet.manager.data.local.db.ExpenseDao
import com.wallet.manager.data.local.db.ExpenseWithFriends
import com.wallet.manager.data.local.entity.Friend
import com.wallet.manager.data.local.entity.ExpenseFriendCrossRef
import com.wallet.manager.data.remote.supabase.SupabaseService
import kotlinx.coroutines.flow.Flow

interface FriendRepository {
    fun getAllFriends(): Flow<List<Friend>>
    fun getFriendsWithSpending(): Flow<List<FriendWithSpending>>
    suspend fun addFriend(friend: Friend): Long
    suspend fun updateFriend(friend: Friend)
    suspend fun deleteFriend(friend: Friend)
    suspend fun addExpenseFriendCrossRef(expenseId: Long, friendId: Long)
    suspend fun getFriendByPhone(phone: String): Friend?
    fun getExpensesByFriend(friendId: Long): Flow<List<ExpenseWithFriends>>
}

class FriendRepositoryImpl(
    private val dao: FriendDao,
    private val expenseDao: ExpenseDao,
    private val supabaseService: SupabaseService = SupabaseService()
) : FriendRepository {
    override fun getAllFriends(): Flow<List<Friend>> = dao.getAllFriends()
    override fun getFriendsWithSpending(): Flow<List<FriendWithSpending>> = dao.getFriendsWithSpending()
    
    override suspend fun addFriend(friend: Friend): Long {
        val id = dao.insertFriend(friend)
        try {
            supabaseService.syncFriend(friend.copy(id = id))
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return id
    }

    override suspend fun updateFriend(friend: Friend) {
        dao.updateFriend(friend)
        try {
            supabaseService.syncFriend(friend)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override suspend fun deleteFriend(friend: Friend) {
        dao.deleteFriend(friend)
        try {
            supabaseService.deleteFriend(friend.id)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override suspend fun addExpenseFriendCrossRef(expenseId: Long, friendId: Long) {
        dao.insertExpenseFriendCrossRef(ExpenseFriendCrossRef(expenseId, friendId))
        try {
            supabaseService.syncExpenseFriendCrossRef(expenseId, friendId)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override suspend fun getFriendByPhone(phone: String): Friend? = dao.getFriendByPhone(phone)
    override fun getExpensesByFriend(friendId: Long): Flow<List<ExpenseWithFriends>> = expenseDao.getExpensesByFriend(friendId)
}
