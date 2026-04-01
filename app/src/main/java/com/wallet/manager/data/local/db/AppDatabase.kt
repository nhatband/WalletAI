package com.wallet.manager.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.wallet.manager.data.local.entity.Expense
import com.wallet.manager.data.local.entity.ChatMessageEntity
import com.wallet.manager.data.local.entity.Friend
import com.wallet.manager.data.local.entity.ExpenseFriendCrossRef

@Database(
    entities = [
        Expense::class, 
        ChatMessageEntity::class, 
        Friend::class, 
        ExpenseFriendCrossRef::class
    ],
    version = 8,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun expenseDao(): ExpenseDao
    abstract fun chatDao(): ChatDao
    abstract fun friendDao(): FriendDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "wallet_db"
                )
                .fallbackToDestructiveMigration()
                .build().also { INSTANCE = it }
            }
    }
}
