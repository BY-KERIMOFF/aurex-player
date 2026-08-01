package com.bykerimoff.player.models

import java.io.Serializable

data class ResumeItem(
    val id: String,
    val name: String,
    val logoUrl: String,
    val streamUrl: String,
    val categoryName: String,
    val position: Long,
    val duration: Long,
    val timestamp: Long = System.currentTimeMillis()
) : Serializable
