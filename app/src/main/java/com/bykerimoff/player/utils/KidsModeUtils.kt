package com.bykerimoff.player.utils

object KidsModeUtils {

    private val kidsKeywords = listOf(
        "cizgi", "kids", "детские", "uşaq", "cartoon", "animation", 
        "trt çocuk", "minika", "disney", "boing", "nick", "baby",
        "junior", "family", "kids", "uşaqlar"
    )

    fun isKidsCategory(categoryName: String?): Boolean {
        if (categoryName == null) return false
        val lowerName = categoryName.lowercase()
        return kidsKeywords.any { lowerName.contains(it) }
    }
}
