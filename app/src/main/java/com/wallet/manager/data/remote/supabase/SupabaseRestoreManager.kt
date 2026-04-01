package com.wallet.manager.data.remote.supabase

import android.content.Context
import androidx.room.withTransaction
import com.wallet.manager.data.local.db.AppDatabase
import com.wallet.manager.data.mapper.toEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object SupabaseRestoreManager {
    suspend fun restoreIfLocalEmpty(context: Context) = withContext(Dispatchers.IO) {
        val db = AppDatabase.get(context)
        val expenseDao = db.expenseDao()
        val friendDao = db.friendDao()

        val hasLocalData = expenseDao.getExpenseCount() > 0 || friendDao.getFriendCount() > 0
        if (hasLocalData) return@withContext

        val service = SupabaseService()
        val remoteFriends = try {
            service.fetchFriends()
        } catch (_: Throwable) {
            emptyList()
        }
        val remoteExpenses = try {
            service.fetchExpenses()
        } catch (_: Throwable) {
            emptyList()
        }
        val remoteCrossRefs = try {
            service.fetchExpenseFriendCrossRefs()
        } catch (_: Throwable) {
            emptyList()
        }

        if (remoteFriends.isEmpty() && remoteExpenses.isEmpty() && remoteCrossRefs.isEmpty()) {
            return@withContext
        }

        db.withTransaction {
            expenseDao.deleteAllFriendCrossRefs()
            expenseDao.deleteAllExpenses()
            friendDao.deleteAllFriends()

            if (remoteFriends.isNotEmpty()) {
                friendDao.insertAllFriends(remoteFriends.map { it.toEntity() })
            }
            if (remoteExpenses.isNotEmpty()) {
                expenseDao.insertAll(remoteExpenses.map { it.toEntity() })
            }
            if (remoteCrossRefs.isNotEmpty()) {
                expenseDao.insertFriendCrossRefs(remoteCrossRefs.map { it.toEntity() })
            }
        }
    }
}
