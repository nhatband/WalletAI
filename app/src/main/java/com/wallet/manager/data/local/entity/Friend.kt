package com.wallet.manager.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "friends")
data class Friend(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val phoneNumber: String? = null,
    val imageUri: String? = null,
    val totalOwed: Double = 0.0,
    val createdAt: Long = System.currentTimeMillis()
)
