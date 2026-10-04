package com.example.service

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.example.data.model.LiveSubtitle
import com.example.data.model.OverlaySettings
import com.example.data.model.SubtitleMode
import kotlin.math.roundToInt

class SubtitleOverlayManager(
    private val context: Context,
    private val onSaveNote: (String) -> Unit,
    private val onToggleMode: () -> Unit,
    private val onToggleLanguage: () -> Unit,
    private val onCloseClicked: () -> Unit
) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    private var subtitlePrimaryText: TextView? = null
    private var subtitleSecondaryText: TextView? = null
    private var modeBadgeText: TextView? = null
    private var languageBadgeText: TextView? = null

    private var currentSettings = OverlaySettings()
    private var currentMode = SubtitleMode.TRANSCRIPT_AND_TRANSLATE
    private var currentLangCode = "en"
    private var currentSubtitle: LiveSubtitle? = null

    fun isShowing(): Boolean = overlayView != null

    fun canDrawOverlays(): Boolean {
        return Settings.canDrawOverlays(context)
    }

    @SuppressLint("ClickableViewAccessibility")
    fun showOverlay(settings: OverlaySettings, mode: SubtitleMode, langCode: String) {
        if (!canDrawOverlays()) return
        if (overlayView != null) {
            updateSettings(settings, mode, langCode)
            return
        }

        currentSettings = settings
        currentMode = mode
        currentLangCode = langCode

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = settings.positionX
            y = settings.positionY
        }
        layoutParams = params

        val container = createOverlayLayout()
        overlayView = container

        // Dragging listener
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        container.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    try {
                        windowManager.updateViewLayout(container, params)
                    } catch (e: Exception) {
                        // Ignored
                    }
                    true
                }
                else -> false
            }
        }

        try {
            windowManager.addView(container, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createOverlayLayout(): View {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(10))
            background = createRoundedBackground(currentSettings.backgroundColor)
        }

        // Top bar: Controls (drag handle, badges, actions)
        val topBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(4)
            }
        }

        // Drag indicator / title
        val titleText = TextView(context).apply {
            text = "CC ✣"
            setTextColor(Color.parseColor("#94A3B8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        topBar.addView(titleText)

        // Language Badge button
        languageBadgeText = TextView(context).apply {
            text = currentLangCode.uppercase()
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            setPadding(dpToPx(6), dpToPx(2), dpToPx(6), dpToPx(2))
            background = createBadgeBackground("#3B82F6")
            setOnClickListener { onToggleLanguage() }
        }
        topBar.addView(languageBadgeText)

        // Spacer
        topBar.addView(View(context).apply {
            layoutParams = LinearLayout.LayoutParams(dpToPx(6), 1)
        })

        // Mode Badge button
        modeBadgeText = TextView(context).apply {
            text = getModeShortLabel(currentMode)
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            setPadding(dpToPx(6), dpToPx(2), dpToPx(6), dpToPx(2))
            background = createBadgeBackground("#10B981")
            setOnClickListener { onToggleMode() }
        }
        topBar.addView(modeBadgeText)

        // Spacer
        topBar.addView(View(context).apply {
            layoutParams = LinearLayout.LayoutParams(dpToPx(6), 1)
        })

        // Save note icon button
        val saveNoteBtn = ImageView(context).apply {
            setImageResource(android.R.drawable.ic_menu_save)
            setColorFilter(Color.WHITE)
            setPadding(dpToPx(4), dpToPx(2), dpToPx(4), dpToPx(2))
            setOnClickListener {
                val current = currentSubtitle
                if (current != null) {
                    val noteContent = if (current.turkishText.isNotBlank()) {
                        "${current.turkishText}\n(Orijinal: ${current.originalText})"
                    } else {
                        current.originalText
                    }
                    onSaveNote(noteContent)
                }
            }
        }
        topBar.addView(saveNoteBtn)

        // Close button
        val closeBtn = ImageView(context).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setColorFilter(Color.parseColor("#EF4444"))
            setPadding(dpToPx(4), dpToPx(2), dpToPx(4), dpToPx(2))
            setOnClickListener { onCloseClicked() }
        }
        topBar.addView(closeBtn)

        root.addView(topBar)

        // Primary Subtitle (Large)
        subtitlePrimaryText = TextView(context).apply {
            text = "Video sesi bekleniyor..."
            setTextColor(currentSettings.textColor.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, currentSettings.fontSizeSp)
            maxLines = 2
            setLineSpacing(0f, 1.15f)
        }
        root.addView(subtitlePrimaryText)

        // Secondary Subtitle (Small faded original text for bilingual mode)
        subtitleSecondaryText = TextView(context).apply {
            text = ""
            setTextColor(Color.parseColor("#94A3B8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, (currentSettings.fontSizeSp * 0.82f))
            maxLines = 1
            visibility = View.GONE
        }
        root.addView(subtitleSecondaryText)

        return root
    }

    fun updateSubtitle(subtitle: LiveSubtitle) {
        currentSubtitle = subtitle
        val primary = subtitlePrimaryText ?: return
        val secondary = subtitleSecondaryText ?: return

        when (currentMode) {
            SubtitleMode.TRANSCRIPT_ONLY -> {
                primary.text = subtitle.originalText.ifBlank { "..." }
                primary.setTextColor(if (subtitle.isFinal) currentSettings.textColor.toInt() else Color.parseColor("#94A3B8"))
                secondary.visibility = View.GONE
            }
            SubtitleMode.TRANSCRIPT_AND_TRANSLATE -> {
                if (currentLangCode == "tr") {
                    primary.text = subtitle.originalText.ifBlank { "..." }
                    primary.setTextColor(if (subtitle.isFinal) currentSettings.textColor.toInt() else Color.parseColor("#94A3B8"))
                    secondary.visibility = View.GONE
                } else {
                    if (subtitle.turkishText.isNotBlank()) {
                        primary.text = subtitle.turkishText
                        primary.setTextColor(currentSettings.textColor.toInt())
                        secondary.text = subtitle.originalText
                        secondary.visibility = View.VISIBLE
                    } else {
                        primary.text = subtitle.originalText.ifBlank { "..." }
                        primary.setTextColor(if (subtitle.isFinal) currentSettings.textColor.toInt() else Color.parseColor("#94A3B8"))
                        secondary.visibility = View.GONE
                    }
                }
            }
            SubtitleMode.TRANSLATE_ONLY -> {
                if (currentLangCode == "tr") {
                    primary.text = subtitle.originalText.ifBlank { "..." }
                } else {
                    primary.text = subtitle.turkishText.ifBlank {
                        if (subtitle.isFinal) subtitle.originalText else "..."
                    }
                }
                primary.setTextColor(if (subtitle.isFinal) currentSettings.textColor.toInt() else Color.parseColor("#94A3B8"))
                secondary.visibility = View.GONE
            }
        }
    }

    fun updateSettings(settings: OverlaySettings, mode: SubtitleMode, langCode: String) {
        currentSettings = settings
        currentMode = mode
        currentLangCode = langCode

        overlayView?.background = createRoundedBackground(settings.backgroundColor)
        subtitlePrimaryText?.apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, settings.fontSizeSp)
            setTextColor(settings.textColor.toInt())
        }
        subtitleSecondaryText?.apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, settings.fontSizeSp * 0.82f)
        }
        modeBadgeText?.text = getModeShortLabel(mode)
        languageBadgeText?.text = langCode.uppercase()

        currentSubtitle?.let { updateSubtitle(it) }
    }

    fun hideOverlay() {
        if (overlayView != null) {
            try {
                windowManager.removeView(overlayView)
            } catch (e: Exception) {
                // Ignored
            }
            overlayView = null
            subtitlePrimaryText = null
            subtitleSecondaryText = null
            modeBadgeText = null
            languageBadgeText = null
        }
    }

    private fun getModeShortLabel(mode: SubtitleMode): String = when (mode) {
        SubtitleMode.TRANSCRIPT_ONLY -> "Orijinal"
        SubtitleMode.TRANSCRIPT_AND_TRANSLATE -> "TR + Orj"
        SubtitleMode.TRANSLATE_ONLY -> "Yalnız TR"
    }

    private fun createRoundedBackground(color: Long): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(14).toFloat()
            // Cap alpha to max 80% (0xCC) as requested in requirements
            var colorInt = color.toInt()
            val alpha = (colorInt ushr 24) and 0xFF
            if (alpha > 204) {
                colorInt = (colorInt and 0x00FFFFFF) or (204 shl 24)
            }
            setColor(colorInt)
            setStroke(dpToPx(1), Color.parseColor("#475569"))
        }
    }

    private fun createBadgeBackground(hexColor: String): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(6).toFloat()
            setColor(Color.parseColor(hexColor))
        }
    }

    private fun dpToPx(dp: Int): Int {
        val density = context.resources.displayMetrics.density
        return (dp * density).roundToInt()
    }
}
