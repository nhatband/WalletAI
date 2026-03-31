package com.wallet.manager.data.remote.supabase

import com.wallet.manager.data.mapper.toDto
import com.wallet.manager.data.local.entity.Expense
import com.wallet.manager.data.local.entity.Friend
import com.wallet.manager.data.remote.supabase.model.ExpenseFriendDto
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SupabaseService {
    private val client = SupabaseConfig.client

    suspend fun syncFriend(friend: Friend) = withContext(Dispatchers.IO) {
        client.postgrest["friends"].upsert(friend.toDto())
    }

    suspend fun syncExpense(expense: Expense) = withContext(Dispatchers.IO) {
        client.postgrest["expenses"].upsert(expense.toDto())
    }

    suspend fun syncExpenseFriendCrossRef(expenseId: Long, friendId: Long) = withContext(Dispatchers.IO) {
        client.postgrest["expense_friend_cross_ref"].upsert(
            ExpenseFriendDto(expenseId, friendId)
        )
    }

    suspend fun deleteExpense(expenseId: Long) = withContext(Dispatchers.IO) {
        client.postgrest["expenses"].delete {
            filter {
                eq("id", expenseId)
            }
        }
    }

    suspend fun deleteFriend(friendId: Long) = withContext(Dispatchers.IO) {
        client.postgrest["friends"].delete {
            filter {
                eq("id", friendId)
            }
        }
    }
}
