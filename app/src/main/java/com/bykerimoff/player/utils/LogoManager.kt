package com.bykerimoff.player.utils

import android.content.Context
import android.util.Log
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.ConcurrentHashMap

object LogoManager {
    private const val TAG = "LogoManager"
    private const val GITHUB_LOGOS_BASE = "https://raw.githubusercontent.com/BY-KERIMOFF/aurex-player/main/logos/"
    
    private val logoCache = ConcurrentHashMap<String, String>()
    @Volatile private var isLoaded = false

    // Silinməli olan lüzumsuz IPTV əlavələri
    private val SUFFIX_REGEX = Regex("\\b(hd|sd|fhd|uhd|4k|5k|8k|fullhd|yedek|backup|rezerv|reserve|test|back|plus|\\+\\d|\\(\\d+\\)|1080p|720p|hevc|h265|60fps|50fps)\\b", RegexOption.IGNORE_CASE)

    fun loadLogoDatabase(context: Context) {
        if (isLoaded) return
        isLoaded = true
        Log.d(TAG, "Logo Manager initialized.")
    }

    /**
     * Kanalın adını ən təmiz formaya salır: "AzTV FHD" -> "aztv"
     */
    private fun getCleanName(name: String?): String {
        if (name == null) return ""
        return name.lowercase()
            .replace(SUFFIX_REGEX, "") // "HD", "SD" silinir
            .replace(" ", "") // Bütün boşluqlar silinir
            .replace(Regex("[^a-z0-9а-я]"), "") // Simvollar silinir
            .trim()
    }

    @JvmStatic
    fun resolveLogo(existingLogo: String?, channelId: String?, channelName: String?): String? {
        if (channelName.isNullOrEmpty()) return existingLogo

        val clean = getCleanName(channelName)
        if (clean.isEmpty()) return existingLogo

        // Birbaşa GitHub-dakı təmizlənmiş PNG faylına müraciət edirik
        // Məsələn: "AzTV FHD" üçün "aztv.png" linkini qaytarır
        return "$GITHUB_LOGOS_BASE$clean.png"
    }
}
