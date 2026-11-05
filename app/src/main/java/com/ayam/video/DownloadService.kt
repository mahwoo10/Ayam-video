package com.la.videodl

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object DownloadBus {
    @Volatile var running = false
    @Volatile var cancelled = false
    @Volatile var paused = false
    @Volatile var progress = 0
    @Volatile var speed = "0 Ko/s"
    @Volatile var eta = "0s"
    @Volatile var message = ""
    @Volatile var historyChanged = false
    @Volatile var listener: (() -> Unit)? = null
}

class DownloadService : Service() {

    companion object {
        const val ACTION_START = "com.la.videodl.START"
        const val ACTION_CANCEL = "com.la.videodl.CANCEL"
        const val ACTION_PAUSE = "com.la.videodl.PAUSE"
        const val ACTION_RESUME = "com.la.videodl.RESUME"
        const val ACTION_ADD_QUEUE = "com.la.videodl.ADD_QUEUE"
        private const val CHANNEL = "downloads"
        private const val NOTIF_ID = 1
        private const val DONE_ID = 2
        private const val PROCESS_ID = "videodl"
    }

    private val main = Handler(Looper.getMainLooper())
    private val queue = mutableListOf<Bundle>()

    override fun onBind(intent: Intent?): IBinder? = null

    private fun push() {
        main.post { DownloadBus.listener?.invoke() }
    }

    private fun killProcess() {
        try {
            YoutubeDL.getInstance().destroyProcessById(PROCESS_ID)
        } catch (e: Exception) {
        }
    }

    private fun isXLink(url: String): Boolean =
        url.matches(Regex("^https?://(www\\.|mobile\\.)?(x|twitter)\\.com/.*"))

    private fun friendlyError(url: String, m: String?): String {
        val t = (m ?: "").lowercase()
        if (isXLink(url) && (t.contains("nsfw") || t.contains("age-restricted") ||
                t.contains("authenticat") || t.contains("log in") || t.contains("login") ||
                t.contains("cookies"))
        ) {
            return "Cette vidéo exige d'être connecté à X. Appuie sur « Connecter mon compte X » en bas de l'écran, puis réessaie."
        }
        return "Échec : " + (m ?: "erreur inconnue").take(300)
    }

    private fun cleanTitle(t: String): String {
        val s = t.replace(Regex("[/\\:*?<>|]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .trim('.')
        return if (s.length > 100) s.substring(0, 100).trim() else s
    }

    private fun readyFiles(tmp: File): List<File> =
        tmp.listFiles()?.filter {
            it.isFile && !it.name.endsWith(".part") &&
                it.extension.lowercase() !in setOf("jpg", "jpeg", "png", "webp", "ytdl")
        } ?: emptyList()

    private fun notif(text: String, progress: Int, ongoing: Boolean, paused: Boolean = false): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        val b = Notification.Builder(this, CHANNEL)
            .setContentTitle("VideoDL")
            .setContentText(text)
            .setContentIntent(open)
            .setOnlyAlertOnce(true)

        if (ongoing) {
            b.setSmallIcon(android.R.drawable.stat_sys_download)
            b.setOngoing(true)
            b.setProgress(100, progress, progress <= 0)

            if (paused) {
                val resumeIntent = PendingIntent.getService(
                    this, 3,
                    Intent(this, DownloadService::class.java).setAction(ACTION_RESUME),
                    PendingIntent.FLAG_IMMUTABLE
                )
                b.addAction(android.R.drawable.ic_media_play, "Reprendre", resumeIntent)
            } else {
                val pauseIntent = PendingIntent.getService(
                    this, 2,
                    Intent(this, DownloadService::class.java).setAction(ACTION_PAUSE),
                    PendingIntent.FLAG_IMMUTABLE
                )
                b.addAction(android.R.drawable.ic_media_pause, "Pause", pauseIntent)
            }

            val cancelIntent = PendingIntent.getService(
                this, 1,
                Intent(this, DownloadService::class.java).setAction(ACTION_CANCEL),
                PendingIntent.FLAG_IMMUTABLE
            )
            b.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Annuler", cancelIntent)
        } else {
            b.setSmallIcon(
                if (paused) android.R.drawable.ic_media_pause
                else android.R.drawable.stat_sys_download_done
            )
            b.setAutoCancel(true)
        }
        return b.build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val i = intent
        if (i == null) {
            if (queue.isEmpty() && !DownloadBus.running) stopSelf()
            return START_NOT_STICKY
        }
        val action = i.action
        if (action == ACTION_CANCEL) {
            DownloadBus.cancelled = true
            queue.clear()
            killProcess()
            if (!DownloadBus.running) stopSelf()
            return START_NOT_STICKY
        }
        if (action == ACTION_PAUSE) {
            DownloadBus.paused = true
            killProcess()
            if (!DownloadBus.running) stopSelf()
            return START_NOT_STICKY
        }

        val url = i.getStringExtra("url") ?: ""
        if (url.isBlank() && action != ACTION_RESUME && action != ACTION_START) {
            if (queue.isEmpty() && !DownloadBus.running) stopSelf()
            return START_NOT_STICKY
        }

        val bundle = Bundle().apply {
            putString("url", url)
            putBoolean("video", i.getBooleanExtra("video", true))
            putInt("height", i.getIntExtra("height", 1080))
            putString("abr", i.getStringExtra("abr") ?: "320K")
            putString("info", i.getStringExtra("info") ?: "")
            putString("title", i.getStringExtra("title") ?: "")
        }

        if (DownloadBus.running) {
            queue.add(bundle)
            DownloadBus.message = "Ajouté à la file d'attente (${queue.size})"
            push()
            return START_NOT_STICKY
        }

        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Téléchargements", NotificationManager.IMPORTANCE_LOW)
        )
        startForeground(
            NOTIF_ID, notif("Préparation…", 0, true),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )

        val resume = action == ACTION_RESUME
        val prefs = getSharedPreferences("job", MODE_PRIVATE)
        val settings = getSharedPreferences("settings", MODE_PRIVATE)

        val targetUrl = if (resume) (prefs.getString("url", "") ?: "") else bundle.getString("url", "")
        val targetVideo = if (resume) prefs.getBoolean("video", true) else bundle.getBoolean("video", true)
        val targetHeight = if (resume) prefs.getInt("height", 1080) else bundle.getInt("height", 1080)
        val targetAbr = if (resume) (prefs.getString("abr", "320K") ?: "320K") else bundle.getString("abr", "320K")
        val targetInfo = if (resume) (prefs.getString("info", "") ?: "") else bundle.getString("info", "")
        val targetTitle = if (resume) (prefs.getString("title", "") ?: "") else (bundle.getString("title", "") ?: "")

        if (targetUrl.isBlank()) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        if (!resume) {
            prefs.edit().clear()
                .putString("url", targetUrl)
                .putBoolean("video", targetVideo)
                .putInt("height", targetHeight)
                .putString("abr", targetAbr)
                .putString("info", targetInfo)
                .putString("title", targetTitle)
                .apply()
        }

        DownloadBus.running = true
        DownloadBus.cancelled = false
        DownloadBus.paused = false
        DownloadBus.progress = 0
        DownloadBus.speed = "0 Ko/s"
        DownloadBus.eta = "0s"
        DownloadBus.message = if (resume) "Reprise du téléchargement…" else "Téléchargement en cours…"
        push()

        Thread { runDownload(targetUrl, targetVideo, targetHeight, targetAbr, targetInfo, targetTitle, resume) }.start()
        return START_NOT_STICKY
    }

