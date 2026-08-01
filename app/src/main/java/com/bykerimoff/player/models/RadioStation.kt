package com.bykerimoff.player.models

import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class RadioStation(
    @SerializedName("stationuuid")
    val id: String,
    
    @SerializedName("name")
    val name: String,
    
    @SerializedName("url_resolved")
    val streamUrl: String,
    
    @SerializedName("favicon")
    val logoUrl: String?,
    
    @SerializedName("tags")
    val tags: String?,
    
    @SerializedName("country")
    val country: String?
) : Serializable
