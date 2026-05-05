package com.wallet.manager.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

const val TRANSACTION_KIND_EXPENSE = "expense"
const val TRANSACTION_KIND_INCOME = "income"

@Entity(tableName = "expenses")
data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val transactionKind: String = TRANSACTION_KIND_EXPENSE,
    val type: String,
    val title: String,
    val content: String,
    val amount: Double,
    val date: Long,
    val imageUri: String?,
    val createdAt: Long,
    val isSplit: Boolean = false,
    val payerId: Long? = null, // null means "Me", otherwise it's a Friend ID
    val isSettled: Boolean = false,
    val myShareCount: Int = 1, // How many shares "Me" is responsible for
    val creditCardId: Long? = null
)
