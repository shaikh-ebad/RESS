package com.example.offlinemeshchat

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    @Insert
    suspend fun insertMessage(
        message: Message
    )

    @Query(
        "SELECT * FROM messages " +
                "WHERE node = :node " +
                "ORDER BY timestamp ASC"
    )
    fun observeMessagesForNode(
        node: String
    ): Flow<List<Message>>

    @Query(
        "SELECT * FROM messages " +
                "WHERE node = :node " +
                "ORDER BY timestamp ASC"
    )
    suspend fun getMessagesForNode(
        node: String
    ): List<Message>

    @Query(
        "SELECT DISTINCT node " +
                "FROM messages " +
                "ORDER BY node ASC"
    )
    suspend fun getAllPhones(): List<String>

    @Query("DELETE FROM messages")
    suspend fun deleteAllMessages()
}