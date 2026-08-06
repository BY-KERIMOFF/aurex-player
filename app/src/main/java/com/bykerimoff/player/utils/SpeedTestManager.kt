package com.bykerimoff.player.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

object SpeedTestManager {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    // Test URL - 10MB file from Cloudflare (very reliable)
    private const val TEST_URL = "https://speed.cloudflare.com/__down?bytes=10000000"
    private const val PING_URL = "https://1.1.1.1"

    suspend fun measurePing(): Long = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        return@withContext try {
            val request = Request.Builder().url(PING_URL).head().build()
            client.newCall(request).execute().use {
                System.currentTimeMillis() - start
            }
        } catch (e: Exception) {
            -1L
        }
    }

    suspend fun measureDownloadSpeed(
        onProgress: (currentMbps: Double) -> Unit
    ): Double = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(TEST_URL).build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext 0.0
                
                val body = response.body ?: return@withContext 0.0
                val inputStream = body.byteStream()
                val data = ByteArray(8192)
                var bytesRead: Int
                var totalBytesRead = 0L
                val startTime = System.currentTimeMillis()
                
                while (inputStream.read(data).also { bytesRead = it } != -1) {
                    totalBytesRead += bytesRead
                    val currentTime = System.currentTimeMillis()
                    val timeDiff = (currentTime - startTime) / 1000.0 // seconds
                    
                    if (timeDiff > 0.1) {
                        // bits / seconds / 1,000,000 = Mbps
                        val mbps = (totalBytesRead * 8.0) / (timeDiff * 1000000.0)
                        withContext(Dispatchers.Main) {
                            onProgress(mbps)
                        }
                    }
                    
                    // Stop test if it takes too long (e.g. 15 seconds)
                    if (timeDiff > 15.0) break
                }
                
                val finalTime = (System.currentTimeMillis() - startTime) / 1000.0
                return@withContext (totalBytesRead * 8.0) / (finalTime * 1000000.0)
            }
        } catch (e: Exception) {
            0.0
        }
    }
}
