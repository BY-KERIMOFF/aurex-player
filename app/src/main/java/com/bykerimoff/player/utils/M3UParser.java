package com.bykerimoff.player.utils;

import com.bykerimoff.player.models.Channel;

import java.util.ArrayList;
import java.util.List;

public class M3UParser {

    public static List<Channel> parse(String m3uContent) {
        if (m3uContent == null) return new ArrayList<>();
        
        // BOM (Byte Order Mark) təmizlənməsi
        if (m3uContent.startsWith("\uFEFF")) {
            m3uContent = m3uContent.substring(1);
        }

        List<Channel> channels = new ArrayList<>();
        // Daha etibarlı sətir bölməsi (\r\n dəstəyi)
        String[] lines = m3uContent.split("\\r?\\n");

        String currentId = "";
        String currentName = "";
        String currentLogo = "";
        String currentGroup = "";
        String currentUrl = "";
        String currentTvgId = "";
        String currentCatchupType = "";
        String currentCatchupDays = "0";
        String currentCatchupSource = "";

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;

            if (line.startsWith("#EXTM3U")) {
                // EPG Linkini axtar
                String epgUrl = getAttribute(line, "url-tvg");
                if (epgUrl.isEmpty()) epgUrl = getAttribute(line, "x-tvg-url");
                if (!epgUrl.isEmpty()) {
                    DataManager.setGlobalEpgUrl(epgUrl);
                }
                continue;
            }

            if (line.startsWith("#EXTINF")) {
                // Atributları çıxar
                currentId = getAttribute(line, "tvg-id");
                currentTvgId = getAttribute(line, "tvg-id");
                if (currentTvgId.isEmpty()) currentTvgId = getAttribute(line, "tvg-name");
                
                currentLogo = getAttribute(line, "tvg-logo");
                if (currentLogo.isEmpty()) {
                    currentLogo = getAttribute(line, "logo");
                }
                
                currentGroup = getAttribute(line, "group-title");

                currentCatchupType = getAttribute(line, "catchup");
                currentCatchupDays = getAttribute(line, "catchup-days");
                currentCatchupSource = getAttribute(line, "catchup-source");
                
                // Kanal adını virgül-dən sonra götür
                int lastComma = line.lastIndexOf(",");
                if (lastComma != -1) {
                    currentName = line.substring(lastComma + 1).trim();
                    
                    // BƏZİ HALLARDA URL EYNİ SƏTİRDƏ OLUR
                    int urlIndex = currentName.indexOf("http");
                    if (urlIndex != -1) {
                        currentUrl = currentName.substring(urlIndex).trim();
                        currentName = currentName.substring(0, urlIndex).trim();
                        if (currentName.endsWith(",")) {
                            currentName = currentName.substring(0, currentName.length() - 1).trim();
                        }
                    }
                }
                
            } else if (line.startsWith("#EXTGRP:")) {
                String group = line.substring(8).trim();
                if (!group.isEmpty()) {
                    currentGroup = group;
                }
            } else if (!line.startsWith("#")) {
                // Bu sətir URL-dir
                currentUrl = line;
            }

            // Əgər URL tapılıbsa və ad boş deyilsə kanalı əlavə et
            if (!currentUrl.isEmpty()) {
                if (currentId.isEmpty()) currentId = String.valueOf(channels.size());
                if (currentGroup == null || currentGroup.isEmpty()) {
                    currentGroup = "(Adsız)";
                }
                if (currentName.isEmpty()) currentName = "Adsız Kanal " + channels.size();

                int catchupDays = 0;
                try {
                    if (currentCatchupDays != null && !currentCatchupDays.isEmpty()) {
                        catchupDays = Integer.parseInt(currentCatchupDays);
                    }
                } catch (Exception ignored) {}

                channels.add(new Channel(currentId, currentName, currentLogo, SecurityUtils.encryptUrl(currentUrl), currentGroup, currentTvgId, currentCatchupType, catchupDays, currentCatchupSource));
                
                // Növbəti kanal üçün sıfırla
                currentId = "";
                currentName = "";
                currentLogo = "";
                currentGroup = "";
                currentUrl = "";
                currentTvgId = "";
                currentCatchupType = "";
                currentCatchupDays = "0";
                currentCatchupSource = "";
            }
        }
        return channels;
    }

    public static boolean isVodChannel(String url) {
        if (url == null) return false;
        String lowerUrl = url.toLowerCase();
        return lowerUrl.contains(".mp4") || lowerUrl.contains(".mkv") || 
               lowerUrl.contains(".avi") || lowerUrl.contains(".mov") || 
               lowerUrl.contains(".flv") || lowerUrl.contains(".mpg") ||
               lowerUrl.contains(".wmv") || lowerUrl.contains(".asf") ||
               lowerUrl.contains(".3gp") || lowerUrl.contains(".webm") ||
               lowerUrl.contains(".ogv") ||
               lowerUrl.contains("/movie/") || lowerUrl.contains("/series/") || 
               lowerUrl.contains("/vod/") || lowerUrl.contains("type=vod");
    }

    public static boolean hasVodContent(String m3uContent) {
        if (m3uContent == null) return false;
        String[] lines = m3uContent.split("\\r?\\n");
        for (String line : lines) {
            line = line.trim();
            if (!line.startsWith("#") && !line.isEmpty()) {
                if (isVodChannel(line)) return true;
            }
        }
        return false;
    }

    public static boolean isSensitiveCategory(String categoryName) {
        if (categoryName == null) return false;
        String name = categoryName.toLowerCase();
        return name.contains("adult") || name.contains("взрослые") || name.contains("yetişkin") || name.contains("xxx") || name.contains("+18");
    }

    public static String getAttribute(String line, String attrName) {
        // həm key="value" həm də key='value' formatını dəstəkləyir
        String[] quotes = {"\"", "'"};
        for (String quote : quotes) {
            String key = attrName + "=" + quote;
            int start = line.indexOf(key);
            if (start != -1) {
                start += key.length();
                int end = line.indexOf(quote, start);
                if (end != -1) {
                    return line.substring(start, end);
                }
            }
        }
        return "";
    }
}
