package com.ayam.video

import android.content.Context
import android.os.Environment
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

object VideoEngine {

    @Volatile
    private var ready = false

    suspend fun ensureInit(context: Context) = withContext(Dispatchers.IO) {
        if (!ready) {
            val app = context.applicationContext
            YoutubeDL.getInstance().init(app)
            FFmpeg.getInstance().init(app)
            ready = true
        }
    }

    suspend fun search(context: Context, query: String, count: Int = 10): List<VideoItem> =
        withContext(Dispatchers.IO) {
            ensureInit(context)
            val request = YoutubeDLRequest("ytsearch$count:$query")
            request.addOption("--flat-playlist")
            request.addOption("--dump-single-json")
            request.addOption("--no-warnings")
            val response = YoutubeDL.getInstance().execute(request)
            val entries = JSONObject(response.out).optJSONArray("entries")
            val result = mutableListOf<VideoItem>()
            if (entries != null) {
                for (i in 0 until entries.length()) {
                    val e = entries.optJSONObject(i) ?: continue
                    val id = e.optString("id")
                    if (id.isBlank()) continue
                    val channel = e.optString("channel").ifBlank { e.optString("uploader") }
                    val seconds = e.optLong("duration", 0L)
                    val views = e.optLong("view_count", 0L)
                    val details = listOf(
                        channel,
                        if (seconds > 0) formatDuration(seconds) else "",
                        if (views > 0) "${formatViews(views)} vues" else ""
                    ).filter { it.isNotBlank() }.joinToString(" • ")
                    result.add(
                        VideoItem(
                            id = id,
                            title = e.optString("title", "Sans titre"),
                            details = details,
                            source = "YouTube",
                            videoUrl = "https://www.youtube.com/watch?v=$id",
                            thumbnailUrl = "https://i.ytimg.com/vi/$id/hqdefault.jpg"
                        )
                    )
                }
            }
            result
        }

    suspend fun download(
        context: Context,
        url: String,
        onProgress: (Float) -> Unit
    ): File = withContext(Dispatchers.IO) {
        ensureInit(context)
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "AyamVideo"
        )
        if (!dir.exists()) dir.mkdirs()

        val request = YoutubeDLRequest(url)
        request.addOption("--no-playlist")
        request.addOption("--no-mtime")
        request.addOption("-f", "bv*[height<=720][ext=mp4]+ba[ext=m4a]/b[height<=720][ext=mp4]/b")
        request.addOption("--merge-output-format", "mp4")
        request.addOption("-o", dir.absolutePath + "/%(title).80s.%(ext)s")

        YoutubeDL.getInstance().execute(request, url) { progress, _, _ ->
            onProgress(progress)
        }
        dir
    }

     suspend fun resolveStream(context: Context, url: String): String =
        withContext(Dispatchers.IO) {
            ensureInit(context)
            val request = YoutubeDLRequest(url)
            request.addOption("--no-playlist")
            request.addOption("--no-warnings")
            request.addOption("-f", "b[ext=mp4][height<=720]/b[ext=mp4]/b")
            request.addOption("-g")
            val response = YoutubeDL.getInstance().execute(request)
            response.out.lineSequence().map { it.trim() }.firstOrNull { it.startsWith("http") }
                ?: throw IllegalStateException("Aucun lien de lecture")
        }
        
    private fun formatDuration(total: Long): String {
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    private fun formatViews(n: Long): String = when {
        n >= 1_000_000 -> "%.1f M".format(n / 1_000_000.0)
        n >= 1_000 -> "%.1f k".format(n / 1_000.0)
        else -> n.toString()
    }
}
