package com.bykerimoff.player;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.SurfaceHolder;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.Tracks;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.MediaItem;
import java.util.Collections;
import androidx.media3.ui.CaptionStyleCompat;
import androidx.media3.ui.SubtitleView;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.okhttp.OkHttpDataSource;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;

import okhttp3.OkHttpClient;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.Date;

import com.bumptech.glide.Glide;
import com.bykerimoff.player.adapters.ArchiveAdapter;
import com.bykerimoff.player.adapters.CategoryAdapter;
import com.bykerimoff.player.adapters.ChannelAdapter;
import com.bykerimoff.player.adapters.TrackAdapter;
import com.bykerimoff.player.api.ApiClient;
import com.bykerimoff.player.databinding.ActivityPlayerBinding;
import com.bykerimoff.player.models.Category;
import com.bykerimoff.player.models.Channel;
import com.bykerimoff.player.models.EpgProgram;
import com.bykerimoff.player.models.XtreamEpg;
import com.bykerimoff.player.models.ResumeItem;
import com.bykerimoff.player.utils.DataManager;
import com.bykerimoff.player.utils.FavoriteManager;
import com.bykerimoff.player.utils.NetworkUtils;
import com.bykerimoff.player.utils.ResumeManager;
import com.bykerimoff.player.utils.SleepTimerManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class PlayerActivity extends AppCompatActivity {

    private ActivityPlayerBinding binding;
    private final Handler osdHandler = new Handler(Looper.getMainLooper());
    
    private ExoPlayer exoPlayer;
    private AudioManager audioManager;
    private int currentIndex = 0;
    private List<Channel> channelList;
    private String playerType = "exo2";
    
    private String channelNumberInput = "";
    private final Handler channelSwitchHandler = new Handler(Looper.getMainLooper());
    private final Runnable channelSwitchRunnable = new Runnable() {
        @Override
        public void run() {
            processNumericInput();
        }
    };
    
    private int retryCount = 0;
    private final int MAX_RETRIES = 5;

    private final Runnable bufferingTimeoutRunnable = new Runnable() {
        @Override
        public void run() {
            if (exoPlayer != null && (exoPlayer.getPlaybackState() == Player.STATE_BUFFERING)) {
                if (retryCount < MAX_RETRIES) {
                    retryCount++;
                    exoPlayer.prepare();
                    exoPlayer.play();
                } else {
                    showErrorOverlay("Yayım Gecikir", "Kanal açılmır və ya internet zəifdir");
                }
            }
        }
    };
    
    private final Handler progressHandler = new Handler(Looper.getMainLooper());
    private final Runnable updateProgressRunnable = new Runnable() {
        @Override
        public void run() {
            if (exoPlayer != null && exoPlayer.isPlaying()) {
                long duration = exoPlayer.getDuration();
                long current = exoPlayer.getCurrentPosition();
                if (duration > 0) {
                    binding.vodProgressLayout.setVisibility(View.VISIBLE);
                    binding.vodSeekBar.setMax((int) duration);
                    binding.vodSeekBar.setProgress((int) current);
                    binding.tvCurrentPosition.setText(formatTime(current));
                    binding.tvTotalDuration.setText(formatTime(duration));
                } else {
                    binding.vodProgressLayout.setVisibility(View.GONE);
                }
            }
            progressHandler.postDelayed(this, 1000);
        }
    };
    
    private static final Map<String, String> epgCache = new HashMap<>();

    private CategoryAdapter playerCategoryAdapter;
    private ChannelAdapter channelAdapter;
    private List<Channel> allCategoryChannels = new ArrayList<>();
    private String currentPlaylistType = "m3u";
    private int currentResizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL;
    
    private List<EpgProgram> archiveList = new ArrayList<>();
    private com.bykerimoff.player.adapters.ArchiveAdapter archiveAdapter;
    
    private List<TrackAdapter.TrackInfo> trackList = new ArrayList<>();
    private TrackAdapter trackAdapter;
    private int currentTrackType = -1; // C.TRACK_TYPE_AUDIO or C.TRACK_TYPE_TEXT

    private android.os.CountDownTimer testCountDownTimer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        com.bykerimoff.player.utils.ThemeManager.INSTANCE.applyTheme(this);
        super.onCreate(savedInstanceState);
        binding = ActivityPlayerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        channelList = DataManager.getCurrentChannelList();
        currentIndex = getIntent().getIntExtra("channel_index", 0);

        SharedPreferences prefs = getSharedPreferences("neoplay_prefs", MODE_PRIVATE);
        playerType = prefs.getString("player_type", "exo2");
        currentPlaylistType = prefs.getString("playlist_type", "m3u");

        initExoPlayer(playerType);
        setupPlayerChannelList();
        setupPlayerCategoryList();
        setupPlayerSearch();
        setupArchiveList();
        setupTrackList();
        

        progressHandler.post(updateProgressRunnable);

        binding.vodSeekBar.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && exoPlayer != null) {
                    exoPlayer.seekTo(progress);
                }
            }
            @Override
            public void onStartTrackingTouch(android.widget.SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(android.widget.SeekBar seekBar) {}
        });

        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (binding.rvPlayerCategories.getVisibility() == View.VISIBLE) {
                    binding.rvPlayerCategories.setVisibility(View.GONE);
                } else if (binding.playerChannelSidebar.getVisibility() == View.VISIBLE) {
                    binding.playerChannelSidebar.setVisibility(View.GONE);
                } else if (binding.playerArchiveSidebar.getVisibility() == View.VISIBLE) {
                    binding.playerArchiveSidebar.setVisibility(View.GONE);
                } else if (binding.playerTracksSidebar.getVisibility() == View.VISIBLE) {
                    binding.playerTracksSidebar.setVisibility(View.GONE);
                } else {
                    setEnabled(false);
                    onBackPressed();
                }
            }
        });

        if (channelList != null && !channelList.isEmpty()) {
            loadChannel(channelList.get(currentIndex));
        }

        setupAnnouncement();
    }

    private void setupAnnouncement() {
        SharedPreferences prefs = getSharedPreferences("neoplay_prefs", MODE_PRIVATE);
        String announcement = DataManager.getAdminAnnouncement();
        String colorHex = DataManager.getAdminAnnouncementColor();
        
        // Əgər DataManager-də hələ yoxdursa, yaddaşdan oxu
        if (announcement == null || announcement.isEmpty()) {
            announcement = prefs.getString("last_announcement", "");
        }
        if (colorHex == null || colorHex.isEmpty()) {
            colorHex = prefs.getString("last_announcement_color", "");
        }

        if (announcement != null && !announcement.isEmpty()) {
            // Yeni sətirləri təmizləyirik ki, lentdə tam görsənsin
            String cleanAnnouncement = announcement.replace("\n", "  |  ");
            binding.tvAnnouncement.setText(cleanAnnouncement);
            
            // Rəngi tətbiq et
            if (!colorHex.isEmpty()) {
                try {
                    binding.tvAnnouncement.setTextColor(android.graphics.Color.parseColor(colorHex));
                } catch (Exception e) {
                    binding.tvAnnouncement.setTextColor(android.graphics.Color.WHITE);
                }
            } else {
                binding.tvAnnouncement.setTextColor(android.graphics.Color.WHITE);
            }
            
            binding.announcementContainer.setVisibility(View.VISIBLE);
            startAnnouncementAnimation();
        } else {
            binding.announcementContainer.setVisibility(View.GONE);
        }
    }

    private void startAnnouncementAnimation() {
        binding.tvAnnouncement.post(() -> {
            float screenWidth = getResources().getDisplayMetrics().widthPixels;
            float textWidth = binding.tvAnnouncement.getPaint().measureText(binding.tvAnnouncement.getText().toString());
            
            // Animasiya: Sağdan sola (Konteynerin içində)
            android.view.animation.TranslateAnimation animation = new android.view.animation.TranslateAnimation(
                    screenWidth, 
                    -textWidth - 1000, // Tam itənə qədər getsin
                    0, 0);
            
            animation.setDuration(25000); // Daha səliqəli və yavaş sürət
            animation.setRepeatCount(android.view.animation.Animation.INFINITE);
            animation.setInterpolator(new android.view.animation.LinearInterpolator());
            
            binding.tvAnnouncement.startAnimation(animation);
        });
    }

    private void setupPlayerChannelList() {
        if (channelList == null) return;
        channelAdapter = new ChannelAdapter(channelList, new ChannelAdapter.OnChannelClickListener() {
            @Override
            public void onChannelClick(Channel channel) {
                currentIndex = channelList.indexOf(channel);
                loadChannel(channel);
                binding.playerChannelSidebar.setVisibility(View.GONE);
                binding.rvPlayerCategories.setVisibility(View.GONE);
            }

            @Override
            public void onChannelFocus(Channel channel) {}

            @Override
            public void onChannelLongClick(Channel channel) {
                FavoriteManager fm = new FavoriteManager(PlayerActivity.this);
                boolean isAdded = fm.toggleFavorite(channel.getId());
                channelAdapter.notifyDataSetChanged();
                String message = isAdded ? "Sevimli siyahısına əlavə edildi" : "Sevimli siyahısından çıxarıldı";
                android.widget.Toast.makeText(PlayerActivity.this, message, android.widget.Toast.LENGTH_SHORT).show();
            }
        });
        binding.rvPlayerChannels.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(this));
        binding.rvPlayerChannels.setAdapter(channelAdapter);
    }

    private void setupPlayerCategoryList() {
        List<Category> categories = DataManager.getCurrentCategoryList();
        if (categories == null || categories.isEmpty()) return;

        playerCategoryAdapter = new CategoryAdapter(categories, category -> {
            updateChannelsByCategory(category);
            binding.etPlayerSearch.requestFocus();
        });

        playerCategoryAdapter.setOnCategoryFocusListener(this::updateChannelsByCategory);

        binding.rvPlayerCategories.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(this));
        binding.rvPlayerCategories.setAdapter(playerCategoryAdapter);
    }

    private void updateChannelsByCategory(Category category) {
        String key = "xtream".equalsIgnoreCase(currentPlaylistType) ? category.getId() : category.getName();
        List<Channel> categoryChannels;
        
        if ("0".equals(category.getId()) || "Sevimlilər".equals(category.getName())) {
            categoryChannels = new ArrayList<>();
            FavoriteManager fm = new FavoriteManager(this);
            for (Channel c : DataManager.getCurrentChannelList()) {
                if (fm.isFavorite(c.getId())) categoryChannels.add(c);
            }
        } else {
            categoryChannels = DataManager.getCurrentChannelMap().get(key);
        }

        if (categoryChannels != null) {
            allCategoryChannels = new ArrayList<>(categoryChannels);
            channelList = new ArrayList<>(allCategoryChannels);
            binding.etPlayerSearch.setText(""); // Reset search
            setupPlayerChannelList();
            binding.playerChannelSidebar.setVisibility(View.VISIBLE);
        }
    }

    private void setupPlayerSearch() {
        binding.etPlayerSearch.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterChannels(s.toString());
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {}
        });
    }

    private void setupArchiveList() {
        archiveAdapter = new com.bykerimoff.player.adapters.ArchiveAdapter(archiveList, new com.bykerimoff.player.adapters.ArchiveAdapter.OnProgramClickListener() {
            @Override
            public void onProgramClick(EpgProgram program) {
                playArchiveProgram(program);
                binding.playerArchiveSidebar.setVisibility(View.GONE);
            }

            @Override
            public void onProgramLongClick(EpgProgram program) {
                // Funksiya silindi
            }
        });
        binding.rvArchive.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(this));
        binding.rvArchive.setAdapter(archiveAdapter);
    }

    private void playArchiveProgram(EpgProgram program) {
        if (channelList == null || currentIndex < 0 || currentIndex >= channelList.size()) return;
        Channel channel = channelList.get(currentIndex);
        
        String baseUrl = channel.getStreamUrl();
        // Xtream format: http://host/live/user/pass/stream_id.m3u8
        // Archive format: http://host/timeshift/user/pass/duration/start_time/stream_id.m3u8
        
        if (currentPlaylistType.equalsIgnoreCase("xtream")) {
            try {
                SharedPreferences prefs = getSharedPreferences("neoplay_prefs", MODE_PRIVATE);
                String host = prefs.getString("xtream_host", "");
                String user = prefs.getString("xtream_user", "");
                String pass = prefs.getString("xtream_pass", "");
                
                String startTimeStr = new SimpleDateFormat("yyyy-MM-dd:HH-mm", Locale.US).format(new Date(program.getStartTime()));
                int durationMinutes = (int) ((program.getEndTime() - program.getStartTime()) / 60000);
                
                String archiveUrl = host + "/timeshift/" + user + "/" + pass + "/" + durationMinutes + "/" + startTimeStr + "/" + channel.getId() + ".ts";
                
                if (exoPlayer == null) initExoPlayer(playerType);
                exoPlayer.stop();
                exoPlayer.clearMediaItems();
                exoPlayer.setMediaItem(androidx.media3.common.MediaItem.fromUri(archiveUrl));
                exoPlayer.prepare();
                exoPlayer.play();
                
                binding.tvEpgInfo.setText("ARXİV: " + program.getTitle());
                binding.tvQuality.setText("ARXİV");
                binding.vodProgressLayout.setVisibility(View.VISIBLE);
                showOsd();
                
            } catch (Exception e) {
                showErrorOverlay("Arxiv xətası", "Yayım başladıla bilmədi");
            }
        } else {
            // M3U catchup support (simplified Default/Shift)
            String catchupUrl = channel.getCatchupSource();
            if (catchupUrl == null || catchupUrl.isEmpty()) catchupUrl = baseUrl;
            
            // Zaman ştamplarını yerləşdir
            long startUnix = program.getStartTime() / 1000;
            String finalUrl = catchupUrl.replace("${start}", String.valueOf(startUnix))
                                        .replace("{utc}", String.valueOf(startUnix))
                                        .replace("${offset}", "0");
            
            if (exoPlayer == null) initExoPlayer(playerType);
            exoPlayer.stop();
            exoPlayer.clearMediaItems();
            exoPlayer.setMediaItem(androidx.media3.common.MediaItem.fromUri(finalUrl));
            exoPlayer.prepare();
            exoPlayer.play();
            
            binding.tvEpgInfo.setText("ARXİV: " + program.getTitle());
            binding.vodProgressLayout.setVisibility(View.VISIBLE);
            showOsd();
        }
    }

    private void filterChannels(String query) {
        if (allCategoryChannels == null) return;
        
        List<Channel> filtered = new ArrayList<>();
        for (Channel c : allCategoryChannels) {
            if (c.getName().toLowerCase().contains(query.toLowerCase())) {
                filtered.add(c);
            }
        }
        channelList = filtered;
        setupPlayerChannelList();
    }

    @OptIn(markerClass = UnstableApi.class)
    private void initExoPlayer(String mode) {
        if (exoPlayer == null) {
            OkHttpDataSource.Factory dataSourceFactory = NetworkUtils.getDataSourceFactory(this);
            
            // IPTV axınları üçün daha dözümlü Extractor sazlamaları
            androidx.media3.extractor.DefaultExtractorsFactory extractorsFactory = new androidx.media3.extractor.DefaultExtractorsFactory()
                    .setTsExtractorFlags(androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES 
                                       | androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS
                                       | androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory.FLAG_IGNORE_SPLICE_INFO_STREAM
                                       | androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory.FLAG_ENABLE_HDMV_DTS_AUDIO_STREAMS)
                    .setAdtsExtractorFlags(androidx.media3.extractor.ts.AdtsExtractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING);

            DefaultMediaSourceFactory mediaSourceFactory = new DefaultMediaSourceFactory(dataSourceFactory, extractorsFactory);
            
            DefaultTrackSelector trackSelector = new DefaultTrackSelector(this);
            DefaultTrackSelector.Parameters.Builder trackParamsBuilder = trackSelector.buildUponParameters()
                    .setPreferredAudioLanguage("az")
                    .setExceedAudioConstraintsIfNecessary(true)
                    .setExceedRendererCapabilitiesIfNecessary(true)
                    .setExceedVideoConstraintsIfNecessary(true)
                    .setTunnelingEnabled(false);

            SharedPreferences prefs = getSharedPreferences("neoplay_prefs", MODE_PRIVATE);
            if (prefs.getBoolean("data_saver_enabled", false)) {
                trackParamsBuilder.setMaxVideoSizeSd();
                trackParamsBuilder.setMaxVideoBitrate(1000000); // 1 Mbps
            }

            trackSelector.setParameters(trackParamsBuilder);

            androidx.media3.common.AudioAttributes audioAttributes = new androidx.media3.common.AudioAttributes.Builder()
                    .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                    .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build();

            // Daha mükəmməl buferləmə ayarları (50-60 FPS üçün optimallaşdırıldı)
            androidx.media3.exoplayer.DefaultLoadControl loadControl = new androidx.media3.exoplayer.DefaultLoadControl.Builder()
                    .setBufferDurationsMs(
                            20000, // minBufferMs
                            60000, // maxBufferMs
                            2000,  // bufferForPlaybackMs
                            5000   // bufferForPlaybackAfterRebufferMs
                    )
                    .setPrioritizeTimeOverSizeThresholds(true)
                    .build();

            // AC3, DTS, AAC və digər multi-kanal səslər üçün dekoder prioriteti və fallback
            androidx.media3.exoplayer.DefaultRenderersFactory renderersFactory = new androidx.media3.exoplayer.DefaultRenderersFactory(this)
                    .setExtensionRendererMode(androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
                    .setEnableDecoderFallback(true);

            exoPlayer = new ExoPlayer.Builder(this, renderersFactory)
                    .setMediaSourceFactory(mediaSourceFactory)
                    .setTrackSelector(trackSelector)
                    .setLoadControl(loadControl)
                    .setAudioAttributes(audioAttributes, true)
                    .setHandleAudioBecomingNoisy(true)
                    .build();

            binding.playerView.setPlayer(exoPlayer);

            // Altyazı stilini təyin et (Ağ mətn, Qara haşiyə)
            CaptionStyleCompat style = new CaptionStyleCompat(
                    android.graphics.Color.WHITE,
                    android.graphics.Color.TRANSPARENT,
                    android.graphics.Color.TRANSPARENT,
                    CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                    android.graphics.Color.BLACK,
                    null
            );
            if (binding.playerView.getSubtitleView() != null) {
                binding.playerView.getSubtitleView().setApplyEmbeddedStyles(false);
                binding.playerView.getSubtitleView().setApplyEmbeddedFontSizes(false);
                binding.playerView.getSubtitleView().setStyle(style);
                binding.playerView.getSubtitleView().setFixedTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 24f);
            }
            
            exoPlayer.addListener(new Player.Listener() {
                @Override
                public void onPlaybackStateChanged(int playbackState) {
                    if (playbackState == Player.STATE_BUFFERING) {
                        binding.bufferingLayout.setVisibility(View.VISIBLE);
                        // 15 saniyəlik yüklənmə taymautu
                        osdHandler.removeCallbacks(bufferingTimeoutRunnable);
                        osdHandler.postDelayed(bufferingTimeoutRunnable, 15000);
                    } else {
                        binding.bufferingLayout.setVisibility(View.GONE);
                        osdHandler.removeCallbacks(bufferingTimeoutRunnable);
                        if (playbackState == Player.STATE_READY) {
                            binding.errorLayout.setVisibility(View.GONE);
                            binding.errorLayout.clearAnimation();
                            retryCount = 0; // Yalnız uğurlu qoşulmada sıfırla
                            updateVideoMetrics();
                        }
                    }
                }

                @Override
                public void onVideoSizeChanged(@NonNull androidx.media3.common.VideoSize videoSize) {
                    if (videoSize.width > 0 && videoSize.height > 0) {
                        String res = videoSize.width + "x" + videoSize.height;
                        binding.tvQuality.setText(res);
                        if (binding.tvQualityCorner != null) {
                            binding.tvQualityCorner.setText(res);
                        }
                        updateVideoMetrics();
                    }
                }

                @Override
                public void onTracksChanged(@NonNull Tracks tracks) {
                    updateVideoMetrics();
                }

                private void updateVideoMetrics() {
                    if (exoPlayer == null) return;
                    
                    Tracks tracks = exoPlayer.getCurrentTracks();
                    for (Tracks.Group group : tracks.getGroups()) {
                        if (group.getType() == C.TRACK_TYPE_VIDEO && group.isSelected()) {
                            for (int i = 0; i < group.length; i++) {
                                if (group.isTrackSelected(i)) {
                                    Format format = group.getTrackFormat(i);
                                    if (format.frameRate > 0) {
                                        String fpsText = Math.round(format.frameRate) + " FPS";
                                        binding.tvFps.setText(fpsText);
                                        binding.tvFps.setVisibility(View.VISIBLE);
                                        if (binding.tvFpsCorner != null) {
                                            binding.tvFpsCorner.setText(fpsText);
                                            binding.tvFpsCorner.setVisibility(View.VISIBLE);
                                        }
                                    } else {
                                        binding.tvFps.setVisibility(View.GONE);
                                        if (binding.tvFpsCorner != null) binding.tvFpsCorner.setVisibility(View.GONE);
                                    }
                                    return;
                                }
                            }
                        }
                    }
                }

                @Override
                public void onPlayerError(@NonNull androidx.media3.common.PlaybackException error) {
                    binding.bufferingLayout.setVisibility(View.GONE);
                    binding.errorLayout.setVisibility(View.GONE); 
                    osdHandler.removeCallbacks(bufferingTimeoutRunnable);
                    
                    if (retryCount < MAX_RETRIES) {
                        retryCount++;
                        binding.tvEpgInfo.setText("Yenidən yoxlanılır (" + retryCount + "/" + MAX_RETRIES + ")...");
                        binding.osdLayout.setVisibility(View.VISIBLE);
                        
                        osdHandler.postDelayed(() -> {
                            if (exoPlayer != null) {
                                exoPlayer.prepare();
                                exoPlayer.play();
                            }
                        }, 1500); // Daha sürətli təkrar yoxlama
                    } else {
                        showErrorOverlay("Müvəqqəti texniki nasazlıq", "Yayım tezliklə bərpa olunacaq");
                    }
                }
            });
        }
    }

    private void loadChannel(Channel channel) {
        if (exoPlayer == null) initExoPlayer(playerType);
        
        retryCount = 0; // Retry sayını sıfırla
        osdHandler.removeCallbacks(bufferingTimeoutRunnable);
        binding.errorLayout.setVisibility(View.GONE);
        binding.errorLayout.clearAnimation();
        
        exoPlayer.stop();
        exoPlayer.clearMediaItems();
        binding.vodProgressLayout.setVisibility(View.GONE); // Live TV-də progress barı gizlə

        String url = channel.getStreamUrl();
        MediaItem.Builder mediaItemBuilder = new MediaItem.Builder();
        if (url != null) {
            mediaItemBuilder.setUri(Uri.parse(url));
            String lower = url.toLowerCase(Locale.ROOT);
            if (lower.contains("m3u8") || lower.contains("stream.php") || lower.contains(".php") || lower.contains("/hls/")) {
                mediaItemBuilder.setMimeType(androidx.media3.common.MimeTypes.APPLICATION_M3U8);
            } else if (lower.contains(".ts") || lower.contains("output=ts") || lower.contains("output=mpegts") || lower.contains("/live/") || lower.contains("/mpegts")) {
                // MPEG-TS formatı bir çox canlı yayımda istifadə olunur və AC3 səs bu formatdadır
                mediaItemBuilder.setMimeType(androidx.media3.common.MimeTypes.VIDEO_MP2T);
            } else if (lower.contains(".mpd")) {
                mediaItemBuilder.setMimeType(androidx.media3.common.MimeTypes.APPLICATION_MPD);
            }
        }

        MediaItem mediaItem = mediaItemBuilder.build();
        exoPlayer.setMediaItem(mediaItem);
        exoPlayer.prepare();

        // Davam etmə yoxlanışı
        long resumePos = getIntent().getLongExtra("resume_position", -1L);
        if (resumePos != -1L) {
            exoPlayer.seekTo(resumePos);
            getIntent().removeExtra("resume_position"); // Bir dəfə istifadə et
        } else {
            // Əgər Dashboard-dan gəlməyibsə, yaddaşda olub olmadığını yoxla
            List<ResumeItem> list = ResumeManager.INSTANCE.getResumeList(this);
            for (ResumeItem ri : list) {
                if (ri.getStreamUrl().equals(channel.getStreamUrl()) && ri.getPosition() > 60000) { // 1 dəqiqədən çox baxılıbsa
                    showResumeDialog(ri.getPosition());
                    break;
                }
            }
        }

        exoPlayer.play();

        binding.tvChannelName.setAlpha(0f);
        binding.tvChannelName.setText(channel.getName());
        binding.tvChannelName.animate().alpha(1f).setDuration(400).start();

        if (binding.ivChannelLogo != null) {
            binding.ivChannelLogo.setAlpha(0f);
            Glide.with(this)
                    .load(channel.getLogoUrl())
                    .placeholder(R.drawable.default_logo)
                    .error(R.drawable.default_logo)
                    .into(binding.ivChannelLogo);
            binding.ivChannelLogo.animate().alpha(1f).setDuration(400).start();
        }

        binding.tvQuality.setText("...");
        binding.tvFps.setText("");
        if (binding.tvQualityCorner != null) {
            binding.tvQualityCorner.setText("...");
            binding.tvFpsCorner.setText("");
        }

        if (channelAdapter != null) {
            channelAdapter.setSelectedPosition(currentIndex);
        }

        // Son baxılan kanalı yadda saxla
        getSharedPreferences("neoplay_prefs", MODE_PRIVATE)
                .edit()
                .putString("last_channel_url", channel.getStreamUrl())
                .putString("last_channel_id", channel.getId())
                .apply();

        fetchEpg(channel.getId());
        showOsd();
        
        startTestCountdownInPlayer();
    }

    private void startTestCountdownInPlayer() {
        updateTestCountdownInPlayer();
    }

    private void fetchEpg(String channelId) {
        if (epgCache.containsKey(channelId)) {
            binding.tvEpgInfo.setText(epgCache.get(channelId));
            return;
        }

        // Birinci Xtream EPG-ni yoxla
        SharedPreferences prefs = getSharedPreferences("neoplay_prefs", MODE_PRIVATE);
        String host = prefs.getString("xtream_host", "");
        String user = prefs.getString("xtream_user", "");
        String pass = prefs.getString("xtream_pass", "");
        
        if (!host.isEmpty() && !user.isEmpty() && !pass.isEmpty()) {
            String url = host + "/player_api.php?username=" + user + "&password=" + pass + "&action=get_short_epg&id=" + channelId;
            ApiClient.getService().getXtreamEpg(url).enqueue(new Callback<XtreamEpg>() {
                @Override
                public void onResponse(Call<XtreamEpg> call, Response<XtreamEpg> response) {
                    if (response.isSuccessful() && response.body() != null && response.body().getListings() != null && !response.body().getListings().isEmpty()) {
                        String title = response.body().getListings().get(0).title;
                        epgCache.put(channelId, title);
                        binding.tvEpgInfo.setText(title);
                    } else {
                        checkXmltvEpg(channelId);
                    }
                }

                @Override
                public void onFailure(Call<XtreamEpg> call, Throwable t) {
                    checkXmltvEpg(channelId);
                }
            });
        } else {
            checkXmltvEpg(channelId);
        }
    }

    private void checkXmltvEpg(String channelId) {
        Map<String, String> xmltv = DataManager.getXmltvCache();
        if (xmltv != null && !xmltv.isEmpty()) {
            // Channel obyektini tap ki tvgId-ni götürək
            Channel currentChannel = null;
            if (channelList != null && currentIndex >= 0 && currentIndex < channelList.size()) {
                currentChannel = channelList.get(currentIndex);
            }

            if (currentChannel != null) {
                String title = xmltv.get(currentChannel.getTvgId());
                if (title == null || title.isEmpty()) title = xmltv.get(currentChannel.getName());
                
                // Ağıllı ad uyğunlaşdırması (Normalized)
                if (title == null || title.isEmpty()) {
                    String normalized = com.bykerimoff.player.utils.XMLTVParser.normalizeName(currentChannel.getName());
                    title = xmltv.get(normalized);
                }

                if (title != null && !title.isEmpty()) {
                    epgCache.put(channelId, title);
                    binding.tvEpgInfo.setText(title);
                    return;
                }
            }
        }
        binding.tvEpgInfo.setText("EPG məlumatı yoxdur");
    }

    private String formatTime(long ms) {
        long seconds = (ms / 1000) % 60;
        long minutes = (ms / (1000 * 60)) % 60;
        long hours = (ms / (1000 * 60 * 60));
        if (hours > 0) {
            return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int keyCode = event.getKeyCode();
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (keyCode == KeyEvent.KEYCODE_CHANNEL_UP) {
                playNextChannel();
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_CHANNEL_DOWN) {
                playPreviousChannel();
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // Rəqəm düymələrini tut (0-9)
        if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) {
            appendNumericInput(keyCode - KeyEvent.KEYCODE_0);
            return true;
        }

        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    event.startTracking();
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                // Əgər hər hansı bir yan menyu açıqdırsa, menyuda hərəkət et
                if (binding.playerChannelSidebar.getVisibility() == View.VISIBLE || 
                    binding.rvPlayerCategories.getVisibility() == View.VISIBLE ||
                    binding.playerArchiveSidebar.getVisibility() == View.VISIBLE ||
                    binding.playerTracksSidebar.getVisibility() == View.VISIBLE) {
                    
                    if (binding.playerChannelSidebar.getVisibility() == View.VISIBLE) {
                        binding.playerChannelSidebar.setVisibility(View.GONE);
                        binding.rvPlayerCategories.setVisibility(View.VISIBLE);
                        binding.rvPlayerCategories.requestFocus();
                        return true;
                    }
                    return super.onKeyDown(keyCode, event);
                }
                
                // Menyular bağlıdırsa
                if (exoPlayer != null) {
                    // Əgər FİLM-dirsə (Live deyilsə) -> 15s GERİ çək
                    if (!exoPlayer.isCurrentMediaItemLive() && exoPlayer.getDuration() > 0) {
                        long newPos = Math.max(0, exoPlayer.getCurrentPosition() - 15000);
                        exoPlayer.seekTo(newPos);
                        showOsd();
                        return true;
                    } else {
                        // Əgər CANLI yayım-dırsa -> Kateqoriyaları aç
                        binding.rvPlayerCategories.setVisibility(View.VISIBLE);
                        binding.rvPlayerCategories.requestFocus();
                        return true;
                    }
                }
                return super.onKeyDown(keyCode, event);

            case KeyEvent.KEYCODE_DPAD_RIGHT:
                // Əgər kateqoriya siyahısı açıqdırsa, kanal siyahısına keç
                if (binding.rvPlayerCategories.getVisibility() == View.VISIBLE) {
                    binding.playerChannelSidebar.setVisibility(View.VISIBLE);
                    binding.rvPlayerChannels.requestFocus();
                    return true;
                }
                
                // Digər menyular açıqdırsa, default davranışı saxla
                if (binding.playerChannelSidebar.getVisibility() == View.VISIBLE || 
                    binding.playerArchiveSidebar.getVisibility() == View.VISIBLE ||
                    binding.playerTracksSidebar.getVisibility() == View.VISIBLE) {
                    return super.onKeyDown(keyCode, event);
                }

                // Menyular bağlıdırsa VƏ FİLM-dirsə -> 15s İRƏLİ çək
                if (exoPlayer != null && !exoPlayer.isCurrentMediaItemLive() && exoPlayer.getDuration() > 0) {
                    long newPos = Math.min(exoPlayer.getDuration(), exoPlayer.getCurrentPosition() + 15000);
                    exoPlayer.seekTo(newPos);
                    showOsd();
                    return true;
                }

                return super.onKeyDown(keyCode, event);
            case KeyEvent.KEYCODE_DPAD_UP:
                if (binding.playerChannelSidebar.getVisibility() == View.VISIBLE) {
                    if (binding.rvPlayerChannels.hasFocus() && 
                        ((androidx.recyclerview.widget.LinearLayoutManager)binding.rvPlayerChannels.getLayoutManager()).findFirstCompletelyVisibleItemPosition() == 0) {
                        binding.etPlayerSearch.requestFocus();
                        return true;
                    }
                    return super.onKeyDown(keyCode, event);
                }
                if (binding.playerArchiveSidebar.getVisibility() == View.VISIBLE || 
                    binding.playerTracksSidebar.getVisibility() == View.VISIBLE ||
                    binding.rvPlayerCategories.getVisibility() == View.VISIBLE) {
                    return super.onKeyDown(keyCode, event);
                }
                playNextChannel();
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (binding.playerChannelSidebar.getVisibility() == View.VISIBLE) {
                    if (binding.etPlayerSearch.hasFocus()) {
                        binding.rvPlayerChannels.requestFocus();
                        return true;
                    }
                    return super.onKeyDown(keyCode, event);
                }
                if (binding.playerArchiveSidebar.getVisibility() == View.VISIBLE || 
                    binding.playerTracksSidebar.getVisibility() == View.VISIBLE ||
                    binding.rvPlayerCategories.getVisibility() == View.VISIBLE) {
                    return super.onKeyDown(keyCode, event);
                }
                playPreviousChannel();
                return true;
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                // Səsin dəyişməsini gözləmək üçün kiçik gecikmə ilə UI-ı yenilə
                osdHandler.postDelayed(this::updateVolumeUI, 50);
                return super.onKeyDown(keyCode, event);
            case KeyEvent.KEYCODE_PROG_YELLOW:
            case KeyEvent.KEYCODE_Y:
                toggleAspectRatio();
                return true;
            case KeyEvent.KEYCODE_PROG_RED:
                showTrackSidebar(androidx.media3.common.C.TRACK_TYPE_AUDIO);
                return true;
            case KeyEvent.KEYCODE_PROG_GREEN:
                showTrackSidebar(androidx.media3.common.C.TRACK_TYPE_TEXT);
                return true;
            case KeyEvent.KEYCODE_PROG_BLUE:
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    event.startTracking();
                }
                return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void setupTrackList() {
        trackAdapter = new TrackAdapter(trackList, track -> {
            if (exoPlayer == null) return;
            
            androidx.media3.common.TrackSelectionParameters params;
            if (track.trackIndex == -1) {
                // Altyazını söndür
                params = exoPlayer.getTrackSelectionParameters()
                        .buildUpon()
                        .setTrackTypeDisabled(currentTrackType, true)
                        .build();
            } else {
                params = exoPlayer.getTrackSelectionParameters()
                        .buildUpon()
                        .setOverrideForType(new androidx.media3.common.TrackSelectionOverride(track.group.getMediaTrackGroup(), track.trackIndex))
                        .setTrackTypeDisabled(currentTrackType, false)
                        .build();
            }
            
            exoPlayer.setTrackSelectionParameters(params);
            binding.playerTracksSidebar.setVisibility(View.GONE);
            String type = (currentTrackType == androidx.media3.common.C.TRACK_TYPE_AUDIO) ? "Səs dili" : "Altyazı";
            android.widget.Toast.makeText(this, type + " dəyişdirildi: " + track.name, android.widget.Toast.LENGTH_SHORT).show();
        });
        binding.rvTracks.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(this));
        binding.rvTracks.setAdapter(trackAdapter);
    }

    private void showTrackSidebar(int type) {
        if (exoPlayer == null) return;
        this.currentTrackType = type;
        trackList.clear();
        
        binding.tvTracksTitle.setText(type == androidx.media3.common.C.TRACK_TYPE_AUDIO ? "SƏS DİLLƏRİ" : "ALTYAZILAR");
        
        androidx.media3.common.Tracks tracks = exoPlayer.getCurrentTracks();
        for (androidx.media3.common.Tracks.Group group : tracks.getGroups()) {
            if (group.getType() == type) {
                for (int i = 0; i < group.length; i++) {
                    androidx.media3.common.Format format = group.getTrackFormat(i);
                    String label = format.label != null ? format.label : (format.language != null ? format.language : "Naməlum Dil");
                    trackList.add(new TrackAdapter.TrackInfo(label, group, i, group.isTrackSelected(i)));
                }
            }
        }
        
        if (type == androidx.media3.common.C.TRACK_TYPE_TEXT) {
            // Altyazını söndürmək variantı
            trackList.add(0, new TrackAdapter.TrackInfo("Söndür", null, -1, !tracks.isTypeSelected(type)));
            binding.btnSearchSubtitles.setVisibility(View.VISIBLE);
        } else {
            binding.btnSearchSubtitles.setVisibility(View.GONE);
        }

        if (trackList.isEmpty() && type != androidx.media3.common.C.TRACK_TYPE_TEXT) {
            android.widget.Toast.makeText(this, "Bu yayımda seçim yoxdur", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }

        trackAdapter.notifyDataSetChanged();
        binding.playerTracksSidebar.setVisibility(View.VISIBLE);
        binding.btnSearchSubtitles.setOnClickListener(v -> showSubtitleSearchDialog());
        binding.playerChannelSidebar.setVisibility(View.GONE);
        binding.rvPlayerCategories.setVisibility(View.GONE);
        binding.playerArchiveSidebar.setVisibility(View.GONE);
        binding.rvTracks.requestFocus();
    }

    @Override
    public boolean onKeyLongPress(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
            // Sevimlilərə əlavə/çıxarma (Long Press)
            if (channelList != null && currentIndex >= 0 && currentIndex < channelList.size()) {
                Channel currentChannel = channelList.get(currentIndex);
                FavoriteManager fm = new FavoriteManager(this);
                boolean isAdded = fm.toggleFavorite(currentChannel.getId());
                String message = isAdded ? "Sevimli siyahısına əlavə edildi" : "Sevimli siyahısından çıxarıldı";
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
                if (channelAdapter != null) channelAdapter.notifyDataSetChanged();
            }
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_PROG_BLUE) {
            // Arxiv/EPG açılması (Long Press)
            showArchiveSidebar();
            return true;
        }
        return super.onKeyLongPress(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
            if (event.isTracking() && !event.isCanceled()) {
                // Heç bir menyu açıq deyilsə
                if (binding.playerChannelSidebar.getVisibility() != View.VISIBLE && 
                    binding.rvPlayerCategories.getVisibility() != View.VISIBLE &&
                    binding.playerArchiveSidebar.getVisibility() != View.VISIBLE &&
                    binding.playerTracksSidebar.getVisibility() != View.VISIBLE) {
                    
                    // Əgər FİLM-dirsə (Live deyilsə) -> Pause/Play
                    if (exoPlayer != null && !exoPlayer.isCurrentMediaItemLive() && exoPlayer.getDuration() > 0) {
                        if (exoPlayer.isPlaying()) {
                            exoPlayer.pause();
                        } else {
                            exoPlayer.play();
                        }
                        showOsd();
                    } else {
                        // Əgər CANLI yayım-dırsa -> Kanal siyahısını aç
                        toggleChannelSidebar();
                    }
                } else {
                    // Menyular açıqdırsa qısa basma - Seçimi təsdiqlə (Sidebar daxili məntiq)
                    toggleChannelSidebar();
                }
            }
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_PROG_BLUE) {
            if (event.isTracking() && !event.isCanceled()) {
                // Göy düymə qısa basıldıqda - Sürətli Axtarış
                binding.playerChannelSidebar.setVisibility(View.VISIBLE);
                binding.etPlayerSearch.requestFocus();
            }
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    private void toggleChannelSidebar() {
        if (binding.playerChannelSidebar.getVisibility() == View.VISIBLE) {
            if (binding.etPlayerSearch.hasFocus()) {
                // Axtarış yerindədirsə, klaviaturanı açmaq üçün default davranışı saxla
                return;
            }
            binding.playerChannelSidebar.setVisibility(View.GONE);
        } else {
            binding.playerChannelSidebar.setVisibility(View.VISIBLE);
            binding.rvPlayerChannels.scrollToPosition(currentIndex);
            binding.rvPlayerChannels.postDelayed(() -> {
                RecyclerView.ViewHolder vh = binding.rvPlayerChannels.findViewHolderForAdapterPosition(currentIndex);
                if (vh != null) vh.itemView.requestFocus();
                else binding.rvPlayerChannels.requestFocus();
            }, 50);
        }
    }

    private void showArchiveSidebar() {
        if (channelList == null || currentIndex < 0 || currentIndex >= channelList.size()) return;
        Channel channel = channelList.get(currentIndex);
        
        binding.playerArchiveSidebar.setVisibility(View.VISIBLE);
        binding.playerChannelSidebar.setVisibility(View.GONE);
        binding.rvPlayerCategories.setVisibility(View.GONE);
        
        binding.tvArchiveTitle.setText("ARXİV: " + channel.getName());
        
        fetchArchiveEpg(channel);
    }

    private void fetchArchiveEpg(Channel channel) {
        archiveList.clear();
        archiveAdapter.notifyDataSetChanged();
        
        if (currentPlaylistType.equalsIgnoreCase("xtream")) {
            SharedPreferences prefs = getSharedPreferences("neoplay_prefs", MODE_PRIVATE);
            String host = prefs.getString("xtream_host", "");
            String user = prefs.getString("xtream_user", "");
            String pass = prefs.getString("xtream_pass", "");
            
            String url = host + "/player_api.php?username=" + user + "&password=" + pass + "&action=get_short_epg&stream_id=" + channel.getId();
            
            ApiClient.getService().getXtreamEpg(url).enqueue(new Callback<XtreamEpg>() {
                @Override
                public void onResponse(Call<XtreamEpg> call, Response<XtreamEpg> response) {
                    if (response.isSuccessful() && response.body() != null && response.body().getListings() != null) {
                        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
                        for (XtreamEpg.EpgListing listing : response.body().getListings()) {
                            try {
                                long start = sdf.parse(listing.start).getTime();
                                long end = sdf.parse(listing.stop).getTime();
                                archiveList.add(new EpgProgram(listing.title, start, end, "", true));
                            } catch (Exception ignored) {}
                        }
                        // Siyahını tərsinə düzək (ən yeni birinci)
                        java.util.Collections.reverse(archiveList);
                        archiveAdapter.notifyDataSetChanged();
                        binding.rvArchive.requestFocus();
                    }
                }

                @Override
                public void onFailure(Call<XtreamEpg> call, Throwable t) {}
            });
        } else {
            // M3U üçün XMLTV cache-dən və ya günlərdən istifadə etmək olar
            // Hələlik boş saxlayırıq və ya sadə mesaj veririk
            binding.tvArchiveTitle.setText("Arxiv dəstəklənmir (M3U)");
        }
    }

    private void playNextChannel() {
        if (channelList != null && !channelList.isEmpty()) {
            currentIndex++;
            if (currentIndex >= channelList.size()) {
                currentIndex = 0;
            }
            loadChannel(channelList.get(currentIndex));
        }
    }

    private void playPreviousChannel() {
        if (channelList != null && !channelList.isEmpty()) {
            currentIndex--;
            if (currentIndex < 0) {
                currentIndex = channelList.size() - 1;
            }
            loadChannel(channelList.get(currentIndex));
        }
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
        
        // Əvvəlki taymeri təmizlə və yenisini qoy
        osdHandler.removeCallbacks(volumeHideRunnable);
        osdHandler.postDelayed(volumeHideRunnable, 3000);
    }

    private final Runnable volumeHideRunnable = () -> binding.volumeLayout.setVisibility(View.GONE);

    @Override
    protected void onStop() {
        super.onStop();
        if (exoPlayer != null) {
            savePlaybackProgress();
            exoPlayer.pause();
        }
    }

    private void savePlaybackProgress() {
        if (exoPlayer != null && !exoPlayer.isCurrentMediaItemLive() && exoPlayer.getDuration() > 0) {
            if (channelList != null && currentIndex >= 0 && currentIndex < channelList.size()) {
                Channel channel = channelList.get(currentIndex);
                
                // Əgər video demək olar bitibsə (95%), siyahıdan təmizləyək
                if (exoPlayer.getCurrentPosition() > exoPlayer.getDuration() * 0.95) {
                    ResumeManager.INSTANCE.removeProgress(this, channel.getStreamUrl());
                    return;
                }

                ResumeItem item = new ResumeItem(
                    channel.getId(),
                    channel.getName(),
                    channel.getLogoUrl(),
                    channel.getRawEncryptedUrl(),
                    channel.getCategoryName(),
                    exoPlayer.getCurrentPosition(),
                    exoPlayer.getDuration(),
                    System.currentTimeMillis()
                );
                ResumeManager.INSTANCE.saveProgress(this, item);
            }
        }
    }

    private void showResumeDialog(long position) {
        new android.app.AlertDialog.Builder(this)
            .setTitle("Davam et")
            .setMessage("Qaldığınız yerdən davam edilsin?")
            .setPositiveButton("BƏLİ", (dialog, which) -> exoPlayer.seekTo(position))
            .setNegativeButton("XEYR", null)
            .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        progressHandler.removeCallbacksAndMessages(null);
        osdHandler.removeCallbacksAndMessages(null);
        if (testCountDownTimer != null) {
            testCountDownTimer.cancel();
        }
        if (exoPlayer != null) {
            exoPlayer.release();
        }
    }

    private void showErrorOverlay(String title, String subtitle) {
        binding.errorLayout.setVisibility(View.VISIBLE);
        binding.errorLayout.startAnimation(android.view.animation.AnimationUtils.loadAnimation(PlayerActivity.this, R.anim.pulse));
        
        binding.osdLayout.setVisibility(View.GONE);
        binding.volumeLayout.setVisibility(View.GONE);
        binding.bufferingLayout.setVisibility(View.GONE);
        
        binding.tvErrorTitle.setText(title.toUpperCase(Locale.ROOT));
        binding.tvErrorSubtitle.setText(subtitle);
        binding.tvEpgInfo.setText(title);
    }

    private void showOsd() {
        binding.osdLayout.setVisibility(View.VISIBLE);
        
        updateTestCountdownInPlayer();

        SleepTimerManager timerManager = SleepTimerManager.getInstance();
        if (timerManager.isRunning()) {
            String remaining = timerManager.getFormattedRemainingTime();
            binding.tvEpgInfo.setText(binding.tvEpgInfo.getText() + " | ⏳ " + remaining);
        }

        osdHandler.removeCallbacksAndMessages(null);
        osdHandler.postDelayed(() -> binding.osdLayout.setVisibility(View.GONE), 5000);
    }

    private void appendNumericInput(int digit) {
        channelNumberInput += digit;
        binding.tvNumericInput.setText(channelNumberInput);
        binding.tvNumericInput.setVisibility(View.VISIBLE);
        
        channelSwitchHandler.removeCallbacks(channelSwitchRunnable);
        channelSwitchHandler.postDelayed(channelSwitchRunnable, 2500); // 2.5 saniyə gözlə
    }

    private void processNumericInput() {
        try {
            int targetIndex = Integer.parseInt(channelNumberInput) - 1; // 1-based to 0-based
            if (channelList != null && targetIndex >= 0 && targetIndex < channelList.size()) {
                currentIndex = targetIndex;
                loadChannel(channelList.get(currentIndex));
            } else {
                binding.tvEpgInfo.setText("Səhv nömrə: " + channelNumberInput);
                showOsd();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        channelNumberInput = "";
        binding.tvNumericInput.setVisibility(View.GONE);
    }

    @OptIn(markerClass = UnstableApi.class)
    private void toggleAspectRatio() {
        String modeName;
        switch (currentResizeMode) {
            case androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT:
                currentResizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL;
                modeName = "Tam Ekran (Fill)";
                break;
            case androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL:
                currentResizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM;
                modeName = "Yaxınlaşdır (Zoom)";
                break;
            case androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM:
            default:
                currentResizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT;
                modeName = "Orijinal (Fit)";
                break;
        }

        binding.playerView.setResizeMode(currentResizeMode);
        showAspectRatioStatus(modeName);
    }

    private void showAspectRatioStatus(String modeName) {
        binding.tvAspectRatioStatus.setText("Görüntü: " + modeName);
        binding.tvAspectRatioStatus.setVisibility(View.VISIBLE);
        
        osdHandler.removeCallbacks(aspectRatioHideRunnable);
        osdHandler.postDelayed(aspectRatioHideRunnable, 2500);
    }

    private void showSubtitleSearchDialog() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("Onlayn Alt-yazı Axtarışı");
        
        final android.widget.EditText input = new android.widget.EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        input.setHint("Kino və ya Serial adı");
        
        // Cari kanal adını default olaraq qoyaq
        String currentName = binding.tvChannelName.getText().toString();
        input.setText(currentName);
        
        builder.setView(input);
        builder.setPositiveButton("AXTAR", (dialog, which) -> {
            String query = input.getText().toString();
            if (!query.isEmpty()) {
                searchSubtitlesOnOpenSubtitles(query);
            }
        });
        builder.setNeutralButton("DİREKT URL", (dialog, which) -> showDirectSubtitleUrlDialog());
        builder.setNegativeButton("LƏĞV ET", null);
        builder.show();
    }

    private void showDirectSubtitleUrlDialog() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("SRT Linki Daxil Et");
        
        final android.widget.EditText input = new android.widget.EditText(this);
        input.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_URI);
        input.setHint("https://example.com/movie.srt");
        
        builder.setView(input);
        builder.setPositiveButton("YÜKLƏ", (dialog, which) -> {
            String url = input.getText().toString();
            if (url.startsWith("http")) {
                loadExternalSubtitle(url, "Xarici Alt-yazı");
            }
        });
        builder.setNegativeButton("GERİ", (dialog, which) -> showSubtitleSearchDialog());
        builder.show();
    }

    private void searchSubtitlesOnOpenSubtitles(String query) {
        // Real API inteqrasiyası üçün OpenSubtitles API key lazımdır.
        // Hazırda istifadəçini müvafiq axtarış səhifəsinə yönləndirmək və ya placeholder göstərmək olar.
        // Biz sadəlik üçün Google-da axtarış linki verək və ya istifadəçiyə bildirək.
        android.widget.Toast.makeText(this, "Axtarılır: " + query + " (OpenSubtitles)...", android.widget.Toast.LENGTH_LONG).show();
        
        // Nümunə: Google üzərindən spesifik axtarış
        String searchUrl = "https://www.google.com/search?q=" + query + "+srt+subtitles+opensubtitles";
        android.content.Intent i = new android.content.Intent(android.content.Intent.ACTION_VIEW);
        i.setData(Uri.parse(searchUrl));
        startActivity(i);
        
        android.widget.Toast.makeText(this, "Alt-yazını tapdıqdan sonra linkini 'Direkt URL' hissəsinə yapışdırın", android.widget.Toast.LENGTH_LONG).show();
    }

    @OptIn(markerClass = UnstableApi.class)
    private void loadExternalSubtitle(String url, String label) {
        if (exoPlayer == null) return;
        
        MediaItem currentItem = exoPlayer.getCurrentMediaItem();
        if (currentItem == null) return;

        MediaItem.SubtitleConfiguration subtitleConfig = new MediaItem.SubtitleConfiguration.Builder(Uri.parse(url))
                .setMimeType(MimeTypes.APPLICATION_SUBRIP) // SRT formatı
                .setLanguage("az")
                .setSelectionFlags(androidx.media3.common.C.SELECTION_FLAG_DEFAULT)
                .setLabel(label)
                .build();

        MediaItem newItem = currentItem.buildUpon()
                .setSubtitleConfigurations(Collections.singletonList(subtitleConfig))
                .build();

        long pos = exoPlayer.getCurrentPosition();
        exoPlayer.setMediaItem(newItem);
        exoPlayer.prepare();
        exoPlayer.seekTo(pos);
        exoPlayer.play();
        
        android.widget.Toast.makeText(this, "Xarici alt-yazı uğurla qoşuldu", android.widget.Toast.LENGTH_SHORT).show();
        binding.playerTracksSidebar.setVisibility(View.GONE);
    }

    private final Runnable aspectRatioHideRunnable = () -> binding.tvAspectRatioStatus.setVisibility(View.GONE);

    private void updateTestCountdownInPlayer() {
        SharedPreferences prefs = getSharedPreferences("neoplay_prefs", MODE_PRIVATE);
        long expireTime = prefs.getLong("test_expire_time", 0L);
        
        android.util.Log.d("PlayerActivity", "Test Expire Time: " + expireTime + ", Current: " + System.currentTimeMillis());

        if (expireTime > System.currentTimeMillis()) {
            long remainingSeconds = (expireTime - System.currentTimeMillis()) / 1000;
            startTestCountDownTimer((int) remainingSeconds);
        } else {
            if (testCountDownTimer != null) {
                testCountDownTimer.cancel();
            }
            binding.testBannerPlayer.setVisibility(View.GONE);
        }
    }

    private void startTestCountDownTimer(int seconds) {
        if (testCountDownTimer != null) {
            testCountDownTimer.cancel();
        }

        testCountDownTimer = new android.os.CountDownTimer(seconds * 1000L, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                int remaining = (int) (millisUntilFinished / 1000);
                String timeLeft = formatTimeForTest(remaining);
                
                binding.testBannerPlayer.setVisibility(android.view.View.VISIBLE);
                binding.testTitlePlayer.setText("TEST REJİMİ");
                binding.testTimerPlayer.setText("Test: " + timeLeft);

                int color;
                if (remaining < 60) {
                    color = android.graphics.Color.RED;
                } else if (remaining < 300) {
                    color = android.graphics.Color.parseColor("#FFA500"); // Orange
                } else {
                    color = android.graphics.Color.parseColor("#D4AF37"); // Gold
                }
                
                binding.testTitlePlayer.setTextColor(color);
                binding.testTimerPlayer.setTextColor(color);
                binding.testTimerPlayer.setBackgroundColor(android.graphics.Color.TRANSPARENT);
            }

            @Override
            public void onFinish() {
                binding.testTimerPlayer.setText("⏱ Test bitdi!");
                binding.testTimerPlayer.setTextColor(android.graphics.Color.parseColor("#ef4444"));
                binding.testTimerPlayer.setBackgroundColor(android.graphics.Color.parseColor("#450a0a"));
                
                new android.app.AlertDialog.Builder(PlayerActivity.this)
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
        return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, secs);
    }
}
