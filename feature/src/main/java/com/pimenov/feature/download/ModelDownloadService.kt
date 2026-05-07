package com.pimenov.feature.download

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
import com.pimenov.feature.R
import com.pimenov.feature.api.DownloadEvent
import com.pimenov.feature.api.ModelDownloader
import com.pimenov.feature.api.ModelVariant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/**
 * Foreground service that runs the model download independently of the UI.
 * Survives Activity recreation, app backgrounding, and screen lock.
 * Reports progress via [DownloadController] and a system notification.
 */
class ModelDownloadService : Service() {

    private val downloader: ModelDownloader by inject()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var job: Job? = null
    private var lastNotifiedPercent = -1

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val variantName = intent?.getStringExtra(EXTRA_VARIANT)
        val variant = variantName?.let { runCatching { ModelVariant.valueOf(it) }.getOrNull() }
        if (variant == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        ensureChannel()
        startForegroundCompat(buildNotification(variant, 0, indeterminate = true))

        if (job?.isActive == true) return START_NOT_STICKY

        job = scope.launch {
            downloader.download(variant).collect { event ->
                DownloadController.onEvent(variant, event)
                when (event) {
                    is DownloadEvent.Progress -> {
                        val pct = (event.ratio * 100).toInt()
                        if (pct != lastNotifiedPercent) {
                            lastNotifiedPercent = pct
                            notify(buildNotification(variant, pct, indeterminate = false))
                        }
                    }
                    is DownloadEvent.Done -> {
                        notify(buildFinalNotification(variant, success = true))
                        stopForegroundAndSelf()
                    }
                    is DownloadEvent.Failed -> {
                        notify(buildFinalNotification(variant, success = false, error = event.error.message))
                        stopForegroundAndSelf()
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundCompat(notification: android.app.Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun stopForegroundAndSelf() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_DETACH)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(false)
        }
        stopSelf()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.download_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.download_channel_desc)
            setShowBadge(false)
        }
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(
        variant: ModelVariant,
        progressPercent: Int,
        indeterminate: Boolean
    ): android.app.Notification {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.download_notif_title, variant.displayName))
            .setContentText(
                if (indeterminate) getString(R.string.download_notif_starting)
                else getString(R.string.download_notif_progress, progressPercent)
            )
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(launchPendingIntent())
        if (indeterminate) builder.setProgress(0, 0, true)
        else builder.setProgress(100, progressPercent, false)
        return builder.build()
    }

    private fun buildFinalNotification(
        variant: ModelVariant,
        success: Boolean,
        error: String? = null
    ): android.app.Notification {
        val title =
            if (success) getString(R.string.download_notif_done, variant.displayName)
            else getString(R.string.download_notif_failed, variant.displayName)
        val text = if (success) getString(R.string.download_notif_done_text)
        else error ?: getString(R.string.download_notif_failed_text)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(
                if (success) android.R.drawable.stat_sys_download_done
                else android.R.drawable.stat_notify_error
            )
            .setAutoCancel(true)
            .setContentIntent(launchPendingIntent())
            .build()
    }

    private fun notify(notification: android.app.Notification) {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        nm.notify(NOTIFICATION_ID, notification)
    }

    private fun launchPendingIntent(): PendingIntent? {
        val launch = packageManager.getLaunchIntentForPackage(packageName) ?: return null
        return PendingIntent.getActivity(
            this, 0, launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    companion object {
        const val EXTRA_VARIANT = "variant_name"
        private const val CHANNEL_ID = "model_download"
        private const val NOTIFICATION_ID = 1042
    }
}
