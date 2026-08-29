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
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.extractor.DefaultExtractorsFactory;
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory;
import androidx.media3.ui.CaptionStyleCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

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
    private boolean isSportEnabled = true;
    private boolean isKidsModeActive = false;

    private android.os.CountDownTimer testCountDownTimer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
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
        isSportEnabled = prefs.getBoolean("is_sport_enabled", true);
        isKidsModeActive = prefs.getBoolean("kids_mode_active", false);

        // Xtream və ya M3U rejimini dəqiq təyin et
        boolean isXtream = "xtream".equalsIgnoreCase(playlistType) || (xtHost != null && !xtHost.trim().isEmpty() && xtUser != null && !xtUser.trim().isEmpty());

        initMiniPlayer();
        setupRecyclerViews();
        setupSearch();
        
        binding.btnBack.setOnClickListener(v -> finish());
        
        String filterCategory = getIntent().getStringExtra("filter_category");
        String currentPlaylistId = (isXtream ? (xtHost + xtUser) : m3uUrl) + "_" + (filterCategory != null ? filterCategory : "live");
        
        // 1. Check Memory Cache
        if (DataManager.getPlaylistIdentifier().equals(currentPlaylistId) && !DataManager.getAllChannels().isEmpty()) {
            loadFromMemory();
            refreshDataInBackground(isXtream);
        } 
        // 2. Check Disk Cache
        else {
            List<Channel> cachedChannels = com.bykerimoff.player.utils.DiskCacheManager.loadChannels(this, currentPlaylistId);
            List<Category> cachedCategories = com.bykerimoff.player.utils.DiskCacheManager.loadCategories(this, currentPlaylistId);
            
            if (!cachedChannels.isEmpty()) {
                allChannels.clear();
                allChannels.addAll(cachedChannels);
                categories.clear();
                categories.addAll(cachedCategories);
                
                // Rebuild channel map for fast filtering
                channelMap.clear();
                for (Channel c : allChannels) {
                    String catId = c.getCategoryName();
                    if (catId == null) catId = "0";
                    if (!channelMap.containsKey(catId)) channelMap.put(catId, new ArrayList<>());
                    channelMap.get(catId).add(c);
                }
                
                originalCategories.clear();
                originalCategories.addAll(cachedCategories);
                
                categoryAdapter.notifyDataSetChanged();
                handleStartCategory();
                
                // Still refresh in background silently
                DataManager.setPlaylistIdentifier(currentPlaylistId);
                refreshDataInBackground(isXtream);
            } else {
                // 3. First time load or cache empty
                if (isXtream) {
                    loadXtreamData();
                } else {
                    loadM3UData();
                }
                DataManager.setPlaylistIdentifier(currentPlaylistId);
            }
        }

        // Manual EPG-ni yoxla
        String manualEpg = prefs.getString("manual_epg_url", "");
        if (!manualEpg.isEmpty()) {
            XMLTVParser.downloadAndParse(manualEpg);
        }
        
        updateTestCountdown();
    }

    private void loadFromMemory() {
        allChannels.clear();
        allChannels.addAll(DataManager.getAllChannels());
        channelMap.clear();
        channelMap.putAll(DataManager.getCurrentChannelMap());
        categories.clear();
        categories.addAll(DataManager.getCurrentCategoryList());
        
        categoryAdapter.notifyDataSetChanged();
        handleStartCategory();
    }

    private void refreshDataInBackground(boolean isXtream) {
        // Keş olsa belə arxa planda yeniləyək ki, kanal siyahısı aktual qalsın
        if (isXtream) {
            loadXtreamDataInternal(false);
        } else {
            loadM3UFromUrl(m3uUrl);
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

                // 5 dəqiqədən az qaldıqda marqatla
                if (remaining < 300) {
                    if (binding.testBannerLive.getAnimation() == null) {
                        binding.testBannerLive.startAnimation(android.view.animation.AnimationUtils.loadAnimation(LiveTvActivity.this, R.anim.blink));
                    }
                } else {
                    binding.testBannerLive.clearAnimation();
                }
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
        String lowerQuery = query.toLowerCase();
        for (Channel channel : allChannels) {
            if (channel.getName().toLowerCase().contains(lowerQuery)) {
                // Filtrləri yoxla
                if (!isAdultEnabled && M3UParser.isSensitiveCategory(channel.getCategoryName())) continue;
                if (!isSportEnabled && M3UParser.isSportCategory(channel.getCategoryName())) continue;
                
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
                        5000,  // minBufferMs (20000 -> 5000)
                        15000, // maxBufferMs (60000 -> 15000)
                        1000,  // bufferForPlaybackMs (2000 -> 1000)
                        2000   // bufferForPlaybackAfterRebufferMs (5000 -> 2000)
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
            
            // Save selected category ID
            getSharedPreferences("neoplay_prefs", MODE_PRIVATE).edit()
                .putString("last_category_id", category.getId())
                .apply();
                
            binding.rvChannels.requestFocus();
        });
        binding.rvCategories.setLayoutManager(new LinearLayoutManager(this));
        binding.rvCategories.setAdapter(categoryAdapter);
        binding.rvCategories.setHasFixedSize(true);
        binding.rvCategories.setItemViewCacheSize(100); // Keşi artır

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
        binding.rvChannels.setItemViewCacheSize(200); // Kanallar üçün yüksək keş
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

    private List<XtreamCategory> pendingXtCats = null;
    private List<XtreamChannel> pendingXtStreams = null;
    private String pendingXtType = "";

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
        
        pendingXtCats = null;
        pendingXtStreams = null;

        if ("VOD_MOVIES".equals(filterCategory)) {
            pendingXtType = "movie";
            String catUrl = baseUrl + "&action=get_vod_categories";
            String streamUrl = baseUrl + "&action=get_vod_streams";
            
            ApiClient.getService().getXtreamVodCategories(catUrl).enqueue(new CategoryCallback());
            ApiClient.getService().getXtreamVod(streamUrl).enqueue(new StreamCallback());
            
        } else if ("VOD_SERIES".equals(filterCategory)) {
            pendingXtType = "series";
            String catUrl = baseUrl + "&action=get_series_categories";
            String streamUrl = baseUrl + "&action=get_series";
            
            ApiClient.getService().getXtreamSeriesCategories(catUrl).enqueue(new CategoryCallback());
            ApiClient.getService().getXtreamVod(streamUrl).enqueue(new StreamCallback()); // Both use same model
            
        } else {
            pendingXtType = "live";
            String catUrl = baseUrl + "&action=get_live_categories";
            String streamUrl = baseUrl + "&action=get_live_streams";

            ApiClient.getService().getXtreamCategories(catUrl).enqueue(new CategoryCallback());
            ApiClient.getService().getXtreamChannels(streamUrl).enqueue(new StreamCallback());
        }
    }

    private class CategoryCallback implements Callback<List<XtreamCategory>> {
        @Override
        public void onResponse(Call<List<XtreamCategory>> call, Response<List<XtreamCategory>> response) {
            if (response.isSuccessful() && response.body() != null) {
                pendingXtCats = response.body();
                checkAndProcessParallel();
            } else {
                handleXtreamError("Category Error: " + response.code());
            }
        }
        @Override
        public void onFailure(Call<List<XtreamCategory>> call, Throwable t) {
            handleXtreamError("Category Connection Error");
        }
    }

    private class StreamCallback<T> implements Callback<List<T>> {
        @Override
        public void onResponse(Call<List<T>> call, Response<List<T>> response) {
            if (response.isSuccessful() && response.body() != null) {
                pendingXtStreams = (List<XtreamChannel>) response.body();
                checkAndProcessParallel();
            } else {
                handleXtreamError("Stream Error: " + response.code());
            }
        }
        @Override
        public void onFailure(Call<List<T>> call, Throwable t) {
            handleXtreamError("Stream Connection Error");
        }
    }

    private synchronized void checkAndProcessParallel() {
        if (pendingXtCats != null && pendingXtStreams != null) {
            processParallelData(pendingXtCats, pendingXtStreams, pendingXtType);
        }
    }

    private void handleXtreamError(String msg) {
        runOnUiThread(() -> {
            binding.mainLoadingLayout.setVisibility(View.GONE);
            android.util.Log.e("XTREAM_ERROR", msg);
            // Fallback to M3U if necessary
            if (pendingXtCats == null && pendingXtStreams == null) {
                loadM3UData();
            }
        });
    }

    private void processParallelData(List<XtreamCategory> xtCats, List<XtreamChannel> xtStreams, String type) {
        // Build map first for fast access
        Map<String, List<Channel>> tempMap = new HashMap<>();
        List<Channel> tempAll = new ArrayList<>(xtStreams.size());
        
        String cleanHost = xtHost != null ? xtHost.trim().replaceAll("/+$", "") : "";
        if (!cleanHost.isEmpty() && !cleanHost.startsWith("http://") && !cleanHost.startsWith("https://")) {
            cleanHost = "http://" + cleanHost;
        }

        String creds = "";
        String vodTypePath = "";
        if ("movie".equals(type) || "series".equals(type)) {
            vodTypePath = type.equals("series") ? "series" : "movie";
            creds = "/" + xtUser + "/" + xtPass + "/";
        } else {
            creds = "/live/" + xtUser + "/" + xtPass + "/";
        }

        for (XtreamChannel xc : xtStreams) {
            String sid = xc.getStreamId();
            if (sid == null || sid.isEmpty() || xc.getName() == null) continue;
            
            String streamLink;
            if ("movie".equals(type) || "series".equals(type)) {
                String ext = type.equals("series") ? "mkv" : xc.getContainerExtension();
                streamLink = cleanHost + "/" + vodTypePath + creds + sid + "." + ext;
            } else {
                streamLink = cleanHost + creds + sid + ".ts";
            }

            Channel channel = new Channel(sid, xc.getName(), xc.getLogo(), 
                com.bykerimoff.player.utils.SecurityUtils.encryptUrl(streamLink), xc.getCategoryId());
            
            tempAll.add(channel);
            
            String catId = xc.getCategoryId();
            if (catId == null) catId = "0";
            List<Channel> list = tempMap.get(catId);
            if (list == null) {
                list = new ArrayList<>();
                tempMap.put(catId, list);
            }
            list.add(channel);
        }

        allChannels.clear();
        allChannels.addAll(tempAll);
        channelMap.clear();
        channelMap.putAll(tempMap);

        originalCategories.clear();
        originalCategories.add(new Category("0", "Sevimlilər", 0));
        originalCategories.add(new Category("all", "Bütün Kanallar", 0));
        for (XtreamCategory xc : xtCats) {
            originalCategories.add(new Category(xc.getId(), xc.getName(), 0));
        }

        runOnUiThread(() -> {
            updateCategoryCounts();
            
            // Save to Disk Cache
            String filterCategory = getIntent().getStringExtra("filter_category");
            String isXtreamStr = ("xtream".equalsIgnoreCase(playlistType) || (xtHost != null && !xtHost.trim().isEmpty())) ? "xt" : "m3u";
            String currentPlaylistId = (isXtreamStr.equals("xt") ? (xtHost + xtUser) : m3uUrl) + "_" + (filterCategory != null ? filterCategory : "live");
            com.bykerimoff.player.utils.DiskCacheManager.saveChannels(LiveTvActivity.this, currentPlaylistId, allChannels);
            com.bykerimoff.player.utils.DiskCacheManager.saveCategories(LiveTvActivity.this, currentPlaylistId, categories);

            binding.mainLoadingLayout.setVisibility(View.GONE);
            handleStartCategory();
        });
    }

    // Remove separate slow processing methods
    private void processXtreamVod(List<XtreamChannel> vods, String type) {}
    private void processXtreamChannels(List<XtreamChannel> xtChannels) {}

    private void handleStartCategory() {
        String filter = getIntent().getStringExtra("filter_category");
        if (filter != null) {
            Category target = null;
            if (filter.equals("Sevimlilər")) {
                for (Category cat : originalCategories) {
                    if ("Sevimlilər".equals(cat.getName())) { target = cat; break; }
                }
            } else if (filter.startsWith("VOD_")) {
                target = originalCategories.get(1); // "all" category
            }
            
            if (target != null) {
                checkAndSetVodMode(target);
                filterChannelsByCategory(target);
            }
        } else {
            if (!originalCategories.isEmpty()) {
                filterChannelsByCategory(originalCategories.get(1)); // All channels
            }
        }
    }

    private void updateCategoryCounts() {
        FavoriteManager favoriteManager = new FavoriteManager(this);
        Set<String> favs = favoriteManager.getFavorites();
        
        int totalKidsChannels = 0;
        int favKidsChannels = 0;
        
        if (isKidsModeActive) {
            for (Channel c : allChannels) {
                if (M3UParser.isKidsCategory(c.getCategoryName())) totalKidsChannels++;
            }
            for (String favId : favs) {
                for (Channel c : allChannels) {
                    if (c.getId().equals(favId) && M3UParser.isKidsCategory(c.getCategoryName())) {
                        favKidsChannels++;
                        break;
                    }
                }
            }
        }

        SharedPreferences settingsPrefs = getSharedPreferences("neoplay_prefs", MODE_PRIVATE);
        String sortMode = settingsPrefs.getString("category_sort_mode", "default");

        for (int i = 0; i < originalCategories.size(); i++) {
            Category cat = originalCategories.get(i);
            String cid = cat.getId();
            if (cid.equals("0")) {
                int count = isKidsModeActive ? favKidsChannels : favs.size();
                originalCategories.set(i, new Category("0", "Sevimlilər", count));
            } else if (cid.equals("all")) {
                int count = isKidsModeActive ? totalKidsChannels : (allChannels != null ? allChannels.size() : 0);
                originalCategories.set(i, new Category("all", "Bütün Kanallar", count));
            } else {
                List<Channel> list = channelMap.get(cid);
                int count = 0;
                if (list != null) {
                    if (isKidsModeActive) {
                        for (Channel c : list) if (M3UParser.isKidsCategory(c.getCategoryName())) count++;
                    } else {
                        count = list.size();
                    }
                }
                originalCategories.set(i, new Category(cid, cat.getName(), count));
            }
        }

        List<Category> filtered = new ArrayList<>();
        for (Category cat : originalCategories) {
            if (cat.getChannelCount() > 0 || cat.getId().equals("all") || cat.getId().equals("0")) {
                // Uşaq Rejimi Filtri
                if (isKidsModeActive) {
                    if (cat.getChannelCount() == 0) continue; // Boş bölmələri gizlə
                    if (!cat.getId().equals("0") && !cat.getId().equals("all") && !M3UParser.isKidsCategory(cat.getName())) {
                        continue;
                    }
                }

                // Sensitive / Adult filter
                if (!isAdultEnabled && M3UParser.isSensitiveCategory(cat.getName())) continue;
                if (!isSportEnabled && M3UParser.isSportCategory(cat.getName())) continue;
                
                filtered.add(cat);
            }
        }

        // Sıralama
        if ("name".equals(sortMode)) {
            java.util.Collections.sort(filtered, (c1, c2) -> {
                if (c1.getId().equals("0") || c1.getId().equals("all")) return -1;
                if (c2.getId().equals("0") || c2.getId().equals("all")) return 1;
                return c1.getName().compareToIgnoreCase(c2.getName());
            });
        } else if ("count".equals(sortMode)) {
            java.util.Collections.sort(filtered, (c1, c2) -> {
                if (c1.getId().equals("0") || c1.getId().equals("all")) return -1;
                if (c2.getId().equals("0") || c2.getId().equals("all")) return 1;
                return Integer.compare(c2.getChannelCount(), c1.getChannelCount());
            });
        }

        categories.clear();
        categories.addAll(filtered);
        
        // Cache current state
        DataManager.setCurrentCategoryList(categories);
        DataManager.setCurrentChannelMap(channelMap);
        DataManager.setAllChannels(allChannels);

        runOnUiThread(() -> categoryAdapter.notifyDataSetChanged());
    }

    private void handleAutoStartLastChannel() {
        SharedPreferences prefs = getSharedPreferences("neoplay_prefs", MODE_PRIVATE);
        String lastUrl = prefs.getString("last_channel_url", "");
        String lastCatId = prefs.getString("last_category_id", "all");
        
        if (lastUrl.isEmpty() || allChannels.isEmpty()) return;

        List<Channel> targetList = new ArrayList<>();
        if ("all".equals(lastCatId)) {
            targetList.addAll(allChannels);
        } else if ("0".equals(lastCatId)) {
            FavoriteManager fav = new FavoriteManager(this);
            for (Channel c : allChannels) {
                if (fav.isFavorite(c.getId())) targetList.add(c);
            }
        } else {
            List<Channel> list = channelMap.get(lastCatId);
            if (list != null) targetList.addAll(list);
            else targetList.addAll(allChannels);
        }

        for (int i = 0; i < targetList.size(); i++) {
            Channel channel = targetList.get(i);
            if (lastUrl.equals(channel.getStreamUrl())) {
                if (isKidsModeActive && !M3UParser.isKidsCategory(channel.getCategoryName())) {
                    return; 
                }
                
                DataManager.setCurrentChannelList(targetList);
                Intent intent = new Intent(this, PlayerActivity.class);
                intent.putExtra("channel_index", i);
                startActivity(intent);
                getIntent().removeExtra("auto_start");
                return;
            }
        }
    }

    private void filterChannelsByCategory(Category category) {
        channels.clear();
        if ("all".equals(category.getId())) {
            for (Channel c : allChannels) {
                if (isKidsModeActive && !M3UParser.isKidsCategory(c.getCategoryName())) continue;
                channels.add(c);
            }
        } else if ("0".equals(category.getId())) {
            FavoriteManager favoriteManager = new FavoriteManager(this);
            for (Channel channel : allChannels) {
                if (favoriteManager.isFavorite(channel.getId())) {
                    if (isKidsModeActive && !M3UParser.isKidsCategory(channel.getCategoryName())) continue;
                    channels.add(channel);
                }
            }
        } else {
            List<Channel> list = channelMap.get(category.getId());
            if (list != null) {
                for (Channel c : list) {
                    if (isKidsModeActive && !M3UParser.isKidsCategory(c.getCategoryName())) continue;
                    channels.add(c);
                }
            }
        }
        
        binding.tvPanelTitle.setText(category.getName().toUpperCase());
        channelAdapter.notifyDataSetChanged();
        
        if (!channels.isEmpty() && !isVodMode) {
            binding.rvChannels.scrollToPosition(0);
            playMiniStream(channels.get(0));
        }
    }

    private void checkAndSetVodMode(Category category) {
        String filter = getIntent().getStringExtra("filter_category");
        isVodMode = (filter != null && (filter.equals("VOD_MOVIES") || filter.equals("VOD_SERIES")));
        setVodMode(isVodMode);
    }

    private void setVodMode(boolean active) {
        isVodMode = active;
        binding.panelPlayer.setVisibility(active ? View.GONE : View.VISIBLE);
        if (active) {
            miniPlayer.stop();
        }
    }

    private void loadM3UFromUrl(String url) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            try {
                java.net.URL m3uUrlObj = new java.net.URL(url);
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) m3uUrlObj.openConnection();
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(30000);
                
                java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(conn.getInputStream()));
                List<Channel> parsedChannels = M3UParser.parse(reader);
                reader.close();

                Map<String, List<Channel>> tempMap = new HashMap<>();
                List<Channel> tempAll = new ArrayList<>();
                Set<String> catNames = new LinkedHashSet<>();

                for (Channel c : parsedChannels) {
                    tempAll.add(c);
                    String cat = c.getCategoryName();
                    if (!tempMap.containsKey(cat)) tempMap.put(cat, new ArrayList<>());
                    tempMap.get(cat).add(c);
                    catNames.add(cat);
                }

                FavoriteManager favoriteManager = new FavoriteManager(LiveTvActivity.this);
                int favCount = favoriteManager.getFavorites().size();
                
                final List<Channel> channelsCopy = new ArrayList<>(tempAll);
                
                runOnUiThread(() -> {
                    originalCategories.clear();
                    
                    originalCategories.add(new Category("0", "Sevimlilər", favCount));
                    originalCategories.add(new Category("all", "Bütün Kanallar", channelsCopy.size()));
                    for (String name : catNames) {
                        List<Channel> list = tempMap.get(name);
                        originalCategories.add(new Category(name, name, list != null ? list.size() : 0));
                    }

                    allChannels.clear();
                    allChannels.addAll(tempAll);
                    channelMap.clear();
                    channelMap.putAll(tempMap);
                    
                    binding.mainLoadingLayout.setVisibility(android.view.View.GONE);
                    updateCategoryCounts();
                    handleStartCategory();
                    
                    if (getIntent().getBooleanExtra("auto_start", false)) {
                        handleAutoStartLastChannel();
                    }
                });

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    binding.mainLoadingLayout.setVisibility(android.view.View.GONE);
                    Toast.makeText(LiveTvActivity.this, "Playlist yüklənmədi: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    updateCategoryCounts();
                });
            }
        });
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            View focused = getCurrentFocus();
            if (focused != null && isViewInRecyclerView(focused, binding.rvCategories)) {
                int pos = binding.rvCategories.getChildLayoutPosition(focused);
                if (pos == RecyclerView.NO_POSITION) {
                    View parent = (View) focused.getParent();
                    while (parent != null && parent != binding.rvCategories) {
                        pos = binding.rvCategories.getChildLayoutPosition(parent);
                        if (pos != RecyclerView.NO_POSITION) break;
                        parent = (View) parent.getParent();
                    }
                }
                
                if (pos != RecyclerView.NO_POSITION) {
                    int count = categoryAdapter.getItemCount();
                    if (keyCode == KeyEvent.KEYCODE_DPAD_UP && pos == 0) {
                        binding.rvCategories.scrollToPosition(count - 1);
                        binding.rvCategories.postDelayed(() -> {
                            RecyclerView.ViewHolder vh = binding.rvCategories.findViewHolderForAdapterPosition(count - 1);
                            if (vh != null) vh.itemView.requestFocus();
                        }, 50);
                        return true;
                    } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && pos == count - 1) {
                        binding.rvCategories.scrollToPosition(0);
                        binding.rvCategories.postDelayed(() -> {
                            RecyclerView.ViewHolder vh = binding.rvCategories.findViewHolderForAdapterPosition(0);
                            if (vh != null) vh.itemView.requestFocus();
                        }, 50);
                        return true;
                    }
                }
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    private boolean isViewInRecyclerView(View view, RecyclerView rv) {
        if (view == null) return false;
        if (view == rv) return true;
        Object parent = view.getParent();
        while (parent instanceof View) {
            if (parent == rv) return true;
            parent = ((View) parent).getParent();
        }
        return false;
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (miniPlayer != null) {
            miniPlayer.stop();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (miniPlayer != null) {
            miniPlayer.stop();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (miniPlayer != null) {
            miniPlayer.release();
            miniPlayer = null;
        }
        if (testCountDownTimer != null) {
            testCountDownTimer.cancel();
        }
    }
}
