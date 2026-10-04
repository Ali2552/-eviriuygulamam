package com.example.data.model

import com.google.mlkit.nl.translate.TranslateLanguage

enum class SubtitleMode(val title: String, val description: String) {
    TRANSCRIPT_ONLY("Sadece Yazıya Dök", "Videodaki dilde orijinal metin"),
    TRANSCRIPT_AND_TRANSLATE("Yazıya Dök + Türkçeye Çevir", "Türkçe çeviri ve altta orijinal"),
    TRANSLATE_ONLY("Sadece Türkçe Alt Yazı", "Yalnızca Türkçe çevirisi")
}

sealed class PackStatus {
    object NotDownloaded : PackStatus()
    data class Downloading(val progressPercent: Int) : PackStatus()
    object Ready : PackStatus()
    data class Error(val message: String) : PackStatus()
}

data class LanguagePack(
    val code: String,
    val name: String,
    val voskModelName: String,
    val voskSizeMB: Int,
    val mlKitLanguage: String,
    val isLargeModel: Boolean = false,
    val status: PackStatus = PackStatus.NotDownloaded
) {
    val downloadUrl: String
        get() = "https://alphacephei.com/vosk/models/$voskModelName.zip"

    val isTurkish: Boolean
        get() = code == "tr"
}

object LanguagePacksCatalog {
    val DEFAULT_PACKS = listOf(
        LanguagePack(
            code = "en",
            name = "İngilizce",
            voskModelName = "vosk-model-small-en-us-0.15",
            voskSizeMB = 40,
            mlKitLanguage = TranslateLanguage.ENGLISH
        ),
        LanguagePack(
            code = "tr",
            name = "Türkçe",
            voskModelName = "vosk-model-small-tr-0.3",
            voskSizeMB = 35,
            mlKitLanguage = TranslateLanguage.TURKISH
        ),
        LanguagePack(
            code = "ru",
            name = "Rusça",
            voskModelName = "vosk-model-small-ru-0.22",
            voskSizeMB = 45,
            mlKitLanguage = TranslateLanguage.RUSSIAN
        ),
        LanguagePack(
            code = "it",
            name = "İtalyanca",
            voskModelName = "vosk-model-small-it-0.22",
            voskSizeMB = 48,
            mlKitLanguage = TranslateLanguage.ITALIAN
        ),
        LanguagePack(
            code = "ko",
            name = "Korece",
            voskModelName = "vosk-model-small-ko-0.22",
            voskSizeMB = 82,
            mlKitLanguage = TranslateLanguage.KOREAN
        ),
        LanguagePack(
            code = "ja",
            name = "Japonca",
            voskModelName = "vosk-model-small-ja-0.22",
            voskSizeMB = 48,
            mlKitLanguage = TranslateLanguage.JAPANESE
        ),
        LanguagePack(
            code = "hi",
            name = "Hintçe",
            voskModelName = "vosk-model-small-hi-0.22",
            voskSizeMB = 42,
            mlKitLanguage = TranslateLanguage.HINDI
        ),
        LanguagePack(
            code = "ar",
            name = "Arapça",
            voskModelName = "vosk-model-ar-mgb2-0.4",
            voskSizeMB = 318,
            mlKitLanguage = TranslateLanguage.ARABIC,
            isLargeModel = true
        )
    )
}

data class LiveSubtitle(
    val id: String = java.util.UUID.randomUUID().toString(),
    val timestampMs: Long = System.currentTimeMillis(),
    val timeFormatted: String = "",
    val originalText: String = "",
    val turkishText: String = "",
    val isFinal: Boolean = false
)

data class OverlaySettings(
    val fontSizeSp: Float = 16f,
    val textColor: Long = 0xFFFFFFFF,
    val backgroundColor: Long = 0xCC111827, // Alpha approx 0.8
    val positionX: Int = 0,
    val positionY: Int = 200
)

data class AppPreferences(
    val selectedLanguageCode: String = "en",
    val subtitleMode: SubtitleMode = SubtitleMode.TRANSCRIPT_AND_TRANSLATE,
    val openTranslateInWebView: Boolean = false,
    val wifiOnlyDownload: Boolean = true,
    val overlaySettings: OverlaySettings = OverlaySettings()
)
