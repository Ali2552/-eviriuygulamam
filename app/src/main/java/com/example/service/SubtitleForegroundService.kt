package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.VideoSubtitleApplication
import com.example.data.model.LanguagePacksCatalog
import com.example.data.model.LiveSubtitle
import com.example.data.model.SubtitleMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "SubtitleForegroundService"
private const val NOTIFICATION_ID = 4040
private const val CHANNEL_ID = "channel_subtitle_service"

object ServiceController {
    const val ACTION_START = "com.example.service.START"
    const val ACTION_STOP = "com.example.service.STOP"
    const val ACTION_TOGGLE_OVERLAY = "com.example.service.TOGGLE_OVERLAY"
    const val ACTION_CHANGE_MODE = "com.example.service.CHANGE_MODE"
    const val ACTION_CHANGE_LANGUAGE = "com.example.service.CHANGE_LANGUAGE"

    const val EXTRA_RESULT_CODE = "extra_result_code"
    const val EXTRA_RESULT_DATA = "extra_result_data"
    const val EXTRA_LANGUAGE_CODE = "extra_language_code"
    const val EXTRA_MODE = "extra_mode"

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _statusText = MutableStateFlow("Durduruldu")
    val statusText: StateFlow<String> = _statusText.asStateFlow()

    private val _currentPeak = MutableStateFlow(0f)
    val currentPeak: StateFlow<Float> = _currentPeak.asStateFlow()

    private val _liveSubtitles = MutableStateFlow<List<LiveSubtitle>>(emptyList())
    val liveSubtitles: StateFlow<List<LiveSubtitle>> = _liveSubtitles.asStateFlow()

    private val _currentLanguageCode = MutableStateFlow("en")
    val currentLanguageCode: StateFlow<String> = _currentLanguageCode.asStateFlow()

    private val _currentMode = MutableStateFlow(SubtitleMode.TRANSCRIPT_AND_TRANSLATE)
    val currentMode: StateFlow<SubtitleMode> = _currentMode.asStateFlow()

    fun updateRunning(running: Boolean) { _isRunning.value = running }
    fun updateStatus(status: String) { _statusText.value = status }
    fun updatePeak(peak: Float) { _currentPeak.value = peak }
    fun updateSubtitles(list: List<LiveSubtitle>) { _liveSubtitles.value = list }
    fun updateLanguage(code: String) { _currentLanguageCode.value = code }
    fun updateMode(mode: SubtitleMode) { _currentMode.value = mode }
    fun clearSubtitles() { _liveSubtitles.value = emptyList() }
}

