package com.bykerimoff.player.utils

import android.content.Context
import android.util.Log
import android.widget.Toast
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import android.os.Handler
import android.os.Looper
import java.io.BufferedReader

object LogoManager {
    private const val TAG = "LogoManager"
    private const val LOGO_API_URL = "https://iptv-org.github.io/api/logos.json"
    
    // GitHub-dakı RAW logos.txt linki (Prioritet budur)
    private const val REMOTE_LOGOS_URL = "https://raw.githubusercontent.com/BY-KERIMOFF/aurex-player/main/app/src/main/assets/logos.txt"
    
    private val logoCache = mutableMapOf<String, String>()
    
    // Əsas loqo bazası: Key = normalize olunmuş ad
    private val customLogoCache = mutableMapOf<String, String>()
    private var isLoaded = false

    // Təmizlənməli olan keyfiyyət sözləri
    private val QUALITY_REGEX = Regex("\\b(hd|sd|fhd|uhd|4k|5k|8k|50fps|60fps|hevc|h265|double|plus|\\+\\d|\\(\\d+\\))\\b", RegexOption.IGNORE_CASE)

    fun loadLogoDatabase(context: Context) {
        if (isLoaded) return
        
        Executors.newSingleThreadExecutor().execute {
            var loadedCount = 0

            // 1. ÖNCƏ GİTHUB-DAN YÜKLƏMƏYƏ ÇALIŞ
            try {
                Log.d(TAG, "GitHub logos.txt çəkilir...")
                val url = URL(REMOTE_LOGOS_URL)
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 10000
                conn.readTimeout = 15000
                conn.setRequestProperty("User-Agent", "Mozilla/5.0")
                
                if (conn.responseCode == 200) {
                    conn.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                        parseLogos(reader)
                    }
                    loadedCount = customLogoCache.size
                    Log.d(TAG, "GitHub-dan $loadedCount loqo yükləndi.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "GitHub yükləmə xətası: ${e.message}")
            }

            // 2. ƏGƏR GİTHUB UĞURSUZ OLDUSA VƏ YA BOŞDURSA, ASSETS-DƏN OXU
            if (customLogoCache.isEmpty()) {
                try {
                    context.assets.open("logos.txt").bufferedReader(Charsets.UTF_8).use { reader ->
                        parseLogos(reader)
                    }
                    loadedCount = customLogoCache.size
                    Log.d(TAG, "Assets-dən $loadedCount loqo yükləndi.")
                } catch (e: Exception) {
                    Log.e(TAG, "Assets oxunma xətası: ${e.message}")
                }
            }

            if (customLogoCache.isNotEmpty()) {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, "Loqolar aktivdir ($loadedCount kanal)", Toast.LENGTH_SHORT).show()
                }
            }

            // 3. QLOBAL BAZA (iptv-org)
            try {
                val url = URL(LOGO_API_URL)
                val response = url.readText()
                val jsonArray = JSONArray(response)
                for (i in 0 until jsonArray.length()) {
                    val item = jsonArray.getJSONObject(i)
                    val channelId = item.optString("channel", "").lowercase()
                    val logoUrl = item.optString("url", "")
                    if (channelId.isNotEmpty() && logoUrl.isNotEmpty()) {
                        val key = normalizeKey(channelId)
                        if (!logoCache.containsKey(key)) {
                            logoCache[key] = logoUrl
                        }
                    }
                }
                isLoaded = true
            } catch (e: Exception) {
                Log.e(TAG, "Qlobal loqo xətası: ${e.message}")
            }
        }
    }

    private fun parseLogos(reader: BufferedReader) {
        reader.forEachLine { line ->
            val trimmed = line.trim()
            if (trimmed.contains("->")) {
                val parts = trimmed.split("->")
                if (parts.size >= 2) {
                    val name = parts[0].trim()
                    val url = parts[1].trim()
                    if (name.isNotEmpty() && url.startsWith("http")) {
                        // Həm tam adı, həm də sadələşdirilmiş adı saxla
                        customLogoCache[normalizeKey(name)] = url
                        customLogoCache[getCoreName(name)] = url
                    }
                }
            }
        }
    }

    /**
     * Mükəmməl Normalizasiya: 
     * Boşluqları, nöqtələri silir, balaca hərflə saxlayır.
     */
    private fun normalizeKey(name: String?): String {
        if (name == null) return ""
        return name.lowercase()
            .replace(" ", "")
            .replace(Regex("[^\\p{L}\\d]"), "") // Hər dildə hərflər və rəqəmlər qalır
            .trim()
    }

    private fun getCoreName(name: String): String {
        return name.lowercase()
            .replace(QUALITY_REGEX, "") // HD, SD və s. silir
            .replace(" ", "")
            .replace(Regex("[^\\p{L}\\d]"), "")
            .trim()
    }

    @JvmStatic
    fun resolveLogo(existingLogo: String?, channelId: String?, channelName: String?): String? {
        if (channelName.isNullOrEmpty()) return existingLogo

        // 1. TAM ADLA AXTARIŞ
        val fullKey = normalizeKey(channelName)
        var result = customLogoCache[fullKey]
        
        // 2. KÖK ADLA AXTARIŞ (HD/SD-SİZ)
        if (result == null) {
            val coreKey = getCoreName(channelName)
            result = customLogoCache[coreKey]
        }

        // 3. ƏGƏR BİZİM SİYAHIDA VARSA, ONU QAYTAR (Mütləq Prioritet)
        if (result != null) return result

        // 4. PLAYLIST-DƏKİ ORİJİNAL LOQO
        if (existingLogo != null && (existingLogo.startsWith("http://") || existingLogo.startsWith("https://"))) {
            return existingLogo
        }
        
        // 5. QLOBAL BAZA
        return logoCache[normalizeKey(channelName)] ?: existingLogo
    }
}
