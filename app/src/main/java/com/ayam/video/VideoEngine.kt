package com.ayam.video

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

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
