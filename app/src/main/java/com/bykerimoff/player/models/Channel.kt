package com.bykerimoff.player.models

import com.bykerimoff.player.utils.SecurityUtils
import java.io.Serializable

data class Channel @JvmOverloads constructor(
    val id: String,
    val name: String,
    var logoUrl: String,
    private val encryptedStreamUrl: String, // Şifrələnmiş saxlayırıq
    val categoryName: String,
    var tvgId: String = "",
    var catchupType: String = "",
    var catchupDays: Int = 0,
    var catchupSource: String = ""
) : Serializable {

    /**
     * Pleyer üçün linki şifrədən çıxarır.
     */
    fun getStreamUrl(): String {
        return SecurityUtils.decryptUrl(encryptedStreamUrl)
    }

    /**
     * Linki şifrələnmiş formada qaytarır (Yaddaş axtarışından qorunmaq üçün).
     */
    fun getRawEncryptedUrl(): String {
        return encryptedStreamUrl
    }
}
