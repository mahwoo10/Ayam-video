package com.ayam.video

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Logos simplifiés des sources, dessinés dans les couleurs de chaque marque. */
@Composable
fun SourceLogo(source: String, dp: Dp = 16.dp) {
    Canvas(modifier = Modifier.size(dp)) {
        val w = size.width
        val h = size.height
        when (source) {
            "YouTube" -> {
                drawRoundRect(
                    color = Color(0xFFFF0000),
                    topLeft = Offset(0f, h * 0.2f),
                    size = Size(w, h * 0.6f),
                    cornerRadius = CornerRadius(h * 0.18f, h * 0.18f)
                )
                val tri = Path().apply {
                    moveTo(w * 0.41f, h * 0.35f)
                    lineTo(w * 0.41f, h * 0.65f)
                    lineTo(w * 0.69f, h * 0.5f)
                    close()
                }
                drawPath(tri, Color.White)
            }
            "SoundCloud" -> {
                val c = Color(0xFFFF5500)
                drawCircle(c, radius = h * 0.19f, center = Offset(w * 0.28f, h * 0.62f))
                drawCircle(c, radius = h * 0.27f, center = Offset(w * 0.52f, h * 0.5f))
                drawCircle(c, radius = h * 0.2f, center = Offset(w * 0.76f, h * 0.6f))
                drawRoundRect(
                    color = c,
                    topLeft = Offset(w * 0.28f, h * 0.55f),
                    size = Size(w * 0.48f, h * 0.26f),
                    cornerRadius = CornerRadius(h * 0.05f, h * 0.05f)
                )
            }
            "PeerTube" -> {
                drawRoundRect(
                    color = Color(0xFFF1680D),
                    size = Size(w, h),
                    cornerRadius = CornerRadius(h * 0.22f, h * 0.22f)
                )
                val tri = Path().apply {
                    moveTo(w * 0.36f, h * 0.24f)
                    lineTo(w * 0.36f, h * 0.76f)
                    lineTo(w * 0.78f, h * 0.5f)
                    close()
                }
                drawPath(tri, Color.White)
            }
            else -> {
                drawCircle(Color(0xFF9A8CB9), radius = h * 0.4f, center = Offset(w / 2f, h / 2f))
            }
        }
    }
}
