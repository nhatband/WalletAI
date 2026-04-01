package com.wallet.manager.data.remote.supabase

import android.content.Context
import androidx.room.withTransaction
import com.wallet.manager.data.local.db.AppDatabase
import com.wallet.manager.data.local.entity.ExpenseFriendCrossRef
import com.wallet.manager.data.mapper.toEntity
import com.wallet.manager.data.prefs.SettingsDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

object SupabaseRestoreManager {
    suspend fun clearLocalData(context: Context) = withContext(Dispatchers.IO) {
        val db = AppDatabase.get(context)
        db.withTransaction {
            db.expenseDao().deleteAllFriendCrossRefs()
            db.expenseDao().deleteAllExpenses()
            db.friendDao().deleteAllFriends()
            db.creditCardDao().deleteAll()
            db.chatDao().clearHistory()
        }
    }

    suspend fun restoreIfLocalEmpty(context: Context) = withContext(Dispatchers.IO) {
        val settings = SettingsDataStore(context)
        val isSignedIn = settings.isSignedInFlow.first()
        val autoRestoreDone = settings.autoRestoreDoneFlow.first()
        if (!isSignedIn || autoRestoreDone) return@withContext

        val restored = restoreFromCloud(context, onlyWhenLocalEmpty = true)
        settings.setAutoRestoreDone(true)
        if (restored) {
            settings.setLastCloudSyncAt(System.currentTimeMillis())
        }
    }

    suspend fun restoreFromCloud(
        context: Context,
        onlyWhenLocalEmpty: Boolean = false
    ): Boolean = withContext(Dispatchers.IO) {
        val db = AppDatabase.get(context)
        val expenseDao = db.expenseDao()
        val friendDao = db.friendDao()
        val creditCardDao = db.creditCardDao()

        val hasLocalData = expenseDao.getExpenseCount() > 0 ||
            friendDao.getFriendCount() > 0 ||
            creditCardDao.getCardCount() > 0
        if (onlyWhenLocalEmpty && hasLocalData) return@withContext false

        val service = SupabaseService()
        val remoteCreditCards = service.fetchCreditCards()
        val remoteFriends = service.fetchFriends()
        val remoteExpenses = service.fetchExpenses()
        val remoteCrossRefs = service.fetchExpenseFriendCrossRefs()

        if (remoteCreditCards.isEmpty() && remoteFriends.isEmpty() && remoteExpenses.isEmpty() && remoteCrossRefs.isEmpty()) {
            return@withContext false
        }

        db.withTransaction {
            expenseDao.deleteAllFriendCrossRefs()
            expenseDao.deleteAllExpenses()
            friendDao.deleteAllFriends()
            creditCardDao.deleteAll()

            if (remoteCreditCards.isNotEmpty()) {
                creditCardDao.insertAll(remoteCreditCards.map { it.toEntity() })
            }
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
        true
    }

    suspend fun pushLocalToCloud(context: Context): Boolean = withContext(Dispatchers.IO) {
        val db = AppDatabase.get(context)
        val expenseDao = db.expenseDao()
        val friendDao = db.friendDao()
        val creditCardDao = db.creditCardDao()
        val service = SupabaseService()

        val creditCards = creditCardDao.getAllCardsList()
        val friends = friendDao.getAllFriendsList()
        val expenses = expenseDao.getAllExpensesList()
        val crossRefs = expenseDao.getAllFriendCrossRefsList()

        if (creditCards.isEmpty() && friends.isEmpty() && expenses.isEmpty() && crossRefs.isEmpty()) {
            return@withContext false
        }

        creditCards.forEach { card ->
            service.syncCreditCard(card)
        }
        friends.forEach { friend ->
            service.syncFriend(friend)
        }
        expenses.forEach { expense ->
            service.syncExpense(expense)
        }
        crossRefs.forEach { crossRef: ExpenseFriendCrossRef ->
            service.syncExpenseFriendCrossRef(
                expenseId = crossRef.expenseId,
                friendId = crossRef.friendId,
                shareCount = crossRef.shareCount,
                isSettled = crossRef.isSettled
            )
        }
        true
    }
}
