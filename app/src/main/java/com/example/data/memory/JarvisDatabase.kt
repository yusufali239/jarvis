package com.example.data.memory

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import android.content.Context
import kotlinx.coroutines.flow.Flow

@Dao
interface JarvisDao {

    // --- Memories ---
    @Query("SELECT * FROM jarvis_memories ORDER BY timestamp DESC")
    fun getAllMemories(): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM jarvis_memories WHERE `key` LIKE '%' || :query || '%' OR `value` LIKE '%' || :query || '%'")
    suspend fun searchMemories(query: String): List<MemoryEntity>

    @Query("SELECT * FROM jarvis_memories WHERE `key` = :key LIMIT 1")
    suspend fun getMemoryByKey(key: String): MemoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(memory: MemoryEntity): Long

    @Query("DELETE FROM jarvis_memories WHERE `key` = :key")
    suspend fun deleteMemoryByKey(key: String)

    @Query("DELETE FROM jarvis_memories WHERE id = :id")
    suspend fun deleteMemoryById(id: Long)

    @Query("DELETE FROM jarvis_memories")
    suspend fun clearAllMemories()

    // --- Legacy Conversations ---
    @Query("SELECT * FROM jarvis_conversations ORDER BY timestamp ASC")
    fun getAllConversations(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM jarvis_conversations ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentConversations(limit: Int = 30): List<ConversationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversation(message: ConversationEntity): Long

    @Query("DELETE FROM jarvis_conversations")
    suspend fun clearConversationHistory()

    // --- ChatGPT-Style Chat Sessions ---
    @Query("SELECT * FROM jarvis_chat_sessions ORDER BY updatedAt DESC")
    fun getAllChatSessions(): Flow<List<ChatSessionEntity>>

    @Query("SELECT * FROM jarvis_chat_sessions WHERE title LIKE '%' || :query || '%' ORDER BY updatedAt DESC")
    fun searchChatSessions(query: String): Flow<List<ChatSessionEntity>>

    @Query("SELECT * FROM jarvis_chat_sessions WHERE id = :id LIMIT 1")
    suspend fun getChatSessionById(id: String): ChatSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChatSession(session: ChatSessionEntity)

    @Query("UPDATE jarvis_chat_sessions SET title = :title, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateChatSessionTitle(id: String, title: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE jarvis_chat_sessions SET messageCount = :count, updatedAt = :updatedAt, provider = :provider, model = :model WHERE id = :id")
    suspend fun updateChatSessionMetadata(id: String, count: Int, updatedAt: Long, provider: String, model: String)

    @Query("DELETE FROM jarvis_chat_sessions WHERE id = :id")
    suspend fun deleteChatSession(id: String)

    @Query("DELETE FROM jarvis_chat_sessions")
    suspend fun clearAllChatSessions()

    // --- Chat Messages per Session ---
    @Query("SELECT * FROM jarvis_chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getMessagesForSession(sessionId: String): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM jarvis_chat_messages WHERE sessionId = :sessionId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentMessagesForSession(sessionId: String, limit: Int = 30): List<ChatMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChatMessage(message: ChatMessageEntity): Long

    @Query("DELETE FROM jarvis_chat_messages WHERE sessionId = :sessionId")
    suspend fun deleteMessagesForSession(sessionId: String)

    @Query("DELETE FROM jarvis_chat_messages")
    suspend fun clearAllChatMessages()

    @Query("SELECT * FROM jarvis_chat_messages WHERE text LIKE '%' || :query || '%' ORDER BY timestamp DESC LIMIT 50")
    suspend fun searchChatMessages(query: String): List<ChatMessageEntity>

    @Transaction
    suspend fun deleteSessionWithMessages(sessionId: String) {
        deleteMessagesForSession(sessionId)
        deleteChatSession(sessionId)
    }

    // --- Action Logs ---
    @Query("SELECT * FROM jarvis_action_logs ORDER BY timestamp DESC")
    fun getAllActionLogs(): Flow<List<ActionLogEntity>>

    @Query("SELECT * FROM jarvis_action_logs ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentActionLogs(limit: Int = 50): List<ActionLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActionLog(log: ActionLogEntity): Long

    @Query("DELETE FROM jarvis_action_logs")
    suspend fun clearActionLogs()
}

@Database(
    entities = [
        MemoryEntity::class,
        ConversationEntity::class,
        ActionLogEntity::class,
        ChatSessionEntity::class,
        ChatMessageEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class JarvisDatabase : RoomDatabase() {
    abstract fun jarvisDao(): JarvisDao

    companion object {
        @Volatile
        private var INSTANCE: JarvisDatabase? = null

        fun getInstance(context: Context): JarvisDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    JarvisDatabase::class.java,
                    "jarvis_os.db"
                ).fallbackToDestructiveMigration()
                 .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

