package com.ayam.video

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object DownloadNotifier {
    const val CHANNEL_ID = "ayam_downloads"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "Téléchargements", NotificationManager.IMPORTANCE_LOW)
                )
            }
        }
    }

    private fun openAppIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    fun progressNotification(context: Context, text: String, percent: Int?): Notification {
        ensureChannel(context)
        val b = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("AYAM VIDEO")
            .setContentText(text)
            .setContentIntent(openAppIntent(context))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
        if (percent != null) b.setProgress(100, percent, false) else b.setProgress(0, 0, true)
        return b.build()
    }

    private fun post(context: Context, id: Int, title: String, text: String, icon: Int) {
        try {
            ensureChannel(context)
            val n = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(icon)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(openAppIntent(context))
                .setAutoCancel(true)
                .build()
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(id, n)
        } catch (e: Exception) {
            // notifications refusées : pas grave
        }
    }

    fun done(context: Context, videoId: String, title: String) =
        post(context, videoId.hashCode(), "Téléchargement terminé", title, android.R.drawable.stat_sys_download_done)

    fun failed(context: Context, videoId: String, title: String) =
        post(context, videoId.hashCode(), "Échec du téléchargement", title, android.R.drawable.stat_notify_error)
}

/**
 * Ce service ne télécharge rien lui-même : il garde l'appli en vie en arrière-plan
 * et affiche la notification de progression pendant que DownloadHolder travaille.
 */
class DownloadService : Service() {

    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val first = DownloadNotifier.progressNotification(this, "Téléchargement en cours…", null)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, first, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIF_ID, first)
        }

        if (job?.isActive != true) {
            job = scope.launch {
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                while (true) {
                    val active = DownloadHolder.progress.filter { it.value >= 0f }
                    if (active.isEmpty()) break
                    val notif = if (active.size == 1) {
                        val id = active.keys.first()
                        val pct = (active.values.first() * 100).toInt()
                        val title = DownloadHolder.titles[id] ?: "Vidéo"
                        DownloadNotifier.progressNotification(this@DownloadService, "$title • $pct %", pct)
                    } else {
                        DownloadNotifier.progressNotification(
                            this@DownloadService,
                            "${active.size} téléchargements en cours",
                            null
                        )
                    }
                    try { nm.notify(NOTIF_ID, notif) } catch (e: Exception) { }
                    delay(1000)
                }
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        job?.cancel()
        super.onDestroy()
    }

    companion object {
        private const val NOTIF_ID = 4242

        fun start(context: Context) {
            try {
                val i = Intent(context, DownloadService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(i)
                } else {
                    context.startService(i)
                }
            } catch (e: Exception) {
                // si le système refuse, le téléchargement continue quand même tant que l'appli est ouverte
            }
        }
    }
}
