package com.bykerimoff.player;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.extractor.DefaultExtractorsFactory;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.bumptech.glide.Glide;
import com.bykerimoff.player.adapters.RadioAdapter;
import com.bykerimoff.player.api.ApiClient;
import com.bykerimoff.player.databinding.ActivityRadioBinding;
import com.bykerimoff.player.models.RadioStation;
import com.bykerimoff.player.utils.WallpaperManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RadioActivity extends AppCompatActivity {
    private ActivityRadioBinding binding;
    private ExoPlayer exoPlayer;
    private RadioAdapter adapter;
    private List<RadioStation> radioList = new ArrayList<>();
    private AudioManager audioManager;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private String radioNumberInput = "";
    private final Handler radioSwitchHandler = new Handler(Looper.getMainLooper());
    private final Runnable radioSwitchRunnable = this::processNumericInput;

    private int currentMirrorIndex = 0;
    private final String[] mirrors = {
        "all.api.radio-browser.info",
        "de1.api.radio-browser.info",
        "at1.api.radio-browser.info",
        "nl1.api.radio-browser.info"
    };

    private android.os.CountDownTimer testCountDownTimer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRadioBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        WallpaperManager.INSTANCE.applyWallpaper(this, binding.ivAppBackground);
        
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        
        initPlayer();
        setupRecyclerView();
        loadRadios();

        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnRetryRadio.setOnClickListener(v -> {
            binding.btnRetryRadio.setVisibility(View.GONE);
            currentMirrorIndex = 0;
            loadRadios();
        });
        
        setupFocusEffect(binding.btnBack);
        setupFocusEffect(binding.btnRetryRadio);

        updateTestCountdown();
    }

    private void updateTestCountdown() {
        SharedPreferences prefs = getSharedPreferences("neoplay_prefs", MODE_PRIVATE);
        long expireTime = prefs.getLong("test_expire_time", 0L);

        if (expireTime > System.currentTimeMillis()) {
            long remainingSeconds = (expireTime - System.currentTimeMillis()) / 1000;
            startTestTimer((int) remainingSeconds);
        } else {
            if (testCountDownTimer != null) {
                testCountDownTimer.cancel();
            }
            binding.testBannerRadio.setVisibility(android.view.View.GONE);
        }
    }

    private void startTestTimer(int seconds) {
        if (testCountDownTimer != null) {
            testCountDownTimer.cancel();
        }

        testCountDownTimer = new android.os.CountDownTimer(seconds * 1000L, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                int remaining = (int) (millisUntilFinished / 1000);
                String timeLeft = formatTimeForTest(remaining);
                
                binding.testBannerRadio.setVisibility(android.view.View.VISIBLE);
                binding.testTitleRadio.setText("🧪 TEST REJİMİ");
                binding.testTimerRadio.setText("🧪 TEST - " + timeLeft);

                int color;
                if (remaining < 60) {
                    color = android.graphics.Color.parseColor("#ef4444");
                } else if (remaining < 300) {
                    color = android.graphics.Color.parseColor("#FFA500"); // Orange
                } else {
                    color = android.graphics.Color.parseColor("#D4AF37"); // Gold
                }
                
                binding.testTitleRadio.setTextColor(color);
                binding.testTimerRadio.setTextColor(color);

                // 5 dəqiqədən az qaldıqda marqatla
                if (remaining < 300) {
                    if (binding.testBannerRadio.getAnimation() == null) {
                        binding.testBannerRadio.startAnimation(android.view.animation.AnimationUtils.loadAnimation(RadioActivity.this, R.anim.blink));
                    }
                } else {
                    binding.testBannerRadio.clearAnimation();
                }
            }

            @Override
            public void onFinish() {
                binding.testTimerRadio.setText("⏱ Test bitdi!");
                binding.testTimerRadio.setTextColor(android.graphics.Color.parseColor("#ef4444"));
                
                new android.app.AlertDialog.Builder(RadioActivity.this)
                    .setTitle("🧪 Test Bitdi")
                    .setMessage("Test müddəti bitdi! Zəhmət olmasa dilerinizlə əlaqə saxlayın.")
                    .setCancelable(false)
                    .setPositiveButton("Bağla", (dialog, which) -> finish())
                    .show();
            }
        }.start();
    }

    private String formatTimeForTest(int seconds) {
        int hours = seconds / 3600;
        int minutes = (seconds % 3600) / 60;
        int secs = seconds % 60;
        return java.util.Locale.getDefault() != null ? 
            String.format(java.util.Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, secs) : 
            String.format("%02d:%02d:%02d", hours, minutes, secs);
    }

    @OptIn(markerClass = UnstableApi.class)
    private void initPlayer() {
        DefaultExtractorsFactory extractorsFactory = new DefaultExtractorsFactory();
        androidx.media3.exoplayer.source.DefaultMediaSourceFactory mediaSourceFactory = new androidx.media3.exoplayer.source.DefaultMediaSourceFactory(this, extractorsFactory);

        DefaultRenderersFactory renderersFactory = new DefaultRenderersFactory(this)
                .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
                .setEnableDecoderFallback(true);

        exoPlayer = new ExoPlayer.Builder(this, renderersFactory)
                .setMediaSourceFactory(mediaSourceFactory)
                .build();
        exoPlayer.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_BUFFERING) {
                    binding.radioBuffering.setVisibility(View.VISIBLE);
                    binding.tvStatus.setText("Yüklənir...");
                } else if (state == Player.STATE_READY) {
                    binding.radioBuffering.setVisibility(View.GONE);
                    binding.tvStatus.setText("Canlı Yayım");
                    // Logo animasiyası başladırıq (pulse)
                    binding.ivCurrentRadioLogo.startAnimation(AnimationUtils.loadAnimation(RadioActivity.this, R.anim.pulse));
                } else {
                    binding.radioBuffering.setVisibility(View.GONE);
                    binding.ivCurrentRadioLogo.clearAnimation();
                }
            }

            @Override
            public void onPlayerError(@NonNull androidx.media3.common.PlaybackException error) {
                binding.radioBuffering.setVisibility(View.GONE);
                binding.tvStatus.setText("Xəta baş verdi");
                binding.ivCurrentRadioLogo.clearAnimation();
                Toast.makeText(RadioActivity.this, "Yayım açılmadı", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupRecyclerView() {
        adapter = new RadioAdapter(radioList, radio -> {
            playRadio(radio);
        });
        binding.rvRadios.setLayoutManager(new LinearLayoutManager(this));
        binding.rvRadios.setAdapter(adapter);
        binding.rvRadios.requestFocus();
    }

    @OptIn(markerClass = UnstableApi.class)
    private void loadRadios() {
        binding.tvStatus.setText("Radiolar axtarılır...");
        binding.btnRetryRadio.setVisibility(View.GONE);
        radioList.clear();
        
        String mirror = mirrors[currentMirrorIndex];
        
        loadRadiosByCode(mirror, "AZ", () -> {
            loadRadiosByCode(mirror, "TR", () -> {
                loadRadiosByCode(mirror, "RU", () -> {
                    if (radioList.isEmpty()) {
                        tryNextMirrorOrShowError();
                    } else {
                        binding.tvStatus.setText("Siyahı hazır (" + radioList.size() + ")");
                    }
                });
            });
        });
    }

    private void tryNextMirrorOrShowError() {
        currentMirrorIndex++;
        if (currentMirrorIndex < mirrors.length) {
            loadRadios();
        } else {
            binding.tvStatus.setText("Bağlantı xətası");
            binding.btnRetryRadio.setVisibility(View.VISIBLE);
            binding.btnRetryRadio.requestFocus();
        }
    }

    @OptIn(markerClass = UnstableApi.class)
    private void loadRadiosByCode(String mirror, String code, Runnable onComplete) {
        String url = "https://" + mirror + "/json/stations/bycountrycodeexact/" + code;
        ApiClient.getService().getRadioStations(url).enqueue(new Callback<List<RadioStation>>() {
            @Override
            public void onResponse(Call<List<RadioStation>> call, Response<List<RadioStation>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    radioList.addAll(response.body());
                    adapter.notifyDataSetChanged();
                }
                onComplete.run();
            }

            @Override
            public void onFailure(Call<List<RadioStation>> call, Throwable t) {
                onComplete.run();
            }
        });
    }

    @OptIn(markerClass = UnstableApi.class)
    private void loadTopRadios() {
        String mirror = mirrors[0]; // Top radiolar üçün əsas mirror kifayətdir
        String url = "https://" + mirror + "/json/stations/topclick/50";
        ApiClient.getService().getRadioStations(url).enqueue(new Callback<List<RadioStation>>() {
            @Override
            public void onResponse(Call<List<RadioStation>> call, Response<List<RadioStation>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    radioList.addAll(response.body());
                    adapter.notifyDataSetChanged();
                    binding.tvStatus.setText("Siyahı hazır (Global)");
                }
            }

            @Override
            public void onFailure(Call<List<RadioStation>> call, Throwable t) {
                binding.tvStatus.setText("Bağlantı xətası");
                binding.btnRetryRadio.setVisibility(View.VISIBLE);
            }
        });
    }

    private void playRadio(RadioStation radio) {
        binding.tvCurrentRadioName.setText(radio.getName());
        
        Glide.with(this)
                .load(radio.getLogoUrl())
                .placeholder(android.R.drawable.ic_lock_silent_mode_off)
                .error(android.R.drawable.ic_lock_silent_mode_off)
                .into(binding.ivCurrentRadioLogo);

        MediaItem mediaItem = MediaItem.fromUri(radio.getStreamUrl());
        exoPlayer.setMediaItem(mediaItem);
        exoPlayer.prepare();
        exoPlayer.play();
    }

    private void setupFocusEffect(View view) {
        view.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                v.startAnimation(AnimationUtils.loadAnimation(this, R.anim.scale_up));
            } else {
                v.startAnimation(AnimationUtils.loadAnimation(this, R.anim.scale_down));
            }
        });
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // Rəqəm düymələrini tut (0-9)
        if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) {
            appendNumericInput(keyCode - KeyEvent.KEYCODE_0);
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            handler.postDelayed(this::updateVolumeUI, 50);
            return super.onKeyDown(keyCode, event);
        }
        return super.onKeyDown(keyCode, event);
    }

    private void updateVolumeUI() {
        if (audioManager == null) return;

        int maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        if (maxVolume == 0) maxVolume = 1;
        int percent = (currentVolume * 100) / maxVolume;

        binding.volumeLayout.setVisibility(View.VISIBLE);
        binding.volumeProgress.setProgress(percent);
        binding.tvVolumePercent.setText(percent + "%");

        handler.removeCallbacks(volumeHideRunnable);
        handler.postDelayed(volumeHideRunnable, 3000);
    }

    private final Runnable volumeHideRunnable = () -> {
        if (binding != null) {
            binding.volumeLayout.setVisibility(View.GONE);
        }
    };

    private void appendNumericInput(int digit) {
        radioNumberInput += digit;
        binding.tvNumericInput.setText(radioNumberInput);
        binding.tvNumericInput.setVisibility(View.VISIBLE);

        radioSwitchHandler.removeCallbacks(radioSwitchRunnable);
        radioSwitchHandler.postDelayed(radioSwitchRunnable, 1500);
    }

    private void processNumericInput() {
        if (radioNumberInput.isEmpty()) return;
        
        try {
            int targetIndex = Integer.parseInt(radioNumberInput) - 1; // 1-əsaslı giriş
            if (targetIndex >= 0 && targetIndex < radioList.size()) {
                RadioStation station = radioList.get(targetIndex);
                playRadio(station);
                adapter.setSelectedPosition(targetIndex);
                binding.rvRadios.scrollToPosition(targetIndex);
            } else {
                Toast.makeText(this, "Bu nömrədə radio yoxdur", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        radioNumberInput = "";
        binding.tvNumericInput.setVisibility(View.GONE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (exoPlayer != null) {
            exoPlayer.release();
        }
    }
}
