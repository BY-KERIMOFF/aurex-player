package com.bykerimoff.player.utils

import android.content.Context
import com.bykerimoff.player.models.Channel
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object RecentChannelsManager {
    private const val PREFS_NAME = "recent_channels_prefs"
    private const val KEY_RECENT = "recent_list"
    private const val MAX_RECENT = 15

    fun addChannel(context: Context, channel: Channel) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val recentList = getRecentChannels(context).toMutableList()

        // Əgər kanal artıq siyahıda varsa, köhnəni silək (yenisini başa qoymaq üçün)
        recentList.removeAll { it.id == channel.id }
        
        // Siyahının başına əlavə et
        recentList.add(0, channel)

        // Limiti qoru
        if (recentList.size > MAX_RECENT) {
            recentList.removeAt(recentList.size - 1)
        }

        val json = Gson().toJson(recentList)
        prefs.edit().putString(KEY_RECENT, json).apply()
    }

    fun getRecentChannels(context: Context): List<Channel> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_RECENT, null) ?: return emptyList()
        val type = object : TypeToken<List<Channel>>() {}.type
        return try {
            Gson().fromJson(json, type)
        } catch (e: Exception) {
            emptyList()
        }
    }
}
