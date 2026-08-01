package com.bykerimoff.player.utils;

import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class UpdateManager {
    private static final String TAG = "UpdateManager";
    private static final String UPDATE_URL = "http://kanal65.xyz/by-kerimoff-player/update.json";
    
    public static boolean isUpdateFound = false; 
    public static boolean isCheckFinished = false; 
    
    private final Context context;
    private long downloadId = -1;

    public UpdateManager(Context context) {
        this.context = context;
    }

    @androidx.media3.common.util.UnstableApi
    public void checkForUpdates() {
        isCheckFinished = false;
        isUpdateFound = false;
        new Thread(() -> {
            try {
                Log.d(TAG, "Checking for updates...");
                okhttp3.Request request = new okhttp3.Request.Builder()
                        .url(UPDATE_URL)
                        .build();

                okhttp3.Response response = NetworkUtils.getSafeOkHttpClient().newCall(request).execute();
                if (!response.isSuccessful()) return;

                String responseBody = response.body().string();
                JSONObject json = new JSONObject(responseBody);
                int latestVersionCode = json.getInt("versionCode");
                String latestVersionName = json.getString("versionName");
                String apkUrl = json.getString("apkUrl");
                String notes = json.optString("releaseNotes", "");
                String announcement = json.optString("announcement", "");
                String announcementColor = json.optString("announcementColor", "");

                DataManager.setAdminAnnouncement(announcement);
                DataManager.setAdminAnnouncementColor(announcementColor);
                
                // Daimi yaddaşa yaz
                context.getSharedPreferences("neoplay_prefs", Context.MODE_PRIVATE)
                        .edit()
                        .putString("last_announcement", announcement)
                        .putString("last_announcement_color", announcementColor)
                        .apply();

                long currentVersionCode = getAppVersionCode();

                if (latestVersionCode > currentVersionCode) {
                    Log.d(TAG, "Update available: " + latestVersionName);
                    isUpdateFound = true; // Bayrağı aktiv et
                    showUpdateDialog(latestVersionName, apkUrl, notes);
                } else {
                    isUpdateFound = false;
                    Log.d(TAG, "App is up to date");
                }
                isCheckFinished = true;

            } catch (Exception e) {
                Log.e(TAG, "Update check failed: " + e.getMessage());
                isCheckFinished = true;
            }
        }).start();
    }

    private long getAppVersionCode() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                return context.getPackageManager().getPackageInfo(context.getPackageName(), 0).getLongVersionCode();
            } else {
                return context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionCode;
            }
        } catch (Exception e) {
            return 1;
        }
    }

    private void showUpdateDialog(String versionName, String apkUrl, String notes) {
        if (context instanceof android.app.Activity) {
            ((android.app.Activity) context).runOnUiThread(() -> {
                new AlertDialog.Builder(context)
                        .setTitle("Yeni Yeniləmə Mövcuddur (v" + versionName + ")")
                        .setMessage(notes.isEmpty() ? "Tətbiqi yeniləmək istəyirsiniz?" : notes)
                        .setPositiveButton("YENİLƏ", (dialog, which) -> downloadAndInstall(apkUrl))
                        .setNegativeButton("SONRA", null)
                        .setCancelable(false)
                        .show();
            });
        }
    }

    private void downloadAndInstall(String apkUrl) {
        Toast.makeText(context, "Yükləmə başlayır...", Toast.LENGTH_LONG).show();
        
        // Daxili qovluq istifadə edək (FileProvider üçün daha etibarlıdır)
        File file = new File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "update.apk");
        if (file.exists()) file.delete();

        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(apkUrl))
                .setTitle("Neo Play Yeniləmə")
                .setDescription("Yeni versiya yüklənir...")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationUri(Uri.fromFile(file));

        DownloadManager manager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        if (manager != null) {
            final long id = manager.enqueue(request);

            BroadcastReceiver receiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    long completedId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                    if (id == completedId) {
                        if (file.exists() && file.length() > 1024) {
                            installApk(file);
                        } else {
                            Log.e(TAG, "Downloaded file is invalid");
                            Toast.makeText(context, "Yükləmə xətası, fayl tapılmadı", Toast.LENGTH_SHORT).show();
                        }
                        context.unregisterReceiver(this);
                    }
                }
            };

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_EXPORTED);
            } else {
                context.registerReceiver(receiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
            }
        }
    }

    private void installApk(File file) {
        try {
            Uri apkUri;
            Intent intent = new Intent(Intent.ACTION_VIEW);
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                apkUri = FileProvider.getUriForFile(context, context.getPackageName() + ".provider", file);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } else {
                apkUri = Uri.fromFile(file);
            }

            intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            
        } catch (Exception e) {
            Log.e(TAG, "Installation failed: " + e.getMessage());
            Toast.makeText(context, "Quraşdırma xətası: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
