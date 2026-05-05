package com.wallet.manager.data.remote.supabase

import android.content.Context
import com.wallet.manager.data.prefs.SettingsDataStore
import com.wallet.manager.util.NetworkUtils
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

object CloudSyncManager {
    private val syncMutex = Mutex()
    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun requestSync(context: Context) {
        val appContext = context.applicationContext
        syncScope.launch {
            runCatching { syncPendingIfPossible(appContext) }
        }
    }

    suspend fun markPending(context: Context) {
        syncMutex.withLock {
            SettingsDataStore(context.applicationContext).markCloudSyncPending()
        }
    }

    suspend fun markExpenseDeleted(context: Context, expenseId: Long) {
        syncMutex.withLock {
            val settings = SettingsDataStore(context.applicationContext)
            settings.addPendingDeletedExpenseId(expenseId)
        }
    }

    suspend fun markFriendDeleted(context: Context, friendId: Long) {
        syncMutex.withLock {
            val settings = SettingsDataStore(context.applicationContext)
            settings.addPendingDeletedFriendId(friendId)
        }
    }

    suspend fun markCreditCardDeleted(context: Context, cardId: Long) {
        syncMutex.withLock {
            val settings = SettingsDataStore(context.applicationContext)
            settings.addPendingDeletedCardId(cardId)
        }
    }

    suspend fun syncPendingIfPossible(context: Context): Boolean = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val settings = SettingsDataStore(appContext)
        if (!settings.pendingCloudSyncFlow.first()) return@withContext false
        syncNow(appContext)
    }

    suspend fun syncNow(context: Context): Boolean = withContext(Dispatchers.IO) {
        syncMutex.withLock {
            val appContext = context.applicationContext
            val settings = SettingsDataStore(appContext)
            val isSignedIn = settings.isSignedInFlow.first()
            val hasSession = SupabaseConfig.client.auth.currentSessionOrNull() != null
            if (!isSignedIn || !hasSession || !NetworkUtils.isInternetAvailable(appContext)) {
                settings.markCloudSyncPending()
                false
            } else {
                val service = SupabaseService()
                val deletedExpenseIds = settings.pendingDeletedExpenseIds()
                val deletedFriendIds = settings.pendingDeletedFriendIds()
                val deletedCardIds = settings.pendingDeletedCardIds()

                deletedExpenseIds.forEach { expenseId ->
                    service.deleteExpenseFriendCrossRefsForExpense(expenseId)
                    service.deleteExpense(expenseId)
                }
                deletedFriendIds.forEach { friendId ->
                    service.deleteExpenseFriendCrossRefsForFriend(friendId)
                }

                val pushed = SupabaseRestoreManager.pushLocalToCloud(appContext)

                deletedFriendIds.forEach { friendId ->
                    service.deleteFriend(friendId)
                }
                deletedCardIds.forEach { cardId ->
                    service.deleteCreditCard(cardId)
                }

                val deletedSomething = deletedExpenseIds.isNotEmpty() ||
                    deletedFriendIds.isNotEmpty() ||
                    deletedCardIds.isNotEmpty()

                settings.setLastCloudSyncAt(System.currentTimeMillis())
                settings.clearCloudSyncPending()
                pushed || deletedSomething
            }
        }
    }
}
