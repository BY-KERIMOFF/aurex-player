package com.bykerimoff.player.models

import java.io.Serializable

data class EpgProgram(
    val title: String,
    val startTime: Long, // Unix timestamp in milliseconds
    val endTime: Long,
    val description: String = "",
    val isArchiveAvailable: Boolean = false
) : Serializable {
    fun isPast(): Boolean = System.currentTimeMillis() > endTime
    fun isCurrent(): Boolean = System.currentTimeMillis() in startTime..endTime
}
