package com.example.offlinemeshchat.ai

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ai_messages")
data class AIMessage(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val message: String,

    val isUser: Boolean,

    val timestamp: Long = System.currentTimeMillis()
)