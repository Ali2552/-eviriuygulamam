package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.data.model.AppPreferences
import com.example.data.model.OverlaySettings
import com.example.data.model.SubtitleMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("video_subtitle_prefs", Context.MODE_PRIVATE)

    private val _preferences = MutableStateFlow(loadPreferences())
    val preferences: StateFlow<AppPreferences> = _preferences.asStateFlow()

    private fun loadPreferences(): AppPreferences {
        val lang = prefs.getString("selected_lang", "en") ?: "en"
        val modeStr = prefs.getString("subtitle_mode", SubtitleMode.TRANSCRIPT_AND_TRANSLATE.name)
        val mode = try {
            SubtitleMode.valueOf(modeStr ?: SubtitleMode.TRANSCRIPT_AND_TRANSLATE.name)
        } catch (e: Exception) {
            SubtitleMode.TRANSCRIPT_AND_TRANSLATE
        }
        val inWebView = prefs.getBoolean("open_translate_webview", false)
        val wifiOnly = prefs.getBoolean("wifi_only_download", true)
        val fontSize = prefs.getFloat("overlay_font_size", 16f)
        val textColor = prefs.getLong("overlay_text_color", 0xFFFFFFFF)
        val bgColor = prefs.getLong("overlay_bg_color", 0xCC111827)
        val posX = prefs.getInt("overlay_pos_x", 0)
        val posY = prefs.getInt("overlay_pos_y", 200)

        return AppPreferences(
            selectedLanguageCode = lang,
            subtitleMode = mode,
            openTranslateInWebView = inWebView,
            wifiOnlyDownload = wifiOnly,
            overlaySettings = OverlaySettings(
                fontSizeSp = fontSize,
                textColor = textColor,
                backgroundColor = bgColor,
                positionX = posX,
                positionY = posY
            )
        )
    }

    fun setSelectedLanguage(code: String) {
        prefs.edit().putString("selected_lang", code).apply()
        _preferences.update { it.copy(selectedLanguageCode = code) }
    }

    fun setSubtitleMode(mode: SubtitleMode) {
        prefs.edit().putString("subtitle_mode", mode.name).apply()
        _preferences.update { it.copy(subtitleMode = mode) }
    }

    fun setOpenTranslateInWebView(enabled: Boolean) {
        prefs.edit().putBoolean("open_translate_webview", enabled).apply()
        _preferences.update { it.copy(openTranslateInWebView = enabled) }
    }

    fun setWifiOnlyDownload(enabled: Boolean) {
        prefs.edit().putBoolean("wifi_only_download", enabled).apply()
        _preferences.update { it.copy(wifiOnlyDownload = enabled) }
    }

    fun updateOverlaySettings(settings: OverlaySettings) {
        prefs.edit()
            .putFloat("overlay_font_size", settings.fontSizeSp)
            .putLong("overlay_text_color", settings.textColor)
            .putLong("overlay_bg_color", settings.backgroundColor)
            .putInt("overlay_pos_x", settings.positionX)
            .putInt("overlay_pos_y", settings.positionY)
            .apply()
        _preferences.update { it.copy(overlaySettings = settings) }
    }
}
