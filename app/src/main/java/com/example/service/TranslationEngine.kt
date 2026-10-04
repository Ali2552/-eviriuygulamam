package com.example.service

import android.content.Context
import android.util.Log
import android.util.LruCache
import com.example.data.model.LanguagePack
import com.example.data.model.LanguagePacksCatalog
import com.example.data.model.LiveSubtitle
import com.example.data.model.SubtitleMode
import com.example.data.repository.LanguagePackManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File

private const val TAG = "TranslationEngine"

class TranslationEngine(
    private val context: Context,
    private val languagePackManager: LanguagePackManager,
    private val onEmptyVoskResult: (Boolean) -> Unit
) {

    private val scope = CoroutineScope(Dispatchers.Default)
    private val mutex = Mutex()

    private var currentModel: Model? = null
    private var currentRecognizer: Recognizer? = null
    private var currentTranslator: Translator? = null
    private var currentLanguageCode: String? = null

    // Cache translations for instant repeat lookups
    private val translationCache = LruCache<String, String>(250)

    private val _subtitleFlow = MutableSharedFlow<LiveSubtitle>(extraBufferCapacity = 64)
    val subtitleFlow: SharedFlow<LiveSubtitle> = _subtitleFlow.asSharedFlow()

    private var lastSpokenTimeMs = 0L
    private var lastEmittedFinalText = ""

    suspend fun loadLanguage(languageCode: String): Boolean = mutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                if (currentLanguageCode == languageCode && currentRecognizer != null) {
                    return@withContext true
                }

                closeResources()

                val pack = LanguagePacksCatalog.DEFAULT_PACKS.find { it.code == languageCode }
                    ?: return@withContext false

                val modelDir = languagePackManager.getModelDirectory(pack.voskModelName)
                if (!modelDir.exists() || modelDir.list().isNullOrEmpty()) {
                    Log.e(TAG, "Vosk model directory missing or empty: ${modelDir.absolutePath}")
                    return@withContext false
                }

                Log.i(TAG, "Loading Vosk model for ${pack.name} from ${modelDir.absolutePath}")
                val model = Model(modelDir.absolutePath)
                val recognizer = Recognizer(model, 16000.0f)

                var translator: Translator? = null
                if (!pack.isTurkish) {
                    val options = TranslatorOptions.Builder()
                        .setSourceLanguage(pack.mlKitLanguage)
                        .setTargetLanguage(TranslateLanguage.TURKISH)
                        .build()
                    translator = Translation.getClient(options)
                }

                currentModel = model
                currentRecognizer = recognizer
                currentTranslator = translator
                currentLanguageCode = languageCode
                lastEmittedFinalText = ""

                Log.i(TAG, "Language $languageCode loaded successfully")
                true
            } catch (e: Throwable) {
                Log.e(TAG, "Error loading language $languageCode", e)
                closeResources()
                false
            }
        }
    }

    fun processAudioChunk(
        audioData: ByteArray,
        length: Int,
        mode: SubtitleMode
    ) {
        val recognizer = currentRecognizer ?: return

        try {
            val isFinal = recognizer.acceptWaveForm(audioData, length)
            val now = System.currentTimeMillis()

            if (isFinal) {
                val jsonStr = recognizer.result
                handleFinalResult(jsonStr, mode, now)
            } else {
                val jsonStr = recognizer.partialResult
                handlePartialResult(jsonStr, mode, now)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in recognizer processing", e)
        }
    }

    private fun handlePartialResult(jsonStr: String, mode: SubtitleMode, now: Long) {
        try {
            val jsonObj = JSONObject(jsonStr)
            val partial = jsonObj.optString("partial", "").trim()

            if (partial.isEmpty()) {
                // If silent for > 1500 ms and we had speech, finalize what we have
                if (lastSpokenTimeMs > 0 && (now - lastSpokenTimeMs) > 1500) {
                    // Check if recognizer has pending final result
                    currentRecognizer?.let { rec ->
                        val finalJson = rec.finalResult
                        handleFinalResult(finalJson, mode, now)
                    }
                    lastSpokenTimeMs = 0L
                }
                return
            }

            lastSpokenTimeMs = now
            onEmptyVoskResult(false)

            val formattedText = formatSentence(partial, isFinal = false)
            val subtitle = LiveSubtitle(
                timestampMs = now,
                timeFormatted = formatTime(now),
                originalText = formattedText,
                turkishText = "",
                isFinal = false
            )
            _subtitleFlow.tryEmit(subtitle)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing partial result", e)
        }
    }

    private fun handleFinalResult(jsonStr: String, mode: SubtitleMode, now: Long) {
        try {
            val jsonObj = JSONObject(jsonStr)
            val text = jsonObj.optString("text", "").trim()

            if (text.isEmpty()) {
                onEmptyVoskResult(true)
                return
            }

            onEmptyVoskResult(false)

            // Prevent immediate duplicates
            if (text.equals(lastEmittedFinalText, ignoreCase = true)) {
                return
            }
            lastEmittedFinalText = text

            val formattedOriginal = formatSentence(text, isFinal = true)

            scope.launch {
                var turkish = ""
                val isTurkishSource = currentLanguageCode == "tr"

                if (mode != SubtitleMode.TRANSCRIPT_ONLY && !isTurkishSource) {
                    turkish = translateText(formattedOriginal)
                }

                val subtitle = LiveSubtitle(
                    timestampMs = now,
                    timeFormatted = formatTime(now),
                    originalText = formattedOriginal,
                    turkishText = turkish,
                    isFinal = true
                )
                _subtitleFlow.emit(subtitle)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing final result", e)
        }
    }

    private suspend fun translateText(text: String): String {
        val cached = translationCache.get(text)
        if (cached != null) return cached

        val translator = currentTranslator ?: return ""
        return try {
            val result = translator.translate(text).await()
            val capitalized = result.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            translationCache.put(text, capitalized)
            capitalized
        } catch (e: Exception) {
            Log.w(TAG, "ML Kit translation failed or model not downloaded yet: ${e.localizedMessage}")
            ""
        }
    }

    private fun formatSentence(raw: String, isFinal: Boolean): String {
        if (raw.isBlank()) return ""
        val capitalized = raw.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        return if (isFinal && !capitalized.endsWith(".") && !capitalized.endsWith("?") && !capitalized.endsWith("!")) {
            "$capitalized."
        } else {
            capitalized
        }
    }

    private fun formatTime(millis: Long): String {
        val seconds = (millis / 1000) % 60
        val minutes = (millis / (1000 * 60)) % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    private fun closeResources() {
        try {
            currentRecognizer?.close()
        } catch (e: Exception) {
            // Ignored
        }
        currentRecognizer = null

        try {
            currentModel?.close()
        } catch (e: Exception) {
            // Ignored
        }
        currentModel = null

        try {
            currentTranslator?.close()
        } catch (e: Exception) {
            // Ignored
        }
        currentTranslator = null
        currentLanguageCode = null
    }

    fun release() {
        scope.launch {
            mutex.withLock {
                closeResources()
            }
        }
    }
}
