package com.ayam.video

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.DeliveryMethod
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

data class PlayOption(
    val label: String,
    val url: String,
    val audioUrl: String = "",
    val height: Int = 0,
    val isDefault: Boolean = false,
    val isAudio: Boolean = false
)

data class VideoDetails(
    val playOptions: List<PlayOption>,
    val related: List<VideoItem>
)

/** Sources réellement prises en charge (chacune a de vraies tendances et une vraie recherche). */
val SOURCE_NAMES = listOf("YouTube", "SoundCloud", "PeerTube")

private class Fetch(val items: List<VideoItem>, val error: Exception?)

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

    private fun serviceOf(source: String): StreamingService = when (source) {
        "SoundCloud" -> ServiceList.SoundCloud
        "PeerTube" -> ServiceList.PeerTube
        else -> ServiceList.YouTube
    }

    /** Mélange les listes une vidéo à la fois (YouTube, SoundCloud, PeerTube, YouTube…). */
    private fun interleave(lists: List<List<VideoItem>>, count: Int): List<VideoItem> {
        val out = mutableListOf<VideoItem>()
        val seen = HashSet<String>()
        var i = 0
        while (out.size < count && lists.any { i < it.size }) {
            for (l in lists) {
                if (i < l.size && out.size < count && seen.add(l[i].id)) out.add(l[i])
            }
            i++
        }
        return out
    }

    private fun searchOne(source: String, query: String): List<VideoItem> {
        val service = serviceOf(source)
        val filter = if (source == "SoundCloud") "tracks" else "videos"
        val handler = service.searchQHFactory.fromQuery(query, listOf(filter), "")
        val info = SearchInfo.getInfo(service, handler)
        return info.relatedItems
            .filterIsInstance<StreamInfoItem>()
            .mapNotNull { toVideoItem(it, source) }
    }

    private fun trendingOne(source: String): List<VideoItem> {
        val service = serviceOf(source)
        val items = try {
            val kiosk = service.kioskList.defaultKioskExtractor
            kiosk.fetchPage()
            kiosk.initialPage.items
                .filterIsInstance<StreamInfoItem>()
                .mapNotNull { toVideoItem(it, source) }
        } catch (e: Exception) {
            emptyList()
        }
        if (items.isNotEmpty()) return items
        // YouTube a retiré sa page Tendances : on se rabat sur une recherche musicale populaire
        if (source == "YouTube") return searchOne("YouTube", "tendance musique")
        return emptyList()
    }

    suspend fun trending(context: Context, source: String? = null, count: Int = 30): List<VideoItem> =
        withContext(Dispatchers.IO) {
            ensureInit()
            if (source != null) return@withContext trendingOne(source).take(count)
            val lists = coroutineScope {
                SOURCE_NAMES.map { s ->
                    async {
                        try { trendingOne(s) } catch (e: Exception) { emptyList<VideoItem>() }
                    }
                }.awaitAll()
            }
            interleave(lists, count)
        }

    suspend fun search(
        context: Context,
        query: String,
        count: Int = 15,
        source: String? = null
    ): List<VideoItem> =
        withContext(Dispatchers.IO) {
            ensureInit()
            if (source != null) return@withContext searchOne(source, query).take(count)
            val fetched = coroutineScope {
                SOURCE_NAMES.map { s ->
                    async {
                        try {
                            Fetch(searchOne(s, query), null)
                        } catch (e: Exception) {
                            Fetch(emptyList(), e)
                        }
                    }
                }.awaitAll()
            }
            if (fetched.all { it.error != null }) throw fetched.first().error!!
            interleave(fetched.map { it.items }, count)
        }

    private fun toVideoItem(item: StreamInfoItem, source: String): VideoItem? {
        val url = item.url ?: return null
        val id: String
        val videoUrl: String
        val thumb: String
        if (source == "YouTube") {
            val vid = url.substringAfter("v=", "").substringBefore("&")
            if (vid.isBlank()) return null
            id = vid
            videoUrl = "https://www.youtube.com/watch?v=$vid"
            thumb = "https://i.ytimg.com/vi/$vid/hqdefault.jpg"
        } else {
            id = source.take(2).lowercase() + "_" + Integer.toHexString(url.hashCode())
            videoUrl = url
            thumb = item.thumbnails.maxByOrNull { it.height }?.url ?: ""
        }
        val seconds = item.duration
        val views = item.viewCount
        val details = listOf(
            item.uploaderName ?: "",
            if (seconds > 0) formatDuration(seconds) else "",
            if (views >= 0) "${formatViews(views)} vues" else ""
        ).filter { it.isNotBlank() }.joinToString(" • ")
        return VideoItem(
            id = id,
            title = item.name ?: "Sans titre",
            details = details,
            source = source,
            videoUrl = videoUrl,
            thumbnailUrl = thumb
        )
    }

    /** Qualités de lecture + vidéos suggérées, en un seul appel réseau. */
    suspend fun loadDetails(context: Context, url: String, source: String = "YouTube"): VideoDetails =
        withContext(Dispatchers.IO) {
            ensureInit()
            val info = StreamInfo.getInfo(serviceOf(source), url)
            val options = mutableListOf<PlayOption>()

            // Flux avec image + son déjà ensemble
            val muxed = info.videoStreams
                .filter { it.isUrl && it.content.isNotBlank() }
                .distinctBy { height(it.resolution) }
            for (s in muxed) {
                val h = height(s.resolution)
                options.add(PlayOption(label = "${h}p", url = s.content, height = h))
            }
            val muxedHeights = muxed.map { height(it.resolution) }.toSet()

            // Qualités HD : image seule (H.264) + meilleur son M4A, assemblés à la lecture
            val audio = info.audioStreams
                .filter { it.isUrl && it.content.isNotBlank() && it.format?.suffix == "m4a" }
                .maxByOrNull { it.averageBitrate }
            if (audio != null) {
                info.videoOnlyStreams
                    .filter { it.isUrl && it.content.isNotBlank() }
                    .filter { it.format?.suffix == "mp4" }
                    .filter { (it.codec ?: "").startsWith("avc1") }
                    .filter { height(it.resolution) >= 360 }
                    .filter { height(it.resolution) !in muxedHeights }
                    .sortedBy { it.fps }
                    .distinctBy { height(it.resolution) }
                    .forEach { v ->
                        val h = height(v.resolution)
                        options.add(
                            PlayOption(
                                label = "${h}p HD",
                                url = v.content,
                                audioUrl = audio.content,
                                height = h
                            )
                        )
                    }
            }

            var sorted = options.sortedByDescending { it.height }
            if (sorted.isEmpty()) {
                // Musique (SoundCloud…) : pas d'image, seulement du son
                val audios = info.audioStreams
                    .filter { it.isUrl && it.content.isNotBlank() }
                    .sortedByDescending { it.averageBitrate }
                val a = audios.firstOrNull { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
                    ?: audios.firstOrNull()
                if (a != null) {
                    sorted = listOf(PlayOption(label = "Audio", url = a.content, isAudio = true))
                }
            }
            if (sorted.isEmpty()) {
                val hls = info.hlsUrl
                if (!hls.isNullOrBlank()) {
                    sorted = listOf(PlayOption(label = "Auto", url = hls))
                }
            }
            if (sorted.isEmpty()) throw IllegalStateException("Aucun flux lisible trouvé")

            // Par défaut : le meilleur flux simple (économe en données), 720p maximum
            val def = sorted.firstOrNull { it.audioUrl.isBlank() && it.height <= 720 } ?: sorted.first()
            sorted = sorted.map { it.copy(isDefault = it == def) }

            val related = try {
                info.relatedItems
                    .filterIsInstance<StreamInfoItem>()
                    .take(15)
                    .mapNotNull { toVideoItem(it, source) }
            } catch (e: Exception) {
                emptyList()
            }

            VideoDetails(playOptions = sorted, related = related)
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

    suspend fun listQualities(context: Context, videoUrl: String, source: String = "YouTube"): List<QualityOption> =
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
            val info = StreamInfo.getInfo(serviceOf(source), videoUrl)
            val raw = mutableListOf<QualityOption>()

            // Flux avec image + son déjà ensemble
            val muxed = info.videoStreams
                .filter { it.isUrl && it.content.isNotBlank() }
                .filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
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

            val audioPick = audio ?: info.audioStreams
                .filter { it.isUrl && it.content.isNotBlank() }
                .filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
                .maxByOrNull { it.averageBitrate }
            if (audioPick != null) {
                val aExt = audioPick.format?.suffix ?: "m4a"
                videos.add(
                    QualityOption(
                        label = "Audio seulement • " + aExt.uppercase(),
                        tag = "audio",
                        url = audioPick.content,
                        ext = aExt,
                        mime = audioPick.format?.mimeType ?: "audio/mp4",
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
