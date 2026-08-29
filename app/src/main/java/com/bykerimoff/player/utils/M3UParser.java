package com.bykerimoff.player.utils;

import com.bykerimoff.player.models.Channel;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

public class M3UParser {

    public static List<Channel> parse(String m3uContent) {
        if (m3uContent == null) return new ArrayList<>();
        return parseInternal(new BufferedReader(new StringReader(m3uContent)));
    }

    public static List<Channel> parse(InputStream inputStream) {
        if (inputStream == null) return new ArrayList<>();
        return parseInternal(new BufferedReader(new InputStreamReader(inputStream)));
    }

    public static List<Channel> parse(BufferedReader reader) {
        if (reader == null) return new ArrayList<>();
        return parseInternal(reader);
    }

    private static List<Channel> parseInternal(BufferedReader reader) {
        List<Channel> channels = new ArrayList<>();
        
        try {
            String line;
            // İlk sətri oxu (BOM yoxlaması üçün)
            line = reader.readLine();
            if (line == null) return channels;
            
            if (line.startsWith("\uFEFF")) {
                line = line.substring(1);
            }

            String currentId = "";
            String currentName = "";
            String currentLogo = "";
            String currentGroup = "";
            String currentUrl = "";
            String currentTvgId = "";
            String currentCatchupType = "";
            String currentCatchupDays = "0";
            String currentCatchupSource = "";

            do {
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
            } while ((line = reader.readLine()) != null);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                reader.close();
            } catch (Exception ignored) {}
        }
        return channels;
    }

    public static boolean isVodChannel(String url) {
        if (url == null || url.isEmpty()) return false;
        String lowerUrl = url.toLowerCase().trim();
        
        // 1. HARD DENY: Bu sözlər varsa qətiyyən VOD deyil (IPTV indikatorları)
        if (lowerUrl.contains(".m3u8") || lowerUrl.contains(".ts") || 
            lowerUrl.contains(".mpd") || lowerUrl.contains(".m4s") ||
            lowerUrl.contains("/live/") || lowerUrl.contains("/live.php") ||
            lowerUrl.contains("stream.php") || lowerUrl.contains("index.php") || 
            lowerUrl.contains("/hls/") || lowerUrl.contains("output=") ||
            lowerUrl.contains("type=m3u8") || lowerUrl.contains("type=ts") ||
            lowerUrl.contains(":8080") || lowerUrl.contains(":8000") ||
            lowerUrl.contains("mpegts") || lowerUrl.contains("get.php") ||
            lowerUrl.contains("player_api.php") || lowerUrl.contains("xmltv.php") ||
            lowerUrl.contains("stream_id=") || lowerUrl.contains("channel_id=") ||
            lowerUrl.contains("ch_id=") || lowerUrl.contains("action=stream") ||
            lowerUrl.contains("link.php") || lowerUrl.contains("streaming") ||
            lowerUrl.contains("protocol=") || lowerUrl.contains("/live") ||
            lowerUrl.contains("tvg-") || lowerUrl.contains("logo") || 
            lowerUrl.contains("epg")) {
            return false;
        }

        // 2. VOD FORMATLARI: Mütləq bu formatlardan biri ilə bitməli və ya ayrılmalıdır
        String[] vodExtensions = {".mp4", ".mkv", ".avi", ".mov", ".flv", ".wmv", ".asf", ".3gp", ".webm", ".ogv"};
        for (String ext : vodExtensions) {
            // Fayl uzantısı mütləq nöqtə ilə başlamalı və ya ?/. ilə ayrılmalıdır
            if (lowerUrl.endsWith(ext) || lowerUrl.contains(ext + "?") || lowerUrl.contains(ext + "&")) {
                // Linkin içində yenə də /live/ keçirsə imtina et
                return !lowerUrl.contains("/live/");
            }
        }

        return false;
    }

    public static boolean isMovieUrl(String url) {
        if (url == null) return false;
        return isVodChannel(url) && !isSeriesUrl(url);
    }

    public static boolean isSeriesUrl(String url) {
        if (url == null) return false;
        String lowerUrl = url.toLowerCase();
        // Həm VOD uzantısı olmalı, həm də serial qovluğu indikatoru
        return isVodChannel(url) && (lowerUrl.contains("/series/") || lowerUrl.contains("/tvshow/") || lowerUrl.contains("/serial/"));
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

    public static boolean isKidsCategory(String categoryName) {
        if (categoryName == null) return false;
        String name = categoryName.toLowerCase().trim();
        return name.contains("kids") || name.contains("детски") || name.contains("мульт") || 
               name.contains("uşaq") || name.contains("cartoon") || name.contains("animation") ||
               name.contains("disney") || name.contains("nickelodeon") || name.contains("minika") ||
               name.contains("trt çocuk") || name.contains("karikatür") || name.contains("boing") ||
               name.contains("cartoonito") || name.contains("baby") || name.contains("nursery") ||
               name.contains("jimjam") || name.contains("pogo") || name.contains("gulli") ||
               name.contains("discovery family") || name.contains("spacetoon") || name.contains("duck tv");
    }

    public static boolean isSensitiveCategory(String categoryName) {
        if (categoryName == null) return false;
        String name = categoryName.toLowerCase().trim();
        return name.contains("adult") || name.contains("взрослые") || name.contains("yetişkin") || 
               name.contains("xxx") || name.contains("+18") || name.contains("erotic") ||
               name.contains("pembe") || name.contains("gece") || name.contains("cinsellik") ||
               name.contains("pink") || name.contains("for adult") || name.contains("18+") ||
               name.equals("18") || name.contains("porn") || name.contains("sex") ||
               name.contains("x-x-x") || name.contains("blue") || name.contains("mavi");
    }

    public static boolean isSportCategory(String categoryName) {
        if (categoryName == null) return false;
        String name = categoryName.toLowerCase().trim();
        return name.contains("sport") || name.contains("spor") || name.contains("idman") || 
               name.contains("football") || name.contains("futbol") || name.contains("bein") || 
               name.contains("sky sport") || name.contains("setanta") || name.contains("arena sport") ||
               name.contains("tivibu") || name.contains("exxen") || name.contains("d-smart") ||
               name.contains("lig tv") || name.contains("match tv") || name.contains("eurosport") ||
               name.contains("supersport") || name.contains("nba");
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
