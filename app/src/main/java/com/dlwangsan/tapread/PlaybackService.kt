package com.dlwangsan.tapread

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

/** Owns the foreground lifetime of user-requested speech, independently of the overlay. */
class PlaybackService : Service() {
    private var active = false
    private var lastStartId = 0
    private val listener: (TtsManager.PlaybackState) -> Unit = { state ->
        if (active && state == TtsManager.PlaybackState.IDLE) {
            active = false
            stopSelf(lastStartId)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.playback_channel), NotificationManager.IMPORTANCE_LOW)
        )
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, PlaybackService::class.java).setAction(STOP), PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_speaker)
            .setContentTitle(getString(R.string.playback_channel))
            .setContentText(getString(R.string.playback_notification))
            .setContentIntent(open).setOngoing(true)
            .addAction(R.drawable.ic_overlay_stop, getString(R.string.playback_stop), stop)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(1003, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else startForeground(1003, notification)
        TtsManager.addPlaybackListener(listener)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        if (intent?.action == STOP) {
            TtsManager.stop()
            stopSelf(startId)
        } else {
            TtsManager.init(applicationContext)
            active = true
            if (!TtsManager.speakInForeground(intent?.getStringExtra(TEXT).orEmpty(), intent?.getBooleanExtra(FLUSH, true) ?: true)) {
                TtsManager.stop()
                active = false
                stopSelf(startId)
                android.widget.Toast.makeText(this, R.string.tts_speak_failed, android.widget.Toast.LENGTH_LONG).show()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        TtsManager.removePlaybackListener(listener)
        if (active) TtsManager.stop()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "tapread_playback"
        private const val STOP = "com.dlwangsan.tapread.STOP_PLAYBACK"
        private const val TEXT = "text"
        private const val FLUSH = "flush"
        fun start(context: Context, text: String, flush: Boolean): Boolean = runCatching {
            context.startForegroundService(Intent(context, PlaybackService::class.java).putExtra(TEXT, text).putExtra(FLUSH, flush))
        }.isSuccess
    }
}
