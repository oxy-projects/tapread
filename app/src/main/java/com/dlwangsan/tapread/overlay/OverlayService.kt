package com.dlwangsan.tapread.overlay

import android.animation.ValueAnimator
import android.content.res.Configuration
import android.graphics.Rect
import android.view.WindowInsets
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.ViewConfiguration
import android.widget.ImageButton
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.dlwangsan.tapread.ClipboardHelper
import com.dlwangsan.tapread.MainActivity
import com.dlwangsan.tapread.Prefs
import com.dlwangsan.tapread.R
import com.dlwangsan.tapread.TtsManager
import kotlin.math.abs

class OverlayService : Service() {
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var params: WindowManager.LayoutParams? = null
    private var focusReadPending = false
    private var snapAnimator: ValueAnimator? = null
    private var ballVisual: View? = null
    private var aiRing: AiRingView? = null
    private val playbackListener: (TtsManager.PlaybackState) -> Unit = { state ->
        aiRing?.setPlaybackState(state)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        try {
            createChannel()
            startAsForeground()
            showOverlay()
            Prefs.overlayWanted = true
            isRunning = true
        } catch (e: Exception) {
            android.util.Log.w("TapReadOverlay", "Overlay could not start", e)
            Toast.makeText(this, R.string.overlay_start_failed, Toast.LENGTH_LONG).show()
            stopSelf()
        }
    }

    override fun onDestroy() {
        removeOverlay()
        Prefs.overlayWanted = false
        isRunning = false
        super.onDestroy()
    }

