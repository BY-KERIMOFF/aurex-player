package com.bykerimoff.player.utils;

import android.net.Uri;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class YoutubeUtils {

    /**
     * YouTube linkindən Video ID-ni çıxarır.
     * Dəstəklənən formatlar:
     * - youtube.com/watch?v=VIDEO_ID
     * - youtube.com/live/VIDEO_ID
     * - youtu.be/VIDEO_ID
     * - youtube.com/v/VIDEO_ID
     */
    public static String getYouTubeVideoId(String url) {
        if (url == null || url.trim().isEmpty()) return null;

        // 1. Düzgün youtube.com/watch?v=... formatı
        if (url.contains("v=")) {
            Uri uri = Uri.parse(url);
            String videoId = uri.getQueryParameter("v");
            if (videoId != null && !videoId.isEmpty()) return videoId;
        }

        // 2. youtube.com/live/VIDEO_ID formatı
        if (url.contains("/live/")) {
            Pattern pattern = Pattern.compile("/live/([^/?#&]+)");
            Matcher matcher = pattern.matcher(url);
            if (matcher.find()) return matcher.group(1);
        }

        // 3. youtu.be/VIDEO_ID formatı
        if (url.contains("youtu.be/")) {
            Pattern pattern = Pattern.compile("youtu.be/([^/?#&]+)");
            Matcher matcher = pattern.matcher(url);
            if (matcher.find()) return matcher.group(1);
        }
        
        // 4. youtube.com/v/VIDEO_ID və ya youtube.com/embed/VIDEO_ID formatı
        Pattern pattern = Pattern.compile("(?:/v/|/embed/|/shorts/)([^/?#&]+)");
        Matcher matcher = pattern.matcher(url);
        if (matcher.find()) return matcher.group(1);

        return null;
    }

    public static boolean isYouTubeUrl(String url) {
        if (url == null) return false;
        String lower = url.toLowerCase();
        return lower.contains("youtube.com") || lower.contains("youtu.be");
    }
}
