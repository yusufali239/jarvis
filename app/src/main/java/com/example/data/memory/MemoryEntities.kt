package com.example.data.memory

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "jarvis_memories")
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val key: String,
    val value: String,
    val category: String = "general",
    val timestamp: Long = System.currentTimeMillis(),
    val importance: Int = 1
)

@Entity(tableName = "jarvis_conversations")
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val role: String, // "user", "assistant", "system"
    val content: String,
    val toolCallsJson: String? = null,
    val thinkingContent: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "jarvis_action_logs")
data class ActionLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val actionType: String,
    val description: String,
    val status: String, // "SUCCESS", "FAILED", "PENDING_CONFIRMATION", "CANCELLED"
    val riskLevel: String, // "LOW", "MEDIUM", "HIGH", "CRITICAL"
    val details: String? = null
)

/**
 * Dedicated Chat Session Entity for ChatGPT-like multi-session conversation history.
 */
@Entity(tableName = "jarvis_chat_sessions")
data class ChatSessionEntity(
    @PrimaryKey val id: String, // Unique UUID or session identifier
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val provider: String = "Gemini",
    val model: String = "gemini-2.5-flash",
    val messageCount: Int = 0,
    val isLiveSession: Boolean = false
) {
    val sessionId: String get() = id
}

/**
 * Individual chat message associated with a ChatSessionEntity.
 */
@Entity(
    tableName = "jarvis_chat_messages",
    indices = [Index(value = ["sessionId"])]
)
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val role: String, // "user", "assistant", "system", "tool"
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val audioAvailable: Boolean = false,
    val toolCall: String? = null,
    val thinkingContent: String? = null,
    val metadata: String? = null,
    val provider: String? = null
)

