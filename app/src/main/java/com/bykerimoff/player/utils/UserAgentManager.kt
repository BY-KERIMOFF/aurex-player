package com.bykerimoff.player.utils

object UserAgentManager {

    private val STABLE_IPTV = listOf(
        "VLC/3.0.21 LibVLC/3.0.21",
        "TiviMate/5.1.5",
        "IPTVSmartersPlayer",
        "Kodi/21.1",
        "OTT Navigator/1.7.0",
        "Televizo/1.9",
        "PerfectPlayer/1.6.1",
        "GSE SMART IPTV",
        "XCIPTV",
        "IMPlayer/1.0"
    )

    private val BROWSERS = listOf(
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36",
        "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 Version/17.5 Mobile/15E148 Safari/604.1",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_5) AppleWebKit/605.1.15 Version/17.5 Safari/605.1.15",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:129.0) Gecko/20100101 Firefox/129.0",
        "Mozilla/5.0 (Linux; Android 14; SAMSUNG SM-S918B) AppleWebKit/537.36 Chrome/128.0.0.0 Mobile Safari/537.36 SamsungBrowser/27.0"
    )

    private val HARDWARE = listOf(
        "Mozilla/5.0 (Web0S; Linux/SmartTV) AppleWebKit/537.36 Chrome/68.0.3440.106 Safari/537.36",
        "Mozilla/5.0 (SMART-TV; Linux; Tizen 7.0) AppleWebKit/537.36 Chrome/118.0.5993.80 TV Safari/537.36",
        "Mozilla/5.0 (Linux; Android 11; Android TV) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36",
        "Mozilla/5.0 (PlayStation 5 5.50) AppleWebKit/605.1.15"
    )

    /**
     * Cəhd sayına görə ən yaxşı User-Agent-i qaytarır.
     * 0: VLC (Standart)
     * 1: TiviMate (IPTV Pro)
     * 2: Chrome (CDN Bypass)
     * 3: iPhone (Mobile Bypass)
     * 4: Kodi (Alternative)
     */
    fun getBestUserAgent(attempt: Int): String {
        return when (attempt) {
            0 -> STABLE_IPTV[0] // VLC 3.0.21
            1 -> STABLE_IPTV[1] // TiviMate 5.1.5
            2 -> BROWSERS[0]    // Chrome Windows
            3 -> BROWSERS[1]    // iPhone Safari
            4 -> STABLE_IPTV[3] // Kodi 21.1
            5 -> HARDWARE[0]    // LG WebOS
            6 -> STABLE_IPTV[2] // IPTV Smarters
            else -> STABLE_IPTV[0]
        }
    }
}
