package com.example.service

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

private const val TAG = "AudioCaptureManager"

sealed class AudioCaptureState {
    object Idle : AudioCaptureState()
    object Initializing : AudioCaptureState()
    object Capturing : AudioCaptureState()
    data class Error(val message: String) : AudioCaptureState()
    object WaitingForAudio : AudioCaptureState()
    object AppBlockingAudio : AudioCaptureState() // Music active & 10s peak 0
}

class AudioCaptureManager(
    private val context: Context,
    private val onChunkReady: (ByteArray, Int) -> Unit
) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var audioRecord: AudioRecord? = null
    private var captureJob: Job? = null
    private var healthCheckJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    private val _captureState = MutableStateFlow<AudioCaptureState>(AudioCaptureState.Idle)
    val captureState: StateFlow<AudioCaptureState> = _captureState.asStateFlow()

    private val _currentPeak = MutableStateFlow(0f)
    val currentPeak: StateFlow<Float> = _currentPeak.asStateFlow()

    private val _statusMessage = MutableStateFlow("Bekleniyor...")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private var startTimeMs = 0L
    private var consecutiveZeroPeakSeconds = 0
    private var consecutiveEmptyVoskSeconds = 0

    @RequiresApi(Build.VERSION_CODES.Q)
    @SuppressLint("MissingPermission")
    fun startCapture(mediaProjection: MediaProjection): Boolean {
        stopCapture()

        try {
            _captureState.value = AudioCaptureState.Initializing
            _statusMessage.value = "Ses yakalama yapılandırılıyor..."

            val config = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                .build()

            val sampleRateIn = 44100
            val channelConfigIn = AudioFormat.CHANNEL_IN_STEREO
            val encoding = AudioFormat.ENCODING_PCM_16BIT

            val minBufferSize = AudioRecord.getMinBufferSize(sampleRateIn, channelConfigIn, encoding)
            if (minBufferSize <= 0) {
                _captureState.value = AudioCaptureState.Error("Ses arabelleği hesaplanamadı")
                return false
            }

            val bufferSize = max(minBufferSize * 2, 8192)

            val audioFormat = AudioFormat.Builder()
                .setEncoding(encoding)
                .setSampleRate(sampleRateIn)
                .setChannelMask(channelConfigIn)
                .build()

            audioRecord = AudioRecord.Builder()
                .setAudioPlaybackCaptureConfig(config)
                .setAudioFormat(audioFormat)
                .setBufferSizeInBytes(bufferSize)
                .build()

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord state != STATE_INITIALIZED")
                _captureState.value = AudioCaptureState.Error("Ses yakalama başlatılamadı (AudioRecord başlatılamadı)")
                _statusMessage.value = "Ses yakalama başlatılamadı"
                return false
            }

            audioRecord?.startRecording()
            startTimeMs = System.currentTimeMillis()
            consecutiveZeroPeakSeconds = 0
            consecutiveEmptyVoskSeconds = 0
            _captureState.value = AudioCaptureState.WaitingForAudio
            _statusMessage.value = "Ses bekleniyor..."

            startReadingLoop(sampleRateIn)
            startHealthCheckLoop()

            Log.i(TAG, "AudioPlaybackCapture started successfully. Buffer: $bufferSize")
            return true
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to start audio playback capture", e)
            _captureState.value = AudioCaptureState.Error("Hata: ${e.localizedMessage}")
            _statusMessage.value = "Hata: ${e.localizedMessage}"
            return false
        }
    }

    private fun startReadingLoop(sampleRateIn: Int) {
        captureJob = scope.launch(Dispatchers.IO) {
            val record = audioRecord ?: return@launch
            // Read in 250ms chunks of 44.1kHz Stereo (16-bit: 4 bytes per frame)
            // 44100 * 0.25 = 11025 frames * 4 = 44100 bytes
            val inputChunkBytes = 44100 / 4 // ~250ms chunk
            val inputBuffer = ByteArray(inputChunkBytes)

            while (isActive && record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                val bytesRead = record.read(inputBuffer, 0, inputBuffer.size)
                if (bytesRead > 0) {
                    processAudioChunk(inputBuffer, bytesRead, sampleRateIn)
                }
            }
        }
    }

    private fun processAudioChunk(inputBuffer: ByteArray, bytesRead: Int, sampleRateIn: Int) {
        val numShorts = bytesRead / 2
        if (numShorts < 2) return

        // 1. Convert to shorts & downmix Stereo to Mono
        val numStereoFrames = numShorts / 2
        val monoShorts = ShortArray(numStereoFrames)

        var peakAmplitude = 0
        var sumSquares = 0.0

        for (i in 0 until numStereoFrames) {
            val byteIndexLeft = i * 4
            val left = (inputBuffer[byteIndexLeft].toInt() and 0xFF) or (inputBuffer[byteIndexLeft + 1].toInt() shl 8)
            val right = (inputBuffer[byteIndexLeft + 2].toInt() and 0xFF) or (inputBuffer[byteIndexLeft + 3].toInt() shl 8)

            val leftShort = left.toShort()
            val rightShort = right.toShort()
            val mono = ((leftShort.toInt() + rightShort.toInt()) / 2).coerceIn(-32768, 32767).toShort()
            monoShorts[i] = mono

            val absVal = abs(mono.toInt())
            if (absVal > peakAmplitude) {
                peakAmplitude = absVal
            }
            sumSquares += (mono.toDouble() * mono.toDouble())
        }

        // Calculate normalized peak for UI meter (0.0 to 1.0)
        val normalizedPeak = (peakAmplitude / 32768f).coerceIn(0f, 1f)
        _currentPeak.value = normalizedPeak

        val rms = sqrt(sumSquares / numStereoFrames)

        // 2. Simple VAD (Voice Activity Detection): Skip if energy is pure silence
        // Threshold: Peak > 150 or RMS > 60
        val isVoiceActive = peakAmplitude > 150 || rms > 60

        // 3. Resample Mono from 44100 Hz to 16000 Hz
        val targetSampleRate = 16000
        val resampledShorts = resampleLinear(monoShorts, sampleRateIn, targetSampleRate)

        // Convert resampled shorts to ByteArray for Vosk (Little Endian)
        val outBytes = ByteArray(resampledShorts.size * 2)
        for (i in resampledShorts.indices) {
            val sample = resampledShorts[i].toInt()
            outBytes[i * 2] = (sample and 0xFF).toByte()
            outBytes[i * 2 + 1] = ((sample shr 8) and 0xFF).toByte()
        }

        if (isVoiceActive) {
            onChunkReady(outBytes, outBytes.size)
        }
    }

    private fun resampleLinear(input: ShortArray, fromRate: Int, toRate: Int): ShortArray {
        if (fromRate == toRate) return input
        val ratio = fromRate.toDouble() / toRate.toDouble()
        val outLength = (input.size / ratio).toInt()
        val output = ShortArray(outLength)

        for (i in 0 until outLength) {
            val srcPos = i * ratio
            val index0 = srcPos.toInt()
            val index1 = (index0 + 1).coerceAtMost(input.size - 1)
            val frac = (srcPos - index0).toFloat()

            val sample0 = input[index0].toFloat()
            val sample1 = input[index1].toFloat()
            val interpolated = sample0 + frac * (sample1 - sample0)
            output[i] = interpolated.toInt().coerceIn(-32768, 32767).toShort()
        }
        return output
    }

    private fun startHealthCheckLoop() {
        healthCheckJob = scope.launch(Dispatchers.Default) {
            while (isActive) {
                delay(1000)
                val durationSec = (System.currentTimeMillis() - startTimeMs) / 1000
                val peak = _currentPeak.value

                if (peak <= 0.005f) {
                    consecutiveZeroPeakSeconds++
                } else {
                    consecutiveZeroPeakSeconds = 0
                }

                val musicActive = audioManager.isMusicActive

                if (durationSec > 15 && musicActive && consecutiveZeroPeakSeconds >= 10) {
                    _captureState.value = AudioCaptureState.AppBlockingAudio
                    _statusMessage.value = "Bu uygulama dahili ses yakalamaya izin vermiyor (DRM/Gizlilik korumalı)"
                } else if (consecutiveZeroPeakSeconds >= 2) {
                    if (_captureState.value !is AudioCaptureState.AppBlockingAudio) {
                        _captureState.value = AudioCaptureState.WaitingForAudio
                        _statusMessage.value = "Ses bekleniyor... (Videoyu oynatın)"
                    }
                } else {
                    _captureState.value = AudioCaptureState.Capturing
                    _statusMessage.value = "Ses yakalanıyor"
                }

                // Empty Vosk notice check
                if (consecutiveEmptyVoskSeconds >= 10) {
                    _statusMessage.value = "Seçili dil videoyla uyuşmuyor olabilir"
                }
            }
        }
    }

    fun notifyVoskEmptyResult(isEmpty: Boolean) {
        if (isEmpty) {
            consecutiveEmptyVoskSeconds++
        } else {
            consecutiveEmptyVoskSeconds = 0
        }
    }

    fun stopCapture() {
        captureJob?.cancel()
        captureJob = null
        healthCheckJob?.cancel()
        healthCheckJob = null

        try {
            audioRecord?.stop()
        } catch (e: Exception) {
            // Ignored
        }
        try {
            audioRecord?.release()
        } catch (e: Exception) {
            // Ignored
        }
        audioRecord = null
        _captureState.value = AudioCaptureState.Idle
        _currentPeak.value = 0f
        _statusMessage.value = "Durduruldu"
    }
}
