package com.example.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.window.Dialog
import com.example.domain.agent.PendingConfirmation
import com.example.ui.theme.JarvisAmber
import com.example.ui.theme.JarvisBackground
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisRed
import com.example.ui.theme.JarvisSurfaceVariant
import com.example.ui.theme.JarvisTextPrimary
import com.example.ui.theme.JarvisTextSecondary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun RiskConfirmationDialog(
    pending: PendingConfirmation,
    scope: CoroutineScope
) {
    val borderColor = when (pending.riskLevel.name) {
        "CRITICAL", "HIGH" -> JarvisRed
        "MEDIUM" -> JarvisAmber
        else -> JarvisCyan
    }

    Dialog(onDismissRequest = { pending.onCancel() }) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .border(2.dp, borderColor, RoundedCornerShape(16.dp)),
            color = JarvisBackground
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SECURITY AUTHORIZATION",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = borderColor,
                        letterSpacing = 1.sp
                    )

                    Text(
                        text = pending.riskLevel.label.uppercase(),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = borderColor,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(borderColor.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Text(
                    text = pending.prompt,
                    fontSize = 14.sp,
                    color = JarvisTextPrimary,
                    lineHeight = 20.sp
                )

                // Parameter inspection box
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(JarvisSurfaceVariant)
                        .padding(10.dp)
                ) {
                    Text(
                        text = "TOOL: ${pending.functionCall.name}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = JarvisCyan
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "ARGS: ${pending.functionCall.args}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = JarvisTextSecondary
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { pending.onCancel() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = JarvisTextSecondary)
                    ) {
                        Text("Deny", fontFamily = FontFamily.Monospace)
                    }

                    Button(
                        onClick = {
                            scope.launch {
                                pending.onConfirm()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = borderColor,
                            contentColor = Color.Black
                        )
                    ) {
                        Text("Authorize", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
