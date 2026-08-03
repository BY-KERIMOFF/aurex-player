package com.bykerimoff.player.utils

import android.content.Context
import com.bykerimoff.player.models.ResumeItem
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object ResumeManager {
    private const val PREF_NAME = "resume_prefs"
    private const val KEY_RESUME_LIST = "resume_list"
    private const val MAX_ITEMS = 15

    fun saveProgress(context: Context, item: ResumeItem) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val list = getResumeList(context).toMutableList()

        // Köhnə varsa sil
        list.removeAll { it.id == item.id || it.streamUrl == item.streamUrl }
        
        // Yenisini ən başa əlavə et
        list.add(0, item)

        // Limit
        if (list.size > MAX_ITEMS) {
            val trimmedList = list.take(MAX_ITEMS)
            saveList(context, trimmedList)
        } else {
            saveList(context, list)
        }
    }

    fun getResumeList(context: Context): List<ResumeItem> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_RESUME_LIST, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<ResumeItem>>() {}.type
            val fullList: List<ResumeItem> = Gson().fromJson(json, type)
            
            // 24 saatlıq (1 gün) təmizləmə: 24 saat * 60 dəq * 60 san * 1000 ms
            val oneDayAgo = System.currentTimeMillis() - (24 * 60 * 60 * 1000)
            val filteredList = fullList.filter { it.timestamp > oneDayAgo }
            
            // Əgər silinən varsa, yaddaşı yenilə
            if (filteredList.size != fullList.size) {
                saveList(context, filteredList)
            }
            
            filteredList
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveList(context: Context, list: List<ResumeItem>) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val json = Gson().toJson(list)
        prefs.edit().putString(KEY_RESUME_LIST, json).apply()
    }
    
    fun removeProgress(context: Context, streamUrl: String) {
        val list = getResumeList(context).toMutableList()
        list.removeAll { it.streamUrl == streamUrl }
        saveList(context, list)
    }
}
