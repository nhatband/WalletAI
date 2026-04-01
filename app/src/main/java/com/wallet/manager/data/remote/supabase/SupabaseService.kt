package com.wallet.manager.data.remote.supabase

import com.wallet.manager.data.mapper.toDto
import com.wallet.manager.data.local.entity.CreditCard
import com.wallet.manager.data.local.entity.Expense
import com.wallet.manager.data.local.entity.Friend
import com.wallet.manager.data.local.entity.ExpenseFriendCrossRef
import com.wallet.manager.data.remote.supabase.model.CreditCardDto
import com.wallet.manager.data.remote.supabase.model.ExpenseFriendDto
import com.wallet.manager.data.remote.supabase.model.ExpenseDto
import com.wallet.manager.data.remote.supabase.model.FriendDto
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SupabaseService {
    private val client = SupabaseConfig.client

    suspend fun syncFriend(friend: Friend) = withContext(Dispatchers.IO) {
        SupabaseConfig.ensureAuthenticated()
        client.postgrest["friends"].upsert(friend.toDto())
    }

    suspend fun syncExpense(expense: Expense) = withContext(Dispatchers.IO) {
        SupabaseConfig.ensureAuthenticated()
        client.postgrest["expenses"].upsert(expense.toDto())
    }

    suspend fun syncCreditCard(card: CreditCard) = withContext(Dispatchers.IO) {
        SupabaseConfig.ensureAuthenticated()
        client.postgrest["credit_cards"].upsert(card.toDto())
    }

    suspend fun syncExpenseFriendCrossRef(
        expenseId: Long,
        friendId: Long,
        shareCount: Int = 1,
        isSettled: Boolean = false
    ) = withContext(Dispatchers.IO) {
        SupabaseConfig.ensureAuthenticated()
        client.postgrest["expense_friend_cross_ref"].upsert(
            ExpenseFriendDto(expenseId, friendId, shareCount, isSettled)
        )
    }

    suspend fun deleteExpense(expenseId: Long) = withContext(Dispatchers.IO) {
        SupabaseConfig.ensureAuthenticated()
        client.postgrest["expenses"].delete {
            filter {
                eq("id", expenseId)
            }
        }
    }

    suspend fun deleteFriend(friendId: Long) = withContext(Dispatchers.IO) {
        SupabaseConfig.ensureAuthenticated()
        client.postgrest["friends"].delete {
            filter {
                eq("id", friendId)
            }
        }
    }

    suspend fun deleteCreditCard(cardId: Long) = withContext(Dispatchers.IO) {
        SupabaseConfig.ensureAuthenticated()
        client.postgrest["credit_cards"].delete {
            filter {
                eq("id", cardId)
            }
        }
    }

    suspend fun fetchFriends(): List<FriendDto> = withContext(Dispatchers.IO) {
        SupabaseConfig.ensureAuthenticated()
        client.postgrest["friends"].select {
            filter {
                eq("user_id", SupabaseConfig.currentUserId())
            }
        }.decodeList<FriendDto>()
    }

    suspend fun fetchExpenses(): List<ExpenseDto> = withContext(Dispatchers.IO) {
        SupabaseConfig.ensureAuthenticated()
        client.postgrest["expenses"].select {
            filter {
                eq("user_id", SupabaseConfig.currentUserId())
            }
        }.decodeList<ExpenseDto>()
    }

    suspend fun fetchExpenseFriendCrossRefs(): List<ExpenseFriendDto> = withContext(Dispatchers.IO) {
        SupabaseConfig.ensureAuthenticated()
        client.postgrest["expense_friend_cross_ref"].select().decodeList<ExpenseFriendDto>()
    }

    suspend fun fetchCreditCards(): List<CreditCardDto> = withContext(Dispatchers.IO) {
        SupabaseConfig.ensureAuthenticated()
        client.postgrest["credit_cards"].select {
            filter {
                eq("user_id", SupabaseConfig.currentUserId())
            }
        }.decodeList<CreditCardDto>()
    }
}