    private fun runDownload(
        url: String, video: Boolean, height: Int, abr: String, info: String,
        title: String, resume: Boolean
    ) {
        val nm = getSystemService(NotificationManager::class.java)
        val wl = (getSystemService(POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "videodl:download")
        wl.acquire(60 * 60 * 1000L)
        val tmp = File(getExternalFilesDir(null), "tmp")
        if (!resume) tmp.deleteRecursively()
        tmp.mkdirs()
        val safeTitle = cleanTitle(title)
        var message: String
        var ok = false
        try {
            YoutubeDL.getInstance().init(application)
            FFmpeg.getInstance().init(application)
            val req = YoutubeDLRequest(url)
            req.addOption("--no-playlist")
            req.addOption("--continue")
            val ck = File(filesDir, "x_cookies.txt")
            if (ck.exists() && isXLink(url)) {
                req.addOption("--cookies", ck.absolutePath)
            }
            val outName = if (safeTitle.isNotEmpty()) safeTitle.replace("%", "%%") else "%(title).80s"
            req.addOption("-o", tmp.absolutePath + "/" + outName + ".%(ext)s")
            if (safeTitle.isNotEmpty()) {
                req.addCommands(
                    listOf("--replace-in-metadata", "title", "(?s).+", safeTitle.replace("\\\\", ""))
                )
            }
            if (video) {
                req.addOption("-f", "bv*+ba/b")
                req.addOption("-S", "res:$height,ext:mp4:m4a")
                req.addOption("--merge-output-format", "mp4")
            } else {
                req.addOption("-x")
                req.addOption("--audio-format", "mp3")
                req.addOption("--audio-quality", abr)
                req.addOption("--embed-metadata")
                req.addOption("--embed-thumbnail")
                req.addOption("--convert-thumbnails", "jpg")
            }
            var last = -1
            YoutubeDL.getInstance().execute(
                request = req,
                processId = PROCESS_ID,
                redirectErrorStream = false,
                callback = { p, eta, line ->
                    val pc = p.toInt().coerceIn(0, 100)
                    DownloadBus.progress = pc
                    
                    var currentSpeed = "…"
                    var currentEta = "${eta}s"
                    if (!line.isNullOrBlank()) {
                        if (line.contains("KiB/s") || line.contains("MiB/s") || line.contains("KB/s") || line.contains("MB/s")) {
                            val parts = line.split(Regex("\\s+"))
                            for (pText in parts) {
                                if (pText.contains("B/s")) {
                                    currentSpeed = pText
                                    break
                                }
                            }
                        }
                    }
                    DownloadBus.speed = currentSpeed
                    DownloadBus.eta = currentEta
                    DownloadBus.message = "Téléchargement… $pc % • $currentSpeed • reste $currentEta"
                    
                    if (pc != last) {
                        last = pc
                        nm.notify(NOTIF_ID, notif("$pc % - $currentSpeed", pc, true))
                    }
                    push()
                }
            )
            val files = readyFiles(tmp)
            if (files.isEmpty()) {
                message = "Aucun fichier récupéré."
            } else {
                files.forEach { save(it, info) }
                message = "Terminé ✅ Fichier dans Téléchargements/VideoDL"
                ok = true
            }
        } catch (e: Exception) {
            val mp3 = if (!video && !DownloadBus.paused && !DownloadBus.cancelled) {
                tmp.listFiles()?.filter { it.isFile && it.extension.equals("mp3", true) }
                    ?: emptyList()
            } else {
                emptyList()
            }
            if (mp3.isNotEmpty()) {
                try {
                    mp3.forEach { save(it, info) }
                    message = "Terminé ✅ (sans pochette) Fichier dans Téléchargements/VideoDL"
                    ok = true
                } catch (e2: Exception) {
                    message = friendlyError(url, e2.message)
                }
            } else {
                message = when {
                    DownloadBus.paused ->
                        "En pause à ${DownloadBus.progress} %. Appuie sur Reprendre pour continuer."
                    DownloadBus.cancelled -> "Téléchargement annulé."
                    else -> friendlyError(url, e.message)
                }
            }
        }
        val keep = DownloadBus.paused && !ok
        if (!keep) {
            tmp.deleteRecursively()
            getSharedPreferences("job", MODE_PRIVATE).edit().clear().apply()
        }
        if (wl.isHeld) wl.release()
        DownloadBus.message = message
        DownloadBus.running = false
        DownloadBus.historyChanged = ok
        push()
        if (queue.isEmpty() || DownloadBus.cancelled || DownloadBus.paused) stopForeground(STOP_FOREGROUND_REMOVE)
        
        if (keep) {
            nm.notify(DONE_ID, notif(message, 0, false, true))
        } else if (!DownloadBus.cancelled) {
            nm.notify(DONE_ID, notif(message, 100, false))
        }

        if (queue.isNotEmpty() && !DownloadBus.cancelled && !DownloadBus.paused) {
            val next = queue.removeAt(0)
            val nextIntent = Intent(this, DownloadService::class.java).apply {
                action = ACTION_START
                putExtras(next)
            }
            startService(nextIntent)
        } else if (queue.isEmpty()) {
            stopSelf()
        }
    }

        private fun save(f: File, info: String) {
        val mime = MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(f.extension.lowercase()) ?: "application/octet-stream"
        val size = f.length()
        val name = f.name
        
        // Cherche s'il y a une miniature image extraite à côté du fichier
        var thumbUri = ""
        try {
            val parentDir = f.parentFile
            if (parentDir != null) {
                val thumbFile = parentDir.listFiles()?.firstOrNull { 
                    it.isFile && it.extension.lowercase() in setOf("jpg", "jpeg", "png", "webp") &&
                    it.name.startsWith(f.name.substringBeforeLast('.'))
                } ?: parentDir.listFiles()?.firstOrNull { 
                    it.isFile && it.extension.lowercase() in setOf("jpg", "jpeg", "png", "webp") 
                }
                
                if (thumbFile != null && thumbFile.exists()) {
                    thumbUri = thumbFile.absolutePath
                }
            }
        } catch (e: Exception) {
        }

        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, mime)
            put(MediaStore.Downloads.RELATIVE_PATH, "Download/VideoDL")
        }
        val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw Exception("Impossible d'enregistrer le fichier")
        contentResolver.openOutputStream(uri)?.use { out ->
            f.inputStream().use { it.copyTo(out) }
        }
        f.delete()
        addHistory(name, uri.toString(), mime, size, info, thumbUri)
    }

        private fun addHistory(name: String, uri: String, mime: String, size: Long, info: String, thumbUri: String = "") {
        val prefs = getSharedPreferences("history", MODE_PRIVATE)
        val old = try {
            JSONArray(prefs.getString("items", "[]"))
        } catch (e: Exception) {
            JSONArray()
        }
        val arr = JSONArray()
        arr.put(
            JSONObject().put("name", name).put("uri", uri).put("mime", mime)
                .put("size", size).put("info", info).put("thumbUri", thumbUri)
        )
        for (n in 0 until minOf(old.length(), 49)) arr.put(old.get(n))
        prefs.edit().putString("items", arr.toString()).apply()
    }
}
