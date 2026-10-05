package com.example.offlinemeshchat

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.offlinemeshchat.ai.AIMessage
import com.example.offlinemeshchat.ai.AIMessageDao
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        Message::class,
        AIMessage::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun messageDao(): MessageDao

    abstract fun aiMessageDao(): AIMessageDao

    companion object {

        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
            CREATE TABLE IF NOT EXISTS `ai_messages` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `message` TEXT NOT NULL,
                `isUser` INTEGER NOT NULL,
                `timestamp` INTEGER NOT NULL
            )
            """.trimIndent()
                )
            }
        }

        fun getDatabase(context: Context): AppDatabase {

            return INSTANCE ?: synchronized(this) {

                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "offline_mesh_database"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()

                INSTANCE = instance
                instance
            }
        }
    }
}