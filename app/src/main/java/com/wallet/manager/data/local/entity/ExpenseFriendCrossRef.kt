package com.wallet.manager.data.local.entity

import androidx.room.Entity

@Entity(tableName = "expense_friend_cross_ref", primaryKeys = ["expenseId", "friendId"])
data class ExpenseFriendCrossRef(
    val expenseId: Long,
    val friendId: Long,
    val shareCount: Int = 1,
    val isSettled: Boolean = false
)
