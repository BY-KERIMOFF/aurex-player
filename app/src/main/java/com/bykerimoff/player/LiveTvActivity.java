package com.bykerimoff.player;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.View;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.animation.AnimationUtils;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.okhttp.OkHttpDataSource;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.extractor.DefaultExtractorsFactory;
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory;
import androidx.media3.ui.CaptionStyleCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bykerimoff.player.adapters.CategoryAdapter;
import com.bykerimoff.player.adapters.ChannelAdapter;
import com.bykerimoff.player.api.ApiClient;
import com.bykerimoff.player.databinding.ActivityLiveTvBinding;
import com.bykerimoff.player.models.Category;
import com.bykerimoff.player.models.Channel;
import com.bykerimoff.player.models.XtreamCategory;
import com.bykerimoff.player.models.XtreamChannel;
import com.bykerimoff.player.utils.ChannelOrderManager;
import com.bykerimoff.player.utils.DataManager;
import com.bykerimoff.player.utils.DiskCacheManager;
import com.bykerimoff.player.utils.FavoriteManager;
import com.bykerimoff.player.utils.M3UParser;
import com.bykerimoff.player.utils.NetworkUtils;
import com.bykerimoff.player.utils.PinDialog;
import com.bykerimoff.player.utils.SecurityUtils;
import com.bykerimoff.player.utils.ThemeManager;
import com.bykerimoff.player.utils.WallpaperManager;
import com.bykerimoff.player.utils.XMLTVParser;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
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
    private ChannelOrderManager orderManager;
    private Category currentCategory;
    
    private boolean isVodMode = false;
    private String playlistType;
    private String viewMode = "classic";
    private String m3uUrl;
    private String xtHost, xtUser, xtPass;
    private boolean isAdultEnabled = true;
    private boolean isSportEnabled = true;
    private boolean hideSensitive = false;
    private boolean isKidsModeActive = false;

    private final Handler playbackHandler = new Handler(Looper.getMainLooper());
    private Runnable playbackRunnable;

    private CountDownTimer testCountDownTimer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLiveTvBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        orderManager = new ChannelOrderManager(this);

        // Show loading immediately to avoid blank screen
        binding.mainLoadingLayout.setVisibility(View.VISIBLE);
        binding.mainLoadingProgress.setIndeterminateTintList(ColorStateList.valueOf(ThemeManager.INSTANCE.getThemeColor(this)));

        WallpaperManager.INSTANCE.applyWallpaper(this, binding.ivAppBackground);

        SharedPreferences prefs = getSharedPreferences("neoplay_prefs", MODE_PRIVATE);
        playlistType = prefs.getString("playlist_type", "m3u");
        viewMode = prefs.getString("view_mode", "classic");
        m3uUrl = prefs.getString("m3u_url", "");
        xtHost = prefs.getString("xtream_host", "");
        xtUser = prefs.getString("xtream_user", "");
        xtPass = prefs.getString("xtream_pass", "");
        isAdultEnabled = prefs.getBoolean("is_adult_enabled", true);
        isSportEnabled = prefs.getBoolean("is_sport_enabled", true);
        hideSensitive = prefs.getBoolean("hide_sensitive_categories", false);
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
            List<Channel> cachedChannels = DiskCacheManager.loadChannels(this, currentPlaylistId);
            List<Category> cachedCategories = DiskCacheManager.loadCategories(this, currentPlaylistId);
            
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
        
        applyThemeColors();
        updateTestCountdown();
    }

    private void applyThemeColors() {
        int color = ThemeManager.INSTANCE.getThemeColor(this);
        ColorStateList colorStateList = ColorStateList.valueOf(color);
        
        binding.tvPanelTitle.setTextColor(color);
        binding.testTitleLive.setTextColor(color);
        binding.testTimerLive.setTextColor(color);
        binding.mainLoadingProgress.setIndeterminateTintList(colorStateList);
        binding.tvMainLoadingText.setTextColor(color);
        binding.tvCurrentChannel.setTextColor(color);
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
        applyThemeColors();
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
            binding.testBannerLive.setVisibility(View.GONE);
        }
    }

    private void startTestTimer(int seconds) {
        if (testCountDownTimer != null) {
            testCountDownTimer.cancel();
        }

        testCountDownTimer = new CountDownTimer(seconds * 1000L, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                int remaining = (int) (millisUntilFinished / 1000);
                String timeLeft = formatTimeForTest(remaining);
                
                binding.testBannerLive.setVisibility(View.VISIBLE);
                binding.testTitleLive.setText("TEST REJİMİ");
                binding.testTimerLive.setText("Test: " + timeLeft);

                int color;
                if (remaining < 60) {
                    color = Color.RED;
                } else if (remaining < 300) {
                    color = Color.parseColor("#FFA500"); // Orange
                } else {
                    color = Color.parseColor("#D4AF37"); // Gold
                }
                
                binding.testTitleLive.setTextColor(color);
                binding.testTimerLive.setTextColor(color);
                binding.testTimerLive.setBackgroundColor(Color.TRANSPARENT);

                // 5 dəqiqədən az qaldıqda marqatla
                if (remaining < 300) {
                    if (binding.testBannerLive.getAnimation() == null) {
                        binding.testBannerLive.startAnimation(AnimationUtils.loadAnimation(LiveTvActivity.this, R.anim.blink));
                    }
                } else {
                    binding.testBannerLive.clearAnimation();
                }
            }

            @Override
            public void onFinish() {
                binding.testTimerLive.setText("⏱ Test bitdi!");
                binding.testTimerLive.setTextColor(Color.parseColor("#ef4444"));
                
                new AlertDialog.Builder(LiveTvActivity.this)
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
        return Locale.getDefault() != null ?
            String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, secs) :
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
                if (hideSensitive && M3UParser.isSensitiveCategory(channel.getCategoryName())) continue;
                if (!isSportEnabled && M3UParser.isSportCategory(channel.getCategoryName())) continue;
                if (isKidsModeActive && !M3UParser.isKidsCategory(channel.getCategoryName())) continue;
                
                channels.add(channel);
            }
        }
        channelAdapter.notifyDataSetChanged();
    }

    @OptIn(markerClass = UnstableApi.class)
    private void initMiniPlayer() {
        OkHttpDataSource.Factory dataSourceFactory = NetworkUtils.getDataSourceFactory(this);
        
        DefaultExtractorsFactory extractorsFactory = new DefaultExtractorsFactory()
                .setTsExtractorFlags(DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES
                                   | DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS
                                   | DefaultTsPayloadReaderFactory.FLAG_IGNORE_SPLICE_INFO_STREAM
                                   | DefaultTsPayloadReaderFactory.FLAG_ENABLE_HDMV_DTS_AUDIO_STREAMS);

        DefaultMediaSourceFactory mediaSourceFactory = new DefaultMediaSourceFactory(dataSourceFactory, extractorsFactory);

        DefaultTrackSelector trackSelector = new DefaultTrackSelector(this);
        trackSelector.setParameters(trackSelector.buildUponParameters()
                .setExceedAudioConstraintsIfNecessary(true)
                .setExceedRendererCapabilitiesIfNecessary(true)
                .setExceedVideoConstraintsIfNecessary(true)
                .setTunnelingEnabled(false)
        );

        // Geniş audio/video kodek dəstəyi (AC3, DTS və s. üçün)
        DefaultRenderersFactory renderersFactory = new DefaultRenderersFactory(this)
                .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
                .setEnableDecoderFallback(true);

        // Daha mükəmməl buferləmə ayarları (50-60 FPS üçün)
        DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
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
        
        miniPlayer.addListener(new Player.Listener() {
            @Override
            public void onPlayerError(@NonNull PlaybackException error) {
                // Mini pleyer xətası zamanı avtomatik yenidən qoşulma cəhdi
                miniPlayer.prepare();
                miniPlayer.play();
            }
        });

        // Mini-player üçün altyazı stili
        CaptionStyleCompat style = new CaptionStyleCompat(
                Color.WHITE,
                Color.TRANSPARENT,
                Color.TRANSPARENT,
                CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                Color.BLACK,
                null
        );
        if (binding.miniPlayerView.getSubtitleView() != null) {
            binding.miniPlayerView.getSubtitleView().setApplyEmbeddedStyles(false);
            binding.miniPlayerView.getSubtitleView().setApplyEmbeddedFontSizes(false);
            binding.miniPlayerView.getSubtitleView().setStyle(style);
            binding.miniPlayerView.getSubtitleView().setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, 18f);
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
                
            currentCategory = category;
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
                    if (playbackRunnable != null) playbackHandler.removeCallbacks(playbackRunnable);
                    playbackRunnable = () -> playMiniStream(channel);
                    playbackHandler.postDelayed(playbackRunnable, 500);
                }
            }

            @Override
            public void onChannelLongClick(Channel channel) {
                showChannelMenu(channel);
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

    private void showChannelMenu(Channel channel) {
        FavoriteManager favoriteManager = new FavoriteManager(this);
        boolean isFav = favoriteManager.isFavorite(channel.getId());
        boolean isMarked = channelAdapter.getMarkedChannelIds().contains(channel.getId());
        boolean hasSelection = !channelAdapter.getMarkedChannelIds().isEmpty();

        List<String> options = new ArrayList<>();
        options.add(isFav ? "Sevimli siyahısından çıxar" : "Sevimli siyahısına əlavə et");
        options.add(isMarked ? "Seçimi ləğv et" : "Kanalı seç");
        if (hasSelection) {
            options.add("Seçilmişləri buraya köçür");
        }

        new AlertDialog.Builder(this)
                .setTitle(channel.getName())
                .setItems(options.toArray(new String[0]), (dialog, which) -> {
                    String selected = options.get(which);
                    if (selected.contains("Sevimli")) {
                        favoriteManager.toggleFavorite(channel.getId());
                        channelAdapter.notifyDataSetChanged();
                    } else if (selected.equals("Kanalı seç") || selected.equals("Seçimi ləğv et")) {
                        channelAdapter.toggleMark(channel.getId());
                    } else if (selected.equals("Seçilmişləri buraya köçür")) {
                        moveSelectedChannelsTo(channels.indexOf(channel));
                    }
                })
                .show();
    }

    private void moveSelectedChannelsTo(int targetIndex) {
        if (currentCategory == null) return;
        
        Set<String> selectedIds = channelAdapter.getMarkedChannelIds();
        List<Channel> selectedList = new ArrayList<>();
        
        // Seçilmişləri tap və əsas siyahıdan müvəqqəti çıxar
        List<Channel> newList = new ArrayList<>(channels);
        Iterator<Channel> it = newList.iterator();
        while (it.hasNext()) {
            Channel c = it.next();
            if (selectedIds.contains(c.getId())) {
                selectedList.add(c);
                it.remove();
            }
        }
        
        // Yeni mövqeyə yerləşdir
        int actualTarget = Math.min(targetIndex, newList.size());
        newList.addAll(actualTarget, selectedList);
        
        // Dəyişiklikləri yadda saxla
        List<String> newOrderIds = new ArrayList<>();
        for (Channel c : newList) {
            newOrderIds.add(c.getId());
        }
        orderManager.saveOrder(currentCategory.getId(), newOrderIds);
        
        // Adapteri yenilə
        channels.clear();
        channels.addAll(newList);
        channelAdapter.clearMarks();
        channelAdapter.notifyDataSetChanged();
        
        Toast.makeText(this, "Sıralama yeniləndi", Toast.LENGTH_SHORT).show();
    }

    private void playMiniStream(Channel channel) {
        binding.tvCurrentChannel.setText(channel.getName());
        binding.tvEpgTitle.setText("Yüklənir...");
        
        Glide.with(this)
                .load(channel.getLogoUrl())
                .placeholder(R.drawable.default_logo)
                .error(R.drawable.default_logo)
                .into(binding.ivCurrentChannelLogo);

        String url = channel.getStreamUrl();
        MediaItem.Builder builder = new MediaItem.Builder();
        if (url != null) {
            builder.setUri(Uri.parse(url));
            String lower = url.toLowerCase(Locale.ROOT);
            if (lower.contains("m3u8") || lower.contains("stream.php") || lower.contains(".php") || lower.contains("/hls/")) {
                builder.setMimeType(MimeTypes.APPLICATION_M3U8);
            } else if (lower.contains(".ts") || lower.contains("output=ts") || lower.contains("output=mpegts") || lower.contains("/live/") || lower.contains("/mpegts")) {
                builder.setMimeType(MimeTypes.VIDEO_MP2T);
            } else if (lower.contains(".mpd")) {
                builder.setMimeType(MimeTypes.APPLICATION_MPD);
            }
        }
        miniPlayer.setMediaItem(builder.build());
        miniPlayer.prepare();
        miniPlayer.play();
    }

    private void openExternalPlayer(Channel channel) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(Uri.parse(channel.getStreamUrl()), "video/*");
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
        binding.mainLoadingLayout.setVisibility(View.VISIBLE);
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
            encodedUser = URLEncoder.encode(xtUser, "UTF-8");
            encodedPass = URLEncoder.encode(xtPass, "UTF-8");
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
            Log.e("XTREAM_ERROR", msg);
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
                SecurityUtils.encryptUrl(streamLink), xc.getCategoryId());
            
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
            DiskCacheManager.saveChannels(LiveTvActivity.this, currentPlaylistId, allChannels);
            DiskCacheManager.saveCategories(LiveTvActivity.this, currentPlaylistId, categories);

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
        
        int totalFilteredChannels = 0;
        int favFilteredChannels = 0;
        
        // Bütün kanalları öncə filtrləyək ki, say düzgün olsun
        List<Channel> filteredAll = new ArrayList<>();
        for (Channel c : allChannels) {
            if (isKidsModeActive && !M3UParser.isKidsCategory(c.getCategoryName())) continue;
            if (!isAdultEnabled && M3UParser.isSensitiveCategory(c.getCategoryName())) continue;
            if (hideSensitive && M3UParser.isSensitiveCategory(c.getCategoryName())) continue;
            if (!isSportEnabled && M3UParser.isSportCategory(c.getCategoryName())) continue;
            
            filteredAll.add(c);
        }
        totalFilteredChannels = filteredAll.size();

        // Sevimlilər üçün say hesablama
        for (String favId : favs) {
            for (Channel c : filteredAll) {
                if (c.getId().equals(favId)) {
                    favFilteredChannels++;
                    break;
                }
            }
        }

        SharedPreferences settingsPrefs = getSharedPreferences("neoplay_prefs", MODE_PRIVATE);
        String sortMode = settingsPrefs.getString("category_sort_mode", "default");

        for (int i = 0; i < originalCategories.size(); i++) {
            Category cat = originalCategories.get(i);
            String cid = cat.getId();
            if (cid.equals("0")) {
                originalCategories.set(i, new Category("0", "Sevimlilər", favFilteredChannels));
            } else if (cid.equals("all")) {
                originalCategories.set(i, new Category("all", "Bütün Kanallar", totalFilteredChannels));
            } else {
                List<Channel> list = channelMap.get(cid);
                int count = 0;
                if (list != null) {
                    for (Channel c : list) {
                        if (isKidsModeActive && !M3UParser.isKidsCategory(c.getCategoryName())) continue;
                        if (!isAdultEnabled && M3UParser.isSensitiveCategory(c.getCategoryName())) continue;
                        if (hideSensitive && M3UParser.isSensitiveCategory(c.getCategoryName())) continue;
                        if (!isSportEnabled && M3UParser.isSportCategory(c.getCategoryName())) continue;
                        count++;
                    }
                }
                originalCategories.set(i, new Category(cid, cat.getName(), count));
            }
        }

        List<Category> filtered = new ArrayList<>();
        boolean hideSensitive = getSharedPreferences("neoplay_prefs", MODE_PRIVATE).getBoolean("hide_sensitive_categories", false);
        
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
                if (hideSensitive && M3UParser.isSensitiveCategory(cat.getName())) continue;
                if (!isAdultEnabled && M3UParser.isSensitiveCategory(cat.getName())) continue;
                if (!isSportEnabled && M3UParser.isSportCategory(cat.getName())) continue;
                
                filtered.add(cat);
            }
        }

        // Sıralama
        if ("name".equals(sortMode)) {
            Collections.sort(filtered, (c1, c2) -> {
                if (c1.getId().equals("0") || c1.getId().equals("all")) return -1;
                if (c2.getId().equals("0") || c2.getId().equals("all")) return 1;
                return c1.getName().compareToIgnoreCase(c2.getName());
            });
        } else if ("count".equals(sortMode)) {
            Collections.sort(filtered, (c1, c2) -> {
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
                if (!isAdultEnabled && M3UParser.isSensitiveCategory(c.getCategoryName())) continue;
                if (hideSensitive && M3UParser.isSensitiveCategory(c.getCategoryName())) continue;
                if (!isSportEnabled && M3UParser.isSportCategory(c.getCategoryName())) continue;
                channels.add(c);
            }
        } else if ("0".equals(category.getId())) {
            FavoriteManager favoriteManager = new FavoriteManager(this);
            for (Channel c : allChannels) {
                if (favoriteManager.isFavorite(c.getId())) {
                    if (isKidsModeActive && !M3UParser.isKidsCategory(c.getCategoryName())) continue;
                    if (!isAdultEnabled && M3UParser.isSensitiveCategory(c.getCategoryName())) continue;
                    if (hideSensitive && M3UParser.isSensitiveCategory(c.getCategoryName())) continue;
                    if (!isSportEnabled && M3UParser.isSportCategory(c.getCategoryName())) continue;
                    channels.add(c);
                }
            }
        } else {
            List<Channel> list = channelMap.get(category.getId());
            if (list != null) {
                for (Channel c : list) {
                    if (isKidsModeActive && !M3UParser.isKidsCategory(c.getCategoryName())) continue;
                    // Note: Here we don't necessarily need to check adult/sensitive because 
                    // the category itself would have been hidden if it was sensitive.
                    // But for safety, let's keep it consistent.
                    if (!isAdultEnabled && M3UParser.isSensitiveCategory(c.getCategoryName())) continue;
                    if (hideSensitive && M3UParser.isSensitiveCategory(c.getCategoryName())) continue;
                    channels.add(c);
                }
            }
        }
        
        binding.tvPanelTitle.setText(category.getName().toUpperCase());
        
        // Sıralamanı tətbiq et
        List<Channel> orderedChannels = orderManager.applyOrder(category.getId(), channels);
        channels.clear();
        channels.addAll(orderedChannels);
        
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
                URL m3uUrlObj = new URL(url);
                HttpURLConnection conn = (HttpURLConnection) m3uUrlObj.openConnection();
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(30000);
                
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
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
                    
                    binding.mainLoadingLayout.setVisibility(View.GONE);
                    updateCategoryCounts();
                    handleStartCategory();
                    
                    if (getIntent().getBooleanExtra("auto_start", false)) {
                        handleAutoStartLastChannel();
                    }
                });

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    binding.mainLoadingLayout.setVisibility(View.GONE);
                    Toast.makeText(LiveTvActivity.this, "Playlist yüklənmədi: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    updateCategoryCounts();
                });
            }
        });
    }

    private long lastKeyTime = 0;
    private static final int KEY_DELAY = 30; // ms for snappy feel

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (System.currentTimeMillis() - lastKeyTime < KEY_DELAY) return true;
        lastKeyTime = System.currentTimeMillis();

        View focused = getCurrentFocus();
        if (focused == null) return super.onKeyDown(keyCode, event);

        // --- Categories List ---
        if (isViewInRecyclerView(focused, binding.rvCategories)) {
            int pos = getRvPosition(binding.rvCategories, focused);
            int count = categoryAdapter.getItemCount();

            if (keyCode == KeyEvent.KEYCODE_DPAD_UP && pos == 0) {
                scrollToAndFocus(binding.rvCategories, count - 1);
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && pos == count - 1) {
                scrollToAndFocus(binding.rvCategories, 0);
                return true;
            }
        }

        // --- Channels List ---
        else if (isViewInRecyclerView(focused, binding.rvChannels)) {
            int pos = getRvPosition(binding.rvChannels, focused);
            int count = channelAdapter.getItemCount();

            if (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                if (keyCode == KeyEvent.KEYCODE_DPAD_UP && pos == 0) {
                    binding.etSearch.requestFocus();
                    return true;
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && pos == count - 1) {
                    binding.etSearch.requestFocus();
                    return true;
                }

                // Native movement with correction
                boolean handled = super.onKeyDown(keyCode, event);
                View newFocus = getCurrentFocus();
                if (newFocus != null && isViewInRecyclerView(newFocus, binding.rvCategories)) {
                    // Oops, jumped to sidebar during vertical scroll. Force back to channels.
                    binding.rvChannels.requestFocus();
                    // Optional: find the closest view in channels, but requestFocus on RV usually works
                }
                return handled;
            }
        }

        // --- Search Box ---
        else if (focused.getId() == binding.etSearch.getId()) {
            int count = channelAdapter.getItemCount();
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP && count > 0) {
                scrollToAndFocus(binding.rvChannels, count - 1);
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && count > 0) {
                scrollToAndFocus(binding.rvChannels, 0);
                return true;
            }
        }

        return super.onKeyDown(keyCode, event);
    }

    private int getRvPosition(RecyclerView rv, View focused) {
        View view = rv.findContainingItemView(focused);
        if (view != null) {
            return rv.getChildAdapterPosition(view);
        }
        return RecyclerView.NO_POSITION;
    }

    private void scrollToAndFocus(RecyclerView rv, int position) {
        if (rv.getLayoutManager() instanceof LinearLayoutManager) {
            ((LinearLayoutManager) rv.getLayoutManager()).scrollToPositionWithOffset(position, 0);
        } else {
            rv.scrollToPosition(position);
        }
        rv.postDelayed(() -> {
            RecyclerView.ViewHolder vh = rv.findViewHolderForAdapterPosition(position);
            if (vh != null) vh.itemView.requestFocus();
            else {
                View v = rv.getLayoutManager() != null ? rv.getLayoutManager().findViewByPosition(position) : null;
                if (v != null) v.requestFocus();
            }
        }, 50);
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
