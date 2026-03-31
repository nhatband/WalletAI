package com.wallet.manager.data.mapper

import com.wallet.manager.data.local.entity.Expense
import com.wallet.manager.data.local.entity.Friend
import com.wallet.manager.data.remote.supabase.model.ExpenseDto
import com.wallet.manager.data.remote.supabase.model.FriendDto

fun Expense.toDto() = ExpenseDto(
    id = if (id == 0L) null else id,
    type = type,
    title = title,
    content = content,
    amount = amount,
    date = date,
    image_uri = imageUri,
    created_at = createdAt,
    is_split = isSplit,
    payer_id = payerId,
    is_settled = isSettled,
    my_share_count = myShareCount
)

fun Friend.toDto() = FriendDto(
    id = if (id == 0L) null else id,
    name = name,
    phone_number = phoneNumber,
    image_uri = imageUri,
    total_owed = totalOwed,
    created_at = createdAt
)
