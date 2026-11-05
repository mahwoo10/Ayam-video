package com.ayam.video

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun QualityDialog(
    video: VideoItem,
    onDismiss: () -> Unit,
    onPick: (QualityOption) -> Unit
) {
    val context = LocalContext.current
    var options by remember { mutableStateOf<List<QualityOption>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(video.id) {
        try {
            options = VideoEngine.listQualities(context, video.videoUrl)
        } catch (e: Exception) {
            error = "Impossible de lister les qualités : " + (e.message ?: "").take(120)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF130826),
        title = { Text("Choisir la qualité", color = Color(0xFFE0AAFF)) },
        text = {
            val opts = options
            val err = error
            Column {
                when {
                    err != null -> {
                        Text(err, color = Color(0xFFFF8080), fontSize = 13.sp)
                    }
                    opts == null -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                color = Color(0xFFE0AAFF),
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                "Recherche des qualités...",
                                color = Color(0xFF9A8CB9),
                                fontSize = 14.sp
                            )
                        }
                    }
                    else -> {
                        opts.forEach { opt ->
                            OutlinedButton(
                                onClick = { onPick(opt) },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(opt.label, color = Color.White, fontSize = 14.sp)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler", color = Color(0xFF9A8CB9))
            }
        }
    )
}
