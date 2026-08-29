package com.bykerimoff.player.utils

import android.content.Context
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bykerimoff.player.R

object WallpaperManager {
    private const val PREF_NAME = "neoplay_wallpaper_prefs"
    private const val KEY_WALLPAPER_INDEX = "current_wallpaper_index"
    private const val KEY_WALLPAPER_TYPE = "wallpaper_type"
    private const val KEY_CUSTOM_IMAGE_URI = "custom_image_uri"
    private const val KEY_CUSTOM_VIDEO_URI = "custom_video_uri"

    enum class WallpaperType {
        DEFAULT, CUSTOM_IMAGE, CUSTOM_VIDEO
    }

    data class WallpaperItem(
        val name: String,
        val resId: Int? = null,
        val imageUrl: String? = null
    )

    val wallpapers = listOf(
        WallpaperItem("Bakı Panoraması", R.drawable.app_background),
        WallpaperItem("Aurex Premium Gold", imageUrl = "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?q=80&w=1920"),
        WallpaperItem("Dərin Kosmos", imageUrl = "https://images.unsplash.com/photo-1464802686167-b939a6910659?q=80&w=1920"),
        WallpaperItem("Gecə Şəhəri", imageUrl = "https://images.unsplash.com/photo-1477959858617-67f85cf4f1df?q=80&w=1920"),
        WallpaperItem("Minimalist Dağlar", imageUrl = "https://images.unsplash.com/photo-1464822759023-fed622ff2c3b?q=80&w=1920"),
        WallpaperItem("Modern Abstrakt", imageUrl = "https://images.unsplash.com/photo-1550684848-fac1c5b4e853?q=80&w=1920"),
        WallpaperItem("Qızılı İpək", imageUrl = "https://images.unsplash.com/photo-1502239608882-93b70816d27a?q=80&w=1920"),
        WallpaperItem("Dərin Göy", R.drawable.bg_gradient_blue),
        WallpaperItem("Sakit Bənövşəyi", R.drawable.bg_gradient_purple),
        WallpaperItem("Qaranlıq Gecə", R.drawable.bg_gradient_dark)
    )

    fun getWallpaperType(context: Context): WallpaperType {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val typeStr = prefs.getString(KEY_WALLPAPER_TYPE, WallpaperType.DEFAULT.name)
        return try {
            WallpaperType.valueOf(typeStr!!)
        } catch (e: Exception) {
            WallpaperType.DEFAULT
        }
    }

    fun setWallpaperType(context: Context, type: WallpaperType) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_WALLPAPER_TYPE, type.name).apply()
    }

    fun getCustomImageUri(context: Context): String? {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_CUSTOM_IMAGE_URI, null)
    }

    fun setCustomImageUri(context: Context, uri: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_CUSTOM_IMAGE_URI, uri).apply()
    }

    fun getCustomVideoUri(context: Context): String? {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_CUSTOM_VIDEO_URI, null)
    }

    fun setCustomVideoUri(context: Context, uri: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_CUSTOM_VIDEO_URI, uri).apply()
    }

    fun getCurrentWallpaperIndex(context: Context): Int {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_WALLPAPER_INDEX, 0)
    }

    fun setCurrentWallpaperIndex(context: Context, index: Int) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_WALLPAPER_INDEX, index).apply()
    }

    fun applyWallpaper(context: Context, imageView: ImageView) {
        val type = getWallpaperType(context)
        
        if (type == WallpaperType.CUSTOM_IMAGE) {
            val uri = getCustomImageUri(context)
            if (uri != null) {
                Glide.with(context)
                    .load(uri)
                    .centerCrop()
                    .into(imageView)
                return
            }
        }

        val index = getCurrentWallpaperIndex(context)
        val item = if (index >= 0 && index < wallpapers.size) wallpapers[index] else wallpapers[0]

        if (item.resId != null) {
            Glide.with(context)
                .load(item.resId)
                .centerCrop()
                .into(imageView)
        } else if (item.imageUrl != null) {
            Glide.with(context)
                .load(item.imageUrl)
                .placeholder(R.drawable.app_background)
                .error(R.drawable.bg_gradient_dark)
                .centerCrop()
                .into(imageView)
        }
    }
}
