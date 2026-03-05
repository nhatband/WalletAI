package com.wallet.manager.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val isUser: Boolean,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)
