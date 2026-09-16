package com.bykerimoff.player.utils

import android.content.Context
import android.util.Log
import android.widget.Toast
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors
import android.os.Handler
import android.os.Looper
import java.io.BufferedReader
import java.util.concurrent.ConcurrentHashMap

object LogoManager {
    private const val TAG = "LogoManager"
    private const val LOGO_API_URL = "https://iptv-org.github.io/api/logos.json"
    
    // GitHub-dakı loqoların əsas qovluğu
    private const val GITHUB_LOGOS_BASE = "https://raw.githubusercontent.com/BY-KERIMOFF/aurex-player/main/logos/"
    
    // Lüğət: "aztv" -> "AZ_ AZ TV.png"
    private val logoIndexMap = ConcurrentHashMap<String, String>()
    private val globalLogoCache = ConcurrentHashMap<String, String>()
    
    @Volatile
    private var isLoaded = false

    // Silinməli olan IPTV əlavələri
    private val SUFFIX_REGEX = Regex("\\b(hd|sd|fhd|uhd|4k|5k|8k|fullhd|yedek|backup|rezerv|reserve|test|back|plus|\\+\\d|\\(\\d+\\)|1080p|720p|hevc|h265|60fps|50fps)\\b", RegexOption.IGNORE_CASE)

    fun loadLogoDatabase(context: Context) {
        if (isLoaded) return
        
        Executors.newSingleThreadExecutor().execute {
            try {
                // 1. "Ağıllı İndeks" faylını Assets-dən yüklə
                loadIndex(context)

                // 2. Qlobal bazanı yüklə (Ehtiyat üçün)
                loadGlobalLogos()

                isLoaded = true
                Log.d(TAG, "Logo sistemi hazırlandı: ${logoIndexMap.size} yerli loqo.")
            } catch (e: Exception) {
                Log.e(TAG, "Logo error: ${e.message}")
            }
        }
    }

    private fun loadIndex(context: Context) {
        try {
            context.assets.open("logos_index.txt").bufferedReader(Charsets.UTF_8).use { reader ->
                reader.forEachLine { line ->
                    if (line.contains("->")) {
                        val parts = line.split("->")
                        if (parts.size >= 2) {
                            val key = parts[0].trim().lowercase()
                            val filename = parts[1].trim()
                            if (key.isNotEmpty() && filename.isNotEmpty()) {
                                logoIndexMap[key] = filename
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Index load fail: ${e.message}")
        }
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
                    globalLogoCache[getCoreName(channelId)] = logoUrl
                }
            }
        } catch (e: Exception) {}
    }

    /**
     * Kanalın Kök Adını Tapır: "AzTV FHD Yedek" -> "aztv"
     */
    private fun getCoreName(name: String?): String {
        if (name == null) return ""
        var cleaned = name.lowercase()
            .replace(SUFFIX_REGEX, "") // HD, SD və s. silinir
            .replace(" ", "")
            
        // Hərfləri eyniləşdiririk
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

        // 1. ADDIM: "Ağıllı İndeks"-də axtar
        val filename = logoIndexMap[coreName]
        if (filename != null) {
            return try {
                // Fayl adını URL üçün uyğun formaya salırıq (boşluqları %20 edir)
                val encodedFile = URLEncoder.encode(filename, "UTF-8").replace("+", "%20")
                "$GITHUB_LOGOS_BASE$encodedFile"
            } catch (e: Exception) {
                "$GITHUB_LOGOS_BASE$filename"
            }
        }

        // 2. ADDIM: Əgər playlist-də zatən tam link varsa onu istifadə et
        if (existingLogo != null && (existingLogo.startsWith("http://") || existingLogo.startsWith("https://"))) {
            return existingLogo
        }

        // 3. ADDIM: Qlobal baza
        return globalLogoCache[coreName] ?: existingLogo
    }
}
