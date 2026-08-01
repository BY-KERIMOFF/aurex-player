package com.bykerimoff.player.utils

import android.app.Activity
import android.content.Context
import com.bykerimoff.player.R

object ThemeManager {
    private const val PREF_NAME = "aurex_theme_prefs"
    private const val KEY_THEME = "current_app_theme"

    const val THEME_GOLD = "gold"
    const val THEME_BLUE = "blue"
    const val THEME_PURPLE = "purple"
    const val THEME_PURE_BLACK = "black"

    fun applyTheme(activity: Activity) {
        val theme = getSavedTheme(activity)
        when (theme) {
            THEME_BLUE -> activity.setTheme(R.style.Theme_Aurex_Blue)
            THEME_PURPLE -> activity.setTheme(R.style.Theme_Aurex_Purple)
            THEME_PURE_BLACK -> activity.setTheme(R.style.Theme_Aurex_PureBlack)
            else -> activity.setTheme(R.style.Theme_Aurex_Gold)
        }
    }

    fun setAppTheme(context: Context, theme: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_THEME, theme).apply()
    }

    fun getSavedTheme(context: Context): String {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_THEME, THEME_GOLD) ?: THEME_GOLD
    }
}