    private fun startAsForeground() {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.overlay_notification_title))
            .setContentText(getString(R.string.overlay_notification_text))
            .setSmallIcon(R.drawable.ic_speaker)
            .setContentIntent(openApp)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.overlay_channel_name),
            NotificationManager.IMPORTANCE_LOW
        )
        manager.createNotificationChannel(channel)
    }

    private fun showOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val view = LayoutInflater.from(this).inflate(R.layout.view_overlay_ball, null)
        overlayView = view

        val type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            x = 48
            y = 360
        }

        ballVisual = view.findViewById(R.id.ballVisual)
        aiRing = view.findViewById(R.id.aiRing)
        TtsManager.addPlaybackListener(playbackListener)
        val ball = view.findViewById<ImageButton>(R.id.btnBall)
        setupTouch(ball)
        ball.setOnClickListener { onBallClicked() }

        windowManager?.addView(view, params)
        view.post { if (overlayView === view) snapToEdge(animate = false) }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        resetBallVisual(animate = false)
        overlayView?.post { snapToEdge(animate = false) }
    }

    private fun dragBounds(): Rect {
        val wm = windowManager ?: return Rect()
        val view = overlayView ?: return Rect()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = wm.currentWindowMetrics
            val safe = metrics.windowInsets.getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
            )
            return Rect(safe.left, safe.top,
                (metrics.bounds.width() - safe.right - view.width).coerceAtLeast(safe.left),
                (metrics.bounds.height() - safe.bottom - view.height).coerceAtLeast(safe.top))
        }
        val metrics = resources.displayMetrics
        return Rect(0, 0, (metrics.widthPixels - view.width).coerceAtLeast(0),
            (metrics.heightPixels - view.height).coerceAtLeast(0))
    }

    private fun snapToEdge(animate: Boolean = true) {
        snapAnimator?.cancel()
        val view = overlayView ?: return
        val lp = params ?: return
        val bounds = dragBounds()
        val targetX = if (lp.x.toLong() - bounds.left <= bounds.right.toLong() - lp.x)
            bounds.left else bounds.right
        lp.y = lp.y.coerceIn(bounds.top, bounds.bottom)
        if (!animate || !ValueAnimator.areAnimatorsEnabled() || lp.x == targetX) {
            lp.x = targetX
            runCatching { windowManager?.updateViewLayout(view, lp) }
            return
        }
        snapAnimator = ValueAnimator.ofInt(lp.x, targetX).apply {
            duration = 200L
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                if (overlayView === view) {
                    lp.x = it.animatedValue as Int
                    runCatching { windowManager?.updateViewLayout(view, lp) }
                }
            }
            start()
        }
    }

    private fun resetBallVisual(animate: Boolean = true) {
        val visual = ballVisual ?: return
        visual.animate().cancel()
        if (animate && ValueAnimator.areAnimatorsEnabled()) {
            visual.animate().scaleX(1f).scaleY(1f).rotation(0f)
                .setDuration(340L).setInterpolator(OvershootInterpolator(2f)).start()
        } else {
            visual.scaleX = 1f
            visual.scaleY = 1f
            visual.rotation = 0f
        }
    }

    private fun deformBall(dx: Float, dy: Float) {
        val visual = ballVisual ?: return
        if (!ValueAnimator.areAnimatorsEnabled()) return
        val distance = kotlin.math.hypot(dx, dy)
        if (distance < 0.5f) return
        val amount = (distance / (resources.displayMetrics.density * 18f)).coerceIn(0f, 1f)
        val horizontal = abs(dx) >= abs(dy)
        // Cap stretching at 5% so the artwork stays inside the overlay window.
        val stretch = 1f + 0.05f * amount
        val squash = 1f - 0.07f * amount
        visual.animate().cancel()
        visual.animate().scaleX(if (horizontal) stretch else squash)
            .scaleY(if (horizontal) squash else stretch)
            .rotation(0f).setDuration(65L).setInterpolator(DecelerateInterpolator()).start()
    }

    private fun setupTouch(ball: View) {
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var moved = false
        var lastX = 0f
        var lastY = 0f

        ball.setOnTouchListener { _, event ->
            val lp = params ?: return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    snapAnimator?.cancel()
                    moved = false
                    ballVisual?.let { visual ->
                        visual.animate().cancel()
                        if (ValueAnimator.areAnimatorsEnabled()) {
                            visual.animate().scaleX(0.96f).scaleY(0.96f).setDuration(80L)
                                .setInterpolator(DecelerateInterpolator()).start()
                        }
                    }
                    lastX = event.rawX
                    lastY = event.rawY
                    downX = event.rawX
                    downY = event.rawY
                    startX = lp.x
                    startY = lp.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downX).toInt()
                    val dy = (event.rawY - downY).toInt()
                    if (abs(dx) > ViewConfiguration.get(this).scaledTouchSlop ||
                        abs(dy) > ViewConfiguration.get(this).scaledTouchSlop) moved = true
                    if (moved) {
                        deformBall(event.rawX - lastX, event.rawY - lastY)
                        val bounds = dragBounds()
                        lp.x = (startX + dx).coerceIn(bounds.left, bounds.right)
                        lp.y = (startY + dy).coerceIn(bounds.top, bounds.bottom)
                        runCatching { windowManager?.updateViewLayout(overlayView, lp) }
                    }
                    lastX = event.rawX
                    lastY = event.rawY
                    true
                }
                MotionEvent.ACTION_UP -> {
                    resetBallVisual()
                    if (!moved) ball.performClick() else snapToEdge()
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    resetBallVisual()
                    snapToEdge()
                    true
                }
                else -> false
            }
        }
    }

    private fun onBallClicked() {
        if (focusReadPending) return
        if (TtsManager.playbackState != TtsManager.PlaybackState.IDLE) {
            TtsManager.stop()
            vibrateLight()
            return
        }
        // Android 10+ only allows clipboard reads for the focused app.
        // Temporarily drop NOT_FOCUSABLE so this overlay can read clipboard.
        withFocusForClipboard {
            val text = ClipboardHelper.readText(this, allowSensitive = Prefs.allowSensitiveManualRead)
            if (text.isNullOrBlank()) {
                Toast.makeText(this, R.string.clipboard_unavailable, Toast.LENGTH_LONG).show()
                return@withFocusForClipboard
            }
            val ok = TtsManager.speak(text)
            if (ok) {
                vibrateLight()
            } else {
                Toast.makeText(this, R.string.tts_speak_failed, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun withFocusForClipboard(block: () -> Unit) {
        val view = overlayView
        val lp = params
        val wm = windowManager
        if (view == null || lp == null || wm == null) {
            block()
            return
        }
        focusReadPending = true
        val originalFlags = lp.flags
        var completed = false
        lateinit var listener: android.view.ViewTreeObserver.OnWindowFocusChangeListener
        fun finish(read: Boolean) {
            if (completed) return
            completed = true
            focusReadPending = false
            if (view.viewTreeObserver.isAlive) view.viewTreeObserver.removeOnWindowFocusChangeListener(listener)
            try {
                if (read && overlayView === view) block()
                else if (overlayView === view) Toast.makeText(this, R.string.clipboard_unavailable, Toast.LENGTH_LONG).show()
            } finally {
                lp.flags = originalFlags
                if (overlayView === view) runCatching { wm.updateViewLayout(view, lp) }
            }
        }
        listener = android.view.ViewTreeObserver.OnWindowFocusChangeListener { focused ->
            if (focused) view.post { finish(true) }
        }
        view.viewTreeObserver.addOnWindowFocusChangeListener(listener)
        lp.flags = originalFlags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        if (runCatching { wm.updateViewLayout(view, lp) }.isFailure) {
            finish(false)
            return
        }
        view.isFocusableInTouchMode = true
        view.requestFocus()
        if (view.hasWindowFocus()) view.post { finish(true) }
        view.postDelayed({ finish(false) }, 1000)
    }

    private fun vibrateLight() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(VibratorManager::class.java)
                vm.defaultVibrator.vibrate(
                    VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
                vibrator.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        } catch (_: Exception) {
        }
    }

    private fun removeOverlay() {
        TtsManager.removePlaybackListener(playbackListener)
        aiRing = null
        resetBallVisual(animate = false)
        ballVisual = null
        snapAnimator?.cancel()
        snapAnimator = null
        overlayView?.let { runCatching { windowManager?.removeView(it) } }
        overlayView = null
    }

    companion object {
        const val CHANNEL_ID = "tapread_overlay"
        const val NOTIFICATION_ID = 1001
        @Volatile
        var isRunning: Boolean = false
            private set

        fun start(context: Context) {
            val intent = Intent(context, OverlayService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, OverlayService::class.java))
        }
    }
}