class SubtitleForegroundService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main)
    private var audioCaptureManager: AudioCaptureManager? = null
    private var translationEngine: TranslationEngine? = null
    private var overlayManager: SubtitleOverlayManager? = null
    private var mediaProjection: MediaProjection? = null

    private var subtitleCollectionJob: Job? = null
    private var peakCollectionJob: Job? = null
    private var statusCollectionJob: Job? = null

    private val sessionLines = mutableListOf<Pair<Long, Pair<String, String>>>()
    private var sessionStartTime = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val app = application as VideoSubtitleApplication
        val langManager = app.languagePackManager

        overlayManager = SubtitleOverlayManager(
            context = this,
            onSaveNote = { noteContent ->
                serviceScope.launch(Dispatchers.IO) {
                    val title = "Alt Yazı Notu (${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())})"
                    app.noteRepository.insertNote(title, noteContent)
                }
            },
            onToggleMode = {
                val nextMode = when (ServiceController.currentMode.value) {
                    SubtitleMode.TRANSCRIPT_ONLY -> SubtitleMode.TRANSCRIPT_AND_TRANSLATE
                    SubtitleMode.TRANSCRIPT_AND_TRANSLATE -> SubtitleMode.TRANSLATE_ONLY
                    SubtitleMode.TRANSLATE_ONLY -> SubtitleMode.TRANSCRIPT_ONLY
                }
                ServiceController.updateMode(nextMode)
                app.settingsRepository.setSubtitleMode(nextMode)
                updateOverlayConfig()
            },
            onToggleLanguage = {
                // Cycle between ready languages
                val readyPacks = langManager.packsState.value.filter { langManager.isModelReady(it) }
                if (readyPacks.isNotEmpty()) {
                    val currentCode = ServiceController.currentLanguageCode.value
                    val currentIndex = readyPacks.indexOfFirst { it.code == currentCode }
                    val nextPack = readyPacks[(currentIndex + 1) % readyPacks.size]
                    ServiceController.updateLanguage(nextPack.code)
                    app.settingsRepository.setSelectedLanguage(nextPack.code)
                    serviceScope.launch {
                        translationEngine?.loadLanguage(nextPack.code)
                    }
                    updateOverlayConfig()
                }
            },
            onCloseClicked = {
                overlayManager?.hideOverlay()
            }
        )

        translationEngine = TranslationEngine(
            context = this,
            languagePackManager = langManager,
            onEmptyVoskResult = { isEmpty ->
                audioCaptureManager?.notifyVoskEmptyResult(isEmpty)
            }
        )

        audioCaptureManager = AudioCaptureManager(
            context = this,
            onChunkReady = { pcmData, length ->
                translationEngine?.processAudioChunk(
                    audioData = pcmData,
                    length = length,
                    mode = ServiceController.currentMode.value
                )
            }
        )

        observeAudioCapture()
        observeSubtitles()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_NOT_STICKY

        when (action) {
            ServiceController.ACTION_START -> {
                // Must call startForeground immediately
                startForegroundNotification()

                val resultCode = intent.getIntExtra(ServiceController.EXTRA_RESULT_CODE, 0)
                val resultData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(ServiceController.EXTRA_RESULT_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(ServiceController.EXTRA_RESULT_DATA)
                }
                val langCode = intent.getStringExtra(ServiceController.EXTRA_LANGUAGE_CODE) ?: "en"
                val modeStr = intent.getStringExtra(ServiceController.EXTRA_MODE)
                val mode = if (modeStr != null) SubtitleMode.valueOf(modeStr) else SubtitleMode.TRANSCRIPT_AND_TRANSLATE

                ServiceController.updateLanguage(langCode)
                ServiceController.updateMode(mode)
                ServiceController.clearSubtitles()
                sessionLines.clear()
                sessionStartTime = System.currentTimeMillis()

                if (resultCode != 0 && resultData != null) {
                    initMediaProjectionAndCapture(resultCode, resultData, langCode)
                } else {
                    Log.e(TAG, "Missing MediaProjection resultCode/resultData")
                    stopSelf()
                }
            }

            ServiceController.ACTION_STOP -> {
                stopServiceInternal()
            }

            ServiceController.ACTION_TOGGLE_OVERLAY -> {
                if (overlayManager?.isShowing() == true) {
                    overlayManager?.hideOverlay()
                } else {
                    val app = application as VideoSubtitleApplication
                    val settings = app.settingsRepository.preferences.value.overlaySettings
                    overlayManager?.showOverlay(
                        settings,
                        ServiceController.currentMode.value,
                        ServiceController.currentLanguageCode.value
                    )
                }
            }

            ServiceController.ACTION_CHANGE_MODE -> {
                val modeStr = intent.getStringExtra(ServiceController.EXTRA_MODE)
                if (modeStr != null) {
                    val mode = SubtitleMode.valueOf(modeStr)
                    ServiceController.updateMode(mode)
                    updateOverlayConfig()
                }
            }

            ServiceController.ACTION_CHANGE_LANGUAGE -> {
                val code = intent.getStringExtra(ServiceController.EXTRA_LANGUAGE_CODE)
                if (code != null) {
                    ServiceController.updateLanguage(code)
                    serviceScope.launch {
                        translationEngine?.loadLanguage(code)
                    }
                    updateOverlayConfig()
                }
            }
        }

        return START_NOT_STICKY
    }

    private fun startForegroundNotification() {
        val stopIntent = Intent(this, SubtitleForegroundService::class.java).apply {
            action = ServiceController.ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val appIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val appPendingIntent = PendingIntent.getActivity(
            this, 0, appIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Video Alt Yazı Çalışıyor")
            .setContentText("Dahili ses yakalanıyor ve alt yazı oluşturuluyor")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(appPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Durdur", stopPendingIntent)
            .setOngoing(true)
            .setSilent(true) // DO NOT MAKE ANY SOUND
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun initMediaProjectionAndCapture(resultCode: Int, resultData: Intent, langCode: String) {
        val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val mp = mpManager.getMediaProjection(resultCode, resultData)
        if (mp == null) {
            Log.e(TAG, "MediaProjection was null")
            stopSelf()
            return
        }
        mediaProjection = mp

        mp.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                Log.i(TAG, "MediaProjection stopped by system")
                stopServiceInternal()
            }
        }, null)

        serviceScope.launch {
            val app = application as VideoSubtitleApplication
            val loaded = translationEngine?.loadLanguage(langCode) == true
            if (!loaded) {
                Log.e(TAG, "Failed to load language $langCode")
                ServiceController.updateStatus("Dil modeli yüklenemedi!")
                stopSelf()
                return@launch
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val started = audioCaptureManager?.startCapture(mp) == true
                if (started) {
                    ServiceController.updateRunning(true)
                    // Show overlay if allowed
                    val overlaySettings = app.settingsRepository.preferences.value.overlaySettings
                    overlayManager?.showOverlay(
                        overlaySettings,
                        ServiceController.currentMode.value,
                        ServiceController.currentLanguageCode.value
                    )
                } else {
                    stopSelf()
                }
            }
        }
    }

    private fun observeAudioCapture() {
        val capture = audioCaptureManager ?: return
        peakCollectionJob = serviceScope.launch {
            capture.currentPeak.collect { peak ->
                ServiceController.updatePeak(peak)
            }
        }
        statusCollectionJob = serviceScope.launch {
            capture.statusMessage.collect { status ->
                ServiceController.updateStatus(status)
            }
        }
    }

    private fun observeSubtitles() {
        val engine = translationEngine ?: return
        subtitleCollectionJob = serviceScope.launch {
            engine.subtitleFlow.collect { subtitle ->
                // Update live overlay
                overlayManager?.updateSubtitle(subtitle)

                // Append to UI list
                val current = ServiceController.liveSubtitles.value.toMutableList()
                if (subtitle.isFinal) {
                    // Remove pending partial if any and add final
                    val lastIdx = current.indexOfLast { !it.isFinal }
                    if (lastIdx != -1) {
                        current[lastIdx] = subtitle
                    } else {
                        current.add(subtitle)
                    }
                    sessionLines.add(
                        Pair(subtitle.timestampMs, Pair(subtitle.originalText, subtitle.turkishText))
                    )
                } else {
                    // Update or add partial
                    val lastIdx = current.indexOfLast { !it.isFinal }
                    if (lastIdx != -1) {
                        current[lastIdx] = subtitle
                    } else {
                        current.add(subtitle)
                    }
                }
                ServiceController.updateSubtitles(current)
            }
        }
    }

    private fun updateOverlayConfig() {
        val app = application as VideoSubtitleApplication
        val settings = app.settingsRepository.preferences.value.overlaySettings
        overlayManager?.updateSettings(
            settings,
            ServiceController.currentMode.value,
            ServiceController.currentLanguageCode.value
        )
    }

    private fun stopServiceInternal() {
        serviceScope.launch {
            saveSessionToDatabase()
            cleanup()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private suspend fun saveSessionToDatabase() {
        if (sessionLines.isNotEmpty()) {
            val app = application as VideoSubtitleApplication
            val langCode = ServiceController.currentLanguageCode.value
            val pack = LanguagePacksCatalog.DEFAULT_PACKS.find { it.code == langCode }
            val langName = pack?.name ?: langCode.uppercase()
            val dateStr = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(sessionStartTime))
            val title = "$langName Transkript - $dateStr"

            try {
                app.transcriptRepository.saveSession(
                    title = title,
                    languageCode = langCode,
                    languageName = langName,
                    lines = sessionLines
                )
                Log.i(TAG, "Session saved with ${sessionLines.size} lines")
            } catch (e: Exception) {
                Log.e(TAG, "Error saving transcript session", e)
            }
        }
    }

    private fun cleanup() {
        ServiceController.updateRunning(false)
        ServiceController.updateStatus("Durduruldu")
        ServiceController.updatePeak(0f)

        peakCollectionJob?.cancel()
        statusCollectionJob?.cancel()
        subtitleCollectionJob?.cancel()

        audioCaptureManager?.stopCapture()
        translationEngine?.release()
        overlayManager?.hideOverlay()

        try {
            mediaProjection?.stop()
        } catch (e: Exception) {
            // Ignored
        }
        mediaProjection = null
    }

    override fun onDestroy() {
        cleanup()
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        cleanup()
        super.onTaskRemoved(rootIntent)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Canlı Alt Yazı Servisi",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Video sesi yakalama ve çevrimdışı alt yazı bildirimi"
                setSound(null, null)
                enableVibration(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}
