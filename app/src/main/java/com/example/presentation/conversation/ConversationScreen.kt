package com.example.presentation.conversation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.ai.provider.AIProviderType
import com.example.data.memory.ChatMessageEntity
import com.example.data.memory.ChatSessionEntity
import com.example.presentation.components.ThinkingTraceCard
import com.example.ui.theme.JarvisAmber
import com.example.ui.theme.JarvisBackground
import com.example.ui.theme.JarvisBorder
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisCyanGlow
import com.example.ui.theme.JarvisElectricBlue
import com.example.ui.theme.JarvisEmerald
import com.example.ui.theme.JarvisRed
import com.example.ui.theme.JarvisSurface
import com.example.ui.theme.JarvisSurfaceVariant
import com.example.ui.theme.JarvisTextMuted
import com.example.ui.theme.JarvisTextPrimary
import com.example.ui.theme.JarvisTextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ConversationScreen(
    sessions: List<ChatSessionEntity>,
    activeSessionId: String,
    messages: List<ChatMessageEntity>,
    searchQuery: String,
    activeProviderType: AIProviderType,
    onSelectProvider: (AIProviderType) -> Unit,
    onSelectSession: (String) -> Unit,
    onCreateNewChat: () -> Unit,
    onRenameSession: (String, String) -> Unit,
    onDeleteSession: (String) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSendMessage: (String, Boolean) -> Unit,
    onSpeakText: (String) -> Unit,
    onClearHistory: () -> Unit
) {
    var textInput by remember { mutableStateOf("") }
    var useHighThinking by remember { mutableStateOf(false) }
    var showHistoryDrawer by remember { mutableStateOf(false) }
    var renamingSession by remember { mutableStateOf<ChatSessionEntity?>(null) }
    var renameInput by remember { mutableStateOf("") }

    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    val activeSession = sessions.find { it.sessionId == activeSessionId }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(JarvisBackground)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Top Action & Title Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { showHistoryDrawer = !showHistoryDrawer }
                    .background(if (showHistoryDrawer) JarvisCyan.copy(alpha = 0.2f) else JarvisSurfaceVariant)
                    .border(1.dp, if (showHistoryDrawer) JarvisCyan else JarvisBorder, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = "Toggle History",
                    tint = JarvisCyan,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "CHATS (${sessions.size})",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = JarvisCyan
                )
            }

            // Active Chat Title Header
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
            ) {
                Text(
                    text = activeSession?.title ?: "Current Dialogue",
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = JarvisTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (activeSession?.isLiveSession == true) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(JarvisEmerald)
                        )
                        Text(
                            text = "LIVE VOICE",
                            fontSize = 8.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = JarvisEmerald
                        )
                    } else {
                        Text(
                            text = "${activeSession?.provider ?: activeProviderType.displayName}",
                            fontSize = 8.sp,
                            fontFamily = FontFamily.Monospace,
                            color = JarvisCyan
                        )
                    }
                }
            }

            // New Chat Action
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = {
                        onCreateNewChat()
                        showHistoryDrawer = false
                    },
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(JarvisCyan.copy(alpha = 0.15f))
                        .border(1.dp, JarvisCyan.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .testTag("new_chat_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "New Chat",
                        tint = JarvisCyan,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = onClearHistory,
                    modifier = Modifier.size(34.dp).testTag("clear_dialogue_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = "Clear Current History",
                        tint = JarvisTextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Provider Engine Quick Switcher Row
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(AIProviderType.values().toList()) { prov ->
                val isSelected = activeProviderType == prov
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isSelected) JarvisCyan else JarvisSurface)
                        .border(1.dp, if (isSelected) JarvisCyan else JarvisBorder, RoundedCornerShape(6.dp))
                        .clickable { onSelectProvider(prov) }
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = prov.displayName,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) Color.Black else JarvisTextSecondary
                    )
                }
            }
        }

        // Expandable Chat History Panel
        AnimatedVisibility(
            visible = showHistoryDrawer,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(JarvisSurface)
                    .border(1.dp, JarvisCyan.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SAVED CONVERSATION ARCHIVES",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = JarvisCyan
                    )

                    IconButton(
                        onClick = { showHistoryDrawer = false },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Archives",
                            tint = JarvisTextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = {
                        Text(
                            text = "Search chat history...",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = JarvisTextMuted
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = JarvisTextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = JarvisCyan,
                        unfocusedBorderColor = JarvisBorder,
                        focusedTextColor = JarvisTextPrimary,
                        unfocusedTextColor = JarvisTextPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                )

                // Sessions List
                if (sessions.isEmpty()) {
                    Text(
                        text = "No saved chat history found.",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = JarvisTextMuted,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(sessions) { session ->
                            val isCurrent = session.sessionId == activeSessionId
                            val sessionTimeStr = remember(session.updatedAt) {
                                SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()).format(Date(session.updatedAt))
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isCurrent) JarvisSurfaceVariant else JarvisBackground)
                                    .border(
                                        1.dp,
                                        if (isCurrent) JarvisCyan else JarvisBorder,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        onSelectSession(session.sessionId)
                                        showHistoryDrawer = false
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        if (session.isLiveSession) {
                                            Icon(
                                                imageVector = Icons.Default.GraphicEq,
                                                contentDescription = "Live Voice",
                                                tint = JarvisEmerald,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                        Text(
                                            text = session.title,
                                            fontSize = 12.sp,
                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isCurrent) JarvisCyan else JarvisTextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Text(
                                        text = "${session.provider} • $sessionTimeStr • ${session.messageCount} msgs",
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = JarvisTextMuted
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    IconButton(
                                        onClick = {
                                            renamingSession = session
                                            renameInput = session.title
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Rename",
                                            tint = JarvisTextMuted,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = { onDeleteSession(session.sessionId) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete",
                                            tint = JarvisRed.copy(alpha = 0.7f),
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Messages List for Active Session
        if (messages.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SmartToy,
                        contentDescription = null,
                        tint = JarvisCyan.copy(alpha = 0.5f),
                        modifier = Modifier.size(40.dp)
                    )
                    Text(
                        text = "NEURAL DIALOGUE READY\nActive Engine: ${activeSession?.provider ?: activeProviderType.displayName}\nStart conversation via voice or text input below.",
                        color = JarvisTextMuted,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        lineHeight = 16.sp
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(messages) { msg ->
                    ChatMessageBubble(
                        item = msg,
                        onSpeak = { onSpeakText(msg.text) }
                    )
                }
            }
        }

        // Input bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(JarvisSurface)
                .border(1.dp, JarvisBorder, RoundedCornerShape(12.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { useHighThinking = !useHighThinking },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Psychology,
                    contentDescription = "Toggle High Thinking",
                    tint = if (useHighThinking) JarvisAmber else JarvisTextMuted
                )
            }

            OutlinedTextField(
                value = textInput,
                onValueChange = { textInput = it },
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        text = if (useHighThinking) "Message (High Thinking)..." else "Send to J.A.R.V.I.S...",
                        color = JarvisTextMuted,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace
                    )
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedTextColor = JarvisTextPrimary,
                    unfocusedTextColor = JarvisTextPrimary
                ),
                singleLine = true
            )

            IconButton(
                onClick = {
                    if (textInput.isNotBlank()) {
                        val txt = textInput
                        textInput = ""
                        onSendMessage(txt, useHighThinking)
                    }
                },
                enabled = textInput.isNotBlank(),
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = "Send",
                    tint = if (textInput.isNotBlank()) JarvisCyan else JarvisTextMuted
                )
            }
        }
    }

    // Rename Session Dialog
    renamingSession?.let { sess ->
        AlertDialog(
            onDismissRequest = { renamingSession = null },
            title = {
                Text(
                    text = "Rename Chat Session",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = JarvisCyan
                )
            },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    label = { Text("Chat Title") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = JarvisCyan,
                        unfocusedBorderColor = JarvisBorder,
                        focusedTextColor = JarvisTextPrimary,
                        unfocusedTextColor = JarvisTextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (renameInput.isNotBlank()) {
                            onRenameSession(sess.sessionId, renameInput.trim())
                        }
                        renamingSession = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan, contentColor = Color.Black)
                ) {
                    Text("Save", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { renamingSession = null }) {
                    Text("Cancel", color = JarvisTextSecondary)
                }
            },
            containerColor = JarvisSurface,
            shape = RoundedCornerShape(12.dp)
        )
    }
}

@Composable
private fun ChatMessageBubble(
    item: ChatMessageEntity,
    onSpeak: () -> Unit
) {
    val isUser = item.role == "user"
    val timeStr = remember(item.timestamp) {
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(item.timestamp))
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            Text(
                text = if (isUser) "COMMANDER" else "J.A.R.V.I.S.",
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = if (isUser) JarvisCyan else JarvisCyanGlow
            )
            if (!item.provider.isNullOrBlank()) {
                Text(
                    text = "[${item.provider}]",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 8.sp,
                    color = JarvisEmerald
                )
            }
            Text(
                text = timeStr,
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                color = JarvisTextMuted
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .clip(RoundedCornerShape(12.dp))
                .background(if (isUser) JarvisSurfaceVariant else JarvisSurface)
                .border(
                    1.dp,
                    if (isUser) JarvisElectricBlue.copy(alpha = 0.5f) else JarvisCyan.copy(alpha = 0.3f),
                    RoundedCornerShape(12.dp)
                )
                .padding(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = item.text,
                    fontSize = 13.sp,
                    color = JarvisTextPrimary,
                    lineHeight = 18.sp
                )

                if (!item.thinkingContent.isNullOrBlank()) {
                    ThinkingTraceCard(thinkingText = item.thinkingContent)
                }

                if (!isUser) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        IconButton(
                            onClick = onSpeak,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.VolumeUp,
                                contentDescription = "Replay Speech",
                                tint = JarvisCyan,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
