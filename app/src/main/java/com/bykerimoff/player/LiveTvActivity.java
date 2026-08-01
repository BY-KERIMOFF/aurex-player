package com.bykerimoff.player;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.CaptionStyleCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.bykerimoff.player.adapters.CategoryAdapter;
import com.bykerimoff.player.adapters.ChannelAdapter;
import com.bykerimoff.player.api.ApiClient;
import com.bykerimoff.player.databinding.ActivityLiveTvBinding;
import com.bykerimoff.player.models.Category;
import com.bykerimoff.player.models.Channel;
import com.bykerimoff.player.models.XtreamCategory;
import com.bykerimoff.player.models.XtreamChannel;
import com.bykerimoff.player.utils.DataManager;
import com.bykerimoff.player.utils.FavoriteManager;
import com.bykerimoff.player.utils.M3UParser;
import com.bykerimoff.player.utils.NetworkUtils;
import com.bykerimoff.player.utils.PinDialog;
import com.bykerimoff.player.utils.XMLTVParser;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LiveTvActivity extends AppCompatActivity {

    private ActivityLiveTvBinding binding;
    private ExoPlayer miniPlayer;
    private CategoryAdapter categoryAdapter;
    private ChannelAdapter channelAdapter;
    private final List<Category> categories = new ArrayList<>();
    private final List<Category> originalCategories = new ArrayList<>();
    private final List<Channel> channels = new ArrayList<>();
    private final List<Channel> allChannels = new ArrayList<>();
    private final Map<String, List<Channel>> channelMap = new HashMap<>();
    
    private boolean isVodMode = false;
    private String playlistType;
    private String viewMode = "classic";
    private String m3uUrl;
    private String xtHost, xtUser, xtPass;
    private boolean isAdultEnabled = true;

    private android.os.CountDownTimer testCountDownTimer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        com.bykerimoff.player.utils.ThemeManager.INSTANCE.applyTheme(this);
        super.onCreate(savedInstanceState);
        binding = ActivityLiveTvBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        com.bykerimoff.player.utils.WallpaperManager.INSTANCE.applyWallpaper(this, binding.ivAppBackground);

        SharedPreferences prefs = getSharedPreferences("neoplay_prefs", MODE_PRIVATE);
        playlistType = prefs.getString("playlist_type", "m3u");
        viewMode = prefs.getString("view_mode", "classic");
        m3uUrl = prefs.getString("m3u_url", "");
        xtHost = prefs.getString("xtream_host", "");
        xtUser = prefs.getString("xtream_user", "");
        xtPass = prefs.getString("xtream_pass", "");
        isAdultEnabled = prefs.getBoolean("is_adult_enabled", true);

        // Xtream və ya M3U rejimini dəqiq təyin et
        boolean isXtream = "xtream".equalsIgnoreCase(playlistType) || (xtHost != null && !xtHost.trim().isEmpty() && xtUser != null && !xtUser.trim().isEmpty());

        initMiniPlayer();
        setupRecyclerViews();
        setupSearch();
        
        binding.btnBack.setOnClickListener(v -> finish());
        
        String currentPlaylistId = isXtream ? (xtHost + xtUser) : m3uUrl;
        if (DataManager.getPlaylistIdentifier().equals(currentPlaylistId) && !DataManager.getAllChannels().isEmpty()) {
            // Məlumatlar keşdə var, sürətli yüklə
            allChannels.clear();
            allChannels.addAll(DataManager.getAllChannels());
            channelMap.clear();
            channelMap.putAll(DataManager.getCurrentChannelMap());
            categories.clear();
            categories.addAll(DataManager.getCurrentCategoryList());
            
            categoryAdapter.notifyDataSetChanged();
            handleStartCategory();
            
            // Arxa planda yenilə (səssiz)
            refreshDataInBackground(isXtream);
        } else {
            // İlk dəfə və ya yeni playlist yüklə
            if (isXtream) {
                loadXtreamData();
            } else {
                loadM3UData();
            }
            DataManager.setPlaylistIdentifier(currentPlaylistId);
        }

        // Manual EPG-ni yoxla
        String manualEpg = prefs.getString("manual_epg_url", "");
        if (!manualEpg.isEmpty()) {
            XMLTVParser.downloadAndParse(manualEpg);
        }
        
        updateTestCountdown();
    }

    private void refreshDataInBackground(boolean isXtream) {
        // Keş olsa belə arxa planda yeniləyək ki, kanal siyahısı aktual qalsın
        if (isXtream) {
            loadXtreamDataSilent();
        } else {
            loadM3UDataSilent();
        }
    }

    private void loadM3UDataSilent() {
        loadM3UFromUrl(m3uUrl);
    }

    private void loadXtreamDataSilent() {
        loadXtreamDataInternal(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Ayarlardan qayıtdıqda sıralama dəyişə bilər
        if (!categories.isEmpty()) {
            updateCategoryCounts();
        }
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
            binding.testBannerLive.setVisibility(android.view.View.GONE);
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
                
                binding.testBannerLive.setVisibility(android.view.View.VISIBLE);
                binding.testTitleLive.setText("TEST REJİMİ");
                binding.testTimerLive.setText("Test: " + timeLeft);

                int color;
                if (remaining < 60) {
                    color = android.graphics.Color.RED;
                } else if (remaining < 300) {
                    color = android.graphics.Color.parseColor("#FFA500"); // Orange
                } else {
                    color = android.graphics.Color.parseColor("#D4AF37"); // Gold
                }
                
                binding.testTitleLive.setTextColor(color);
                binding.testTimerLive.setTextColor(color);
                binding.testTimerLive.setBackgroundColor(android.graphics.Color.TRANSPARENT);
            }

            @Override
            public void onFinish() {
                binding.testTimerLive.setText("⏱ Test bitdi!");
                binding.testTimerLive.setTextColor(android.graphics.Color.parseColor("#ef4444"));
                
                new android.app.AlertDialog.Builder(LiveTvActivity.this)
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

    private void setupSearch() {
        Handler searchHandler = new Handler(Looper.getMainLooper());
        Runnable searchRunnable = () -> filterChannelsBySearch(binding.etSearch.getText().toString());

        binding.etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchHandler.removeCallbacks(searchRunnable);
                searchHandler.postDelayed(searchRunnable, 500); 
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void filterChannelsBySearch(String query) {
        channels.clear();
        for (Channel channel : allChannels) {
            if (channel.getName().toLowerCase().contains(query.toLowerCase())) {
                channels.add(channel);
            }
        }
        channelAdapter.notifyDataSetChanged();
    }

    @androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
    private void initMiniPlayer() {
        androidx.media3.datasource.okhttp.OkHttpDataSource.Factory dataSourceFactory = NetworkUtils.getDataSourceFactory(this);
        
        androidx.media3.extractor.DefaultExtractorsFactory extractorsFactory = new androidx.media3.extractor.DefaultExtractorsFactory()
                .setTsExtractorFlags(androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES 
                                   | androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS
                                   | androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory.FLAG_IGNORE_SPLICE_INFO_STREAM
                                   | androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory.FLAG_ENABLE_HDMV_DTS_AUDIO_STREAMS);

        androidx.media3.exoplayer.source.DefaultMediaSourceFactory mediaSourceFactory = new androidx.media3.exoplayer.source.DefaultMediaSourceFactory(dataSourceFactory, extractorsFactory);

        androidx.media3.exoplayer.trackselection.DefaultTrackSelector trackSelector = new androidx.media3.exoplayer.trackselection.DefaultTrackSelector(this);
        trackSelector.setParameters(trackSelector.buildUponParameters()
                .setExceedAudioConstraintsIfNecessary(true)
                .setExceedRendererCapabilitiesIfNecessary(true)
                .setExceedVideoConstraintsIfNecessary(true)
                .setTunnelingEnabled(false)
        );

        // Geniş audio/video kodek dəstəyi (AC3, DTS və s. üçün)
        androidx.media3.exoplayer.DefaultRenderersFactory renderersFactory = new androidx.media3.exoplayer.DefaultRenderersFactory(this)
                .setExtensionRendererMode(androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
                .setEnableDecoderFallback(true);

        // Daha mükəmməl buferləmə ayarları (50-60 FPS üçün)
        androidx.media3.exoplayer.DefaultLoadControl loadControl = new androidx.media3.exoplayer.DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                        20000, // minBufferMs
                        60000, // maxBufferMs
                        2000,  // bufferForPlaybackMs
                        5000   // bufferForPlaybackAfterRebufferMs
                )
                .setPrioritizeTimeOverSizeThresholds(true)
                .build();

        miniPlayer = new ExoPlayer.Builder(this, renderersFactory)
                .setMediaSourceFactory(mediaSourceFactory)
                .setTrackSelector(trackSelector)
                .setLoadControl(loadControl)
                .build();
        binding.miniPlayerView.setPlayer(miniPlayer);
        
        miniPlayer.addListener(new androidx.media3.common.Player.Listener() {
            @Override
            public void onPlayerError(@androidx.annotation.NonNull androidx.media3.common.PlaybackException error) {
                // Mini pleyer xətası zamanı avtomatik yenidən qoşulma cəhdi
                miniPlayer.prepare();
                miniPlayer.play();
            }
        });

        // Mini-player üçün altyazı stili
        CaptionStyleCompat style = new CaptionStyleCompat(
                android.graphics.Color.WHITE,
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
                CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                android.graphics.Color.BLACK,
                null
        );
        if (binding.miniPlayerView.getSubtitleView() != null) {
            binding.miniPlayerView.getSubtitleView().setApplyEmbeddedStyles(false);
            binding.miniPlayerView.getSubtitleView().setApplyEmbeddedFontSizes(false);
            binding.miniPlayerView.getSubtitleView().setStyle(style);
            binding.miniPlayerView.getSubtitleView().setFixedTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 18f);
        }
    }

    private void setupRecyclerViews() {
        categoryAdapter = new CategoryAdapter(categories, category -> {
            if ("Sevimlilər".equals(category.getName())) {
                setVodMode(false);
                loadFavorites();
            } else if (M3UParser.isSensitiveCategory(category.getName())) {
                PinDialog.show(this, new PinDialog.PinListener() {
                    @Override
                    public void onSuccess() {
                        checkAndSetVodMode(category);
                        filterChannelsByCategory(category);
                    }

                    @Override
                    public void onCancel() {}
                });
            } else {
                checkAndSetVodMode(category);
                filterChannelsByCategory(category);
            }
            binding.rvChannels.requestFocus();
        });
        binding.rvCategories.setLayoutManager(new LinearLayoutManager(this));
        binding.rvCategories.setAdapter(categoryAdapter);
        binding.rvCategories.setHasFixedSize(true);
        binding.rvCategories.setItemViewCacheSize(20);

        FavoriteManager favoriteManager = new FavoriteManager(this);
        channelAdapter = new ChannelAdapter(channels, new ChannelAdapter.OnChannelClickListener() {
            @Override
            public void onChannelClick(Channel channel) {
                playChannel(channel, channels.indexOf(channel), channels);
            }

            @Override
            public void onChannelFocus(Channel channel) {
                if (!isVodMode) {
                    playMiniStream(channel);
                }
            }

            @Override
            public void onChannelLongClick(Channel channel) {
                boolean isAdded = favoriteManager.toggleFavorite(channel.getId());
                channelAdapter.notifyDataSetChanged();
                String message = isAdded ? "Sevimli siyahısına əlavə edildi" : "Sevimli siyahısından çıxarıldı";
                Toast.makeText(LiveTvActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
        binding.rvChannels.setLayoutManager(new LinearLayoutManager(this));
        binding.rvChannels.setAdapter(channelAdapter);
        binding.rvChannels.setHasFixedSize(true);
        binding.rvChannels.setItemViewCacheSize(50);
    }

    private void loadFavorites() {
        FavoriteManager favoriteManager = new FavoriteManager(this);
        channels.clear();
        for (Channel channel : allChannels) {
            if (favoriteManager.isFavorite(channel.getId())) {
                channels.add(channel);
            }
        }
        channelAdapter.notifyDataSetChanged();
    }

    private void playMiniStream(Channel channel) {
        binding.tvCurrentChannel.setText(channel.getName());
        binding.tvEpgTitle.setText("Yüklənir...");
        
        com.bumptech.glide.Glide.with(this)
                .load(channel.getLogoUrl())
                .placeholder(R.drawable.default_logo)
                .error(R.drawable.default_logo)
                .into(binding.ivCurrentChannelLogo);

        String url = channel.getStreamUrl();
        MediaItem.Builder builder = new MediaItem.Builder();
        if (url != null) {
            builder.setUri(android.net.Uri.parse(url));
            String lower = url.toLowerCase(java.util.Locale.ROOT);
            if (lower.contains("m3u8") || lower.contains("stream.php") || lower.contains(".php") || lower.contains("/hls/")) {
                builder.setMimeType(androidx.media3.common.MimeTypes.APPLICATION_M3U8);
            } else if (lower.contains(".ts") || lower.contains("output=ts") || lower.contains("output=mpegts") || lower.contains("/live/") || lower.contains("/mpegts")) {
                builder.setMimeType(androidx.media3.common.MimeTypes.VIDEO_MP2T);
            } else if (lower.contains(".mpd")) {
                builder.setMimeType(androidx.media3.common.MimeTypes.APPLICATION_MPD);
            }
        }
        miniPlayer.setMediaItem(builder.build());
        miniPlayer.prepare();
        miniPlayer.play();
    }

    private void openExternalPlayer(Channel channel) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(android.net.Uri.parse(channel.getStreamUrl()), "video/*");
            intent.putExtra("title", channel.getName());
            startActivity(Intent.createChooser(intent, "Pleyer seçin"));
        } catch (Exception e) {
            Toast.makeText(this, "Xarici pleyer açılmadı: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void playChannel(Channel channel, int index, List<Channel> list) {
        SharedPreferences prefs = getSharedPreferences("neoplay_prefs", MODE_PRIVATE);
        if (prefs.getBoolean("use_external_player", false)) {
            openExternalPlayer(channel);
        } else {
            DataManager.setCurrentChannelList(list);
            Intent intent = new Intent(this, PlayerActivity.class);
            intent.putExtra("channel_index", index);
            startActivity(intent);
        }
    }

    private void loadM3UData() {
        binding.mainLoadingLayout.setVisibility(android.view.View.VISIBLE);
        loadM3UFromUrl(m3uUrl);
    }

    private void loadXtreamData() {
        loadXtreamDataInternal(true);
    }

    private void loadXtreamDataInternal(boolean showProgress) {
        if (xtHost.isEmpty() || xtUser.isEmpty() || xtPass.isEmpty()) {
            Toast.makeText(this, "Xtream giriş məlumatları tapılmadı", Toast.LENGTH_SHORT).show();
            return;
        }

        if (showProgress) binding.mainLoadingLayout.setVisibility(View.VISIBLE);

        String filterCategory = getIntent().getStringExtra("filter_category");
        String cleanHost = xtHost != null ? xtHost.trim().replaceAll("/+$", "") : "";
        if (!cleanHost.startsWith("http://") && !cleanHost.startsWith("https://")) {
            cleanHost = "http://" + cleanHost;
        }
        
        String encodedUser = "";
        String encodedPass = "";
        try {
            encodedUser = java.net.URLEncoder.encode(xtUser, "UTF-8");
            encodedPass = java.net.URLEncoder.encode(xtPass, "UTF-8");
        } catch (Exception e) {
            encodedUser = xtUser;
            encodedPass = xtPass;
        }

        String baseUrl = cleanHost + "/player_api.php?username=" + encodedUser + "&password=" + encodedPass;
        
        if ("VOD_MOVIES".equals(filterCategory)) {
            String catUrl = baseUrl + "&action=get_vod_categories";
            ApiClient.getService().getXtreamVodCategories(catUrl).enqueue(new Callback<List<XtreamCategory>>() {
                @Override
                public void onResponse(Call<List<XtreamCategory>> call, Response<List<XtreamCategory>> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        String streamUrl = baseUrl + "&action=get_vod_streams";
                        processXtreamCategories(response.body(), streamUrl, "movie");
                    } else {
                        runOnUiThread(() -> binding.mainLoadingLayout.setVisibility(android.view.View.GONE));
                    }
                }
                @Override
                public void onFailure(Call<List<XtreamCategory>> call, Throwable t) {
                    runOnUiThread(() -> binding.mainLoadingLayout.setVisibility(android.view.View.GONE));
                }
            });
        } else if ("VOD_SERIES".equals(filterCategory)) {
            String catUrl = baseUrl + "&action=get_series_categories";
            ApiClient.getService().getXtreamSeriesCategories(catUrl).enqueue(new Callback<List<XtreamCategory>>() {
                @Override
                public void onResponse(Call<List<XtreamCategory>> call, Response<List<XtreamCategory>> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        String streamUrl = baseUrl + "&action=get_series";
                        processXtreamCategories(response.body(), streamUrl, "series");
                    } else {
                        runOnUiThread(() -> binding.mainLoadingLayout.setVisibility(android.view.View.GONE));
                    }
                }
                @Override
                public void onFailure(Call<List<XtreamCategory>> call, Throwable t) {
                    runOnUiThread(() -> binding.mainLoadingLayout.setVisibility(android.view.View.GONE));
                }
            });
        } else {
            String catUrl = baseUrl + "&action=get_live_categories";
            String streamUrl = baseUrl + "&action=get_live_streams";

            android.util.Log.d("XTREAM_DEBUG", "Live Cat URL: " + catUrl);
            
            // Xam cavabı yoxlamaq üçün loglama
            ApiClient.getService().getRawResponse(catUrl).enqueue(new Callback<okhttp3.ResponseBody>() {
                @Override
                public void onResponse(Call<okhttp3.ResponseBody> call, Response<okhttp3.ResponseBody> response) {
                    try {
                        if (response.isSuccessful() && response.body() != null) {
                            String rawJson = response.body().string();
                            android.util.Log.d("XTREAM_RAW", "Raw JSON Response length: " + rawJson.length());
                            if (rawJson.length() < 500) {
                                android.util.Log.d("XTREAM_RAW", "Raw JSON Content: " + rawJson);
                            } else {
                                android.util.Log.d("XTREAM_RAW", "Raw JSON Content (start): " + rawJson.substring(0, 500));
                            }
                        } else {
                            android.util.Log.e("XTREAM_RAW", "Raw Response failed: " + response.code());
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                @Override
                public void onFailure(Call<okhttp3.ResponseBody> call, Throwable t) {
                    android.util.Log.e("XTREAM_RAW", "Raw Response failure: " + t.getMessage());
                }
            });

            ApiClient.getService().getXtreamCategories(catUrl).enqueue(new Callback<List<XtreamCategory>>() {
                @Override
                public void onResponse(Call<List<XtreamCategory>> call, Response<List<XtreamCategory>> response) {
                    android.util.Log.d("XTREAM_DEBUG", "Live Categories response success: " + response.isSuccessful() + ", size: " + (response.body() != null ? response.body().size() : "null"));
                    if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                        processXtreamCategories(response.body(), streamUrl, "live");
                    } else {
                        runOnUiThread(() -> binding.mainLoadingLayout.setVisibility(android.view.View.GONE));
                        String errorCode = response.code() > 0 ? " (Kod: " + response.code() + ")" : "";
                        android.util.Log.e("XTREAM_DEBUG", "Live Categories boş və ya xətalı gəldi. M3U-ya keçid edilir...");
                        runOnUiThread(() -> Toast.makeText(LiveTvActivity.this, "Xtream məlumatları alınmadı" + errorCode + ", M3U yüklənir...", Toast.LENGTH_LONG).show());
                        loadM3UData();
                    }
                }
                @Override
                public void onFailure(Call<List<XtreamCategory>> call, Throwable t) {
                    runOnUiThread(() -> binding.mainLoadingLayout.setVisibility(android.view.View.GONE));
                    android.util.Log.e("XTREAM_DEBUG", "Live Categories failure: " + t.getMessage(), t);
                    loadM3UData(); // Xəta zamanı M3U-ya keçid
                }
            });
        }
    }

    private void processXtreamCategories(List<XtreamCategory> xtCats, String streamUrl, String type) {
        categories.clear();
        originalCategories.clear();
        
        categories.add(new Category("0", "Sevimlilər", 0));
        for (XtreamCategory xc : xtCats) {
            if (!isAdultEnabled && M3UParser.isSensitiveCategory(xc.getName())) {
                continue;
            }
            categories.add(new Category(xc.getId(), xc.getName(), 0));
        }
        
        originalCategories.addAll(categories);
        runOnUiThread(() -> categoryAdapter.notifyDataSetChanged());

        if (type.equals("live")) {
            ApiClient.getService().getXtreamChannels(streamUrl).enqueue(new Callback<List<XtreamChannel>>() {
                @Override
                public void onResponse(Call<List<XtreamChannel>> call, Response<List<XtreamChannel>> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        processXtreamChannels(response.body());
                    } else {
                        runOnUiThread(() -> binding.mainLoadingLayout.setVisibility(android.view.View.GONE));
                    }
                }
                @Override
                public void onFailure(Call<List<XtreamChannel>> call, Throwable t) {
                    runOnUiThread(() -> binding.mainLoadingLayout.setVisibility(android.view.View.GONE));
                }
            });
        } else {
            fetchXtreamVod(streamUrl, type);
        }
    }

    private void fetchXtreamVod(String url, String type) {
        ApiClient.getService().getXtreamVod(url).enqueue(new Callback<List<XtreamChannel>>() {
            @Override
            public void onResponse(Call<List<XtreamChannel>> call, Response<List<XtreamChannel>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    processXtreamVod(response.body(), type);
                } else {
                    runOnUiThread(() -> binding.mainLoadingLayout.setVisibility(android.view.View.GONE));
                }
            }
            @Override
            public void onFailure(Call<List<XtreamChannel>> call, Throwable t) {
                runOnUiThread(() -> binding.mainLoadingLayout.setVisibility(android.view.View.GONE));
            }
        });
    }

    private void processXtreamVod(List<XtreamChannel> vods, String type) {
        android.util.Log.d("XTREAM_DEBUG", "VOD size received: " + (vods != null ? vods.size() : "null"));
        if (vods == null || vods.isEmpty()) {
            runOnUiThread(() -> {
                runOnUiThread(() -> binding.mainLoadingLayout.setVisibility(android.view.View.GONE));
                handleStartCategory();
            });
            return;
        }

        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            List<Channel> tempAll = new ArrayList<>();
            Map<String, List<Channel>> tempMap = new HashMap<>();

            String cleanHost = xtHost != null ? xtHost.trim().replaceAll("/+$", "") : "";
            if (!cleanHost.isEmpty() && !cleanHost.startsWith("http://") && !cleanHost.startsWith("https://")) {
                cleanHost = "http://" + cleanHost;
            }

            String vodTypePath = type.equals("series") ? "series" : "movie";
            
            for (XtreamChannel xc : vods) {
                if (xc.getStreamId() == null || xc.getStreamId().isEmpty() || xc.getName() == null) continue;
                
                String logo = xc.getLogo();
                // Filmlər üçün loqo axtarışını sürətləndirək (yalnız boşdursa)
                if (logo == null || logo.isEmpty()) {
                    logo = ""; // Filmlərdə loqo axtarışı çox vaxt apardığı üçün onu asinxron edəcəyik və ya buraxacağıq
                }
                
                String ext = type.equals("series") ? "mkv" : xc.getContainerExtension();
                String streamLink = cleanHost + "/" + vodTypePath + "/" + xtUser + "/" + xtPass + "/" + xc.getStreamId() + "." + ext;
                Channel channel = new Channel(xc.getStreamId(), xc.getName(), logo, com.bykerimoff.player.utils.SecurityUtils.encryptUrl(streamLink), xc.getCategoryId());
                tempAll.add(channel);
                
                String catId = xc.getCategoryId();
                if (!tempMap.containsKey(catId)) tempMap.put(catId, new ArrayList<>());
                tempMap.get(catId).add(channel);
            }
            
            runOnUiThread(() -> {
                binding.mainLoadingProgress.setVisibility(android.view.View.GONE);
                allChannels.clear();
                allChannels.addAll(tempAll);
                channelMap.clear();
                channelMap.putAll(tempMap);
                
                DataManager.setAllChannels(allChannels);
                DataManager.setCurrentChannelMap(channelMap);
                updateCategoryCounts();
                handleStartCategory();
            });
        });
    }

    private void processXtreamChannels(List<XtreamChannel> xtChannels) {
        android.util.Log.d("XTREAM_DEBUG", "Live Channels size received: " + (xtChannels != null ? xtChannels.size() : "null"));
        
        // Asinxron olaraq siyahını dərhal emal et və UI-ı bloklama
        Executors.newSingleThreadExecutor().execute(() -> {
            List<Channel> tempAll = new ArrayList<>();
            Map<String, List<Channel>> tempMap = new HashMap<>();
            String cleanHost = xtHost != null ? xtHost.trim().replaceAll("/+$", "") : "";
            if (!cleanHost.isEmpty() && !cleanHost.startsWith("http://") && !cleanHost.startsWith("https://")) {
                cleanHost = "http://" + cleanHost;
            }

            if (xtChannels != null && !xtChannels.isEmpty()) {
                for (XtreamChannel xc : xtChannels) {
                    if (xc.getStreamId() == null || xc.getName() == null) continue;
                    
                    String logo = xc.getLogo();
                    if (logo == null || logo.isEmpty()) {
                        logo = com.bykerimoff.player.utils.LogoManager.INSTANCE.getLogoForChannel(xc.getName());
                    }
                    
                    String streamLink = cleanHost + "/live/" + xtUser + "/" + xtPass + "/" + xc.getStreamId() + ".ts";
                    Channel channel = new Channel(xc.getStreamId(), xc.getName(), logo != null ? logo : "", com.bykerimoff.player.utils.SecurityUtils.encryptUrl(streamLink), xc.getCategoryId());
                    tempAll.add(channel);
                    
                    String catId = xc.getCategoryId() != null ? xc.getCategoryId() : "0";
                    if (!tempMap.containsKey(catId)) tempMap.put(catId, new ArrayList<>());
                    tempMap.get(catId).add(channel);
                }
            }
            
            runOnUiThread(() -> {
                binding.mainLoadingProgress.setVisibility(android.view.View.GONE);
                allChannels.clear();
                allChannels.addAll(tempAll);
                channelMap.clear();
                channelMap.putAll(tempMap);
                
                // Pleyer üçün məlumatları yadda saxla
                DataManager.setAllChannels(allChannels);
                DataManager.setCurrentChannelMap(channelMap);
                
                updateCategoryCounts();
                handleStartCategory();
            });
        });
    }

    private void updateCategoryCounts() {
        if (originalCategories.isEmpty()) return;

        Map<String, Integer> counts = new HashMap<>();
        for (Map.Entry<String, List<Channel>> entry : channelMap.entrySet()) {
            counts.put(entry.getKey(), entry.getValue().size());
        }
        
        FavoriteManager fm = new FavoriteManager(this);
        int favs = 0;
        if (allChannels.size() < 5000) {
            for (Channel c : allChannels) if (fm.isFavorite(c.getId())) favs++;
        }

        // Əvvəlcə orijinal siyahıda sayları yeniləyək
        for (int i = 0; i < originalCategories.size(); i++) {
            Category cat = originalCategories.get(i);
            if (cat.getId().equals("0")) {
                originalCategories.set(i, new Category("0", "Sevimlilər", favs));
            } else {
                Integer count = counts.get(cat.getId());
                originalCategories.set(i, new Category(cat.getId(), cat.getName(), count == null ? 0 : count));
            }
        }
        
        // İndi göstərilən siyahını yeniləyək
        categories.clear();
        categories.addAll(originalCategories);
        
        // Sıralamanı tətbiq et
        sortCategories(categories);
        DataManager.setCurrentCategoryList(categories);
        
        runOnUiThread(() -> categoryAdapter.notifyDataSetChanged());
    }

    private void sortCategories(List<Category> list) {
        if (list == null || list.size() <= 1) return;

        SharedPreferences prefs = getSharedPreferences("neoplay_prefs", MODE_PRIVATE);
        String sortMode = prefs.getString("category_sort_mode", "default");

        if ("default".equals(sortMode)) return;

        // Sevimlilər həmişə başda qalmalıdır
        Category favorites = null;
        for (Category c : list) {
            if (c.getId().equals("0") || "Sevimlilər".equals(c.getName())) {
                favorites = c;
                break;
            }
        }

        if (favorites != null) list.remove(favorites);

        if ("name".equals(sortMode)) {
            java.util.Collections.sort(list, (c1, c2) -> c1.getName().compareToIgnoreCase(c2.getName()));
        } else if ("count".equals(sortMode)) {
            java.util.Collections.sort(list, (c1, c2) -> Integer.compare(c2.getChannelCount(), c1.getChannelCount()));
        }

        if (favorites != null) list.add(0, favorites);
    }

    private void loadM3UFromUrl(String urlString) {
        if (urlString == null || urlString.trim().isEmpty()) return;
        
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                okhttp3.OkHttpClient client = com.bykerimoff.player.utils.NetworkUtils.getSafeOkHttpClient();
                okhttp3.Request request = new okhttp3.Request.Builder()
                        .url(urlString)
                        .build();

                try (okhttp3.Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        android.util.Log.e("M3U_LOAD", "M3U yükləmə uğursuz: " + response.code());
                        runOnUiThread(() -> binding.mainLoadingLayout.setVisibility(android.view.View.GONE));
                        return;
                    }

                    String m3uContent = response.body().string();
                    List<Channel> parsedChannels = M3UParser.parse(m3uContent);
                    
                    // Loqoları yoxla
                    for (Channel ch : parsedChannels) {
                        if (ch.getLogoUrl() == null || ch.getLogoUrl().isEmpty()) {
                            String globalLogo = com.bykerimoff.player.utils.LogoManager.INSTANCE.getLogoForChannel(ch.getName());
                            if (globalLogo != null) ch.setLogoUrl(globalLogo);
                        }
                    }
                    
                    // EPG yükləməsini başlat
                    String epgUrl = DataManager.getGlobalEpgUrl();
                    if (!epgUrl.isEmpty()) {
                        XMLTVParser.downloadAndParse(epgUrl);
                    }
                    
                    runOnUiThread(() -> {
                        allChannels.clear();
                        allChannels.addAll(parsedChannels);
                        processLoadedChannels();
                    });
                }
            } catch (Exception e) {
                android.util.Log.e("M3U_LOAD", "M3U xətası: " + e.getMessage());
                runOnUiThread(() -> binding.mainLoadingLayout.setVisibility(android.view.View.GONE));
                e.printStackTrace();
            }
        });
    }

    private void processLoadedChannels() {
        runOnUiThread(() -> binding.mainLoadingLayout.setVisibility(android.view.View.GONE));
        
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            Map<String, List<Channel>> tempMap = new HashMap<>();
            Set<String> seenCats = new LinkedHashSet<>();
            FavoriteManager favoriteManager = new FavoriteManager(this);
            int favCount = 0;

            for (Channel channel : allChannels) {
                if (favoriteManager.isFavorite(channel.getId())) favCount++;
                String catName = channel.getCategoryName();
                if (!tempMap.containsKey(catName)) tempMap.put(catName, new ArrayList<>());
                tempMap.get(catName).add(channel);
                seenCats.add(catName);
            }

            final int finalFavCount = favCount;
            runOnUiThread(() -> {
                channelMap.clear();
                channelMap.putAll(tempMap);
                categories.clear();
                originalCategories.clear();
                
                categories.add(new Category("0", "Sevimlilər", finalFavCount));
                int id = 1;
                for (String cname : seenCats) {
                    if (!isAdultEnabled && M3UParser.isSensitiveCategory(cname)) {
                        continue;
                    }
                    List<Channel> list = channelMap.get(cname);
                    categories.add(new Category(String.valueOf(id++), cname, list != null ? list.size() : 0));
                }
                
                originalCategories.addAll(categories);
                
                // Pleyer üçün məlumatları yadda saxla
                sortCategories(categories);
                DataManager.setAllChannels(allChannels);
                DataManager.setCurrentCategoryList(categories);
                DataManager.setCurrentChannelMap(channelMap);
                
                categoryAdapter.notifyDataSetChanged();
                handleStartCategory();
            });
        });
    }

    private void handleStartCategory() {
        boolean autoStart = getIntent().getBooleanExtra("auto_start", false);
        if (autoStart) {
            getIntent().removeExtra("auto_start"); // Təkrar işə düşməməsi üçün
            SharedPreferences prefs = getSharedPreferences("neoplay_prefs", MODE_PRIVATE);
            String lastUrl = prefs.getString("last_channel_url", "");
            if (!lastUrl.isEmpty() && !allChannels.isEmpty()) {
                for (int i = 0; i < allChannels.size(); i++) {
                    if (allChannels.get(i).getStreamUrl().equals(lastUrl)) {
                        playChannel(allChannels.get(i), i, allChannels);
                        return;
                    }
                }
            }
        }

        String filterCategory = getIntent().getStringExtra("filter_category");
        if (filterCategory != null) {
            if ("Sevimlilər".equals(filterCategory)) {
                loadFavorites();
            } else if ("VOD_MOVIES".equals(filterCategory) || "VOD_SERIES".equals(filterCategory)) {
                setVodMode(true);
                loadVodContent();
            } else {
                for (Category cat : categories) {
                    if (cat.getName().equalsIgnoreCase(filterCategory)) {
                        filterChannelsByCategory(cat);
                        return;
                    }
                }
                setVodMode(true);
                loadVodContent();
            }
        } else if (!categories.isEmpty()) {
            setVodMode(false);
            
            // "Sevimlilər" boşdursa, içində kanal olan ilk kateqoriyaya keç
            Category targetCategory = categories.get(0);
            if ("Sevimlilər".equals(targetCategory.getName())) {
                FavoriteManager fm = new FavoriteManager(this);
                boolean hasFavorites = false;
                for (Channel c : allChannels) {
                    if (fm.isFavorite(c.getId())) {
                        hasFavorites = true;
                        break;
                    }
                }
                
                if (!hasFavorites && categories.size() > 1) {
                    targetCategory = categories.get(1); // Birinci real kateqoriya
                }
            }
            
            filterChannelsByCategory(targetCategory);
        }
    }

    private void checkAndSetVodMode(Category category) {
        String key = "xtream".equalsIgnoreCase(playlistType) ? category.getId() : category.getName();
        List<Channel> list = channelMap.get(key);
        if (list != null && !list.isEmpty()) {
            setVodMode(M3UParser.isVodChannel(list.get(0).getStreamUrl()));
        } else {
            setVodMode(false);
        }
    }

    private void setVodMode(boolean enabled) {
        this.isVodMode = enabled;
        if (enabled) {
            binding.rvChannels.setLayoutManager(new androidx.recyclerview.widget.GridLayoutManager(this, 5));
            channelAdapter.setViewType(com.bykerimoff.player.adapters.ChannelAdapter.VIEW_TYPE_GRID);
            binding.panelPlayer.setVisibility(android.view.View.GONE);
            binding.tvPanelTitle.setText("FILMLƏR / SERIALYAR");
            if (miniPlayer != null && miniPlayer.isPlaying()) {
                miniPlayer.stop();
            }
            android.widget.LinearLayout.LayoutParams params = (android.widget.LinearLayout.LayoutParams) binding.panelChannels.getLayoutParams();
            params.weight = 6.2f;
            binding.panelChannels.setLayoutParams(params);
        } else {
            if ("compact".equals(viewMode)) {
                binding.rvChannels.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(this));
                channelAdapter.setViewType(com.bykerimoff.player.adapters.ChannelAdapter.VIEW_TYPE_COMPACT);
            } else if ("list".equals(viewMode)) {
                binding.rvChannels.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(this));
                channelAdapter.setViewType(com.bykerimoff.player.adapters.ChannelAdapter.VIEW_TYPE_LIST);
            } else {
                // Classic: Yan-yana böyük loqolar (Grid 3 sütun)
                binding.rvChannels.setLayoutManager(new androidx.recyclerview.widget.GridLayoutManager(this, 3));
                channelAdapter.setViewType(com.bykerimoff.player.adapters.ChannelAdapter.VIEW_TYPE_GRID);
            }
            
            binding.panelPlayer.setVisibility(android.view.View.VISIBLE);
            binding.tvPanelTitle.setText("KANALLAR");
            android.widget.LinearLayout.LayoutParams params = (android.widget.LinearLayout.LayoutParams) binding.panelChannels.getLayoutParams();
            params.weight = 2.8f;
            binding.panelChannels.setLayoutParams(params);
        }
    }

    private void loadVodContent() {
        channels.clear();
        for (Channel channel : allChannels) {
            if (M3UParser.isVodChannel(channel.getStreamUrl())) {
                channels.add(channel);
            }
        }
        channelAdapter.notifyDataSetChanged();
    }

    private void filterChannelsByCategory(Category category) {
        channels.clear();
        String key = "xtream".equalsIgnoreCase(playlistType) ? category.getId() : category.getName();
        List<Channel> list = channelMap.get(key);
        if (list != null) {
            channels.addAll(list);
        }
        channelAdapter.notifyDataSetChanged();
        // Əgər kanallar varsa, birinci kanala fokus ver və ya siyahını yenilə
        if (!channels.isEmpty()) {
            binding.rvChannels.scrollToPosition(0);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            if (binding.rvChannels.hasFocus() || binding.etSearch.hasFocus()) {
                binding.rvCategories.requestFocus();
                return true;
            }
        }
        
        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            if (binding.rvCategories.hasFocus()) {
                binding.rvChannels.requestFocus();
                return true;
            }
        }

        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            if (binding.rvChannels.hasFocus()) {
                View focusedChild = binding.rvChannels.getFocusedChild();
                if (focusedChild != null) {
                    int pos = binding.rvChannels.getChildAdapterPosition(focusedChild);
                    if (pos <= 0) { // Grid üçün 0 və ya daha kiçik
                        int last = channelAdapter.getItemCount() - 1;
                        binding.rvChannels.scrollToPosition(last);
                        binding.rvChannels.postDelayed(() -> {
                            androidx.recyclerview.widget.RecyclerView.ViewHolder vh = binding.rvChannels.findViewHolderForAdapterPosition(last);
                            if (vh != null) vh.itemView.requestFocus();
                        }, 50);
                        return true;
                    }
                }
            } else if (binding.rvCategories.hasFocus()) {
                View focusedChild = binding.rvCategories.getFocusedChild();
                if (focusedChild != null) {
                    int pos = binding.rvCategories.getChildAdapterPosition(focusedChild);
                    if (pos == 0) {
                        int last = categoryAdapter.getItemCount() - 1;
                        binding.rvCategories.scrollToPosition(last);
                        binding.rvCategories.postDelayed(() -> {
                            androidx.recyclerview.widget.RecyclerView.ViewHolder vh = binding.rvCategories.findViewHolderForAdapterPosition(last);
                            if (vh != null) vh.itemView.requestFocus();
                        }, 50);
                        return true;
                    }
                }
            }
        }

        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            if (binding.rvChannels.hasFocus()) {
                View focusedChild = binding.rvChannels.getFocusedChild();
                if (focusedChild != null) {
                    int pos = binding.rvChannels.getChildAdapterPosition(focusedChild);
                    int lastPos = channelAdapter.getItemCount() - 1;
                    if (pos >= lastPos) {
                        binding.rvChannels.scrollToPosition(0);
                        binding.rvChannels.postDelayed(() -> {
                            androidx.recyclerview.widget.RecyclerView.ViewHolder vh = binding.rvChannels.findViewHolderForAdapterPosition(0);
                            if (vh != null) vh.itemView.requestFocus();
                        }, 50);
                        return true;
                    }
                }
            } else if (binding.rvCategories.hasFocus()) {
                View focusedChild = binding.rvCategories.getFocusedChild();
                if (focusedChild != null) {
                    int pos = binding.rvCategories.getChildAdapterPosition(focusedChild);
                    int lastPos = categoryAdapter.getItemCount() - 1;
                    if (pos == lastPos) {
                        binding.rvCategories.scrollToPosition(0);
                        binding.rvCategories.postDelayed(() -> {
                            androidx.recyclerview.widget.RecyclerView.ViewHolder vh = binding.rvCategories.findViewHolderForAdapterPosition(0);
                            if (vh != null) vh.itemView.requestFocus();
                        }, 50);
                        return true;
                    }
                }
            }
        }
        
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            event.startTracking(); // Uzun basmanı izləmək üçün
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyLongPress(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            // Uzun basıldıqda kateqoriyalara qayıt
            binding.rvCategories.requestFocus();
            return true;
        }
        return super.onKeyLongPress(keyCode, event);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (miniPlayer != null) miniPlayer.stop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (miniPlayer != null) miniPlayer.release();
    }
}
