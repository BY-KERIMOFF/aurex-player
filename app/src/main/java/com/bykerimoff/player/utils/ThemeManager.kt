package com.bykerimoff.player.utils

import android.content.Context
import android.graphics.Color
import android.content.SharedPreferences

object ThemeManager {
    private const val PREFS_NAME = "aurex_theme_prefs"
    private const val KEY_THEME_COLOR = "primary_theme_color"

    enum class AppTheme(val colorHex: String, val nameAz: String) {
        NEON_BLUE("#00E5FF", "Neon Mavi"),
        GOLD("#FFD700", "Premium Qızılı"),
        RUBY_RED("#FF0040", "Yaqub Qırmızısı"),
        EMERALD_GREEN("#00FF85", "Zümrüd Yaşılı"),
        PURPLE("#A020F0", "Bənövşəyi"),
        ORANGE("#FF8C00", "Narıncı"),
        PINK("#FF69B4", "Çəhrayı"),
        LIME("#32CD32", "Laym"),
        SILVER("#C0C0C0", "Gümüşü"),
        DEEP_BLUE("#0000FF", "Tünd Mavi"),
        YELLOW("#FFFF00", "Sarı"),
        WHITE("#FFFFFF", "Ağ");

        fun getColorInt(): Int = Color.parseColor(colorHex)
    }

    fun getThemeColor(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val colorHex = prefs.getString(KEY_THEME_COLOR, AppTheme.EMERALD_GREEN.colorHex)
        return Color.parseColor(colorHex)
    }

    fun setTheme(context: Context, theme: AppTheme) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_THEME_COLOR, theme.colorHex).apply()
    }

    fun getCurrentTheme(context: Context): AppTheme {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val colorHex = prefs.getString(KEY_THEME_COLOR, AppTheme.NEON_BLUE.colorHex)
        return AppTheme.values().find { it.colorHex == colorHex } ?: AppTheme.NEON_BLUE
    }
}
