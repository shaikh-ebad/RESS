package com.example.offlinemeshchat.ai

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface AIMessageDao {

    @Insert
    suspend fun insertMessage(message: AIMessage)

    @Query("SELECT * FROM ai_messages ORDER BY timestamp ASC")
    suspend fun getAllMessages(): List<AIMessage>

    @Query("DELETE FROM ai_messages")
    suspend fun deleteAllMessages()
}