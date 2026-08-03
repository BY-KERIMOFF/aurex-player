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
import android.view.View;
import android.widget.Toast;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;

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
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.extractor.DefaultExtractorsFactory;
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.okhttp.OkHttpDataSource;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;

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
import com.bykerimoff.player.utils.RecentChannelsManager;
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
    private DefaultTrackSelector trackSelector;
    private AudioManager audioManager;
    private int currentIndex = 0;
    private List<Channel> channelList; // Sidebar-da görünən aktiv siyahı
    private List<Channel> currentCategoryChannels = new ArrayList<>(); // Əsas kateqoriya siyahısı
    private String playerType = "exo2";
    
    private String channelNumberInput = "";
    private final Handler channelSwitchHandler = new Handler(Looper.getMainLooper());
    private final Runnable channelSwitchRunnable = this::processNumericInput;
    
    private int retryCount = 0;
    private final int MAX_RETRIES = 5;
    private String currentPlayingChannelId = "";
    private List<Channel> playbackList = new ArrayList<>(); // Pleyerin real çalğı siyahısı

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

    private final Handler categorySwitchHandler = new Handler(Looper.getMainLooper());
    private Runnable categorySwitchRunnable;
    
    private static final Map<String, String> epgCache = new HashMap<>();

    private CategoryAdapter playerCategoryAdapter;
    private ChannelAdapter channelAdapter;
    private List<Channel> allCategoryChannels = new ArrayList<>();
    private String currentPlaylistType = "m3u";
    private int currentResizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL;
    
    private List<EpgProgram> archiveList = new ArrayList<>();
    private ArchiveAdapter archiveAdapter;
    
    private List<TrackAdapter.TrackInfo> trackList = new ArrayList<>();
    private TrackAdapter trackAdapter;
    private int currentTrackType = -1;

    private android.os.CountDownTimer testCountDownTimer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityPlayerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        // Başlanğıc kanallarını yüklə
        currentCategoryChannels = new ArrayList<>(DataManager.getCurrentChannelList());
        channelList = new ArrayList<>(currentCategoryChannels);
        playbackList = new ArrayList<>(channelList);
        currentIndex = getIntent().getIntExtra("channel_index", 0);
        
        if (currentIndex >= 0 && currentIndex < playbackList.size()) {
            currentPlayingChannelId = playbackList.get(currentIndex).getId();
        }

        SharedPreferences prefs = getSharedPreferences("neoplay_prefs", MODE_PRIVATE);
        playerType = prefs.getString("player_type", "exo2");
        currentPlaylistType = prefs.getString("playlist_type", "m3u");

        initExoPlayer(playerType);
        setupPlayerChannelList();
        setupPlayerCategoryList();
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
                if (binding.playerChannelSidebar.getVisibility() == View.VISIBLE) {
                    binding.playerChannelSidebar.setVisibility(View.GONE);
                } else if (binding.rvPlayerCategories.getVisibility() == View.VISIBLE) {
                    binding.rvPlayerCategories.setVisibility(View.GONE);
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
        setupPlayerSearch();
    }

    private void setupAnnouncement() {
        SharedPreferences prefs = getSharedPreferences("neoplay_prefs", MODE_PRIVATE);
        
        // Serverdən gələn elan göstərmə əmrini yoxla
        boolean showAnnouncement = DataManager.isShowAnnouncementGlobal() || 
                prefs.getBoolean("show_announcement_global", true);
        
        if (!showAnnouncement) {
            binding.announcementContainer.setVisibility(View.GONE);
            return;
        }

        String announcement = DataManager.getAdminAnnouncement();
        String colorHex = DataManager.getAdminAnnouncementColor();
        
        if (announcement == null || announcement.isEmpty()) announcement = prefs.getString("last_announcement", "");
        if (colorHex == null || colorHex.isEmpty()) colorHex = prefs.getString("last_announcement_color", "");

        if (announcement != null && !announcement.isEmpty()) {
            String cleanAnnouncement = announcement.replace("\n", "  |  ");
            binding.tvAnnouncement.setText(cleanAnnouncement);
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
            android.view.animation.TranslateAnimation animation = new android.view.animation.TranslateAnimation(screenWidth, -textWidth - 1000, 0, 0);
            animation.setDuration(25000);
            animation.setRepeatCount(android.view.animation.Animation.INFINITE);
            animation.setInterpolator(new android.view.animation.LinearInterpolator());
            binding.tvAnnouncement.startAnimation(animation);
        });
    }

    private void setupPlayerChannelList() {
        if (channelList == null) return;
        if (channelAdapter == null) {
            channelAdapter = new ChannelAdapter(channelList, new ChannelAdapter.OnChannelClickListener() {
                @Override
                public void onChannelClick(Channel channel) {
                    playSelectedChannel(channel);
                }
                @Override
                public void onChannelFocus(Channel channel) {}
                @Override
                public void onChannelLongClick(Channel channel) {
                    FavoriteManager fm = new FavoriteManager(PlayerActivity.this);
                    boolean isAdded = fm.toggleFavorite(channel.getId());
                    channelAdapter.notifyDataSetChanged();
                    Toast.makeText(PlayerActivity.this, isAdded ? "Sevimli siyahısına əlavə edildi" : "Sevimli siyahısından çıxarıldı", Toast.LENGTH_SHORT).show();
                }
            });
            binding.rvPlayerChannels.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(this));
            binding.rvPlayerChannels.setAdapter(channelAdapter);
        } else {
            channelAdapter.updateData(channelList);
        }
    }

    private void playSelectedChannel(Channel channel) {
        playbackList = new ArrayList<>(channelList);
        currentCategoryChannels = new ArrayList<>(playbackList); // Seçilən siyahını cari kateqoriya siyahısı kimi yadda saxla
        currentIndex = playbackList.indexOf(channel);
        currentPlayingChannelId = channel.getId();
        loadChannel(channel);
        binding.playerChannelSidebar.setVisibility(View.GONE);
        binding.rvPlayerCategories.setVisibility(View.GONE);
    }

    private void setupPlayerCategoryList() {
        List<Category> categories = DataManager.getCurrentCategoryList();
        if (categories == null || categories.isEmpty()) return;
        playerCategoryAdapter = new CategoryAdapter(categories, category -> {
            if (categorySwitchRunnable != null) categorySwitchHandler.removeCallbacks(categorySwitchRunnable);
            updateChannelsByCategory(category);
            
            // Kateqoriya seçiləndə kanallara keç və dərhal fokusla
            binding.tvPlayerSidebarTitle.setText("KANALLAR");
            binding.playerChannelSidebar.setVisibility(View.VISIBLE);
            binding.rvPlayerChannels.postDelayed(() -> {
                binding.rvPlayerChannels.requestFocus();
                View first = binding.rvPlayerChannels.getChildAt(0);
                if (first != null) first.requestFocus();
                else {
                    binding.rvPlayerChannels.scrollToPosition(0);
                    binding.rvPlayerChannels.postDelayed(() -> {
                        RecyclerView.ViewHolder vh = binding.rvPlayerChannels.findViewHolderForAdapterPosition(0);
                        if (vh != null) vh.itemView.requestFocus();
                    }, 50);
                }
            }, 100);
        });
        playerCategoryAdapter.setOnCategoryFocusListener(category -> {
            if (binding.rvPlayerChannels.hasFocus()) return;
            if (categorySwitchRunnable != null) categorySwitchHandler.removeCallbacks(categorySwitchRunnable);
            categorySwitchRunnable = () -> updateChannelsByCategory(category);
            categorySwitchHandler.postDelayed(categorySwitchRunnable, 250);
        });
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
            currentCategoryChannels = new ArrayList<>(allCategoryChannels); // Kateqoriyanı yadda saxla
            channelList = new ArrayList<>(allCategoryChannels);
            setupPlayerChannelList();
            
            int scrollPos = -1;
            for (int i = 0; i < channelList.size(); i++) {
                if (channelList.get(i).getId().equals(currentPlayingChannelId)) {
                    scrollPos = i;
                    break;
                }
            }
            if (scrollPos != -1) binding.rvPlayerChannels.scrollToPosition(scrollPos);
            else binding.rvPlayerChannels.scrollToPosition(0);
        }
    }



    private void setupArchiveList() {
        archiveAdapter = new ArchiveAdapter(archiveList, new ArchiveAdapter.OnProgramClickListener() {
            @Override public void onProgramClick(EpgProgram program) { playArchiveProgram(program); binding.playerArchiveSidebar.setVisibility(View.GONE); }
            @Override public void onProgramLongClick(EpgProgram program) {}
        });
        binding.rvArchive.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(this));
        binding.rvArchive.setAdapter(archiveAdapter);
    }

    private void playArchiveProgram(EpgProgram program) {
        if (channelList == null || currentIndex < 0 || currentIndex >= channelList.size()) return;
        Channel channel = channelList.get(currentIndex);
        if (currentPlaylistType.equalsIgnoreCase("xtream")) {
            try {
                SharedPreferences prefs = getSharedPreferences("neoplay_prefs", MODE_PRIVATE);
                String startTimeStr = new SimpleDateFormat("yyyy-MM-dd:HH-mm", Locale.US).format(new Date(program.getStartTime()));
                int durationMinutes = (int) ((program.getEndTime() - program.getStartTime()) / 60000);
                String archiveUrl = prefs.getString("xtream_host", "") + "/timeshift/" + prefs.getString("xtream_user", "") + "/" + prefs.getString("xtream_pass", "") + "/" + durationMinutes + "/" + startTimeStr + "/" + channel.getId() + ".ts";
                if (exoPlayer == null) initExoPlayer(playerType);
                exoPlayer.stop(); exoPlayer.clearMediaItems();
                exoPlayer.setMediaItem(MediaItem.fromUri(archiveUrl));
                exoPlayer.prepare(); exoPlayer.play();
                binding.tvEpgInfo.setText("ARXİV: " + program.getTitle());
                binding.tvQuality.setText("ARXİV");
                binding.vodProgressLayout.setVisibility(View.VISIBLE);
                showOsd();
            } catch (Exception e) { showErrorOverlay("Arxiv xətası", "Yayım başladıla bilmədi"); }
        } else {
            String catchupUrl = channel.getCatchupSource();
            if (catchupUrl == null || catchupUrl.isEmpty()) catchupUrl = channel.getStreamUrl();
            long startUnix = program.getStartTime() / 1000;
            String finalUrl = catchupUrl.replace("${start}", String.valueOf(startUnix)).replace("{utc}", String.valueOf(startUnix)).replace("${offset}", "0");
            if (exoPlayer == null) initExoPlayer(playerType);
            exoPlayer.stop(); exoPlayer.clearMediaItems();
            exoPlayer.setMediaItem(MediaItem.fromUri(finalUrl));
            exoPlayer.prepare(); exoPlayer.play();
            binding.tvEpgInfo.setText("ARXİV: " + program.getTitle());
            binding.vodProgressLayout.setVisibility(View.VISIBLE);
            showOsd();
        }
    }



    @OptIn(markerClass = UnstableApi.class)
    private void initExoPlayer(String mode) {
        if (exoPlayer == null) {
            OkHttpDataSource.Factory dataSourceFactory = NetworkUtils.getDataSourceFactory(this);
            
            DefaultExtractorsFactory extractorsFactory = new DefaultExtractorsFactory()
                    .setTsExtractorFlags(DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES 
                                       | DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS
                                       | DefaultTsPayloadReaderFactory.FLAG_IGNORE_SPLICE_INFO_STREAM
                                       | DefaultTsPayloadReaderFactory.FLAG_ENABLE_HDMV_DTS_AUDIO_STREAMS);

            DefaultMediaSourceFactory mediaSourceFactory = new DefaultMediaSourceFactory(dataSourceFactory, extractorsFactory);
            
            trackSelector = new DefaultTrackSelector(this);
            trackSelector.setParameters(trackSelector.buildUponParameters()
                    .setExceedAudioConstraintsIfNecessary(true)
                    .setExceedRendererCapabilitiesIfNecessary(true)
                    .setExceedVideoConstraintsIfNecessary(true)
            );

            DefaultRenderersFactory renderersFactory = new DefaultRenderersFactory(this)
                    .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
                    .setEnableDecoderFallback(true);

            exoPlayer = new ExoPlayer.Builder(this, renderersFactory)
                    .setMediaSourceFactory(mediaSourceFactory)
                    .setTrackSelector(trackSelector)
                    .build();
            binding.playerView.setPlayer(exoPlayer);
            exoPlayer.addListener(new Player.Listener() {
                @Override public void onPlaybackStateChanged(int state) {
                    if (state == Player.STATE_BUFFERING) { binding.bufferingLayout.setVisibility(View.VISIBLE); osdHandler.postDelayed(bufferingTimeoutRunnable, 15000); }
                    else { binding.bufferingLayout.setVisibility(View.GONE); osdHandler.removeCallbacks(bufferingTimeoutRunnable); }
                }
                @Override public void onVideoSizeChanged(@NonNull androidx.media3.common.VideoSize videoSize) {
                    if (videoSize.width > 0) binding.tvQuality.setText(videoSize.width + "x" + videoSize.height);
                }
                @Override public void onPlayerError(@NonNull androidx.media3.common.PlaybackException error) {
                    if (retryCount < MAX_RETRIES) { retryCount++; osdHandler.postDelayed(() -> { if (exoPlayer != null) { exoPlayer.prepare(); exoPlayer.play(); } }, 1500); }
                    else showErrorOverlay("Müvəqqəti texniki nasazlıq", "Yayım tezliklə bərpa olunacaq");
                }
                
                @Override
                public void onTracksChanged(@NonNull androidx.media3.common.Tracks tracks) {}
            });
        }
    }

    private void loadChannel(Channel channel) {
        if (exoPlayer == null) initExoPlayer(playerType);
        retryCount = 0; exoPlayer.stop(); exoPlayer.clearMediaItems();
        binding.vodProgressLayout.setVisibility(View.GONE);
        
        String url = channel.getStreamUrl();
        MediaItem.Builder builder = new MediaItem.Builder().setUri(Uri.parse(url));
        String low = url.toLowerCase();
        if (low.contains("m3u8") || low.contains(".php")) builder.setMimeType(MimeTypes.APPLICATION_M3U8);
        else if (low.contains(".ts") || low.contains("/live/")) builder.setMimeType(MimeTypes.VIDEO_MP2T);
        
        exoPlayer.setMediaItem(builder.build());
        exoPlayer.prepare(); exoPlayer.play();
        
        binding.tvChannelName.setText(channel.getName());
        if (binding.ivChannelLogo != null) {
            Glide.with(this).load(channel.getLogoUrl()).placeholder(R.drawable.default_logo).error(R.drawable.default_logo).into(binding.ivChannelLogo);
        }
        
        if (channelAdapter != null) channelAdapter.setSelectedPosition(currentIndex);
        fetchEpg(channel.getId());
        showOsd();
        startTestCountdownInPlayer();
        
        // Son baxılan kanalı avtomatik açılış üçün yadda saxla
        getSharedPreferences("neoplay_prefs", MODE_PRIVATE).edit()
                .putString("last_channel_url", channel.getStreamUrl())
                .apply();
        
        // Son baxılanlar siyahısına əlavə et
        RecentChannelsManager.INSTANCE.addChannel(this, channel);
    }

    private void fetchEpg(String channelId) {
        if (epgCache.containsKey(channelId)) { binding.tvEpgInfo.setText(epgCache.get(channelId)); return; }
        checkXmltvEpg(channelId);
    }

    private void checkXmltvEpg(String channelId) {
        Map<String, String> xmltv = DataManager.getXmltvCache();
        if (xmltv != null && !xmltv.isEmpty()) {
            Channel current = null;
            if (channelList != null && currentIndex >= 0 && currentIndex < channelList.size()) current = channelList.get(currentIndex);
            if (current != null) {
                String title = xmltv.get(current.getTvgId());
                if (title == null) title = xmltv.get(current.getName());
                if (title != null) { epgCache.put(channelId, title); binding.tvEpgInfo.setText(title); return; }
            }
        }
        binding.tvEpgInfo.setText("EPG məlumatı yoxdur");
    }

    private String formatTime(long ms) {
        long s = (ms / 1000) % 60; long m = (ms / (1000 * 60)) % 60; long h = (ms / (1000 * 60 * 60));
        return h > 0 ? String.format(Locale.getDefault(), "%02d:%02d:%02d", h, m, s) : String.format(Locale.getDefault(), "%02d:%02d", m, s);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int keyCode = event.getKeyCode();
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (keyCode == KeyEvent.KEYCODE_CHANNEL_UP || keyCode == KeyEvent.KEYCODE_PAGE_UP || keyCode == KeyEvent.KEYCODE_MEDIA_NEXT) {
                playNextChannel(); return true;
            }
            if (keyCode == KeyEvent.KEYCODE_CHANNEL_DOWN || keyCode == KeyEvent.KEYCODE_PAGE_DOWN || keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS) {
                playPreviousChannel(); return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) { appendNumericInput(keyCode - KeyEvent.KEYCODE_0); return true; }
        
        boolean isCatOpen = binding.rvPlayerCategories.getVisibility() == View.VISIBLE;
        boolean isChanOpen = binding.playerChannelSidebar.getVisibility() == View.VISIBLE;
        boolean isAnyOpen = isCatOpen || isChanOpen || binding.playerArchiveSidebar.getVisibility() == View.VISIBLE || binding.playerTracksSidebar.getVisibility() == View.VISIBLE;
        
        // VOD (Film/Serial) yoxlanışı
        boolean isVod = exoPlayer != null && !exoPlayer.isCurrentMediaItemLive() && exoPlayer.getDuration() > 0;

        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                if (event.getAction() == KeyEvent.ACTION_DOWN) event.startTracking();
                if (isVod && !isAnyOpen) {
                    if (exoPlayer.isPlaying()) exoPlayer.pause();
                    else exoPlayer.play();
                    showOsd();
                    return true;
                }
                return true;

            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                if (isVod && !isAnyOpen) {
                    // VOD rejimi -> 15 saniyə GERİ
                    exoPlayer.seekTo(Math.max(0, exoPlayer.getCurrentPosition() - 15000));
                    showOsd();
                    return true;
                }
                if (isChanOpen) {
                    // Kanallardan Kateqoriyalara keç
                    binding.rvPlayerCategories.setVisibility(View.VISIBLE);
                    binding.rvPlayerCategories.requestFocus();
                    return true;
                }
                // Yayım gedərkən SOL -> Kateqoriyaları aç
                binding.rvPlayerCategories.setVisibility(View.VISIBLE);
                binding.rvPlayerCategories.requestFocus();
                return true;

            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                if (isVod && !isAnyOpen) {
                    // VOD rejimi -> 15 saniyə İRƏLİ
                    exoPlayer.seekTo(Math.min(exoPlayer.getDuration(), exoPlayer.getCurrentPosition() + 15000));
                    showOsd();
                    return true;
                }
                if (isCatOpen) {
                    // Kateqoriyadan Kanallara keç
                    binding.playerChannelSidebar.setVisibility(View.VISIBLE);
                    binding.rvPlayerChannels.requestFocus();
                    return true;
                }
                // Yayım gedərkən SAĞ -> Kateqoriyaları aç
                binding.rvPlayerCategories.setVisibility(View.VISIBLE);
                binding.rvPlayerCategories.requestFocus();
                return true;

            case KeyEvent.KEYCODE_DPAD_UP:
                if (binding.etPlayerSearch.hasFocus()) return true; // Axtarışdan yuxarı getmə
                if (binding.rvPlayerChannels.hasFocus()) {
                    View f = binding.rvPlayerChannels.getFocusedChild();
                    if (f != null && binding.rvPlayerChannels.getChildAdapterPosition(f) == 0) {
                        binding.etPlayerSearch.requestFocus();
                        return true;
                    }
                }
                if (binding.rvPlayerCategories.hasFocus()) { handleLoop(binding.rvPlayerCategories, playerCategoryAdapter.getItemCount(), true); return true; }
                if (binding.rvPlayerChannels.hasFocus()) { handleLoop(binding.rvPlayerChannels, channelAdapter.getItemCount(), true); return true; }
                if (binding.rvTracks.hasFocus()) { handleLoop(binding.rvTracks, trackAdapter.getItemCount(), true); return true; }
                if (binding.rvArchive.hasFocus()) { handleLoop(binding.rvArchive, archiveAdapter.getItemCount(), true); return true; }
                if (isAnyOpen) return true;
                playNextChannel(); return true;

            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (binding.etPlayerSearch.hasFocus()) {
                    binding.rvPlayerChannels.requestFocus();
                    return true;
                }
                if (binding.rvPlayerCategories.hasFocus()) { handleLoop(binding.rvPlayerCategories, playerCategoryAdapter.getItemCount(), false); return true; }
                if (binding.rvPlayerChannels.hasFocus()) { handleLoop(binding.rvPlayerChannels, channelAdapter.getItemCount(), false); return true; }
                if (binding.rvTracks.hasFocus()) { handleLoop(binding.rvTracks, trackAdapter.getItemCount(), false); return true; }
                if (binding.rvArchive.hasFocus()) { handleLoop(binding.rvArchive, archiveAdapter.getItemCount(), false); return true; }
                if (isAnyOpen) return true;
                playPreviousChannel(); return true;

            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                osdHandler.postDelayed(this::updateVolumeUI, 50); return super.onKeyDown(keyCode, event);
                
            case KeyEvent.KEYCODE_PROG_RED:
                showTrackSidebar(androidx.media3.common.C.TRACK_TYPE_TEXT); return true;
            case KeyEvent.KEYCODE_PROG_GREEN:
                showTrackSidebar(androidx.media3.common.C.TRACK_TYPE_AUDIO); return true;
            case KeyEvent.KEYCODE_PROG_YELLOW:
            case KeyEvent.KEYCODE_Y:
                toggleAspectRatio(); return true;
            case KeyEvent.KEYCODE_PROG_BLUE:
                showCurrentCategoryChannels();
                return true;
            case KeyEvent.KEYCODE_MEDIA_PLAY:
                if (exoPlayer != null && !exoPlayer.isPlaying()) exoPlayer.play();
                return true;
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
                if (exoPlayer != null && exoPlayer.isPlaying()) exoPlayer.pause();
                return true;
            case KeyEvent.KEYCODE_MEDIA_STOP:
                if (exoPlayer != null) exoPlayer.stop();
                return true;
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                if (exoPlayer != null) {
                    if (exoPlayer.isPlaying()) exoPlayer.pause();
                    else exoPlayer.play();
                }
                return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
            if (event.isTracking() && !event.isCanceled()) {
                if (binding.rvPlayerCategories.getVisibility() == View.VISIBLE && binding.rvPlayerCategories.hasFocus()) {
                    // Kateqoriya üzərində OK -> Klik hadisəsini tetikle
                    View f = binding.rvPlayerCategories.getFocusedChild();
                    if (f != null) f.performClick();
                } else if (binding.playerChannelSidebar.getVisibility() == View.VISIBLE) {
                    View f = binding.rvPlayerChannels.getFocusedChild();
                    if (f != null) {
                        int p = binding.rvPlayerChannels.getChildAdapterPosition(f);
                        if (p != -1 && p < channelList.size()) playSelectedChannel(channelList.get(p));
                    }
                } else if (binding.playerTracksSidebar.getVisibility() == View.VISIBLE) {
                    View f = binding.rvTracks.getFocusedChild();
                    if (f != null) f.performClick();
                } else if (binding.playerArchiveSidebar.getVisibility() == View.VISIBLE) {
                    View f = binding.rvArchive.getFocusedChild();
                    if (f != null) f.performClick();
                } else {
                    // Canlı TV-də OK -> Birbaşa Kanal Siyahısını aç (Cari kateqoriya üzrə)
                    boolean isVod = exoPlayer != null && !exoPlayer.isCurrentMediaItemLive() && exoPlayer.getDuration() > 0;
                    if (!isVod) {
                        showCurrentCategoryChannels();
                    }
                }
            }
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    private void setupPlayerSearch() {
        binding.etPlayerSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterChannelsByPlayerSearch(s.toString());
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });
        
        binding.etPlayerSearch.setOnEditorActionListener((v, actionId, event) -> {
            binding.rvPlayerChannels.requestFocus();
            return true;
        });
    }

    private void filterChannelsByPlayerSearch(String query) {
        if (query.isEmpty()) {
            channelList = new ArrayList<>(playbackList);
        } else {
            List<Channel> filtered = new ArrayList<>();
            for (Channel c : playbackList) {
                if (c.getName().toLowerCase().contains(query.toLowerCase())) {
                    filtered.add(c);
                }
            }
            channelList = filtered;
        }
        setupPlayerChannelList();
    }

    private void showCurrentCategoryChannels() {
        channelList = new ArrayList<>(playbackList); // Baxılan siyahını istifadə et
        binding.tvPlayerSidebarTitle.setText("KANALLAR");
        binding.etPlayerSearch.setText(""); // Axtarışı sıfırla
        setupPlayerChannelList();
        
        binding.playerChannelSidebar.setVisibility(View.VISIBLE);
        binding.rvPlayerCategories.setVisibility(View.GONE);
        
        binding.etPlayerSearch.requestFocus(); // Siyahı açılanda axtarışa fokuslan
        
        binding.rvPlayerChannels.postDelayed(() -> {
            int s = -1;
            for (int i = 0; i < channelList.size(); i++) if (channelList.get(i).getId().equals(currentPlayingChannelId)) { s = i; break; }
            
            if (s != -1) {
                binding.rvPlayerChannels.scrollToPosition(s);
            }
        }, 100);
    }

    private void showRecentChannels() {
        List<Channel> recents = RecentChannelsManager.INSTANCE.getRecentChannels(this);
        if (recents.isEmpty()) {
            Toast.makeText(this, "Son baxılan kanal yoxdur", Toast.LENGTH_SHORT).show();
            return;
        }
        
        channelList = new ArrayList<>(recents);
        binding.tvPlayerSidebarTitle.setText("SON BAXILANLAR");
        setupPlayerChannelList();
        
        binding.playerChannelSidebar.setVisibility(View.VISIBLE);
        binding.rvPlayerCategories.setVisibility(View.GONE);
        
        binding.rvPlayerChannels.scrollToPosition(0);
        binding.rvPlayerChannels.postDelayed(() -> {
            RecyclerView.ViewHolder vh = binding.rvPlayerChannels.findViewHolderForAdapterPosition(0);
            if (vh != null) vh.itemView.requestFocus();
            else binding.rvPlayerChannels.requestFocus();
        }, 100);
    }

    private void handleLoop(RecyclerView rv, int count, boolean up) {
        if (count == 0) return;
        View f = rv.getFocusedChild();
        int cur = (f != null) ? rv.getChildAdapterPosition(f) : -1;
        int nxt = up ? (cur <= 0 ? count - 1 : cur - 1) : (cur >= count - 1 ? 0 : cur + 1);
        rv.scrollToPosition(nxt);
        rv.postDelayed(() -> {
            RecyclerView.ViewHolder vh = rv.findViewHolderForAdapterPosition(nxt);
            if (vh != null) vh.itemView.requestFocus();
        }, 50);
    }

    private void handleChannelLoopInternal(boolean up) {
        if (channelAdapter == null) return;
        int count = channelAdapter.getItemCount();
        handleLoop(binding.rvPlayerChannels, count, up);
    }

    private void setupTrackList() {
        trackAdapter = new TrackAdapter(trackList, track -> {
            if (exoPlayer == null) return;
            androidx.media3.common.TrackSelectionParameters p;
            if (track.trackIndex == -1) p = exoPlayer.getTrackSelectionParameters().buildUpon().setTrackTypeDisabled(currentTrackType, true).build();
            else p = exoPlayer.getTrackSelectionParameters().buildUpon().setOverrideForType(new androidx.media3.common.TrackSelectionOverride(track.group.getMediaTrackGroup(), track.trackIndex)).setTrackTypeDisabled(currentTrackType, false).build();
            exoPlayer.setTrackSelectionParameters(p);
            exoPlayer.play(); // Dəyişiklikdən sonra donmanın qarşısını al
            binding.playerTracksSidebar.setVisibility(View.GONE);
        });
        binding.rvTracks.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(this));
        binding.rvTracks.setAdapter(trackAdapter);
    }

    private void showTrackSidebar(int type) {
        if (exoPlayer == null) return;
        this.currentTrackType = type;
        
        // Başlığı təyin et
        if (type == androidx.media3.common.C.TRACK_TYPE_TEXT) {
            binding.tvPlayerSidebarTitle.setText("ALT YAZI");
        } else if (type == androidx.media3.common.C.TRACK_TYPE_AUDIO) {
            binding.tvPlayerSidebarTitle.setText("SES DİLİ");
        }
        
        trackList.clear();
        Tracks trs = exoPlayer.getCurrentTracks();
        for (Tracks.Group g : trs.getGroups()) {
            if (g.getType() == type) {
                for (int i = 0; i < g.length; i++) {
                    Format fmt = g.getTrackFormat(i);
                    String lbl = fmt.label != null ? fmt.label : (fmt.language != null ? fmt.language : "Dil " + (i + 1));
                    trackList.add(new TrackAdapter.TrackInfo(lbl, g, i, g.isTrackSelected(i)));
                }
            }
        }
        if (trackList.isEmpty()) { Toast.makeText(this, "Seçim yoxdur", Toast.LENGTH_SHORT).show(); return; }
        trackAdapter.notifyDataSetChanged();
        binding.playerTracksSidebar.setVisibility(View.VISIBLE);
        binding.rvTracks.requestFocus();
    }

    private void toggleChannelSidebar() { showCurrentCategoryChannels(); }

    private void playNextChannel() {
        if (playbackList != null && !playbackList.isEmpty()) {
            currentIndex++; if (currentIndex >= playbackList.size()) currentIndex = 0;
            Channel n = playbackList.get(currentIndex); currentPlayingChannelId = n.getId(); loadChannel(n);
        }
    }

    private void playPreviousChannel() {
        if (playbackList != null && !playbackList.isEmpty()) {
            currentIndex--; if (currentIndex < 0) currentIndex = playbackList.size() - 1;
            Channel p = playbackList.get(currentIndex); currentPlayingChannelId = p.getId(); loadChannel(p);
        }
    }

    private void updateVolumeUI() {
        if (audioManager == null) return;
        int max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int cur = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        int pct = (cur * 100) / (max == 0 ? 1 : max);
        binding.volumeLayout.setVisibility(View.VISIBLE);
        binding.volumeProgress.setProgress(pct);
        binding.tvVolumePercent.setText(pct + "%");
        osdHandler.removeCallbacks(volumeHideRunnable);
        osdHandler.postDelayed(volumeHideRunnable, 3000);
    }

    private final Runnable volumeHideRunnable = () -> binding.volumeLayout.setVisibility(View.GONE);
    private final Runnable osdHideRunnable = () -> binding.osdLayout.setVisibility(View.GONE);

    @Override
    protected void onStop() { super.onStop(); if (exoPlayer != null) { savePlaybackProgress(); exoPlayer.pause(); } }

    private void savePlaybackProgress() {
        if (exoPlayer != null && !exoPlayer.isCurrentMediaItemLive() && exoPlayer.getDuration() > 0) {
            if (playbackList != null && currentIndex >= 0 && currentIndex < playbackList.size()) {
                Channel c = playbackList.get(currentIndex);
                if (exoPlayer.getCurrentPosition() > exoPlayer.getDuration() * 0.95) { ResumeManager.INSTANCE.removeProgress(this, c.getStreamUrl()); return; }
                ResumeItem item = new ResumeItem(c.getId(), c.getName(), c.getLogoUrl(), c.getRawEncryptedUrl(), c.getCategoryName(), exoPlayer.getCurrentPosition(), exoPlayer.getDuration(), System.currentTimeMillis());
                ResumeManager.INSTANCE.saveProgress(this, item);
            }
        }
    }

    @Override
    protected void onDestroy() { super.onDestroy(); progressHandler.removeCallbacksAndMessages(null); osdHandler.removeCallbacksAndMessages(null); if (exoPlayer != null) exoPlayer.release(); }

    private void showErrorOverlay(String t, String s) {
        binding.errorLayout.setVisibility(View.VISIBLE);
        binding.osdLayout.setVisibility(View.GONE);
        binding.tvErrorTitle.setText(t.toUpperCase(Locale.ROOT));
        binding.tvErrorSubtitle.setText(s);
    }

    private void showOsd() {
        binding.osdLayout.setVisibility(View.VISIBLE);
        osdHandler.removeCallbacks(osdHideRunnable);
        osdHandler.postDelayed(osdHideRunnable, 5000);
    }

    private void appendNumericInput(int d) {
        channelNumberInput += d;
        binding.tvNumericInput.setText(channelNumberInput);
        binding.tvNumericInput.setVisibility(View.VISIBLE);
        channelSwitchHandler.removeCallbacks(channelSwitchRunnable);
        channelSwitchHandler.postDelayed(channelSwitchRunnable, 2500);
    }

    private void processNumericInput() {
        try {
            int t = Integer.parseInt(channelNumberInput) - 1;
            if (channelList != null && t >= 0 && t < channelList.size()) { currentIndex = t; loadChannel(channelList.get(currentIndex)); }
        } catch (Exception e) { e.printStackTrace(); }
        channelNumberInput = ""; binding.tvNumericInput.setVisibility(View.GONE);
    }

    @OptIn(markerClass = UnstableApi.class)
    private void toggleAspectRatio() {
        String modeName = "FIT";
        switch (currentResizeMode) {
            case androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT:
                currentResizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL;
                modeName = "FILL";
                break;
            case androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL:
                currentResizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM;
                modeName = "ZOOM";
                break;
            default:
                currentResizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT;
                modeName = "FIT";
                break;
        }
        binding.playerView.setResizeMode(currentResizeMode);
        Toast.makeText(this, "Ekran rejimi: " + modeName, Toast.LENGTH_SHORT).show();
    }

    private void startTestCountdownInPlayer() {
        SharedPreferences p = getSharedPreferences("neoplay_prefs", MODE_PRIVATE);
        long exp = p.getLong("test_expire_time", 0L);
        if (exp > System.currentTimeMillis()) {
            if (testCountDownTimer != null) testCountDownTimer.cancel();
            testCountDownTimer = new android.os.CountDownTimer(exp - System.currentTimeMillis(), 1000) {
                @Override public void onTick(long ms) {
                    binding.testBannerPlayer.setVisibility(View.VISIBLE);
                    int remaining = (int) (ms / 1000);
                    String timeLeft = String.format(Locale.getDefault(), "%02d:%02d:%02d", ms/3600000, (ms%3600000)/60000, (ms%60000)/1000);
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

                    // 5 dəqiqədən az qaldıqda marqatla
                    if (remaining < 300) {
                        if (binding.testBannerPlayer.getAnimation() == null) {
                            binding.testBannerPlayer.startAnimation(android.view.animation.AnimationUtils.loadAnimation(PlayerActivity.this, R.anim.blink));
                        }
                    } else {
                        binding.testBannerPlayer.clearAnimation();
                    }
                }
                @Override public void onFinish() { finish(); }
            }.start();
        } else binding.testBannerPlayer.setVisibility(View.GONE);
    }
}
