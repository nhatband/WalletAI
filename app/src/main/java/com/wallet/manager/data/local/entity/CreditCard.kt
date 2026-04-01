package com.wallet.manager.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "credit_cards")
data class CreditCard(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val holderName: String,
    val last4Digits: String,
    val statementDay: Int,
    val imageUri: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
