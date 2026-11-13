package com.ayam.video

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.font.FontWeight
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
        containerColor = Pal.surface,
        title = { Text("Choisir la qualité", color = Pal.accent) },
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
                                color = Pal.accent,
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                "Recherche des qualités...",
                                color = Pal.muted,
                                fontSize = 14.sp
                            )
                        }
                    }
                    else -> {
                        val videos = opts.filter { !it.isAudio }
                        val audios = opts.filter { it.isAudio }
                        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                            if (videos.isNotEmpty()) {
                                Text(
                                    "Vidéo",
                                    color = Pal.accent,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                videos.forEach { opt ->
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
                            if (audios.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "Audio seulement",
                                    color = Pal.accent,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                audios.forEach { opt ->
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
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler", color = Pal.muted)
            }
        }
    )
}
