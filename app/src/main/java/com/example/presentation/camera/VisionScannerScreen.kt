package com.example.presentation.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.launch
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.JarvisBackground
import com.example.ui.theme.JarvisBorder
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisCyanGlow
import com.example.ui.theme.JarvisSurface
import com.example.ui.theme.JarvisSurfaceVariant
import com.example.ui.theme.JarvisTextMuted
import com.example.ui.theme.JarvisTextPrimary
import java.io.InputStream

@Composable
fun VisionScannerScreen(
    onAnalyzeImage: (Bitmap, String) -> Unit
) {
    val context = LocalContext.current
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var promptText by remember { mutableStateOf("Describe and analyze what is visible in this image in detail.") }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            capturedBitmap = bitmap
        }
    }

    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
                capturedBitmap = BitmapFactory.decodeStream(inputStream)
            } catch (e: Exception) {
                // handle error
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(JarvisBackground)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Visibility,
                contentDescription = null,
                tint = JarvisCyan,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = "OPTICAL NEURAL RECOGNITION (GEMINI VISION)",
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = JarvisCyan
            )
        }

        // Image Preview Area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(JarvisSurface)
                .border(1.dp, JarvisCyan.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (capturedBitmap != null) {
                Image(
                    bitmap = capturedBitmap!!.asImageBitmap(),
                    contentDescription = "Captured frame",
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = null,
                        tint = JarvisTextMuted,
                        modifier = Modifier.size(44.dp)
                    )
                    Text(
                        text = "Capture or select an image for AI inspection",
                        color = JarvisTextMuted,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        // Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = { takePictureLauncher.launch() },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan, contentColor = Color.Black)
            ) {
                Icon(imageVector = Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.size(6.dp))
                Text("Take Photo", fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = { pickImageLauncher.launch("image/*") },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = JarvisCyanGlow)
            ) {
                Icon(imageVector = Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.size(6.dp))
                Text("Gallery", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            }
        }

        // Prompt Input
        OutlinedTextField(
            value = promptText,
            onValueChange = { promptText = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Vision Inspection Query", color = JarvisCyan, fontFamily = FontFamily.Monospace, fontSize = 12.sp) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = JarvisCyan,
                unfocusedBorderColor = JarvisBorder,
                focusedTextColor = JarvisTextPrimary,
                unfocusedTextColor = JarvisTextPrimary
            )
        )

        Button(
            onClick = {
                capturedBitmap?.let { bmp ->
                    onAnalyzeImage(bmp, promptText)
                }
            },
            enabled = capturedBitmap != null,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("analyze_vision_button"),
            colors = ButtonDefaults.buttonColors(
                containerColor = JarvisCyan,
                contentColor = Color.Black
            )
        ) {
            Icon(imageVector = Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                "Dispatch to J.A.R.V.I.S. Core",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
