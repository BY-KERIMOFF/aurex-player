package com.bykerimoff.player.utils;

import android.app.Activity;
import android.os.CountDownTimer;
import android.widget.Toast;

import java.lang.ref.WeakReference;
import java.util.Locale;

public class SleepTimerManager {
    private static SleepTimerManager instance;
    private CountDownTimer countDownTimer;
    private long remainingMillis = 0;
    private WeakReference<Activity> currentActivity;

    private SleepTimerManager() {}

    public static synchronized SleepTimerManager getInstance() {
        if (instance == null) {
            instance = new SleepTimerManager();
        }
        return instance;
    }

    public void startTimer(int minutes, Activity activity) {
        cancelTimer();
        this.currentActivity = new WeakReference<>(activity);
        
        long durationMillis = (long) minutes * 60 * 1000;
        
        countDownTimer = new CountDownTimer(durationMillis, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                remainingMillis = millisUntilFinished;
                
                // Son 1 dəqiqə qaldıqda xəbərdarlıq et
                if (millisUntilFinished <= 60000 && millisUntilFinished > 59000) {
                    showWarning("Tətbiq 1 dəqiqə sonra bağlanacaq");
                }
            }

            @Override
            public void onFinish() {
                remainingMillis = 0;
                closeApp();
            }
        }.start();
        
        Toast.makeText(activity, "Yuxu taymeri təyin edildi: " + minutes + " dəqiqə", Toast.LENGTH_SHORT).show();
    }

    public void cancelTimer() {
        if (countDownTimer != null) {
            countDownTimer.cancel();
            countDownTimer = null;
        }
        remainingMillis = 0;
    }

    public String getFormattedRemainingTime() {
        if (remainingMillis <= 0) return "";
        long minutes = (remainingMillis / 1000) / 60;
        long seconds = (remainingMillis / 1000) % 60;
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
    }

    public boolean isRunning() {
        return countDownTimer != null;
    }

    private void showWarning(String message) {
        Activity activity = currentActivity != null ? currentActivity.get() : null;
        if (activity != null && !activity.isFinishing()) {
            activity.runOnUiThread(() -> Toast.makeText(activity, message, Toast.LENGTH_LONG).show());
        }
    }

    private void closeApp() {
        Activity activity = currentActivity != null ? currentActivity.get() : null;
        if (activity != null) {
            activity.finishAffinity();
            System.exit(0);
        }
    }
}
