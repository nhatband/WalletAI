package com.wallet.manager.data.remote.supabase.model

import kotlinx.serialization.Serializable

@Serializable
data class ExpenseDto(
    val id: Long? = null,
    val type: String,
    val title: String,
    val content: String,
    val amount: Double,
    val date: Long,
    val image_uri: String? = null,
    val created_at: Long,
    val is_split: Boolean = false,
    val payer_id: Long? = null,
    val is_settled: Boolean = false,
    val my_share_count: Int = 1
)

@Serializable
data class FriendDto(
    val id: Long? = null,
    val name: String,
    val phone_number: String? = null,
    val image_uri: String? = null,
    val total_owed: Double = 0.0,
    val created_at: Long
)

@Serializable
data class ExpenseFriendDto(
    val expense_id: Long,
    val friend_id: Long,
    val share_count: Int = 1,
    val is_settled: Boolean = false
)
