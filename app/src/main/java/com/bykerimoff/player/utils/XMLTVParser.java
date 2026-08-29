package com.bykerimoff.player.utils;

import android.content.Context;
import android.util.Log;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.zip.GZIPInputStream;

public class XMLTVParser {
    private static final String TAG = "XMLTVParser";

    private static final String[] DEFAULT_SOURCES = {
        "https://epg.pw/xmltv/feed/az.xml",
        "https://epg.pw/xmltv/feed/tr.xml",
        "https://iptv-epg.org/files/epg-tr.xml.gz",
        "https://epg.pw/xmltv/feed/ru.xml",
        "https://epgshare01.online/epgshare01/epg_ripper_ALL_SOURCES1.xml.gz",
        "https://epg.pw/xmltv/feed/it.xml",
        "https://epg.pw/xmltv/feed/de.xml",
        "https://epg.pw/xmltv/feed/us.xml"
    };

    public static void syncDefaultSources(Context context) {
        // 1. Yerli faylı yüklə
        parseFromAssets(context, "local_epg.xml");

        // 2. Onlayn mənbələri yüklə
        for (String url : DEFAULT_SOURCES) {
            downloadAndParse(url);
        }
    }

    public static void parseFromAssets(Context context, String fileName) {
        new Thread(() -> {
            try {
                Log.d(TAG, "Parsing EPG from assets: " + fileName);
                InputStream is = context.getAssets().open(fileName);
                Map<String, String> epgMap = parse(is);
                DataManager.mergeXmltvCache(epgMap);
                Log.d(TAG, "Local EPG parsed and merged.");
            } catch (Exception e) {
                Log.e(TAG, "Error parsing local EPG: " + e.getMessage());
            }
        }).start();
    }

    public static String normalizeName(String name) {
        if (name == null) return "";
        return name.toLowerCase(Locale.US)
                .replaceAll("\\s+", "") // boşluqları sil
                .replaceAll("\\(.*?\\)", "") // mötərizə daxilini sil
                .replaceAll("\\[.*?\\]", "") // kvadrat mötərizə daxilini sil
                .replaceAll("hd|sd|fhd|uhd|4k|az|tr|ru|en|us|uk|\\+|test|premium|vip|cinema|film", "") // lazımsız sözləri sil
                .trim();
    }

    public static void downloadAndParse(String urlString) {
        if (urlString == null || urlString.isEmpty()) return;

        new Thread(() -> {
            try {
                Log.d(TAG, "Downloading EPG from: " + urlString);
                URL url = new URL(urlString);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestProperty("User-Agent", "IPTVSmartersPlayer");
                connection.setConnectTimeout(30000);
                connection.setReadTimeout(60000);

                InputStream is = connection.getInputStream();
                if (urlString.endsWith(".gz") || connection.getContentEncoding() != null && connection.getContentEncoding().contains("gzip")) {
                    is = new GZIPInputStream(is);
                }

                Map<String, String> epgMap = parse(is);
                DataManager.mergeXmltvCache(epgMap);
                Log.d(TAG, "EPG parsed and merged. Source: " + urlString);

            } catch (Exception e) {
                Log.e(TAG, "Error downloading/parsing EPG (" + urlString + "): " + e.getMessage());
            }
        }).start();
    }

    public interface EpgCallback {
        void onResult(com.bykerimoff.player.models.EpgProgram program);
    }

    public static void getProgramForChannel(String channelName, EpgCallback callback) {
        String normalized = normalizeName(channelName);
        String title = DataManager.getXmltvProgram(normalized);
        if (title != null) {
            callback.onResult(new com.bykerimoff.player.models.EpgProgram(title, 0, 0, "", false));
        } else {
            callback.onResult(null);
        }
    }

    private static Map<String, String> parse(InputStream is) throws Exception {
        Map<String, String> programs = new HashMap<>();
        XmlPullParser parser = Xml.newPullParser();
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
        parser.setInput(is, null);

        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        String now = sdf.format(new Date());

        int eventType = parser.getEventType();
        String currentChannelId = null;
        String currentTitle = null;
        String start = null;
        String stop = null;

        Map<String, String> channelIdToName = new HashMap<>();

        while (eventType != XmlPullParser.END_DOCUMENT) {
            String name = parser.getName();
            switch (eventType) {
                case XmlPullParser.START_TAG:
                    if ("channel".equals(name)) {
                        String id = parser.getAttributeValue(null, "id");
                        eventType = parser.next();
                        while (!(eventType == XmlPullParser.END_TAG && "channel".equals(parser.getName()))) {
                            if (eventType == XmlPullParser.START_TAG && "display-name".equals(parser.getName())) {
                                String displayName = parser.nextText();
                                channelIdToName.put(id, normalizeName(displayName));
                            }
                            eventType = parser.next();
                        }
                    } else if ("programme".equals(name)) {
                        currentChannelId = parser.getAttributeValue(null, "channel");
                        start = parser.getAttributeValue(null, "start");
                        stop = parser.getAttributeValue(null, "stop");
                    } else if ("title".equals(name) && currentChannelId != null) {
                        currentTitle = parser.nextText();
                    }
                    break;

                case XmlPullParser.END_TAG:
                    if ("programme".equals(name) && currentChannelId != null) {
                        if (isCurrent(start, stop, now)) {
                            // Həm ID ilə, həm də təmizlənmiş adla yadda saxla
                            programs.put(currentChannelId, currentTitle);
                            String normalizedName = channelIdToName.get(currentChannelId);
                            if (normalizedName != null) {
                                programs.put(normalizedName, currentTitle);
                            }
                        }
                        currentChannelId = null;
                        currentTitle = null;
                    }
                    break;
            }
            eventType = parser.next();
        }
        return programs;
    }

    private static boolean isCurrent(String start, String stop, String nowIgnore) {
        if (start == null || stop == null) return false;
        try {
            // XMLTV formatı: 20230724230000 +0300 və ya 20230724230000
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US);
            
            String cleanStart = start.contains(" ") ? start : start + " +0000";
            String cleanStop = stop.contains(" ") ? stop : stop + " +0000";
            
            long startTime = sdf.parse(cleanStart).getTime();
            long stopTime = sdf.parse(cleanStop).getTime();
            long currentTime = System.currentTimeMillis();
            
            return currentTime >= startTime && currentTime < stopTime;
        } catch (Exception e) {
            // Əgər offset formatı fərqlidirsə ehtiyat variant (köhnə məntiq)
            try {
                String s = start.split(" ")[0];
                String e_time = stop.split(" ")[0];
                SimpleDateFormat sdfSimple = new SimpleDateFormat("yyyyMMddHHmmss", Locale.US);
                sdfSimple.setTimeZone(TimeZone.getTimeZone("UTC"));
                long startTime = sdfSimple.parse(s).getTime();
                long stopTime = sdfSimple.parse(e_time).getTime();
                long currentTime = System.currentTimeMillis();
                return currentTime >= startTime && currentTime < stopTime;
            } catch (Exception ex) {
                return false;
            }
        }
    }
}
