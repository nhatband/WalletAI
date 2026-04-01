package com.wallet.manager.data.mapper

import com.wallet.manager.data.local.entity.CreditCard
import com.wallet.manager.data.local.entity.Expense
import com.wallet.manager.data.local.entity.ExpenseFriendCrossRef
import com.wallet.manager.data.local.entity.Friend
import com.wallet.manager.data.remote.supabase.SupabaseConfig
import com.wallet.manager.data.remote.supabase.model.CreditCardDto
import com.wallet.manager.data.remote.supabase.model.ExpenseDto
import com.wallet.manager.data.remote.supabase.model.ExpenseFriendDto
import com.wallet.manager.data.remote.supabase.model.FriendDto

fun Expense.toDto() = ExpenseDto(
    id = if (id == 0L) null else id,
    user_id = SupabaseConfig.currentUserId(),
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
    my_share_count = myShareCount,
    credit_card_id = creditCardId
)

fun Friend.toDto() = FriendDto(
    id = if (id == 0L) null else id,
    user_id = SupabaseConfig.currentUserId(),
    name = name,
    phone_number = phoneNumber,
    image_uri = imageUri,
    total_owed = totalOwed,
    created_at = createdAt
)

fun CreditCard.toDto() = CreditCardDto(
    id = if (id == 0L) null else id,
    user_id = SupabaseConfig.currentUserId(),
    name = name,
    holder_name = holderName,
    last4_digits = last4Digits,
    statement_day = statementDay,
    image_uri = imageUri,
    created_at = createdAt
)

fun ExpenseDto.toEntity() = Expense(
    id = id ?: 0L,
    type = type,
    title = title,
    content = content,
    amount = amount,
    date = date,
    imageUri = image_uri,
    createdAt = created_at,
    isSplit = is_split,
    payerId = payer_id,
    isSettled = is_settled,
    myShareCount = my_share_count,
    creditCardId = credit_card_id
)

fun FriendDto.toEntity() = Friend(
    id = id ?: 0L,
    name = name,
    phoneNumber = phone_number,
    imageUri = image_uri,
    totalOwed = total_owed,
    createdAt = created_at
)

fun CreditCardDto.toEntity() = CreditCard(
    id = id ?: 0L,
    name = name,
    holderName = holder_name,
    last4Digits = last4_digits,
    statementDay = statement_day,
    imageUri = image_uri,
    createdAt = created_at
)

fun ExpenseFriendDto.toEntity() = ExpenseFriendCrossRef(
    expenseId = expense_id,
    friendId = friend_id,
    shareCount = share_count,
    isSettled = is_settled
)
