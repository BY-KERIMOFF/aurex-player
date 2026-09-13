package com.bykerimoff.player.utils;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.OptIn;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.UnstableApi;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

@UnstableApi
public class StreamResolver {
    private static final String TAG = "StreamResolver";
    private static final int MAX_RETRY_COUNT = 3;

    public interface ResolveCallback {
        void onResolved(String resolvedUrl, String mimeType);
        void onError(String errorMessage);
    }

    public static void resolve(String originalUrl, ResolveCallback callback) {
        resolveWithRetry(originalUrl, 0, callback);
    }

    private static void resolveWithRetry(final String url, final int retryCount, final ResolveCallback callback) {
        if (url == null || url.isEmpty()) {
            callback.onError("URL boşdur");
            return;
        }

        String lowerUrl = url.toLowerCase(Locale.ROOT).trim();
        // PHP və ya xüsusi parametrləri olan dinamik link deyilsə və birbaşa tanınan uzantıdırsa, vaxt itirmədən birbaşa qaytar
        if (!lowerUrl.contains(".php") && !lowerUrl.contains("cmd=") && !lowerUrl.contains("?")) {
            if (lowerUrl.endsWith(".m3u8")) {
                callback.onResolved(url, MimeTypes.APPLICATION_M3U8);
                return;
            } else if (lowerUrl.endsWith(".mpd")) {
                callback.onResolved(url, MimeTypes.APPLICATION_MPD);
                return;
            } else if (lowerUrl.endsWith(".ts")) {
                callback.onResolved(url, MimeTypes.VIDEO_MP2T);
                return;
            } else if (lowerUrl.endsWith(".mp4")) {
                callback.onResolved(url, MimeTypes.VIDEO_MP4);
                return;
            }
        }

        try {
            OkHttpClient client = NetworkUtils.getUnsafeOkHttpClient().newBuilder()
                    .followRedirects(true)
                    .followSslRedirects(true)
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(20, TimeUnit.SECONDS)
                    .build();

            Request request = new Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Referer", url.substring(0, Math.min(url.length(), url.indexOf("/", 8) != -1 ? url.indexOf("/", 8) : url.length())))
                    .header("Accept", "*/*")
                    .build();

            client.newCall(request).enqueue(new Callback() {
            private final Handler mainHandler = new Handler(Looper.getMainLooper());

            @Override
            public void onFailure(Call call, IOException e) {
                if (retryCount < MAX_RETRY_COUNT) {
                    mainHandler.postDelayed(() -> resolveWithRetry(url, retryCount + 1, callback), 1500);
                } else {
                    mainHandler.post(() -> callback.onError("Şəbəkə xətası: " + e.getLocalizedMessage()));
                }
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    if (retryCount < MAX_RETRY_COUNT) {
                        response.close();
                        mainHandler.postDelayed(() -> resolveWithRetry(url, retryCount + 1, callback), 1500);
                    } else {
                        mainHandler.post(() -> callback.onError("Server xətası: " + response.code()));
                        response.close();
                    }
                    return;
                }

                String finalUrl = response.request().url().toString();
                String contentType = response.header("Content-Type");
                if (contentType != null) {
                    contentType = contentType.toLowerCase(Locale.ROOT);
                }

                // Təhlükəsizlik qaydası: Əgər daxili/localhost ünvanına redirect edibsə, birbaşa açmağa çalışma, ilkin PHP linkini proxy kimi pleyerə ötür
                if (finalUrl.contains("localhost") || finalUrl.contains("127.0.0.1") || finalUrl.contains("://10.") || finalUrl.contains("://192.168.")) {
                    finalUrl = url; 
                }

                // 1. Mime-Type əsaslı təyinat
                if (contentType != null) {
                    if (contentType.contains("mpegurl") || contentType.contains("hls")) {
                        String resUrl = finalUrl;
                        mainHandler.post(() -> callback.onResolved(resUrl, MimeTypes.APPLICATION_M3U8));
                        response.close();
                        return;
                    } else if (contentType.contains("dash+xml")) {
                        String resUrl = finalUrl;
                        mainHandler.post(() -> callback.onResolved(resUrl, MimeTypes.APPLICATION_MPD));
                        response.close();
                        return;
                    } else if (contentType.contains("mp2t") || contentType.contains("mpegts")) {
                        String resUrl = finalUrl;
                        mainHandler.post(() -> callback.onResolved(resUrl, MimeTypes.VIDEO_MP2T));
                        response.close();
                        return;
                    }
else if (contentType.contains("video/mp4") || contentType.contains("video/m4v")) {
                        String resUrl = finalUrl;
                        mainHandler.post(() -> callback.onResolved(resUrl, MimeTypes.VIDEO_MP4));
                        response.close();
                        return;
                    } else if (contentType.contains("text/html") || contentType.contains("application/json") || contentType.contains("text/plain")) {
                        // Kontenti oxumaq lazımdır
                    } else if (contentType.startsWith("video/")) {
                        String resUrl = finalUrl;
                        String finalContentType = contentType;
                        mainHandler.post(() -> callback.onResolved(resUrl, finalContentType));
                        response.close();
                        return;
                    }
                }

                // 2. Response Body yoxlanışı (HTML/JSON/Plain Text/M3U8)
                ResponseBody body = response.body();
                if (body == null) {
                    String resUrl = finalUrl;
                    mainHandler.post(() -> callback.onResolved(resUrl, MimeTypes.VIDEO_MP2T));
                    return;
                }

                String bodyString = body.string().trim();
                response.close();

                // Dərin Qoxulama (Deep Sniffing): Cavabın içində m3u8 varsa, onu tap
                if (bodyString.contains(".m3u8")) {
                    int start = bodyString.indexOf("http");
                    if (start != -1) {
                        int end = bodyString.indexOf("\"", start);
                        if (end == -1) end = bodyString.indexOf("\n", start);
                        if (end == -1) end = bodyString.length();
                        String extractedUrl = bodyString.substring(start, end).trim();
                        mainHandler.post(() -> callback.onResolved(extractedUrl, MimeTypes.APPLICATION_M3U8));
                        return;
                    }
                }

                String lowerBody = bodyString.toLowerCase(Locale.ROOT);

                // HTML Səhifəsi/Xəta yoxlanışı
                if (lowerBody.contains("<html") || lowerBody.contains("<!doctype html") || lowerBody.contains("<body")) {
                    mainHandler.post(() -> callback.onError("Server media stream qaytarmadı (HTML xətası)"));
                    return;
                }

                // Birbaşa M3U8 kontentidirsə
                if (bodyString.startsWith("#EXTM3U")) {
                    String resUrl = finalUrl;
                    mainHandler.post(() -> callback.onResolved(resUrl, MimeTypes.APPLICATION_M3U8));
                    return;
                }

                // JSON Adapter Sistemi
                if (bodyString.startsWith("{") && bodyString.endsWith("}")) {
                    try {
                        JsonObject jsonObject = new Gson().fromJson(bodyString, JsonObject.class);
                        String extractedUrl = null;
                        if (jsonObject.has("url")) {
                            extractedUrl = jsonObject.get("url").getAsString();
                        } else if (jsonObject.has("stream")) {
                            extractedUrl = jsonObject.get("stream").getAsString();
                        } else if (jsonObject.has("link")) {
                            extractedUrl = jsonObject.get("link").getAsString();
                        } else if (jsonObject.has("data")) {
                            extractedUrl = jsonObject.get("data").getAsString();
                        }

                        if (extractedUrl != null && !extractedUrl.isEmpty()) {
                            // Çıxarılan yeni URL-i rekursiv olaraq həll et
                            resolveWithRetry(extractedUrl, retryCount, callback);
                            return;
                        }
                    } catch (Exception ignored) {}
                }

                // Plain Text URL yoxlanışı
                if ((bodyString.startsWith("http://") || bodyString.startsWith("https://")) && !bodyString.contains("\n")) {
                    resolveWithRetry(bodyString, retryCount, callback);
                    return;
                }

                // Heç bir formata uyğun gəlmədisə, default olaraq MPEG-TS və ya URL sonluğuna görə təyin et
                String finalUrlLower = finalUrl.toLowerCase(Locale.ROOT);
                String fallbackMime = MimeTypes.VIDEO_MP2T;
                if (finalUrlLower.contains("m3u8")) {
                    fallbackMime = MimeTypes.APPLICATION_M3U8;
                } else if (finalUrlLower.contains("mpd")) {
                    fallbackMime = MimeTypes.APPLICATION_MPD;
                } else if (finalUrlLower.contains("mp4")) {
                    fallbackMime = MimeTypes.VIDEO_MP4;
                }

                String resUrl = finalUrl;
                String resMime = fallbackMime;
                mainHandler.post(() -> callback.onResolved(resUrl, resMime));
            }
        });
    } catch (Exception e) {
        callback.onError("Sistem xətası: " + e.getLocalizedMessage());
    }
}
}
