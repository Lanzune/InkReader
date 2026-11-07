package com.inkreader.app

import android.content.Context
import com.inkreader.reader.engine.ReaderStyle

enum class ReaderThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

data class ReaderSettings(
    val themeMode: ReaderThemeMode = ReaderThemeMode.SYSTEM,
    val fontSizeSp: Float = 20f,
    val lineSpacing: Float = 1.5f,
    val horizontalPaddingDp: Int = 24,
    val brightness: Float? = null,
    val pageTurnAnimation: Boolean = true,
) {
    fun toStyle(): ReaderStyle = ReaderStyle(
        fontSizeSp = fontSizeSp,
        lineSpacingMultiplier = lineSpacing,
        horizontalPaddingDp = horizontalPaddingDp,
    )
}

class ReaderSettingsStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("inkreader.settings", Context.MODE_PRIVATE)

    fun load(): ReaderSettings {
        val mode = prefs.getString(KEY_THEME_MODE, null)
            ?.let { runCatching { ReaderThemeMode.valueOf(it) }.getOrNull() }
            ?: ReaderThemeMode.SYSTEM
        val brightness = if (prefs.contains(KEY_BRIGHTNESS)) {
            prefs.getFloat(KEY_BRIGHTNESS, 1f).coerceIn(0.01f, 1f)
        } else {
            null
        }
        return ReaderSettings(
            themeMode = mode,
            fontSizeSp = prefs.getFloat(KEY_FONT_SIZE, 20f).coerceIn(14f, 30f),
            lineSpacing = prefs.getFloat(KEY_LINE_SPACING, 1.5f).coerceIn(1.2f, 2.0f),
            horizontalPaddingDp = prefs.getInt(KEY_PADDING, 24).coerceIn(8, 40),
            brightness = brightness,
            pageTurnAnimation = prefs.getBoolean(KEY_PAGE_ANIMATION, true),
        )
    }

    fun save(settings: ReaderSettings) {
        prefs.edit()
            .putString(KEY_THEME_MODE, settings.themeMode.name)
            .putFloat(KEY_FONT_SIZE, settings.fontSizeSp)
            .putFloat(KEY_LINE_SPACING, settings.lineSpacing)
            .putInt(KEY_PADDING, settings.horizontalPaddingDp)
            .putBoolean(KEY_PAGE_ANIMATION, settings.pageTurnAnimation)
            .apply {
                if (settings.brightness == null) {
                    remove(KEY_BRIGHTNESS)
                } else {
                    putFloat(KEY_BRIGHTNESS, settings.brightness)
                }
            }
            .apply()
    }

    private companion object {
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_FONT_SIZE = "font_size"
        const val KEY_LINE_SPACING = "line_spacing"
        const val KEY_PADDING = "padding"
        const val KEY_BRIGHTNESS = "brightness"
        const val KEY_PAGE_ANIMATION = "page_animation"
    }
}
