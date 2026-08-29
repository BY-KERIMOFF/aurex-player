package com.bykerimoff.player;

import android.app.ActivityManager;
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
import androidx.media3.common.TrackSelectionOverride;
import java.util.Collections;
import androidx.media3.ui.AspectRatioFrameLayout;
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
    private String volumeNumberInput = "";
    private boolean isVolumeInputMode = false;
    private final Handler channelSwitchHandler = new Handler(Looper.getMainLooper());
    private final Runnable channelSwitchRunnable = this::processNumericInput;
    private final Runnable volumeInputRunnable = this::processVolumeInput;
    
    private int retryCount = 0;
    private final int MAX_RETRIES = 5;
    private String currentPlayingChannelId = "";
    private List<Channel> playbackList = new ArrayList<>(); // Pleyerin real çalğı siyahısı
    private int currentAspectRatioMode = AspectRatioFrameLayout.RESIZE_MODE_FILL;
    private boolean isVod = false;

    private final Runnable bufferingTimeoutRunnable = new Runnable() {
        @Override
        public void run() {
            if (exoPlayer != null && (exoPlayer.getPlaybackState() == Player.STATE_BUFFERING)) {
                if (retryCount < MAX_RETRIES) {
                    retryCount++;
                    exoPlayer.prepare();
                } else {
                    showTechnicalError();
                }
            }
        }
    };

    private void showTechnicalError() {
        binding.errorLayout.setVisibility(View.VISIBLE);
        binding.bufferingLayout.setVisibility(View.GONE);
    }

    private void hideTechnicalError() {
        binding.errorLayout.setVisibility(View.GONE);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityPlayerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        
        SharedPreferences settingsPrefs = getSharedPreferences("neoplay_prefs", MODE_PRIVATE);
        playerType = settingsPrefs.getString("player_type", "exo2");

        channelList = DataManager.getCurrentChannelList();
        if (channelList == null || channelList.isEmpty()) {
            finish();
            return;
        }

        currentCategoryChannels = new ArrayList<>(channelList);
        playbackList = new ArrayList<>(channelList);
        currentIndex = getIntent().getIntExtra("channel_index", 0);
        long resumePos = getIntent().getLongExtra("resume_position", 0);

        initExoPlayer();
        
        // Determine if it's VOD based on URL extension or activity flag
        Channel initialChannel = playbackList.get(currentIndex);
        isVod = com.bykerimoff.player.utils.M3UParser.isVodChannel(initialChannel.getStreamUrl());
        
        if (resumePos > 0 && isVod) {
            playChannel(currentIndex, resumePos);
        } else {
            playChannel(currentIndex);
        }
        setupSidebars();
        setupNumericOverlay();
    }

    private void setupNumericOverlay() {
        binding.tvNumericInput.setVisibility(View.GONE);
    }

    @OptIn(markerClass = UnstableApi.class)
    private void initExoPlayer() {
        trackSelector = new DefaultTrackSelector(this);
        
        DefaultRenderersFactory renderersFactory = new DefaultRenderersFactory(this)
                .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
                .setEnableDecoderFallback(true);

        OkHttpDataSource.Factory dataSourceFactory = NetworkUtils.getDataSourceFactory(this);
        
        DefaultExtractorsFactory extractorsFactory = new DefaultExtractorsFactory()
                .setTsExtractorFlags(DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES 
                                   | DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS
                                   | DefaultTsPayloadReaderFactory.FLAG_IGNORE_SPLICE_INFO_STREAM
                                   | DefaultTsPayloadReaderFactory.FLAG_ENABLE_HDMV_DTS_AUDIO_STREAMS);

        DefaultMediaSourceFactory mediaSourceFactory = new DefaultMediaSourceFactory(dataSourceFactory, extractorsFactory);

        androidx.media3.exoplayer.DefaultLoadControl loadControl = new androidx.media3.exoplayer.DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                        5000,
                        15000,
                        1000,
                        2000
                )
                .build();

        exoPlayer = new ExoPlayer.Builder(this, renderersFactory)
                .setTrackSelector(trackSelector)
                .setMediaSourceFactory(mediaSourceFactory)
                .setLoadControl(loadControl)
                .build();

        binding.playerView.setPlayer(exoPlayer);
        
        exoPlayer.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_BUFFERING) {
                    binding.bufferingLayout.setVisibility(View.VISIBLE);
                    osdHandler.removeCallbacks(bufferingTimeoutRunnable);
                    osdHandler.postDelayed(bufferingTimeoutRunnable, 15000); 
                } else {
                    binding.bufferingLayout.setVisibility(View.GONE);
                    osdHandler.removeCallbacks(bufferingTimeoutRunnable);
                    if (state == Player.STATE_READY) {
                        hideTechnicalError();
                        retryCount = 0;
                        updateQualityAndFps();
                    }
                }
            }

            @Override
            public void onPlayerError(@NonNull androidx.media3.common.PlaybackException error) {
                if (retryCount < MAX_RETRIES) {
                    retryCount++;
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        if (exoPlayer != null) {
                            exoPlayer.prepare();
                            exoPlayer.play();
                        }
                    }, 2000);
                } else {
                    showTechnicalError();
                }
            }

            @Override
            public void onTracksChanged(@NonNull Tracks tracks) {
                updateQualityAndFps();
            }
        });

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
    }

    private void updateQualityAndFps() {
        if (exoPlayer == null) return;
        Format videoFormat = exoPlayer.getVideoFormat();
        if (videoFormat != null) {
            String quality = videoFormat.width + "x" + videoFormat.height;
            binding.tvQuality.setText(quality);
            binding.tvQualityCorner.setText(quality);
            
            String fps = (videoFormat.frameRate > 0) ? Math.round(videoFormat.frameRate) + " FPS" : "";
            binding.tvFps.setText(fps);
            binding.tvFpsCorner.setText(fps);
        }
    }

    private void playChannel(int index) {
        playChannel(index, 0);
    }

    private void playChannel(int index, long startPosition) {
        if (index < 0 || index >= playbackList.size()) return;
        currentIndex = index;
        Channel channel = playbackList.get(currentIndex);
        currentPlayingChannelId = channel.getId();

        hideTechnicalError();
        binding.bufferingLayout.setVisibility(View.VISIBLE);
        binding.miniInfoLayout.setVisibility(View.GONE); // Hide mini info on channel change
        
        isVod = com.bykerimoff.player.utils.M3UParser.isVodChannel(channel.getStreamUrl());

        // Stop current playback before switching to prevent audio overlap
        if (exoPlayer != null) {
            exoPlayer.stop();
            exoPlayer.clearMediaItems();
        }

        binding.tvChannelName.setText((currentIndex + 1) + ". " + channel.getName());
        Glide.with(this).load(channel.getLogoUrl()).placeholder(R.drawable.default_logo).into(binding.ivChannelLogo);

        String url = channel.getStreamUrl();
        MediaItem.Builder builder = new MediaItem.Builder().setUri(Uri.parse(url));
        
        String lower = url.toLowerCase(Locale.ROOT);
        if (lower.contains("m3u8") || lower.contains("stream.php") || lower.contains(".php") || lower.contains("/hls/")) {
            builder.setMimeType(MimeTypes.APPLICATION_M3U8);
        } else if (lower.contains(".ts") || lower.contains("output=ts") || lower.contains("output=mpegts") || lower.contains("/live/") || lower.contains("/mpegts")) {
            builder.setMimeType(MimeTypes.VIDEO_MP2T);
        } else if (lower.contains(".mpd")) {
            builder.setMimeType(MimeTypes.APPLICATION_MPD);
        }

        exoPlayer.setMediaItem(builder.build());
        if (startPosition > 0) {
            exoPlayer.seekTo(startPosition);
        }
        exoPlayer.prepare();
        exoPlayer.play();

        showOSD();
        updateEpg(channel);
        updateAnnouncement(channel);
        
        RecentChannelsManager.addRecentChannel(this, channel);
        
        // Save for Resume Playback if VOD
        if (isVod) {
            saveResumePosition();
        }

        // Save the last watched channel URL for auto-start feature
        SharedPreferences prefs = getSharedPreferences("neoplay_prefs", MODE_PRIVATE);
        prefs.edit()
            .putString("last_channel_url", channel.getStreamUrl())
            .putBoolean("last_is_vod", isVod)
            .apply();
    }

    private void saveResumePosition() {
        if (exoPlayer == null || playbackList == null || playbackList.isEmpty()) return;
        Channel current = playbackList.get(currentIndex);
        long pos = exoPlayer.getCurrentPosition();
        long dur = exoPlayer.getDuration();
        
        if (dur > 0 && pos > 0 && pos < dur - 10000) { // Don't save if finished
            ResumeManager.INSTANCE.saveProgress(this, new ResumeItem(
                current.getId(), current.getName(), current.getLogoUrl(), 
                current.getStreamUrl(), current.getCategoryName(), pos, dur, System.currentTimeMillis()
            ));
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (isVod) saveResumePosition();
    }

    private void updateAnnouncement(Channel channel) {
        SharedPreferences prefs = getSharedPreferences("neoplay_prefs", MODE_PRIVATE);
        boolean show = prefs.getBoolean("show_announcement_global", true); // Default to true if not set
        String text = prefs.getString("announcement_text", "");
        String colorStr = prefs.getString("announcement_color", "#FFD700");

        if (show && text != null && !text.isEmpty()) {
            binding.announcementContainer.setVisibility(View.VISIBLE);
            binding.tvAnnouncement.setText(text);
            try {
                binding.tvAnnouncement.setTextColor(android.graphics.Color.parseColor(colorStr));
            } catch (Exception e) {
                binding.tvAnnouncement.setTextColor(android.graphics.Color.WHITE);
            }
            
            binding.tvAnnouncement.setSelected(true); 
            
            // Remove automatic hide after 10s to keep it as a ticker if enabled
        } else {
            binding.announcementContainer.setVisibility(View.GONE);
        }
    }

    private void updateEpg(Channel channel) {
        binding.tvEpgInfo.setText("Proqram məlumatı yüklənir...");
        com.bykerimoff.player.utils.XMLTVParser.getProgramForChannel(channel.getName(), new com.bykerimoff.player.utils.XMLTVParser.EpgCallback() {
            @Override
            public void onResult(com.bykerimoff.player.models.EpgProgram program) {
                runOnUiThread(() -> {
                    if (program != null) {
                        binding.tvEpgInfo.setText(program.getTitle());
                        if (program.getStartTime() > 0 && program.getEndTime() > 0) {
                            long total = program.getEndTime() - program.getStartTime();
                            long current = System.currentTimeMillis() - program.getStartTime();
                            int progress = (int) ((current * 100) / total);
                            binding.epgProgress.setProgress(Math.max(0, Math.min(100, progress)));
                        }
                    } else {
                        binding.tvEpgInfo.setText("EPG Məlumatı yoxdur");
                        binding.epgProgress.setProgress(0);
                    }
                });
            }
        });
    }

    private void showOSD() {
        binding.osdLayout.setVisibility(View.VISIBLE);
        binding.miniInfoLayout.setVisibility(View.GONE);
        osdHandler.removeCallbacksAndMessages(null);
        osdHandler.postDelayed(() -> {
            binding.osdLayout.setVisibility(View.GONE);
        }, 5000);
    }

    private void setupSidebars() {
        binding.rvPlayerChannels.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(this));
        binding.rvPlayerCategories.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(this));
        binding.rvTracks.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(this));

        ChannelAdapter channelAdapter = new ChannelAdapter(playbackList, new ChannelAdapter.OnChannelClickListener() {
            @Override
            public void onChannelClick(Channel channel) {
                int index = playbackList.indexOf(channel);
                if (index != -1) {
                    playChannel(index);
                    binding.playerChannelSidebar.setVisibility(View.GONE);
                }
            }

            @Override
            public void onChannelFocus(Channel channel) {}

            @Override
            public void onChannelLongClick(Channel channel) {}
        });
        binding.rvPlayerChannels.setAdapter(channelAdapter);

        // Axtarış funksiyası
        binding.etPlayerSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterSidebarChannels(s.toString());
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void filterSidebarChannels(String query) {
        List<Channel> filtered = new ArrayList<>();
        for (Channel c : playbackList) {
            if (c.getName().toLowerCase().contains(query.toLowerCase())) {
                filtered.add(c);
            }
        }
        binding.rvPlayerChannels.setAdapter(new ChannelAdapter(filtered, new ChannelAdapter.OnChannelClickListener() {
            @Override
            public void onChannelClick(Channel channel) {
                int index = playbackList.indexOf(channel);
                if (index != -1) {
                    playChannel(index);
                    binding.playerChannelSidebar.setVisibility(View.GONE);
                }
            }
            @Override
            public void onChannelFocus(Channel channel) {}
            @Override
            public void onChannelLongClick(Channel channel) {}
        }));
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) {
            int digit = keyCode - KeyEvent.KEYCODE_0;
            if (isVolumeInputMode || binding.volumeLayout.getVisibility() == View.VISIBLE) {
                isVolumeInputMode = true;
                volumeNumberInput += digit;
                if (volumeNumberInput.length() > 3) volumeNumberInput = volumeNumberInput.substring(1);
                
                binding.tvVolumePercent.setText(volumeNumberInput + "%");
                binding.volumeLayout.setVisibility(View.VISIBLE);
                
                osdHandler.removeCallbacks(volumeInputRunnable);
                osdHandler.postDelayed(volumeInputRunnable, 2000);
            } else {
                channelNumberInput += digit;
                binding.tvNumericInput.setText(channelNumberInput);
                binding.tvNumericInput.setVisibility(View.VISIBLE);
                channelSwitchHandler.removeCallbacks(channelSwitchRunnable);
                channelSwitchHandler.postDelayed(channelSwitchRunnable, 2000);
            }
            return true;
        }

        boolean isSidebarVisible = binding.playerChannelSidebar.getVisibility() == View.VISIBLE ||
                binding.rvPlayerCategories.getVisibility() == View.VISIBLE ||
                binding.playerTracksSidebar.getVisibility() == View.VISIBLE ||
                binding.playerArchiveSidebar.getVisibility() == View.VISIBLE;

        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_UP:
                adjustVolume(true);
                return true;
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                adjustVolume(false);
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_CHANNEL_UP:
            case KeyEvent.KEYCODE_PAGE_UP:
                if (!isSidebarVisible) {
                    playNext();
                    return true;
                } else if (handleCircularFocus(true)) {
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_CHANNEL_DOWN:
            case KeyEvent.KEYCODE_PAGE_DOWN:
                if (!isSidebarVisible) {
                    playPrevious();
                    return true;
                } else if (handleCircularFocus(false)) {
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (!isSidebarVisible) {
                    if (isVod) {
                        exoPlayer.seekTo(Math.max(0, exoPlayer.getCurrentPosition() - 30000));
                        showOSD();
                        return true;
                    }
                    showCategorySidebar();
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (!isSidebarVisible && isVod) {
                    exoPlayer.seekTo(Math.min(exoPlayer.getDuration(), exoPlayer.getCurrentPosition() + 30000));
                    showOSD();
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                if (!isSidebarVisible) {
                    if (isVod) {
                        if (exoPlayer.isPlaying()) exoPlayer.pause();
                        else exoPlayer.play();
                        showOSD();
                        return true;
                    }
                    toggleSidebar();
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_PROG_RED:
            case KeyEvent.KEYCODE_F1: // Audio tracks
                showTracksSidebar(1);
                return true;
            case KeyEvent.KEYCODE_PROG_GREEN:
            case KeyEvent.KEYCODE_F2: // Subtitles
                showTracksSidebar(2);
                return true;
            case KeyEvent.KEYCODE_PROG_YELLOW:
            case KeyEvent.KEYCODE_F3: // Aspect ratio
                cycleAspectRatio();
                return true;
            case KeyEvent.KEYCODE_BACK:
                if (binding.playerChannelSidebar.getVisibility() == View.VISIBLE) {
                    binding.playerChannelSidebar.setVisibility(View.GONE);
                    return true;
                }
                if (binding.rvPlayerCategories.getVisibility() == View.VISIBLE) {
                    binding.rvPlayerCategories.setVisibility(View.GONE);
                    return true;
                }
                if (binding.playerTracksSidebar.getVisibility() == View.VISIBLE) {
                    binding.playerTracksSidebar.setVisibility(View.GONE);
                    return true;
                }
                break;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void cycleAspectRatio() {
        switch (currentAspectRatioMode) {
            case AspectRatioFrameLayout.RESIZE_MODE_FILL:
                currentAspectRatioMode = AspectRatioFrameLayout.RESIZE_MODE_FIT;
                break;
            case AspectRatioFrameLayout.RESIZE_MODE_FIT:
                currentAspectRatioMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM;
                break;
            case AspectRatioFrameLayout.RESIZE_MODE_ZOOM:
            default:
                currentAspectRatioMode = AspectRatioFrameLayout.RESIZE_MODE_FILL;
                break;
        }
        
        binding.playerView.setResizeMode(currentAspectRatioMode);
        
        String modeName;
        switch (currentAspectRatioMode) {
            case AspectRatioFrameLayout.RESIZE_MODE_FIT: modeName = "Orijinal (Fit)"; break;
            case AspectRatioFrameLayout.RESIZE_MODE_FILL: modeName = "Tam Ekran (Fill)"; break;
            case AspectRatioFrameLayout.RESIZE_MODE_ZOOM: modeName = "Yaxınlaşdırılmış (Zoom)"; break;
            default: modeName = "Standart"; break;
        }
        
        binding.tvAspectRatioStatus.setText("Görüntü: " + modeName);
        binding.tvAspectRatioStatus.setVisibility(View.VISIBLE);
        osdHandler.removeCallbacks(aspectRatioHideRunnable);
        osdHandler.postDelayed(aspectRatioHideRunnable, 3000);
    }

    private final Runnable aspectRatioHideRunnable = () -> binding.tvAspectRatioStatus.setVisibility(View.GONE);

    private boolean handleCircularFocus(boolean isUp) {
        View focused = getCurrentFocus();
        if (focused == null) return false;

        if (binding.playerChannelSidebar.getVisibility() == View.VISIBLE) {
            // Channel Sidebar (Search + List)
            if (focused.getId() == binding.etPlayerSearch.getId()) {
                if (isUp) {
                    // Search-də yuxarı basanda ən sonuncu kanala get
                    int count = binding.rvPlayerChannels.getAdapter() != null ? binding.rvPlayerChannels.getAdapter().getItemCount() : 0;
                    if (count > 0) {
                        binding.rvPlayerChannels.scrollToPosition(count - 1);
                        binding.rvPlayerChannels.postDelayed(() -> {
                            RecyclerView.ViewHolder vh = binding.rvPlayerChannels.findViewHolderForAdapterPosition(count - 1);
                            if (vh != null) vh.itemView.requestFocus();
                            else if (binding.rvPlayerChannels.getLayoutManager() != null) {
                                View v = binding.rvPlayerChannels.getLayoutManager().findViewByPosition(count - 1);
                                if (v != null) v.requestFocus();
                            }
                        }, 100);
                        return true;
                    }
                }
            } else {
                int pos = getRvPosition(binding.rvPlayerChannels, focused);
                if (pos != RecyclerView.NO_POSITION) {
                    int count = binding.rvPlayerChannels.getAdapter() != null ? binding.rvPlayerChannels.getAdapter().getItemCount() : 0;
                    if (isUp && pos == 0) {
                        binding.etPlayerSearch.requestFocus();
                        return true;
                    } else if (!isUp && pos == count - 1) {
                        binding.etPlayerSearch.requestFocus();
                        return true;
                    }
                }
            }
        } else if (binding.rvPlayerCategories.getVisibility() == View.VISIBLE) {
            // Category Sidebar (Simple loop)
            int pos = getRvPosition(binding.rvPlayerCategories, focused);
            if (pos != RecyclerView.NO_POSITION) {
                int count = binding.rvPlayerCategories.getAdapter() != null ? binding.rvPlayerCategories.getAdapter().getItemCount() : 0;
                if (isUp && pos == 0) {
                    binding.rvPlayerCategories.scrollToPosition(count - 1);
                    binding.rvPlayerCategories.postDelayed(() -> {
                        RecyclerView.ViewHolder vh = binding.rvPlayerCategories.findViewHolderForAdapterPosition(count - 1);
                        if (vh != null) vh.itemView.requestFocus();
                        else if (binding.rvPlayerCategories.getLayoutManager() != null) {
                            View v = binding.rvPlayerCategories.getLayoutManager().findViewByPosition(count - 1);
                            if (v != null) v.requestFocus();
                        }
                    }, 100);
                    return true;
                } else if (!isUp && pos == count - 1) {
                    binding.rvPlayerCategories.scrollToPosition(0);
                    binding.rvPlayerCategories.postDelayed(() -> {
                        RecyclerView.ViewHolder vh = binding.rvPlayerCategories.findViewHolderForAdapterPosition(0);
                        if (vh != null) vh.itemView.requestFocus();
                        else if (binding.rvPlayerCategories.getLayoutManager() != null) {
                            View v = binding.rvPlayerCategories.getLayoutManager().findViewByPosition(0);
                            if (v != null) v.requestFocus();
                        }
                    }, 100);
                    return true;
                }
            }
        }
        return false;
    }

    private int getRvPosition(RecyclerView rv, View focused) {
        int pos = rv.getChildLayoutPosition(focused);
        if (pos == RecyclerView.NO_POSITION) {
            if (focused.getParent() instanceof View) {
                View parent = (View) focused.getParent();
                while (parent != null && parent != rv) {
                    pos = rv.getChildLayoutPosition(parent);
                    if (pos != RecyclerView.NO_POSITION) break;
                    if (!(parent.getParent() instanceof View)) break;
                    parent = (View) parent.getParent();
                }
            }
        }
        return pos;
    }

    private void showTracksSidebar(int type) {
        List<TrackAdapter.TrackInfo> trackList = new ArrayList<>();
        Tracks tracks = exoPlayer.getCurrentTracks();
        
        int trackType = (type == 1) ? C.TRACK_TYPE_AUDIO : C.TRACK_TYPE_TEXT;
        binding.tvTracksTitle.setText(type == 1 ? "SƏS DİLLƏRİ" : "ALTYAZILAR");

        // Add "OFF" option for subtitles
        if (type == 2) {
            boolean isNoneSelected = true;
            for (Tracks.Group group : tracks.getGroups()) {
                if (group.getType() == C.TRACK_TYPE_TEXT && group.isSelected()) {
                    isNoneSelected = false;
                    break;
                }
            }
            trackList.add(new TrackAdapter.TrackInfo("Söndür", null, -1, isNoneSelected));
        }

        for (Tracks.Group group : tracks.getGroups()) {
            if (group.getType() == trackType) {
                for (int i = 0; i < group.length; i++) {
                    Format format = group.getTrackFormat(i);
                    String label = (format.language != null) ? format.language : "Naməlum";
                    if (format.label != null) label += " (" + format.label + ")";
                    
                    trackList.add(new TrackAdapter.TrackInfo(label, group, i, group.isTrackSelected(i)));
                }
            }
        }
        
        if (trackList.isEmpty()) {
            Toast.makeText(this, "Bu kanal üçün uyğun trek tapılmadı", Toast.LENGTH_SHORT).show();
            return;
        }

        binding.rvTracks.setAdapter(new TrackAdapter(trackList, track -> {
            if (track.trackIndex == -1) {
                exoPlayer.setTrackSelectionParameters(
                    exoPlayer.getTrackSelectionParameters().buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                        .build()
                );
            } else {
                exoPlayer.setTrackSelectionParameters(
                    exoPlayer.getTrackSelectionParameters().buildUpon()
                        .setTrackTypeDisabled(type == 1 ? C.TRACK_TYPE_AUDIO : C.TRACK_TYPE_TEXT, false)
                        .setOverrideForType(new TrackSelectionOverride(track.group.getMediaTrackGroup(), track.trackIndex))
                        .build()
                );
            }
            binding.playerTracksSidebar.setVisibility(View.GONE);
        }));
        binding.playerTracksSidebar.setVisibility(View.VISIBLE);
        binding.rvTracks.requestFocus();
    }

    private void processNumericInput() {
        try {
            int num = Integer.parseInt(channelNumberInput);
            if (num > 0 && num <= playbackList.size()) {
                playChannel(num - 1);
            }
        } catch (Exception ignored) {}
        channelNumberInput = "";
        binding.tvNumericInput.setVisibility(View.GONE);
    }

    private void processVolumeInput() {
        try {
            int vol = Integer.parseInt(volumeNumberInput);
            if (vol >= 0 && vol <= 100) {
                int maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                int targetVol = (vol * maxVol) / 100;
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0);
                updateVolumeUI();
            }
        } catch (Exception ignored) {}
        volumeNumberInput = "";
        isVolumeInputMode = false;
        osdHandler.postDelayed(() -> binding.volumeLayout.setVisibility(View.GONE), 2000);
    }

    private void adjustVolume(boolean increase) {
        isVolumeInputMode = false;
        volumeNumberInput = "";
        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, 
            increase ? AudioManager.ADJUST_RAISE : AudioManager.ADJUST_LOWER, 
            0);
        updateVolumeUI();
    }

    private void updateVolumeUI() {
        int current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        int max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int percent = (current * 100) / max;
        
        binding.volumeProgress.setProgress(percent);
        binding.tvVolumePercent.setText(percent + "%");
        binding.volumeLayout.setVisibility(View.VISIBLE);
        
        // Remove ANY pending hide tasks to reset the timer
        osdHandler.removeCallbacks(volumeHideRunnable);
        osdHandler.postDelayed(volumeHideRunnable, 3000);
    }

    private final Runnable volumeHideRunnable = () -> {
        binding.volumeLayout.setVisibility(View.GONE);
        isVolumeInputMode = false;
        volumeNumberInput = "";
    };

    private void playNext() {
        if (currentIndex < playbackList.size() - 1) {
            playChannel(currentIndex + 1);
        } else {
            playChannel(0);
        }
    }

    private void playPrevious() {
        if (currentIndex > 0) {
            playChannel(currentIndex - 1);
        } else {
            playChannel(playbackList.size() - 1);
        }
    }

    private void showCategorySidebar() {
        if (binding.rvPlayerCategories.getVisibility() == View.VISIBLE) {
            binding.rvPlayerCategories.setVisibility(View.GONE);
        } else {
            binding.playerChannelSidebar.setVisibility(View.GONE);
            binding.playerTracksSidebar.setVisibility(View.GONE);
            binding.playerArchiveSidebar.setVisibility(View.GONE);
            
            List<Category> categoryList = DataManager.getCurrentCategoryList();
            if (categoryList == null || categoryList.isEmpty()) {
                Toast.makeText(this, "Kateqoriya siyahısı hələ yüklənməyib", Toast.LENGTH_SHORT).show();
                return;
            }

            CategoryAdapter adapter = new CategoryAdapter(categoryList, category -> {
                updatePlaybackListByCategory(category);
                binding.rvPlayerCategories.setVisibility(View.GONE);
                // Save last category ID
                getSharedPreferences("neoplay_prefs", MODE_PRIVATE).edit()
                    .putString("last_category_id", category.getId())
                    .apply();
                
                new Handler(Looper.getMainLooper()).postDelayed(this::toggleSidebar, 100);
            });
            binding.rvPlayerCategories.setAdapter(adapter);
            binding.rvPlayerCategories.setVisibility(View.VISIBLE);
            binding.rvPlayerCategories.requestFocus();
        }
    }

    private void updatePlaybackListByCategory(Category category) {
        List<Channel> newList = new ArrayList<>();
        if ("all".equals(category.getId())) {
            newList.addAll(DataManager.getAllChannels());
        } else if ("0".equals(category.getId())) {
            FavoriteManager favManager = new FavoriteManager(this);
            for (Channel c : DataManager.getAllChannels()) {
                if (favManager.isFavorite(c.getId())) newList.add(c);
            }
        } else {
            List<Channel> list = DataManager.getCurrentChannelMap().get(category.getId());
            if (list != null) newList.addAll(list);
        }
        
        if (!newList.isEmpty()) {
            playbackList.clear();
            playbackList.addAll(newList);
            // Siyahı yenilənəndə mövcud adapteri də yeniləmək lazımdır (Sidebar aktivdirsə)
            setupSidebars(); 
        }
    }

    private void toggleSidebar() {
        if (binding.playerChannelSidebar.getVisibility() == View.VISIBLE) {
            binding.playerChannelSidebar.setVisibility(View.GONE);
        } else {
            binding.rvPlayerCategories.setVisibility(View.GONE);
            binding.playerTracksSidebar.setVisibility(View.GONE);
            binding.playerArchiveSidebar.setVisibility(View.GONE);
            
            binding.playerChannelSidebar.setVisibility(View.VISIBLE);
            
            // Focus on the current playing channel
            if (playbackList != null && currentIndex >= 0 && currentIndex < playbackList.size()) {
                binding.rvPlayerChannels.scrollToPosition(currentIndex);
                binding.rvPlayerChannels.postDelayed(() -> {
                    RecyclerView.ViewHolder vh = binding.rvPlayerChannels.findViewHolderForAdapterPosition(currentIndex);
                    if (vh != null) vh.itemView.requestFocus();
                }, 100);
            } else {
                binding.rvPlayerChannels.requestFocus();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (exoPlayer != null) {
            exoPlayer.release();
            exoPlayer = null;
        }
    }
}
