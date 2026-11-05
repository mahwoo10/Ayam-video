package com.ayam.video

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.net.HttpURLConnection
import java.net.URL

data class QualityOption(
    val label: String,
    val tag: String,
    val url: String,
    val ext: String,
    val mime: String,
    val sizeBytes: Long,
    val isAudio: Boolean,
    val audioUrl: String = ""
)

object VideoEngine {

    @Volatile
    private var ready = false

    private fun ensureInit() {
        if (!ready) {
            synchronized(this) {
                if (!ready) {
                    NewPipe.init(AyamDownloader())
                    ready = true
                }
            }
        }
    }

    suspend fun search(context: Context, query: String, count: Int = 15): List<VideoItem> =
        withContext(Dispatchers.IO) {
            ensureInit()
            val yt = ServiceList.YouTube
            val handler = yt.searchQHFactory.fromQuery(query, listOf("videos"), "")
            val info = SearchInfo.getInfo(yt, handler)
            info.relatedItems
                .filterIsInstance<StreamInfoItem>()
                .take(count)
                .mapNotNull { item ->
                    val id = item.url.substringAfter("v=", "").substringBefore("&")
                    if (id.isBlank()) {
                        null
                    } else {
                        val seconds = item.duration
                        val views = item.viewCount
                        val details = listOf(
                            item.uploaderName ?: "",
                            if (seconds > 0) formatDuration(seconds) else "",
                            if (views >= 0) "${formatViews(views)} vues" else ""
                        ).filter { it.isNotBlank() }.joinToString(" • ")
                        VideoItem(
                            id = id,
                            title = item.name ?: "Sans titre",
                            details = details,
                            source = "YouTube",
                            videoUrl = "https://www.youtube.com/watch?v=$id",
                            thumbnailUrl = "https://i.ytimg.com/vi/$id/hqdefault.jpg"
                        )
                    }
                }
        }

    suspend fun resolveStream(context: Context, url: String): String =
        withContext(Dispatchers.IO) {
            ensureInit()
            val info = StreamInfo.getInfo(ServiceList.YouTube, url)
            val muxed = info.videoStreams.filter { it.isUrl && it.content.isNotBlank() }
            val best = muxed.filter { height(it.resolution) <= 720 }
                .maxByOrNull { height(it.resolution) }
                ?: muxed.maxByOrNull { height(it.resolution) }
            if (best != null) return@withContext best.content
            val hls = info.hlsUrl
            if (!hls.isNullOrBlank()) return@withContext hls
            throw IllegalStateException("Aucun flux lisible trouvé")
        }

    suspend fun listQualities(context: Context, videoUrl: String): List<QualityOption> =
        withContext(Dispatchers.IO) {
            if (videoUrl.endsWith(".mp4")) {
                val size = contentSize(videoUrl)
                return@withContext listOf(
                    QualityOption(
                        label = "Qualité d'origine • MP4 • ${formatSize(size)}",
                        tag = "original",
                        url = videoUrl,
                        ext = "mp4",
                        mime = "video/mp4",
                        sizeBytes = size,
                        isAudio = false
                    )
                )
            }

            ensureInit()
            val info = StreamInfo.getInfo(ServiceList.YouTube, videoUrl)
            val raw = mutableListOf<QualityOption>()

            // Flux avec image + son déjà ensemble
            val muxed = info.videoStreams
                .filter { it.isUrl && it.content.isNotBlank() }
                .filter { (it.format?.suffix ?: "") in listOf("mp4", "webm") }
                .sortedByDescending {
                    height(it.resolution) * 10 + (if (it.format?.suffix == "mp4") 1 else 0)
                }
                .distinctBy { height(it.resolution) }
            val muxedHeights = muxed.map { height(it.resolution) }.toSet()

            for (s in muxed) {
                val ext = s.format?.suffix ?: "mp4"
                val res = s.resolution ?: ""
                raw.add(
                    QualityOption(
                        label = "$res • ${ext.uppercase()}",
                        tag = res,
                        url = s.content,
                        ext = ext,
                        mime = s.format?.mimeType ?: "video/mp4",
                        sizeBytes = -1L,
                        isAudio = false
                    )
                )
            }

            // Meilleur son M4A (sert aussi pour l'option audio seule et pour l'assemblage HD)
            val audio = info.audioStreams
                .filter { it.isUrl && it.content.isNotBlank() && it.format?.suffix == "m4a" }
                .maxByOrNull { it.averageBitrate }

            // Qualités HD : image seule (H.264) + son, assemblés après téléchargement
            if (audio != null) {
                val hd = info.videoOnlyStreams
                    .filter { it.isUrl && it.content.isNotBlank() }
                    .filter { it.format?.suffix == "mp4" }
                    .filter { (it.codec ?: "").startsWith("avc1") }
                    .filter { height(it.resolution) >= 360 }
                    .filter { height(it.resolution) !in muxedHeights }
                    .sortedBy { it.fps }
                    .distinctBy { height(it.resolution) }
                for (v in hd) {
                    val res = v.resolution ?: ""
                    raw.add(
                        QualityOption(
                            label = "$res HD • MP4",
                            tag = res,
                            url = v.content,
                            ext = "mp4",
                            mime = "video/mp4",
                            sizeBytes = -1L,
                            isAudio = false,
                            audioUrl = audio.content
                        )
                    )
                }
            }

            val videos = raw.sortedByDescending { height(it.tag) }.toMutableList()

            if (audio != null) {
                videos.add(
                    QualityOption(
                        label = "Audio seulement • M4A",
                        tag = "audio",
                        url = audio.content,
                        ext = "m4a",
                        mime = audio.format?.mimeType ?: "audio/mp4",
                        sizeBytes = -1L,
                        isAudio = true
                    )
                )
            }

            if (videos.isEmpty()) {
                throw IllegalStateException("Aucune qualité téléchargeable trouvée")
            }

            // Tailles calculées en parallèle
            coroutineScope {
                videos.map { opt ->
                    async {
                        val first = contentSize(opt.url)
                        val total = if (opt.audioUrl.isNotBlank()) {
                            val second = contentSize(opt.audioUrl)
                            if (first > 0 && second > 0) first + second else -1L
                        } else {
                            first
                        }
                        opt.copy(
                            sizeBytes = total,
                            label = opt.label + " • " + formatSize(total)
                        )
                    }
                }.awaitAll()
            }
        }

    private fun contentSize(url: String): Long {
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.setRequestProperty("Range", "bytes=0-0")
            val code = conn.responseCode
            val size = when (code) {
                206 -> conn.getHeaderField("Content-Range")
                    ?.substringAfter("/")?.toLongOrNull() ?: -1L
                200 -> conn.contentLengthLong
                else -> -1L
            }
            conn.disconnect()
            size
        } catch (e: Exception) {
            -1L
        }
    }

    private fun formatSize(bytes: Long): String = when {
        bytes <= 0 -> "taille inconnue"
        bytes >= 1_000_000_000 -> "%.2f Go".format(bytes / 1_000_000_000.0)
        bytes >= 1_000_000 -> "%.1f Mo".format(bytes / 1_000_000.0)
        else -> "${bytes / 1000} Ko"
    }

    private fun height(resolution: String?): Int =
        resolution?.takeWhile { it.isDigit() }?.toIntOrNull() ?: 0

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
