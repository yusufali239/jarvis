package com.example.data.memory

import kotlinx.coroutines.flow.Flow
import java.util.UUID

class MemoryManager(private val dao: JarvisDao) {

    val allMemories: Flow<List<MemoryEntity>> = dao.getAllMemories()
    val allConversations: Flow<List<ConversationEntity>> = dao.getAllConversations()
    val allActionLogs: Flow<List<ActionLogEntity>> = dao.getAllActionLogs()
    val allChatSessions: Flow<List<ChatSessionEntity>> = dao.getAllChatSessions()

    // --- Active Chat Session Tracking ---
    @Volatile
    var currentActiveSessionId: String = UUID.randomUUID().toString()
        private set

    fun getMessagesForSession(sessionId: String): Flow<List<ChatMessageEntity>> {
        return dao.getMessagesForSession(sessionId)
    }

    fun searchChatSessions(query: String): Flow<List<ChatSessionEntity>> {
        return dao.searchChatSessions(query)
    }

    suspend fun getOrCreateActiveSession(
        provider: String = "Gemini",
        model: String = "gemini-2.5-flash",
        isLive: Boolean = false,
        initialTitle: String? = null
    ): ChatSessionEntity {
        var session = dao.getChatSessionById(currentActiveSessionId)
        if (session == null) {
            val title = initialTitle ?: if (isLive) "Live Voice Conversation" else "New Conversation"
            session = ChatSessionEntity(
                id = currentActiveSessionId,
                title = title,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                provider = provider,
                model = model,
                messageCount = 0,
                isLiveSession = isLive
            )
            dao.insertChatSession(session)
        }
        return session
    }

    fun startNewSession(
        provider: String = "Gemini",
        model: String = "gemini-2.5-flash",
        isLive: Boolean = false,
        title: String = "New Conversation"
    ): String {
        val newId = UUID.randomUUID().toString()
        currentActiveSessionId = newId
        return newId
    }

    fun setActiveSessionId(sessionId: String) {
        currentActiveSessionId = sessionId
    }

    suspend fun saveChatMessage(
        sessionId: String = currentActiveSessionId,
        role: String,
        text: String,
        audioAvailable: Boolean = false,
        toolCall: String? = null,
        thinkingContent: String? = null,
        metadata: String? = null,
        provider: String = "Gemini",
        model: String = "gemini-2.5-flash"
    ): Long {
        if (text.isBlank() && toolCall == null) return 0L

        var session = dao.getChatSessionById(sessionId)
        val isFirstMessage = session == null || session.messageCount == 0

        val msgId = dao.insertChatMessage(
            ChatMessageEntity(
                sessionId = sessionId,
                role = role,
                text = text,
                timestamp = System.currentTimeMillis(),
                audioAvailable = audioAvailable,
                toolCall = toolCall,
                thinkingContent = thinkingContent,
                metadata = metadata,
                provider = provider
            )
        )

        // Also save to legacy conversation table for backward compatibility
        dao.insertConversation(
            ConversationEntity(
                role = role,
                content = text,
                toolCallsJson = toolCall,
                thinkingContent = thinkingContent,
                timestamp = System.currentTimeMillis()
            )
        )

        val newCount = (session?.messageCount ?: 0) + 1
        var newTitle = session?.title ?: "New Conversation"

        // Auto-generate title on first user turn
        if (isFirstMessage && role.equals("user", ignoreCase = true) && text.isNotBlank()) {
            newTitle = generateSmartChatTitle(text)
        }

        val updatedSession = ChatSessionEntity(
            id = sessionId,
            title = newTitle,
            createdAt = session?.createdAt ?: System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            provider = provider,
            model = model,
            messageCount = newCount,
            isLiveSession = session?.isLiveSession ?: false
        )
        dao.insertChatSession(updatedSession)

        return msgId
    }

    suspend fun renameChatSession(sessionId: String, newTitle: String) {
        if (newTitle.isNotBlank()) {
            dao.updateChatSessionTitle(sessionId, newTitle.trim(), System.currentTimeMillis())
        }
    }

