package com.bykerimoff.player.utils

import android.content.Context
import android.graphics.Color
import android.content.SharedPreferences

object ThemeManager {
    private const val PREFS_NAME = "aurex_theme_prefs"
    private const val KEY_THEME_COLOR = "primary_theme_color"

    enum class AppTheme(val colorHex: String, val nameAz: String) {
        GOLD("#FFD700", "Premium Qızılı"),
        NEON_BLUE("#00F2FF", "Neon Göy"),
        RUBY_RED("#FF0040", "Yaqub Qırmızısı"),
        EMERALD_GREEN("#00FF85", "Zümrüd Yaşılı"),
        SILVER("#E0E0E0", "Gümüşü");

        fun getColorInt(): Int = Color.parseColor(colorHex)
    }

    fun getThemeColor(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val colorHex = prefs.getString(KEY_THEME_COLOR, AppTheme.GOLD.colorHex)
        return Color.parseColor(colorHex)
    }

    fun setTheme(context: Context, theme: AppTheme) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_THEME_COLOR, theme.colorHex).apply()
    }

    fun getCurrentTheme(context: Context): AppTheme {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val colorHex = prefs.getString(KEY_THEME_COLOR, AppTheme.GOLD.colorHex)
        return AppTheme.values().find { it.colorHex == colorHex } ?: AppTheme.GOLD
    }
}
