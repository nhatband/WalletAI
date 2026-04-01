package com.wallet.manager.data.local.db

import androidx.room.*
import com.wallet.manager.data.local.entity.Friend
import com.wallet.manager.data.local.entity.Expense
import com.wallet.manager.data.local.entity.ExpenseFriendCrossRef
import kotlinx.coroutines.flow.Flow

@Dao
interface FriendDao {
    @Query("SELECT COUNT(*) FROM friends")
    suspend fun getFriendCount(): Int

    @Query("SELECT * FROM friends ORDER BY name ASC")
    fun getAllFriends(): Flow<List<Friend>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFriend(friend: Friend): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllFriends(friends: List<Friend>)

    @Update
    suspend fun updateFriend(friend: Friend)

    @Delete
    suspend fun deleteFriend(friend: Friend)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpenseFriendCrossRef(crossRef: ExpenseFriendCrossRef)

    @Transaction
    @Query("""
        SELECT f.*, 
        (SELECT SUM(e.amount / (1 + (SELECT COUNT(*) FROM expense_friend_cross_ref WHERE expenseId = e.id)))
         FROM expenses e 
         JOIN expense_friend_cross_ref ef ON e.id = ef.expenseId 
         WHERE ef.friendId = f.id) as totalSpent
        FROM friends f
    """)
    fun getFriendsWithSpending(): Flow<List<FriendWithSpending>>

    @Query("SELECT * FROM friends WHERE phoneNumber = :phone LIMIT 1")
    suspend fun getFriendByPhone(phone: String): Friend?

    @Query("DELETE FROM friends")
    suspend fun deleteAllFriends()
}

data class FriendWithSpending(
    @Embedded val friend: Friend,
    val totalSpent: Double?
)