    suspend fun deleteChatSession(sessionId: String) {
        dao.deleteSessionWithMessages(sessionId)
        if (currentActiveSessionId == sessionId) {
            currentActiveSessionId = UUID.randomUUID().toString()
        }
    }

    suspend fun getRecentMessagesForSession(sessionId: String = currentActiveSessionId, limit: Int = 30): List<ChatMessageEntity> {
        return dao.getRecentMessagesForSession(sessionId, limit).reversed()
    }

    suspend fun clearAllChatHistory() {
        dao.clearAllChatMessages()
        dao.clearAllChatSessions()
        dao.clearConversationHistory()
        currentActiveSessionId = UUID.randomUUID().toString()
    }

    /**
     * Intelligently formats a user's prompt into a clean, concise title (like ChatGPT).
     */
    private fun generateSmartChatTitle(userText: String): String {
        var clean = userText.trim().replace(Regex("[\\n\\r]+"), " ")
        val lower = clean.lowercase()

        // Strip common conversational request prefixes
        val prefixes = listOf(
            "расскажи мне про", "расскажи про", "расскажи о", "объясни мне", "объясни",
            "как работает", "что такое", "кто такой", "почему", "как сделать",
            "напиши код для", "напиши", "спланируй", "помоги мне с", "помоги",
            "what is", "tell me about", "how does", "explain", "who is", "why is",
            "help me with", "write code for", "how to"
        )

        for (p in prefixes) {
            if (lower.startsWith(p)) {
                clean = clean.substring(p.length).trim().trimStart(':', '-', ' ')
                break
            }
        }

        if (clean.isBlank()) clean = userText.trim()

        // Capitalize first character
        clean = clean.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

        // Truncate to reasonable length (max 36 chars)
        if (clean.length > 36) {
            clean = clean.take(34).trimEnd() + "…"
        }
        return clean
    }

    // --- Memories ---
    suspend fun remember(key: String, value: String, category: String = "general", importance: Int = 1) {
        dao.insertMemory(
            MemoryEntity(
                key = key.trim(),
                value = value.trim(),
                category = category.trim(),
                importance = importance
            )
        )
    }

    suspend fun recall(key: String): String? {
        return dao.getMemoryByKey(key.trim())?.value
    }

    suspend fun forget(key: String) {
        dao.deleteMemoryByKey(key.trim())
    }

    suspend fun deleteById(id: Long) {
        dao.deleteMemoryById(id)
    }

    suspend fun searchMemory(query: String): List<MemoryEntity> {
        return dao.searchMemories(query.trim())
    }

    suspend fun clearAllMemory() {
        dao.clearAllMemories()
    }

    // --- Legacy / Direct Messages ---
    suspend fun saveMessage(
        role: String,
        content: String,
        toolCallsJson: String? = null,
        thinkingContent: String? = null
    ): Long {
        return saveChatMessage(
            sessionId = currentActiveSessionId,
            role = role,
            text = content,
            toolCall = toolCallsJson,
            thinkingContent = thinkingContent
        )
    }

    suspend fun getRecentMessages(limit: Int = 20): List<ConversationEntity> {
        return dao.getRecentConversations(limit).reversed()
    }

    suspend fun clearConversation() {
        dao.clearConversationHistory()
    }

    // --- Action Logs ---
    suspend fun logAction(
        actionType: String,
        description: String,
        status: String,
        riskLevel: String,
        details: String? = null
    ) {
        dao.insertActionLog(
            ActionLogEntity(
                actionType = actionType,
                description = description,
                status = status,
                riskLevel = riskLevel,
                details = details
            )
        )
    }

    suspend fun clearActionLogs() {
        dao.clearActionLogs()
    }

    suspend fun getFormattedMemoryContext(): String {
        val memories = dao.searchMemories("")
        if (memories.isEmpty()) return ""
        return buildString {
            append("\n[JARVIS LONG-TERM MEMORY]\n")
            for (m in memories) {
                append("- ${m.key}: ${m.value}\n")
            }
        }
    }
}

