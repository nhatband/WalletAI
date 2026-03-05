package com.wallet.manager.data.local.db

import androidx.room.*
import com.wallet.manager.data.local.entity.Expense
import com.wallet.manager.data.local.entity.ExpenseFriendCrossRef
import com.wallet.manager.data.local.entity.Friend
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {

    @Query("SELECT * FROM expenses ORDER BY date DESC")
    fun getAllExpenses(): Flow<List<Expense>>

    @Transaction
    @Query("SELECT * FROM expenses ORDER BY date DESC")
    fun getAllExpensesWithFriends(): Flow<List<ExpenseWithFriends>>

    @Transaction
    @Query(
        """SELECT * FROM expenses 
           WHERE date BETWEEN :from AND :to 
           ORDER BY date DESC"""
    )
    fun getExpensesWithFriendsInRange(from: Long, to: Long): Flow<List<ExpenseWithFriends>>

    @Query(
        """SELECT * FROM expenses 
           WHERE date BETWEEN :from AND :to 
           ORDER BY date DESC"""
    )
    fun getExpensesInRange(from: Long, to: Long): Flow<List<Expense>>

    @Query(
        """SELECT type, SUM(amount) as total 
           FROM expenses 
           WHERE date BETWEEN :from AND :to 
           GROUP BY type"""
    )
    fun getTotalByType(from: Long, to: Long): Flow<List<TypeTotalProjection>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(expense: Expense): Long

    @Delete
    suspend fun delete(expense: Expense)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFriendCrossRefs(refs: List<ExpenseFriendCrossRef>)

    @Query("DELETE FROM expense_friend_cross_ref WHERE expenseId = :expenseId")
    suspend fun deleteFriendCrossRefsForExpense(expenseId: Long)
    
    @Transaction
    suspend fun upsertExpenseWithFriends(expense: Expense, friendShares: Map<Long, Int>, isSettled: Boolean) {
        val id = insert(expense)
        deleteFriendCrossRefsForExpense(id)
        val refs = friendShares.map { (friendId, shareCount) -> 
            ExpenseFriendCrossRef(id, friendId, shareCount, isSettled)
        }
        insertFriendCrossRefs(refs)
    }

    @Query("UPDATE expense_friend_cross_ref SET isSettled = :isSettled WHERE expenseId = :expenseId AND friendId = :friendId")
    suspend fun updateSettlementStatus(expenseId: Long, friendId: Long, isSettled: Boolean)

    @Query("UPDATE expense_friend_cross_ref SET isSettled = 1 WHERE friendId = :friendId")
    suspend fun settleAllForFriend(friendId: Long)

    @Transaction
    @Query("""
        SELECT e.* FROM expenses e
        JOIN expense_friend_cross_ref ef ON e.id = ef.expenseId
        WHERE ef.friendId = :friendId
        ORDER BY e.date DESC
    """)
    fun getExpensesByFriend(friendId: Long): Flow<List<ExpenseWithFriends>>
}

data class ExpenseWithFriends(
    @Embedded val expense: Expense,
    @Relation(
        entity = Friend::class,
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = ExpenseFriendCrossRef::class,
            parentColumn = "expenseId",
            entityColumn = "friendId"
        )
    )
    val friends: List<Friend>,
    
    @Relation(
        parentColumn = "id",
        entityColumn = "expenseId"
    )
    val friendCrossRefs: List<ExpenseFriendCrossRef>
)

data class TypeTotalProjection(
    val type: String,
    val total: Double
)
