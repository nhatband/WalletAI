package com.wallet.manager.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.migration.Migration
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.wallet.manager.data.local.entity.Expense
import com.wallet.manager.data.local.entity.ChatMessageEntity
import com.wallet.manager.data.local.entity.CreditCard
import com.wallet.manager.data.local.entity.Friend
import com.wallet.manager.data.local.entity.ExpenseFriendCrossRef

@Database(
    entities = [
        Expense::class, 
        ChatMessageEntity::class, 
        Friend::class, 
        ExpenseFriendCrossRef::class,
        CreditCard::class
    ],
    version = 10,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun expenseDao(): ExpenseDao
    abstract fun chatDao(): ChatDao
    abstract fun friendDao(): FriendDao
    abstract fun creditCardDao(): CreditCardDao

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
                .addMigrations(MIGRATION_9_10)
                .fallbackToDestructiveMigration()
                .build().also { INSTANCE = it }
            }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE expenses ADD COLUMN transactionKind TEXT NOT NULL DEFAULT 'expense'"
                )
            }
        }
    }
}
