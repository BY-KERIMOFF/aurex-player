package com.bykerimoff.player.utils;

import android.util.Log;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import java.io.IOException;
import java.util.Date;

public class TelegramReporter {
    private static final String TAG = "TelegramReporter";
    private static final String BOT_TOKEN = "8788739459:AAHEi6p3Vqg0vrl0EZd1mh0Ij-LyqQS-93o";
    private static final String CHAT_ID = "1606464425";
    
    private static final OkHttpClient client = new OkHttpClient();

    public static void reportError(String channelName, String category, String mac, String errorMsg) {
        new Thread(() -> {
            try {
                String message = "⚠️ *KANAL XƏTASI* ⚠️\n\n" +
                        "📺 *Kanal:* " + channelName + "\n" +
                        "📂 *Kateqoriya:* " + category + "\n" +
                        "🆔 *MAC:* `" + mac + "`\n" +
                        "❌ *Xəta:* " + errorMsg + "\n" +
                        "🕒 *Vaxt:* " + new Date().toString();

                RequestBody formBody = new FormBody.Builder()
                        .add("chat_id", CHAT_ID)
                        .add("text", message)
                        .add("parse_mode", "Markdown")
                        .build();

                Request request = new Request.Builder()
                        .url("https://api.telegram.org/bot" + BOT_TOKEN + "/sendMessage")
                        .post(formBody)
                        .build();

                client.newCall(request).enqueue(new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        Log.e(TAG, "Telegram report failed: " + e.getMessage());
                    }

                    @Override
                    public void onResponse(Call call, Response response) throws IOException {
                        if (response.isSuccessful()) {
                            Log.d(TAG, "Telegram report sent successfully");
                        } else {
                            Log.e(TAG, "Telegram report failed: " + response.code());
                        }
                        response.close();
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Error building Telegram request: " + e.getMessage());
            }
        }).start();
    }
}
