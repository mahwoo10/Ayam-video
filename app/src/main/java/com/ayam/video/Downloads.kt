package com.ayam.video

import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.runtime.mutableStateMapOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

fun downloadLabel(done: Boolean, p: Float?): String = when {
    done -> "Téléchargé"
    p == null -> "Télécharger"
    p < 0f -> "Échec • réessayer"
    else -> "${(p * 100).toInt()} %"
}

object Library {

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext
                .getSharedPreferences("ayam_library", Context.MODE_PRIVATE)
        }
    }

    fun load(key: String): List<VideoItem> {
        val raw = prefs?.getString(key, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                VideoItem(
                    id = o.getString("id"),
                    title = o.optString("title"),
                    details = o.optString("details"),
                    source = o.optString("source"),
                    videoUrl = o.optString("videoUrl"),
                    thumbnailUrl = o.optString("thumbnailUrl"),
                    localUri = o.optString("localUri")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun save(key: String, list: List<VideoItem>) {
        val arr = JSONArray()
        list.forEach { v ->
            arr.put(
                JSONObject().apply {
                    put("id", v.id)
                    put("title", v.title)
                    put("details", v.details)
                    put("source", v.source)
                    put("videoUrl", v.videoUrl)
                    put("thumbnailUrl", v.thumbnailUrl)
                    put("localUri", v.localUri)
                }
            )
        }
        prefs?.edit()?.putString(key, arr.toString())?.apply()
    }
}

object DownloadHolder {

    val progress = mutableStateMapOf<String, Float>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun canStart(id: String): Boolean {
        val p = progress[id]
        return p == null || p < 0f
    }

    fun start(context: Context, video: VideoItem, onDone: (String) -> Unit) {
        if (!canStart(video.id)) return
        val app = context.applicationContext
        progress[video.id] = 0f
        scope.launch {
            try {
                val streamUrl = VideoEngine.resolveStream(app, video.videoUrl)
                if (streamUrl.contains(".m3u8") || streamUrl.contains("manifest")) {
                    throw IllegalStateException("Format non téléchargeable pour cette vidéo")
                }
                val localUri = saveToDownloads(app, safeName(video.title) + ".mp4", streamUrl) { p ->
                    progress[video.id] = p
                }
                withContext(Dispatchers.Main) {
                    onDone(localUri)
                    progress.remove(video.id)
                    Toast.makeText(
                        app,
                        "Téléchargé dans Téléchargements/AyamVideo",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                progress[video.id] = -1f
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        app,
                        "Échec : " + (e.message ?: e.toString()).take(150),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun safeName(title: String): String =
        title.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().take(80).ifBlank { "video" }

    private fun saveToDownloads(
        context: Context,
        name: String,
        url: String,
        onProgress: (Float) -> Unit
    ): String {
        val resolver = context.contentResolver
        var pendingUri: Uri? = null
        val out: OutputStream
        val resultUri: String

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, "video/mp4")
                put(
                    MediaStore.Downloads.RELATIVE_PATH,
                    Environment.DIRECTORY_DOWNLOADS + "/AyamVideo"
                )
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Création du fichier impossible")
            pendingUri = uri
            out = resolver.openOutputStream(uri)
                ?: throw IllegalStateException("Écriture du fichier impossible")
            resultUri = uri.toString()
        } else {
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: context.filesDir
            val file = File(dir, name)
            out = file.outputStream()
            resultUri = Uri.fromFile(file).toString()
        }

        try {
            out.use { copyInChunks(url, it, onProgress) }
            if (pendingUri != null) {
                val done = ContentValues().apply {
                    put(MediaStore.Downloads.IS_PENDING, 0)
                }
                resolver.update(pendingUri, done, null, null)
            }
        } catch (e: Exception) {
            if (pendingUri != null) resolver.delete(pendingUri, null, null)
            throw e
        }
        return resultUri
    }

    private fun copyInChunks(url: String, out: OutputStream, onProgress: (Float) -> Unit) {
        val chunk = 8L * 1024 * 1024
        val buffer = ByteArray(64 * 1024)
        var start = 0L
        var total = -1L
        var lastPercent = -1

        while (total < 0 || start < total) {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 30000
            conn.readTimeout = 30000
            conn.setRequestProperty("Range", "bytes=$start-${start + chunk - 1}")
            val code = conn.responseCode
            if (code != 200 && code != 206) {
                conn.disconnect()
                throw IllegalStateException("Serveur : erreur $code")
            }
            val rangeHeader = conn.getHeaderField("Content-Range")
            total = if (code == 206 && rangeHeader != null) {
                rangeHeader.substringAfter("/").toLongOrNull() ?: -1L
            } else {
                conn.contentLengthLong
            }
            val isFull = code == 200
            var got = 0L

            conn.inputStream.use { input ->
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    out.write(buffer, 0, n)
                    start += n
                    got += n
                    if (total > 0) {
                        val percent = (start * 100 / total).toInt()
                        if (percent != lastPercent) {
                            lastPercent = percent
                            onProgress(percent / 100f)
                        }
                    }
                }
            }
            conn.disconnect()

            if (isFull) break
            if (got == 0L) throw IllegalStateException("Flux interrompu")
            if (total <= 0 && got < chunk) break
        }
    }
}
