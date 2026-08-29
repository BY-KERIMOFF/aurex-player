package com.bykerimoff.player.utils;

import android.content.Context;
import com.bykerimoff.player.models.Category;
import com.bykerimoff.player.models.Channel;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;

public class DiskCacheManager {
    private static final String CACHE_DIR = "playlist_cache";
    private static final Gson gson = new Gson();

    public static void saveChannels(Context context, String key, List<Channel> channels) {
        saveToFile(context, "channels_" + key, channels);
    }

    public static List<Channel> loadChannels(Context context, String key) {
        return loadFromFile(context, "channels_" + key, new TypeToken<List<Channel>>() {});
    }

    public static void saveCategories(Context context, String key, List<Category> categories) {
        saveToFile(context, "categories_" + key, categories);
    }

    public static List<Category> loadCategories(Context context, String key) {
        return loadFromFile(context, "categories_" + key, new TypeToken<List<Category>>() {});
    }

    private static <T> void saveToFile(Context context, String filename, T data) {
        try {
            File dir = new File(context.getCacheDir(), CACHE_DIR);
            if (!dir.exists()) dir.mkdirs();
            File file = new File(dir, filename + ".json");
            try (FileWriter writer = new FileWriter(file)) {
                gson.toJson(data, writer);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static <T> List<T> loadFromFile(Context context, String filename, TypeToken<List<T>> typeToken) {
        try {
            File file = new File(new File(context.getCacheDir(), CACHE_DIR), filename + ".json");
            if (!file.exists()) return new ArrayList<>();
            try (FileReader reader = new FileReader(file)) {
                List<T> data = gson.fromJson(reader, typeToken.getType());
                return data != null ? data : new ArrayList<>();
            }
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
    
    public static void clearCache(Context context) {
        try {
            File dir = new File(context.getCacheDir(), CACHE_DIR);
            if (dir.exists()) {
                for (File f : dir.listFiles()) f.delete();
            }
        } catch (Exception ignored) {}
    }
}
