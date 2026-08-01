package com.bykerimoff.player.utils

import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.provider.Settings
import android.util.Base64
import java.nio.charset.StandardCharsets

object SecurityUtils {

    private const val XOR_KEY = 0x5A.toByte()

    /**
     * Aktiv VPN bağlantısını yoxlayır.
     */
    fun isVpnActive(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networks = cm.allNetworks
        for (network in networks) {
            val caps = cm.getNetworkCapabilities(network) ?: continue
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                return true
            }
        }
        
        return false
    }

    /**
     * Aktiv Proksi (Proxy) serveri yoxlayır.
     */
    fun isProxyActive(): Boolean {
        val proxyHost = System.getProperty("http.proxyHost")
        val proxyPort = System.getProperty("http.proxyPort")
        return !proxyHost.isNullOrEmpty() && !proxyPort.isNullOrEmpty()
    }

    /**
     * Məşhur sniffer paketlərini yoxlayır.
     */
    fun isSnifferAppInstalled(context: Context): Boolean {
        val packages = listOf(
            "com.guoshi.httpcanary",
            "app.http_toolkit",
            "com.emanuelef.remote_terminal",
            "com.minhui.networkcapture",
            "com.evbadrit.networkanalyzer",
            "org.zaproxy.zap",
            "com.portswigger.burp.android"
        )
        val pm = context.packageManager
        for (pkg in packages) {
            try {
                pm.getPackageInfo(pkg, PackageManager.GET_ACTIVITIES)
                return true
            } catch (e: Exception) {}
        }
        return false
    }

    /**
     * Linki şifrələyir (Yaddaşda açıq qalmaması üçün).
     */
    @JvmStatic
    fun encryptUrl(url: String?): String {
        if (url == null) return ""
        val bytes = url.toByteArray(StandardCharsets.UTF_8)
        val xored = ByteArray(bytes.size)
        for (i in bytes.indices) {
            xored[i] = (bytes[i].toInt() xor XOR_KEY.toInt()).toByte()
        }
        return Base64.encodeToString(xored, Base64.NO_WRAP)
    }

    /**
     * Şifrələnmiş linki açır.
     */
    @JvmStatic
    fun decryptUrl(encrypted: String?): String {
        if (encrypted.isNullOrEmpty()) return ""
        return try {
            val bytes = Base64.decode(encrypted, Base64.NO_WRAP)
            val xored = ByteArray(bytes.size)
            for (i in bytes.indices) {
                xored[i] = (bytes[i].toInt() xor XOR_KEY.toInt()).toByte()
            }
            String(xored, StandardCharsets.UTF_8)
        } catch (e: Exception) {
            ""
        }
    }
}
