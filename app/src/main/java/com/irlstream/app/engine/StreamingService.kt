package com.irlstream.app.engine

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
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.irlstream.app.MainActivity
import com.irlstream.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Keeps camera/microphone capture and RTMP publishing alive while the app is in the
 * background or the screen is off (a core IRL requirement).
 */
class StreamingService : Service() {

    companion object {
        private const val CHANNEL_ID = "irlstream-live"
        private const val NOTIFICATION_ID = 0x1A1
        private const val ACTION_STOP = "com.irlstream.app.action.STOP_STREAM"

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, StreamingService::class.java),
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, StreamingService::class.java))
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var stateJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            try {
                StreamEngine.get().stopLive()
            } catch (_: Throwable) {
            }
            stopSelf()
            return START_NOT_STICKY
        }

        val engine = try {
            StreamEngine.get()
        } catch (_: Throwable) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!engine.state.value.isLive) {
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = buildNotification(engine.state.value)
        val type = if (Build.VERSION.SDK_INT >= 30) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        } else {
            0
        }
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
        } catch (t: Throwable) {
            // Foreground-service restrictions should not take the stream down; the
            // activity remains in the foreground if this fails.
            android.util.Log.w("StreamingService", "startForeground failed", t)
            stopSelf()
            return START_NOT_STICKY
        }

        acquireWakeLock()
        observeState(engine)
        return START_NOT_STICKY
    }

    private fun observeState(engine: StreamEngine) {
        stateJob?.cancel()
        stateJob = scope.launch {
            var lastUpdate = 0L
            engine.state.collect { state ->
                if (!state.isLive) {
                    stopSelf()
                    return@collect
                }
                val now = android.os.SystemClock.elapsedRealtime()
                if (now - lastUpdate >= 1_000) {
                    lastUpdate = now
                    updateNotification(state)
                }
            }
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock != null) return
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "RealStreamer:live").apply {
                setReferenceCounted(false)
                acquire(12 * 60 * 60 * 1000L) // 12 h safety cap; released on stop
            }
        } catch (_: Throwable) {
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.release()
        } catch (_: Throwable) {
        }
        wakeLock = null
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Live streaming",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Active broadcast and capture status"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(state: EngineState): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, StreamingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val live = state.destinations.count { it.status == DestinationStatus.LIVE }
        val total = state.destinations.count { it.status != DestinationStatus.DISABLED }
        val text = buildString {
            append(OverlayRenderer.formatDuration(state.durationMs))
            append(" · ")
            append("$live/$total outputs")
            if (state.videoBitrateKbps > 0) {
                append(" · ")
                append(String.format(java.util.Locale.US, "%.1f Mbps", state.videoBitrateKbps / 1000f))
            }
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_stream)
            .setContentTitle("RealStreamer is live")
            .setContentText(text)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, "End stream", stopIntent)
            .build()
    }

    private fun updateNotification(state: EngineState) {
        try {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, buildNotification(state))
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS may be denied; the foreground service still runs.
        }
    }

    override fun onDestroy() {
        stateJob?.cancel()
        scope.cancel()
        releaseWakeLock()
        val engine = try {
            StreamEngine.get()
        } catch (_: Throwable) {
            null
        }
        engine?.onServiceStopped()
        if (engine?.state?.value?.isLive == true) {
            engine.stopLive()
        }
        try {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        } catch (_: Throwable) {
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
