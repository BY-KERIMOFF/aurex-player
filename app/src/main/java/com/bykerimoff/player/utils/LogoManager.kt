package com.bykerimoff.player.utils

import android.content.Context
import android.util.Log
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

object LogoManager {
    private const val TAG = "LogoManager"
    private const val LOGO_API_URL = "https://iptv-org.github.io/api/logos.json"
    
    // Key: Normalized Name, Value: Logo URL
    private val logoCache = mutableMapOf<String, String>()
    private var isLoaded = false

    fun loadLogoDatabase(context: Context) {
        if (isLoaded) return
        
        Executors.newSingleThreadExecutor().execute {
            try {
                Log.d(TAG, "Loading global logo database...")
                val url = URL(LOGO_API_URL)
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 15000
                connection.readTimeout = 30000

                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val jsonArray = JSONArray(response)
                
                for (i in 0 until jsonArray.length()) {
                    val item = jsonArray.getJSONObject(i)
                    val channelId = item.optString("channel", "")
                    val logoUrl = item.optString("url", "")
                    
                    if (channelId.isNotEmpty() && logoUrl.isNotEmpty()) {
                        // "AzTV.az" -> "aztv"
                        val normalized = normalizeForLogo(channelId)
                        if (!logoCache.containsKey(normalized)) {
                            logoCache[normalized] = logoUrl
                        }
                    }
                }
                
                isLoaded = true
                Log.d(TAG, "Logo database loaded: ${logoCache.size} items")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load logo database: ${e.message}")
            }
        }
    }

    private fun normalizeForLogo(name: String): String {
        return name.lowercase()
            .split(".")[0] // Domen hissəsini atırıq (aztv.az -> aztv)
            .replace(Regex("[^a-z0-9]"), "") // Yalnız hərf və rəqəmlər
    }

    fun getLogoForChannel(channelName: String): String? {
        val normalized = normalizeForLogo(channelName)
        // Birbaşa uyğunluq yoxla
        var logo = logoCache[normalized]
        
        // Əgər tapılmasa, daha geniş axtarış et (məs: "aztvhd" -> "aztv")
        if (logo == null) {
            val cleanName = channelName.lowercase()
                .replace("hd", "")
                .replace("sd", "")
                .replace("fhd", "")
                .replace(" ", "")
            logo = logoCache[normalizeForLogo(cleanName)]
        }
        
        return logo
    }
}
