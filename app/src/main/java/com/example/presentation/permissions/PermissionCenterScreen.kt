package com.example.presentation.permissions

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.permissions.PermissionItem
import com.example.ui.theme.JarvisAmber
import com.example.ui.theme.JarvisBackground
import com.example.ui.theme.JarvisBorder
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisCyanGlow
import com.example.ui.theme.JarvisEmerald
import com.example.ui.theme.JarvisSurface
import com.example.ui.theme.JarvisSurfaceVariant
import com.example.ui.theme.JarvisTextMuted
import com.example.ui.theme.JarvisTextPrimary
import com.example.ui.theme.JarvisTextSecondary

@Composable
fun PermissionCenterScreen(
    permissionItems: List<PermissionItem>,
    onRequestPermission: (String) -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenNotificationListenerSettings: () -> Unit,
    onOpenAppSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(JarvisBackground)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = null,
                tint = JarvisCyan,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = "ACCESS & CAPABILITY HUB",
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = JarvisCyan
            )
        }

        // Informational header banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(JarvisSurface)
                .border(1.dp, JarvisBorder, RoundedCornerShape(10.dp))
                .padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = JarvisCyanGlow,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "J.A.R.V.I.S. uses official Android APIs and permissions strictly on-device to execute automation, voice, and vision tasks safely.",
                    fontSize = 12.sp,
                    color = JarvisTextSecondary,
                    lineHeight = 16.sp
                )
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(permissionItems) { item ->
                PermissionCard(
                    item = item,
                    onRequest = {
                        when (item.id) {
                            "accessibility" -> onOpenAccessibilitySettings()
                            "notification_listener" -> onOpenNotificationListenerSettings()
                            else -> item.requiredPermission?.let { onRequestPermission(it) }
                        }
                    }
                )
            }
        }

        OutlinedButton(
            onClick = onOpenAppSettings,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = JarvisCyan)
        ) {
            Icon(imageVector = Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Open Application System Settings", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        }
    }
}

@Composable
private fun PermissionCard(
    item: PermissionItem,
    onRequest: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(JarvisSurfaceVariant)
            .border(
                1.dp,
                if (item.isGranted) JarvisEmerald.copy(alpha = 0.4f) else JarvisAmber.copy(alpha = 0.4f),
                RoundedCornerShape(10.dp)
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = if (item.isGranted) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (item.isGranted) JarvisEmerald else JarvisAmber,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = item.title,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = JarvisTextPrimary
                )
            }

            Text(
                text = if (item.isGranted) "AUTHORIZED" else "NOT ENABLED",
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = if (item.isGranted) JarvisEmerald else JarvisAmber,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        if (item.isGranted) JarvisEmerald.copy(alpha = 0.15f) else JarvisAmber.copy(alpha = 0.15f)
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }

        Text(
            text = item.description,
            fontSize = 12.sp,
            color = JarvisTextSecondary,
            lineHeight = 16.sp
        )

        if (!item.isGranted) {
            Button(
                onClick = onRequest,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = JarvisAmber,
                    contentColor = Color.Black
                )
            ) {
                Text(
                    text = if (item.isSystemSetting) "Enable in Android Settings" else "Grant Permission",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
