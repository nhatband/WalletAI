package com.wallet.manager.data.repository

import com.wallet.manager.data.local.db.FriendDao
import com.wallet.manager.data.local.db.FriendWithSpending
import com.wallet.manager.data.local.db.ExpenseDao
import com.wallet.manager.data.local.db.ExpenseWithFriends
import com.wallet.manager.data.local.entity.Friend
import com.wallet.manager.data.local.entity.ExpenseFriendCrossRef
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
    private val expenseDao: ExpenseDao
) : FriendRepository {
    override fun getAllFriends(): Flow<List<Friend>> = dao.getAllFriends()
    override fun getFriendsWithSpending(): Flow<List<FriendWithSpending>> = dao.getFriendsWithSpending()
    override suspend fun addFriend(friend: Friend): Long = dao.insertFriend(friend)
    override suspend fun updateFriend(friend: Friend) = dao.updateFriend(friend)
    override suspend fun deleteFriend(friend: Friend) = dao.deleteFriend(friend)
    override suspend fun addExpenseFriendCrossRef(expenseId: Long, friendId: Long) {
        dao.insertExpenseFriendCrossRef(ExpenseFriendCrossRef(expenseId, friendId))
    }
    override suspend fun getFriendByPhone(phone: String): Friend? = dao.getFriendByPhone(phone)
    override fun getExpensesByFriend(friendId: Long): Flow<List<ExpenseWithFriends>> = expenseDao.getExpensesByFriend(friendId)
}
