package com.dlwangsan.tapread

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.dlwangsan.tapread.clipboard.ClipboardMonitorService
import com.dlwangsan.tapread.databinding.ActivityMainBinding
import com.dlwangsan.tapread.overlay.OverlayService
import com.google.android.material.slider.Slider
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var overlayRequested = true
    private var overlayStarting = false

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            maybeAutoStartOverlay()
            refreshUi()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overlayRequested = savedInstanceState?.getBoolean("overlay_requested", true) ?: true
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Android 16 enforces edge-to-edge; keep controls clear of bars and cutouts.
        val root = binding.root
        val left = root.paddingLeft
        val top = root.paddingTop
        val right = root.paddingRight
        val bottom = root.paddingBottom
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val safe = insets.getInsets(
                androidx.core.view.WindowInsetsCompat.Type.systemBars() or
                    androidx.core.view.WindowInsetsCompat.Type.displayCutout()
            )
            view.setPadding(left + safe.left, top + safe.top, right + safe.right, bottom + safe.bottom)
            insets
        }
        androidx.core.view.ViewCompat.requestApplyInsets(root)

        TtsManager.init(this)
        setupModeSections()
        setupTtsControls()
        setupModeControls()

        maybeRequestNotificationPermission()
    }

    override fun onResume() {
        super.onResume()
        when (TtsManager.status) {
            TtsManager.Status.UNINITIALIZED,
            TtsManager.Status.NO_ENGINE,
            TtsManager.Status.ERROR -> TtsManager.reinit(this)
            TtsManager.Status.LANG_MISSING -> {
                // Keep current engine; just refresh labels after user may have installed data.
                refreshTtsUi(TtsManager.status)
            }
            else -> Unit
        }
        maybeAutoStartOverlay()
        refreshUi()
        refreshTtsUi(TtsManager.status)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_LAUNCHER)) {
            overlayRequested = true
            maybeAutoStartOverlay()
            refreshUi()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("overlay_requested", overlayRequested)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        TtsManager.setStatusListener(null)
        TtsManager.setSpeakingListener(null)
        super.onDestroy()
    }

    private fun setupModeSections() {
        val legacy = DeviceCapabilities.canBackgroundClipboardRead
        binding.sectionLegacyAutoRead.visibility = if (legacy) View.VISIBLE else View.GONE
        binding.sectionOverlay.visibility = if (legacy) View.GONE else View.VISIBLE
        binding.sectionAccessibility.visibility = if (legacy) View.GONE else View.VISIBLE
        binding.taglineText.setText(
            if (legacy) R.string.tagline_legacy else R.string.tagline
        )
    }

    private fun setupModeControls() {
        binding.switchAutoRead.isChecked = Prefs.autoReadEnabled
        binding.switchLegacyAutoRead.isChecked = Prefs.autoReadEnabled

        binding.btnOverlayPermission.setOnClickListener {
            startActivity(PermissionHelper.overlayPermissionIntent(this))
        }
        binding.btnOverlayPower.setOnClickListener {
            overlayRequested = !(OverlayService.isRunning || overlayStarting)
            if (overlayRequested) startOverlay(userInitiated = true)
            else {
                overlayStarting = false
                OverlayService.stop(this)
                binding.root.postDelayed({ if (!isDestroyed) refreshUi() }, 400)
            }
            refreshUi()
        }
        binding.btnOpenAccessibility.setOnClickListener {
            startActivity(PermissionHelper.accessibilitySettingsIntent())
        }
        binding.btnToggleClipboardMonitor.setOnClickListener { toggleClipboardMonitor() }

        binding.switchAutoRead.setOnCheckedChangeListener { _, checked ->
            Prefs.autoReadEnabled = checked
            binding.switchLegacyAutoRead.isChecked = checked
        }
        binding.switchLegacyAutoRead.setOnCheckedChangeListener { _, checked ->
            Prefs.autoReadEnabled = checked
            binding.switchAutoRead.isChecked = checked
        }
    }

    private fun setupTtsControls() {
        binding.switchSensitiveRead.isChecked = Prefs.allowSensitiveManualRead
        binding.switchSensitiveRead.setOnCheckedChangeListener { _, checked ->
            Prefs.allowSensitiveManualRead = checked
            if (!checked) TtsManager.stop()
        }
        binding.switchSystemVoice.isChecked = Prefs.systemVoiceCompatibility
        binding.switchSystemVoice.setOnCheckedChangeListener { _, checked ->
            TtsManager.stop()
            Prefs.systemVoiceCompatibility = checked
            TtsManager.reinit(this)
        }
        val rate = Prefs.speechRate
        val pitch = Prefs.speechPitch
        binding.speechRateSlider.value = rate
        binding.speechPitchSlider.value = pitch
        updateSpeechRateLabel(rate)
        updateSpeechPitchLabel(pitch)

        binding.speechRateSlider.addOnChangeListener { _: Slider, value: Float, fromUser: Boolean ->
            if (fromUser) {
                TtsManager.applySpeechRate(value)
                updateSpeechRateLabel(value)
            }
        }
        binding.speechPitchSlider.addOnChangeListener { _: Slider, value: Float, fromUser: Boolean ->
            if (fromUser) {
                TtsManager.applyPitch(value)
                updateSpeechPitchLabel(value)
            }
        }

        binding.btnReadClipboard.setOnClickListener {
            val text = ClipboardHelper.readText(this, allowSensitive = Prefs.allowSensitiveManualRead)
            val message = when {
                text.isNullOrBlank() -> R.string.clipboard_unavailable
                !TtsManager.speak(text) -> R.string.tts_speak_failed
                else -> null
            }
            if (message != null) Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
        binding.btnTestSpeak.setOnClickListener { testSpeak() }
        binding.btnStopSpeak.setOnClickListener { TtsManager.stop() }

        binding.btnOpenTtsSettings.setOnClickListener {
            if (!TtsManager.openSystemTtsSettings(this)) {
                Toast.makeText(this, R.string.tts_settings_missing, Toast.LENGTH_LONG).show()
            }
        }
        binding.btnInstallTtsData.setOnClickListener {
            if (!TtsManager.openInstallTtsData(this)) {
                TtsManager.openSystemTtsSettings(this)
            }
        }
        binding.btnRefreshTts.setOnClickListener {
            TtsManager.reinit(this)
            refreshTtsUi(TtsManager.status)
        }

        TtsManager.setStatusListener { status ->
            runOnUiThread { refreshTtsUi(status) }
        }
        TtsManager.setSpeakingListener { speaking ->
            runOnUiThread {
                binding.btnTestSpeak.isEnabled = !speaking
            }
        }
        refreshTtsUi(TtsManager.status)
    }

    private fun testSpeak() {
        val sample = if (DeviceCapabilities.canBackgroundClipboardRead) {
            getString(R.string.test_sample_legacy)
        } else {
            getString(R.string.test_sample)
        }
        val ok = TtsManager.speak(sample)
        if (!ok) {
            Toast.makeText(this, R.string.tts_speak_failed, Toast.LENGTH_LONG).show()
            refreshTtsUi(TtsManager.status)
        }
    }

    private fun refreshTtsUi(status: TtsManager.Status) {
        val engines = TtsManager.listEngines(this)
        val engineName = when {
            TtsManager.engineLabel.isNotBlank() &&
                TtsManager.engineLabel != "未初始化" -> TtsManager.engineLabel
            engines.isNotEmpty() -> {
                val preferred = engines.firstOrNull { it.isDefault } ?: engines.first()
                preferred.label
            }
            else -> "未检测到"
        }

        binding.ttsEngineText.text = getString(R.string.tts_engine_label, engineName)
        binding.ttsLangText.text = buildString {
            append(getString(R.string.tts_lang_label, TtsManager.languageLabel))
            if (engines.isNotEmpty()) {
                append("  ·  ")
                append(getString(R.string.engine_count, engines.size))
            }
        }

        val warn = when (status) {
            TtsManager.Status.READY -> null
            TtsManager.Status.INITIALIZING,
            TtsManager.Status.UNINITIALIZED -> getString(R.string.tts_status_initializing)
            TtsManager.Status.NO_ENGINE -> getString(R.string.tts_status_no_engine)
            TtsManager.Status.LANG_MISSING -> getString(R.string.tts_status_lang_missing)
            TtsManager.Status.OFFLINE_UNAVAILABLE -> getString(R.string.tts_status_offline_unknown)
            TtsManager.Status.ERROR -> getString(R.string.tts_status_error)
        }

        if (warn == null) {
            binding.ttsWarnText.visibility = View.GONE
        } else {
            binding.ttsWarnText.visibility = View.VISIBLE
            binding.ttsWarnText.text = warn
        }

        val canSpeak = status == TtsManager.Status.READY ||
            status == TtsManager.Status.INITIALIZING
        binding.btnTestSpeak.isEnabled = canSpeak || status == TtsManager.Status.UNINITIALIZED
    }

    private fun maybeRequestNotificationPermission() {
        if (!PermissionHelper.notificationPermissionNeeded()) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun maybeAutoStartOverlay() {
        if (!DeviceCapabilities.canBackgroundClipboardRead && overlayRequested &&
            PermissionHelper.canDrawOverlays(this)) startOverlay(userInitiated = false)
    }

    private fun startOverlay(userInitiated: Boolean) {
        if (OverlayService.isRunning || overlayStarting) return
        if (!PermissionHelper.canDrawOverlays(this)) {
            if (userInitiated) {
                Toast.makeText(this, R.string.need_overlay_permission, Toast.LENGTH_LONG).show()
                startActivity(PermissionHelper.overlayPermissionIntent(this))
            }
            return
        }
        // Notification denial does not prevent a user-visible foreground service.
        overlayStarting = true
        val started = runCatching { OverlayService.start(this) }.isSuccess
        if (!started) {
            overlayStarting = false
            Toast.makeText(this, R.string.overlay_start_failed, Toast.LENGTH_LONG).show()
        }
        binding.root.postDelayed({
            if (!isDestroyed) {
                overlayStarting = false
                refreshUi()
            }
        }, 500)
    }

    private fun toggleClipboardMonitor() {
        if (ClipboardMonitorService.isRunning) {
            ClipboardMonitorService.stop(this)
            refreshUi()
            return
        }
        ClipboardMonitorService.start(this)
        binding.root.postDelayed({ refreshUi() }, 400)
    }

    private fun refreshUi() {
        val legacy = DeviceCapabilities.canBackgroundClipboardRead
        val overlayOn = OverlayService.isRunning
        val monitorOn = ClipboardMonitorService.isRunning
        val a11yOn = PermissionHelper.isAccessibilityEnabled(this)

        binding.statusTitle.text = if (legacy) {
            if (monitorOn) {
                getString(R.string.status_clipboard_monitor_on)
            } else {
                getString(R.string.status_clipboard_monitor_off)
            }
        } else {
            if (overlayOn) {
                getString(R.string.status_overlay_on)
            } else {
                getString(R.string.status_overlay_off)
            }
        }

        binding.statusText.text = buildString {
            append("Android ${android.os.Build.VERSION.RELEASE}")
            append("  ·  ")
            append(
                if (legacy) {
                    getString(R.string.status_mode_legacy)
                } else {
                    getString(R.string.status_mode_modern)
                }
            )
            if (!legacy) {
                append('\n')
                append(
                    if (a11yOn) {
                        getString(R.string.status_a11y_on)
                    } else {
                        getString(R.string.status_a11y_off)
                    }
                )
                append("  ·  ")
                append(
                    if (PermissionHelper.canDrawOverlays(this@MainActivity)) {
                        "悬浮窗已授权"
                    } else {
                        "悬浮窗未授权"
                    }
                )
            }
        }

        val powerOn = overlayOn || overlayStarting
        binding.btnOverlayPower.isActivated = powerOn
        binding.btnOverlayPower.setImageResource(
            if (powerOn) R.drawable.ic_overlay_stop else R.drawable.ic_overlay_start
        )
        binding.btnOverlayPower.contentDescription = getString(
            if (powerOn) R.string.stop_overlay else R.string.start_overlay
        )
        val stateLabel = getString(when {
            overlayOn -> R.string.overlay_power_on
            overlayStarting -> R.string.overlay_power_starting
            else -> R.string.overlay_power_off
        })
        binding.overlayPowerState.text = stateLabel
        binding.overlayPowerState.setTextColor(ContextCompat.getColor(
            this, if (powerOn) R.color.accent_deep else R.color.ink_soft
        ))
        binding.overlayPowerAction.setText(
            if (powerOn) R.string.overlay_power_tap_off else R.string.overlay_power_tap_on
        )
        androidx.core.view.ViewCompat.setStateDescription(binding.btnOverlayPower, stateLabel)
        binding.btnOverlayPermission.visibility =
            if (PermissionHelper.canDrawOverlays(this)) View.GONE else View.VISIBLE
        binding.btnToggleClipboardMonitor.text =
            if (monitorOn) {
                getString(R.string.stop_clipboard_monitor)
            } else {
                getString(R.string.start_clipboard_monitor)
            }
    }

    private fun updateSpeechRateLabel(rate: Float) {
        binding.speechRateLabel.text =
            getString(R.string.speech_rate) + "  " + String.format(Locale.US, "%.1fx", rate)
    }

    private fun updateSpeechPitchLabel(pitch: Float) {
        binding.speechPitchLabel.text =
            getString(R.string.speech_pitch) + "  " + String.format(Locale.US, "%.1fx", pitch)
    }
}
