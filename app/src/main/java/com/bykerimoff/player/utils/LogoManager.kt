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

object LogoManager {
    private const val TAG = "LogoManager"
    private const val LOGO_API_URL = "https://iptv-org.github.io/api/logos.json"

    private val logoCache = mutableMapOf<String, String>()
    private val customLogoCache = mutableMapOf<String, String>()
    private var isLoaded = false

    fun loadLogoDatabase(context: Context) {
        if (isLoaded) return
        
        Executors.newSingleThreadExecutor().execute {
            // 1. Assets daxilindəki logos.txt faylını oxu
            try {
                context.assets.open("logos.txt").bufferedReader(Charsets.UTF_8).use { reader ->
                    Log.d(TAG, "logos.txt oxunur...")
                    reader.forEachLine { line ->
                        val trimmed = line.trim()
                        if (trimmed.contains("->")) {
                            val parts = trimmed.split("->")
                            if (parts.size >= 2) {
                                val name = parts[0].trim()
                                val url = parts[1].trim()
                                if (name.isNotEmpty() && url.startsWith("http")) {
                                    // Bütün halları nəzərə alan KEY (boşluqsuz, balaca hərflərlə)
                                    customLogoCache[normalizeKey(name)] = url
                                }
                            }
                        }
                    }
                }

                Handler(Looper.getMainLooper()).post {
                    if (customLogoCache.isNotEmpty()) {
                        Toast.makeText(context, "Loqo bazası yükləndi: ${customLogoCache.size} kanal", Toast.LENGTH_SHORT).show()
                    }
                }
                Log.d(TAG, "Daxili loqo bazası hazır: ${customLogoCache.size} element")
            } catch (e: Exception) {
                Log.e(TAG, "Daxili loqo xətası: ${e.message}")
            }

            // 2. Qlobal bazanı yüklə
            try {
                val url = URL(LOGO_API_URL)
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 10000
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val jsonArray = JSONArray(response)
                for (i in 0 until jsonArray.length()) {
                    val item = jsonArray.getJSONObject(i)
                    val channelId = item.optString("channel", "")
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

    private fun normalizeKey(name: String?): String {
        if (name == null) return ""
        // Boşluqları, simvolları silir, hər şeyi balaca hərflə saxlayır (aztvhd, trt1 və s.)
        return name.lowercase()
            .replace(Regex("\\s+"), "")
            .replace(Regex("[^\\p{L}\\d]"), "")
            .trim()
    }

    @JvmStatic
    fun resolveLogo(existingLogo: String?, channelId: String?, channelName: String?): String? {
        // --- PRIORITET 1: BİZİM LOGOS.TXT (AD-A GÖRƏ) ---
        // Əgər bizim siyahıda bu adda kanal varsa, playlist-dəki loqonu ləğv edib səninkini qoyuruq!
        if (!channelName.isNullOrEmpty()) {
            val key = normalizeKey(channelName)
            val custom = customLogoCache[key]
            if (custom != null) return custom
        }

        // --- PRIORITET 2: BİZİM LOGOS.TXT (ID VƏ YA FAYL ADINA GÖRƏ) ---
        if (!existingLogo.isNullOrEmpty()) {
            val custom = customLogoCache[normalizeKey(existingLogo)]
            if (custom != null) return custom
        }
        if (!channelId.isNullOrEmpty()) {
            val custom = customLogoCache[normalizeKey(channelId)]
            if (custom != null) return custom
        }

        // --- PRIORITET 3: PLAYLIST-DƏKİ ORİJİNAL LOQO ---
        if (existingLogo != null && (existingLogo.startsWith("http://") || existingLogo.startsWith("https://"))) {
            return existingLogo
        }
        
        // --- PRIORITET 4: QLOBAL BAZA ---
        if (!channelName.isNullOrEmpty()) {
            return logoCache[normalizeKey(channelName)]
        }
        
        return existingLogo
    }
}
