package com.example.offlinemeshchat

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class Message(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val node: String,
    val text: String,
    val isSent: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)