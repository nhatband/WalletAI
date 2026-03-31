package com.wallet.manager.data.local.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "expense_friend_cross_ref",
    primaryKeys = ["expenseId", "friendId"],
    indices = [Index(value = ["friendId"])]
)
data class ExpenseFriendCrossRef(
    val expenseId: Long,
    val friendId: Long,
    val shareCount: Int = 1,
    val isSettled: Boolean = false
)
