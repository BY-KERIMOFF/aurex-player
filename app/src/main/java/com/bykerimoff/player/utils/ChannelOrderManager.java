package com.bykerimoff.player.utils;

import android.content.Context;
import android.content.SharedPreferences;

import com.bykerimoff.player.models.Channel;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChannelOrderManager {
    private static final String PREFS_NAME = "channel_orders";
    private final SharedPreferences prefs;
    private final Gson gson;

    public ChannelOrderManager(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.gson = new Gson();
    }

    public void saveOrder(String categoryId, List<String> channelIds) {
        String json = gson.toJson(channelIds);
        prefs.edit().putString(categoryId, json).apply();
    }

    public List<String> getOrder(String categoryId) {
        String json = prefs.getString(categoryId, null);
        if (json == null) return new ArrayList<>();
        Type type = new TypeToken<List<String>>() {}.getType();
        return gson.fromJson(json, type);
    }

    /**
     * Siyahını yadda saxlanılmış sıraya görə nizamlayır.
     */
    public <T extends Channel> List<T> applyOrder(String categoryId, List<T> originalList) {
        List<String> savedOrder = getOrder(categoryId);
        if (savedOrder.isEmpty()) return new ArrayList<>(originalList);

        Map<String, T> channelMap = new HashMap<>();
        for (T channel : originalList) {
            channelMap.put(channel.getId(), channel);
        }

        List<T> orderedList = new ArrayList<>();
        // Əvvəlcə yadda saxlanılmış sıradakıları düz
        for (String id : savedOrder) {
            if (channelMap.containsKey(id)) {
                orderedList.add(channelMap.remove(id));
            }
        }
        // Qalanlarını sona əlavə et (məsələn, yeni əlavə olunan kanallar)
        orderedList.addAll(channelMap.values());
        
        return orderedList;
    }
}
