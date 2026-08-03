package com.bykerimoff.player.utils;

import com.bykerimoff.player.models.Category;
import com.bykerimoff.player.models.Channel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DataManager {
    private static List<Channel> currentChannelList = new ArrayList<>();
    private static List<Category> currentCategoryList = new ArrayList<>();
    private static Map<String, List<Channel>> currentChannelMap = new HashMap<>();
    private static String globalEpgUrl = "";
    private static Map<String, String> xmltvCache = new HashMap<>();
    private static String adminAnnouncement = "";
    private static String adminAnnouncementColor = "";
    private static List<Channel> allChannels = new ArrayList<>();
    private static String playlistIdentifier = "";
    private static boolean showAnnouncementGlobal = true;
    
    public static void setShowAnnouncementGlobal(boolean show) {
        showAnnouncementGlobal = show;
    }

    public static boolean isShowAnnouncementGlobal() {
        return showAnnouncementGlobal;
    }
    
    public static void setGlobalEpgUrl(String url) {
        globalEpgUrl = url;
    }

    public static String getGlobalEpgUrl() {
        return globalEpgUrl;
    }

    public static void setXmltvCache(Map<String, String> cache) {
        xmltvCache = new HashMap<>(cache);
    }

    public static void mergeXmltvCache(Map<String, String> cache) {
        if (xmltvCache == null) xmltvCache = new HashMap<>();
        xmltvCache.putAll(cache);
    }

    public static Map<String, String> getXmltvCache() {
        return xmltvCache;
    }

    public static void setAdminAnnouncement(String announcement) {
        adminAnnouncement = announcement;
    }

    public static String getAdminAnnouncement() {
        return adminAnnouncement;
    }

    public static void setAdminAnnouncementColor(String color) {
        adminAnnouncementColor = color;
    }

    public static String getAdminAnnouncementColor() {
        return adminAnnouncementColor;
    }

    public static void setAllChannels(List<Channel> list) {
        allChannels = new ArrayList<>(list);
    }

    public static List<Channel> getAllChannels() {
        return allChannels;
    }

    public static void setPlaylistIdentifier(String id) {
        playlistIdentifier = id;
    }

    public static String getPlaylistIdentifier() {
        return playlistIdentifier;
    }

    public static void setCurrentChannelList(List<Channel> list) {
        currentChannelList = new ArrayList<>(list);
    }
    
    public static List<Channel> getCurrentChannelList() {
        return currentChannelList;
    }

    public static void setCurrentCategoryList(List<Category> list) {
        currentCategoryList = new ArrayList<>(list);
    }

    public static List<Category> getCurrentCategoryList() {
        return currentCategoryList;
    }

    public static void setCurrentChannelMap(Map<String, List<Channel>> map) {
        currentChannelMap = new HashMap<>(map);
    }

    public static Map<String, List<Channel>> getCurrentChannelMap() {
        return currentChannelMap;
    }
    
    public static void clear() {
        currentChannelList.clear();
        currentCategoryList.clear();
        currentChannelMap.clear();
        allChannels.clear();
        playlistIdentifier = "";
    }
}
