package com.bykerimoff.player.utils

import android.content.Context
import android.provider.Settings
import java.net.NetworkInterface
import java.util.Collections

object MacUtils {

    @JvmStatic
    fun getMacAddress(context: Context): String {
        try {
            val all = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (nif in all) {
                if (!nif.name.equals("wlan0", ignoreCase = true) && !nif.name.equals("eth0", ignoreCase = true)) continue

                val macBytes = nif.hardwareAddress ?: continue

                val res1 = StringBuilder()
                for (b in macBytes) {
                    res1.append(String.format("%02X:", b))
                }

                if (res1.isNotEmpty()) {
                    res1.deleteCharAt(res1.length - 1)
                }
                return res1.toString()
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
        }

        // Fallback to Android ID if MAC is not available
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        if (androidId != null) {
            // Create a pseudo-MAC from Android ID
            val pseudoMac = StringBuilder()
            var i = 0
            while (i < Math.min(androidId.length, 12)) {
                if (i > 0) pseudoMac.append(":")
                pseudoMac.append(androidId.substring(i, i + 2).uppercase())
                i += 2
            }
            // Pad if shorter than 12 chars
            while (pseudoMac.length < 17) {
                pseudoMac.append(":00")
            }
            return pseudoMac.toString()
        }

        return "00:00:00:00:00:00"
    }
}
