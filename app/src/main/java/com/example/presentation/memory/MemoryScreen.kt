package com.example.presentation.memory

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.memory.MemoryEntity
import com.example.ui.theme.JarvisBackground
import com.example.ui.theme.JarvisBorder
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisRed
import com.example.ui.theme.JarvisSurface
import com.example.ui.theme.JarvisSurfaceVariant
import com.example.ui.theme.JarvisTextMuted
import com.example.ui.theme.JarvisTextPrimary
import com.example.ui.theme.JarvisTextSecondary

@Composable
fun MemoryScreen(
    memories: List<MemoryEntity>,
    onAddMemory: (String, String) -> Unit,
    onDeleteMemory: (Long) -> Unit,
    onClearAllMemories: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var newKey by remember { mutableStateOf("") }
    var newValue by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }

    val filteredMemories = remember(memories, searchQuery) {
        if (searchQuery.isBlank()) memories
        else memories.filter {
            it.key.contains(searchQuery, ignoreCase = true) || it.value.contains(searchQuery, ignoreCase = true)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(JarvisBackground)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Bookmark,
                    contentDescription = null,
                    tint = JarvisCyan,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "LONG-TERM KNOWLEDGE BASE (${memories.size})",
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = JarvisCyan
                )
            }

            IconButton(onClick = onClearAllMemories) {
                Icon(imageVector = Icons.Default.DeleteSweep, contentDescription = "Clear All", tint = JarvisTextMuted)
            }
        }

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search remembered knowledge...", color = JarvisTextMuted, fontFamily = FontFamily.Monospace, fontSize = 12.sp) },
            leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = JarvisCyan) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = JarvisCyan,
                unfocusedBorderColor = JarvisBorder,
                focusedTextColor = JarvisTextPrimary,
                unfocusedTextColor = JarvisTextPrimary
            ),
            singleLine = true
        )

        // Add Memory Card Form
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(JarvisSurface)
                .border(1.dp, JarvisBorder, RoundedCornerShape(10.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "STORE NEW MEMORY CONTEXT",
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = JarvisCyan
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = newKey,
                    onValueChange = { newKey = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Key / Concept", color = JarvisTextMuted, fontSize = 11.sp) },
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = JarvisCyan, unfocusedBorderColor = JarvisBorder, focusedTextColor = JarvisTextPrimary, unfocusedTextColor = JarvisTextPrimary),
                    singleLine = true
                )

                OutlinedTextField(
                    value = newValue,
                    onValueChange = { newValue = it },
                    modifier = Modifier.weight(1.5f),
                    placeholder = { Text("Value / Details", color = JarvisTextMuted, fontSize = 11.sp) },
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = JarvisCyan, unfocusedBorderColor = JarvisBorder, focusedTextColor = JarvisTextPrimary, unfocusedTextColor = JarvisTextPrimary),
                    singleLine = true
                )
            }

            Button(
                onClick = {
                    if (newKey.isNotBlank() && newValue.isNotBlank()) {
                        onAddMemory(newKey, newValue)
                        newKey = ""
                        newValue = ""
                    }
                },
                enabled = newKey.isNotBlank() && newValue.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan, contentColor = Color.Black)
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.size(6.dp))
                Text("Commit to Long-Term Memory", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }

        // Memory entries list
        if (filteredMemories.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No memory records found.",
                    color = JarvisTextMuted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredMemories, key = { it.id }) { mem ->
                    MemoryItemRow(memory = mem, onDelete = { onDeleteMemory(mem.id) })
                }
            }
        }
    }
}

@Composable
private fun MemoryItemRow(
    memory: MemoryEntity,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(JarvisSurfaceVariant)
            .border(1.dp, JarvisBorder, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = memory.key,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = JarvisCyan
            )
            Text(
                text = memory.value,
                fontSize = 13.sp,
                color = JarvisTextPrimary
            )
        }

        IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
            Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = JarvisRed.copy(alpha = 0.8f), modifier = Modifier.size(16.dp))
        }
    }
}
