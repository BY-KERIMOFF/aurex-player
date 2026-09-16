package com.bykerimoff.player.utils

import android.content.Context
import android.util.Log
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.io.BufferedReader
import java.util.concurrent.ConcurrentHashMap

object LogoManager {
    private const val TAG = "LogoManager"
    private const val LOGO_API_URL = "https://iptv-org.github.io/api/logos.json"
    
    // GitHub-dakı PNG şəkillərinin olduğu qovluq
    private const val GITHUB_LOGOS_BASE = "https://raw.githubusercontent.com/BY-KERIMOFF/aurex-player/main/logos/"
    private const val REMOTE_LOGOS_URL = "https://raw.githubusercontent.com/BY-KERIMOFF/aurex-player/main/app/src/main/assets/logos.txt"
    
    private val customLogoCache = ConcurrentHashMap<String, String>()
    private val logoCache = ConcurrentHashMap<String, String>()
    
    @Volatile
    private var isLoaded = false

    private val SUFFIX_REGEX = Regex("\\b(hd|sd|fhd|uhd|4k|5k|8k|fullhd|yedek|backup|rezerv|reserve|test|back|plus|\\+\\d|\\(\\d+\\)|1080p|720p|hevc|h265|60fps|50fps)\\b", RegexOption.IGNORE_CASE)

    fun loadLogoDatabase(context: Context) {
        if (isLoaded) return
        
        Executors.newSingleThreadExecutor().execute {
            try {
                loadFromUrl(REMOTE_LOGOS_URL)
                loadFromAssets(context)
                loadGlobalLogos()
                isLoaded = true
            } catch (e: Exception) {
                Log.e(TAG, "Logo error: ${e.message}")
            }
        }
    }

    private fun loadFromUrl(urlString: String) {
        try {
            val url = URL(urlString)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            if (conn.responseCode == 200) {
                conn.inputStream.bufferedReader(Charsets.UTF_8).use { parseContent(it) }
            }
        } catch (e: Exception) {}
    }

    private fun loadFromAssets(context: Context) {
        try {
            context.assets.open("logos.txt").bufferedReader(Charsets.UTF_8).use { parseContent(it) }
        } catch (e: Exception) {}
    }

    private fun loadGlobalLogos() {
        try {
            val response = URL(LOGO_API_URL).readText()
            val jsonArray = JSONArray(response)
            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.getJSONObject(i)
                val channelId = item.optString("channel", "")
                val logoUrl = item.optString("url", "")
                if (channelId.isNotEmpty() && logoUrl.isNotEmpty()) {
                    logoCache[getCoreName(channelId)] = logoUrl
                }
            }
        } catch (e: Exception) {}
    }

    private fun parseContent(reader: BufferedReader) {
        reader.forEachLine { line ->
            val trimmed = line.trim()
            if (trimmed.contains("->")) {
                val parts = trimmed.split("->")
                if (parts.size >= 2) {
                    val namePart = parts[0].trim()
                    val urlPart = parts[1].trim()
                    if (namePart.isNotEmpty() && urlPart.startsWith("http")) {
                        customLogoCache[getCoreName(namePart)] = urlPart
                    }
                }
            }
        }
    }

    /**
     * Mükəmməl "Kök Ad" Tapıcı:
     * 1. Hər şeyi kiçik hərf edir.
     * 2. HD, SD, Yedek kimi sözləri silir.
     * 3. Boşluqları silir.
     * 4. Azərbaycan hərflərini (ə -> e, ı -> i) çevirir (fayl adı üçün).
     */
    private fun getCoreName(name: String?): String {
        if (name == null) return ""
        var cleaned = name.lowercase()
            .replace(SUFFIX_REGEX, "") // HD, SD və s. silinir
            .replace(" ", "")
            
        // Fayl adı uyğunluğu üçün hərfləri dəyişirik
        cleaned = cleaned.replace("ə", "e")
            .replace("ı", "i")
            .replace("ö", "o")
            .replace("ğ", "g")
            .replace("ü", "u")
            .replace("ç", "c")
            .replace("ş", "s")
            
        return cleaned.replace(Regex("[^a-z0-9а-я]"), "").trim()
    }

    @JvmStatic
    fun resolveLogo(existingLogo: String?, channelId: String?, channelName: String?): String? {
        if (channelName.isNullOrEmpty()) return existingLogo

        val coreName = getCoreName(channelName)

        // 1. ÖNCƏLİK: Sənin GitHub-dakı logos/ qovluğuna baxırıq
        val githubAutoLogo = "$GITHUB_LOGOS_BASE$coreName.png"
        
        // 2. İKİNCİ: Əgər logos.txt-də bu kanal üçün xüsusi link yazmısansa
        val custom = customLogoCache[coreName]
        if (custom != null) return custom

        // Ən son çarə olaraq GitHub linkini qaytarırıq
        return githubAutoLogo
    }
}
