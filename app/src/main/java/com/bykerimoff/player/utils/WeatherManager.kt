package com.bykerimoff.player.utils

import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

object WeatherManager {
    private const val API_URL = "https://api.open-meteo.com/v1/forecast?latitude=40.4093&longitude=49.8671&current_weather=true"
    
    interface WeatherCallback {
        fun onSuccess(temp: String, weatherCode: Int)
        fun onFailure(error: String)
    }

    fun fetchWeather(callback: WeatherCallback) {
        val executor = Executors.newSingleThreadExecutor()
        val handler = Handler(Looper.getMainLooper())

        executor.execute {
            try {
                val url = URL(API_URL)
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 5000
                connection.readTimeout = 5000

                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                val currentWeather = json.getJSONObject("current_weather")
                
                val temp = currentWeather.getDouble("temperature")
                val code = currentWeather.getInt("weathercode")

                handler.post {
                    callback.onSuccess("${temp.toInt()}°C", code)
                }
            } catch (e: Exception) {
                handler.post {
                    callback.onFailure(e.message ?: "Bilinməyən xəta")
                }
            }
        }
    }

    // Hava koduna uyğun emojini qaytarır
    fun getWeatherEmoji(code: Int): String {
        return when (code) {
            0 -> "☀️" // Açıq səma
            1, 2, 3 -> "🌤️" // Az buludlu
            45, 48 -> "🌫️" // Duman
            51, 53, 55, 61, 63, 65 -> "🌧️" // Yağış
            71, 73, 75, 77, 85, 86 -> "❄️" // Qar
            95, 96, 99 -> "⛈️" // İldırım
            else -> "☁️"
        }
    }
}
