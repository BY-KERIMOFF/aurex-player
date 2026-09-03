package com.bykerimoff.player;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.text.TextPaint;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.TranslateAnimation;
import android.widget.Toast;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.PlaybackException;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.recyclerview.widget.LinearLayoutManager;
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
import com.bykerimoff.player.utils.M3UParser;
import com.bykerimoff.player.utils.NetworkUtils;
import com.bykerimoff.player.utils.RecentChannelsManager;
import com.bykerimoff.player.utils.ResumeManager;
import com.bykerimoff.player.utils.SleepTimerManager;
import com.bykerimoff.player.utils.ThemeManager;
import com.bykerimoff.player.utils.XMLTVParser;

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
    private CountDownTimer testCountDownTimer;
    private long lastKeyTime = 0;
    private static final int KEY_DELAY = 30; // ms for snappy feel

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
        isVod = M3UParser.isVodChannel(initialChannel.getStreamUrl());
        
        if (resumePos > 0 && isVod) {
            playChannel(currentIndex, resumePos);
        } else {
            playChannel(currentIndex);
        }
        applyThemeColors();
        setupSidebars();
        setupNumericOverlay();
        updateTestCountdown();
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

        // Universal MediaSourceFactory with HLS optimization
        DefaultMediaSourceFactory mediaSourceFactory = new DefaultMediaSourceFactory(dataSourceFactory, extractorsFactory);
        // We can't easily set HLS specific flags on the default factory without custom factories,
        // but the sniffing logic in playChannel will handle the heavy lifting.

        SharedPreferences prefs = getSharedPreferences("neoplay_prefs", MODE_PRIVATE);
        boolean smartBuffer = prefs.getBoolean("smart_buffer_enabled", true);
        int userBufferSec = prefs.getInt("network_buffer_seconds", 5);
        
        int minBuffer = smartBuffer ? 15000 : userBufferSec * 1000;
        int maxBuffer = smartBuffer ? 50000 : (userBufferSec * 1000 * 3);
        int bufferPlayback = smartBuffer ? 2500 : 1000;
        int bufferRebuffer = smartBuffer ? 5000 : 2000;

        DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                        minBuffer,
                        maxBuffer,
                        bufferPlayback,
                        bufferRebuffer
                )
                .setPrioritizeTimeOverSizeThresholds(true)
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
            public void onPlayerError(@NonNull PlaybackException error) {
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
                Color.WHITE,
                Color.TRANSPARENT,
                Color.TRANSPARENT,
                CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                Color.BLACK,
                null
        );
        if (binding.playerView.getSubtitleView() != null) {
            binding.playerView.getSubtitleView().setApplyEmbeddedStyles(false);
            binding.playerView.getSubtitleView().setApplyEmbeddedFontSizes(false);
            binding.playerView.getSubtitleView().setStyle(style);
            binding.playerView.getSubtitleView().setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, 24f);
        }
    }

    private void updateQualityAndFps() {
        if (exoPlayer == null) return;
        int color = ThemeManager.INSTANCE.getThemeColor(this);
        Format videoFormat = exoPlayer.getVideoFormat();
        if (videoFormat != null) {
            String quality = videoFormat.width + "x" + videoFormat.height;
            binding.tvQuality.setText(quality);
            binding.tvQualityCorner.setText(quality);
            binding.tvQuality.setBackgroundColor(color);
            binding.tvQualityCorner.setBackgroundColor(color);
            
            String fps = (videoFormat.frameRate > 0) ? Math.round(videoFormat.frameRate) + " FPS" : "";
            binding.tvFps.setText(fps);
            binding.tvFpsCorner.setText(fps);
            binding.tvFps.setTextColor(color);
            binding.tvFpsCorner.setTextColor(color);
        }
    }

    private void applyThemeColors() {
        int color = ThemeManager.INSTANCE.getThemeColor(this);
        ColorStateList colorStateList = ColorStateList.valueOf(color);
        
        binding.tvPlayerSidebarTitle.setTextColor(color);
        binding.tvArchiveTitle.setTextColor(color);
        binding.tvTracksTitle.setTextColor(color);
        binding.tvBufferingText.setTextColor(color);
        binding.tvAspectRatioStatus.setTextColor(color);
        binding.tvPlayerTestCountdown.setTextColor(color);
        
        binding.testTitlePlayer.setTextColor(color);
        binding.testTimerPlayer.setTextColor(color);
        
        binding.vodSeekBar.setProgressTintList(colorStateList);
        binding.vodSeekBar.setThumbTintList(colorStateList);
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
        
        isVod = M3UParser.isVodChannel(channel.getStreamUrl());

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
        
        // --- Universal Stream Detection (v8.3.5) ---
        // RULE: Only set MimeType for clean, direct file extensions. 
        // For anything with parameters (?) or scripts (.php), let ExoPlayer auto-sniff the byte stream.
        if (!lower.contains("?") && !lower.contains(".php")) {
            if (lower.endsWith(".m3u8")) {
                builder.setMimeType(MimeTypes.APPLICATION_M3U8);
            } else if (lower.endsWith(".mpd")) {
                builder.setMimeType(MimeTypes.APPLICATION_MPD);
            } else if (lower.endsWith(".ts")) {
                builder.setMimeType(MimeTypes.VIDEO_MP2T);
            } else if (lower.endsWith(".mp4")) {
                builder.setMimeType(MimeTypes.VIDEO_MP4);
            }
        } else {
            // For PHP proxies or URLs with parameters, we check for explicit IPTV indicators
            if (lower.contains("type=m3u8")) {
                builder.setMimeType(MimeTypes.APPLICATION_M3U8);
            } else if (lower.contains("type=ts") || lower.contains("output=ts") || lower.contains("output=mpegts")) {
                builder.setMimeType(MimeTypes.VIDEO_MP2T);
            }
            // Otherwise, NO MimeType is set. ExoPlayer will sniff the first few bytes of the stream
            // to determine if it's HLS, TS, MP4, etc. This is the ultimate "Universal" fix.
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
        boolean show = prefs.getBoolean("show_announcement_global", true);
        String rawText = prefs.getString("announcement_text", "");
        String colorStr = prefs.getString("announcement_color", "#FFD700");
        int speedVal = prefs.getInt("announcement_speed", 100);

        if (show && rawText != null && !rawText.isEmpty()) {
            // Clean the text: replace newlines with a separator to ensure it stays on one line
            final String text = rawText.replace("\n", "  •  ").replace("\r", " ").trim();
            
            binding.announcementContainer.setVisibility(View.VISIBLE);
            binding.tvAnnouncement.setText(text);
            try {
                binding.tvAnnouncement.setTextColor(Color.parseColor(colorStr));
            } catch (Exception e) {
                binding.tvAnnouncement.setTextColor(Color.WHITE);
            }
            
            binding.tvAnnouncement.post(() -> {
                float screenWidth = (float) getResources().getDisplayMetrics().widthPixels;
                
                // Measure the actual width of the text content accurately
                TextPaint paint = binding.tvAnnouncement.getPaint();
                float textWidth = paint.measureText(text);
                
                // Force the TextView to be wide enough to hold the entire text without clipping
                ViewGroup.LayoutParams params = binding.tvAnnouncement.getLayoutParams();
                params.width = (int) (textWidth + 100); // Add a small safety margin
                binding.tvAnnouncement.setLayoutParams(params);
                
                binding.tvAnnouncement.clearAnimation();
                
                // Start from the very right edge and go completely past the left edge
                TranslateAnimation anim = new TranslateAnimation(
                    screenWidth,
                    -textWidth - 100f, 
                    0f, 0f
                );
                
                // Calculate duration based on a fixed pixels-per-second speed
                // This ensures constant speed regardless of text length
                long duration = (long) ((screenWidth + textWidth) / (speedVal > 0 ? speedVal : 100) * 1000);
                
                anim.setDuration(duration);
                anim.setRepeatCount(Animation.INFINITE);
                anim.setInterpolator(new LinearInterpolator());
                binding.tvAnnouncement.startAnimation(anim);
            });
        } else {
            binding.announcementContainer.setVisibility(View.GONE);
            binding.tvAnnouncement.clearAnimation();
        }
    }

    private void updateEpg(Channel channel) {
        binding.tvEpgInfo.setText("Proqram məlumatı yüklənir...");
        XMLTVParser.getProgramForChannel(channel.getName(), new XMLTVParser.EpgCallback() {
            @Override
            public void onResult(EpgProgram program) {
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
        binding.rvPlayerChannels.setLayoutManager(new LinearLayoutManager(this));
        binding.rvPlayerCategories.setLayoutManager(new LinearLayoutManager(this));
        binding.rvTracks.setLayoutManager(new LinearLayoutManager(this));

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
        if (System.currentTimeMillis() - lastKeyTime < KEY_DELAY) return true;
        lastKeyTime = System.currentTimeMillis();

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
                    if (event.getRepeatCount() % 3 == 0) playNext();
                    return true;
                } else {
                    boolean handled = handleCircularFocus(true);
                    if (!handled) {
                        handled = super.onKeyDown(keyCode, event);
                        View newFocus = getCurrentFocus();
                        if (newFocus != null && !isViewInSidebars(newFocus)) {
                            restoreSidebarFocus();
                        }
                    }
                    return handled;
                }
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_CHANNEL_DOWN:
            case KeyEvent.KEYCODE_PAGE_DOWN:
                if (!isSidebarVisible) {
                    if (event.getRepeatCount() % 3 == 0) playPrevious();
                    return true;
                } else {
                    boolean handled = handleCircularFocus(false);
                    if (!handled) {
                        handled = super.onKeyDown(keyCode, event);
                        View newFocus = getCurrentFocus();
                        if (newFocus != null && !isViewInSidebars(newFocus)) {
                            restoreSidebarFocus();
                        }
                    }
                    return handled;
                }
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
                if (binding.playerArchiveSidebar.getVisibility() == View.VISIBLE) {
                    binding.playerArchiveSidebar.setVisibility(View.GONE);
                    return true;
                }
                break;
        }
        return super.onKeyDown(keyCode, event);
    }

    private boolean isViewInSidebars(View v) {
        return isViewInRecyclerView(v, binding.rvPlayerChannels) ||
               isViewInRecyclerView(v, binding.rvPlayerCategories) ||
               isViewInRecyclerView(v, binding.rvTracks) ||
               isViewInRecyclerView(v, binding.rvArchive) ||
               v.getId() == binding.etPlayerSearch.getId();
    }

    private void restoreSidebarFocus() {
        if (binding.playerChannelSidebar.getVisibility() == View.VISIBLE) {
            binding.rvPlayerChannels.requestFocus();
        } else if (binding.rvPlayerCategories.getVisibility() == View.VISIBLE) {
            binding.rvPlayerCategories.requestFocus();
        } else if (binding.playerTracksSidebar.getVisibility() == View.VISIBLE) {
            binding.rvTracks.requestFocus();
        } else if (binding.playerArchiveSidebar.getVisibility() == View.VISIBLE) {
            binding.rvArchive.requestFocus();
        }
    }

    private boolean isViewInRecyclerView(View focused, RecyclerView rv) {
        if (focused == null || rv == null) return false;
        View view = rv.findContainingItemView(focused);
        return view != null;
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
            int count = binding.rvPlayerChannels.getAdapter() != null ? binding.rvPlayerChannels.getAdapter().getItemCount() : 0;
            
            if (focused.getId() == binding.etPlayerSearch.getId()) {
                if (isUp) {
                    // Search-də yuxarı basanda ən sonuncu kanala get
                    if (count > 0) {
                        binding.rvPlayerChannels.stopScroll();
                        scrollToAndFocus(binding.rvPlayerChannels, count - 1);
                        return true;
                    }
                } else {
                    // Search-də aşağı basanda 1-ci kanala get
                    if (count > 0) {
                        binding.rvPlayerChannels.stopScroll();
                        scrollToAndFocus(binding.rvPlayerChannels, 0);
                        return true;
                    }
                }
            } else {
                int pos = getRvPosition(binding.rvPlayerChannels, focused);
                if (pos != RecyclerView.NO_POSITION) {
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
                    binding.rvPlayerCategories.stopScroll();
                    scrollToAndFocus(binding.rvPlayerCategories, count - 1);
                    return true;
                } else if (!isUp && pos == count - 1) {
                    binding.rvPlayerCategories.stopScroll();
                    scrollToAndFocus(binding.rvPlayerCategories, 0);
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

    private void scrollToAndFocus(RecyclerView rv, int position) {
        rv.scrollToPosition(position);
        rv.postDelayed(() -> {
            RecyclerView.ViewHolder vh = rv.findViewHolderForAdapterPosition(position);
            if (vh != null) vh.itemView.requestFocus();
            else if (rv.getLayoutManager() != null) {
                View v = rv.getLayoutManager().findViewByPosition(position);
                if (v != null) v.requestFocus();
            }
        }, 100);
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
        SharedPreferences prefs = getSharedPreferences("neoplay_prefs", MODE_PRIVATE);
        boolean isAdultEnabled = prefs.getBoolean("is_adult_enabled", true);
        boolean isSportEnabled = prefs.getBoolean("is_sport_enabled", true);
        boolean hideSensitive = prefs.getBoolean("hide_sensitive_categories", false);
        boolean isKidsMode = prefs.getBoolean("kids_mode_active", false);

        if ("all".equals(category.getId())) {
            for (Channel c : DataManager.getAllChannels()) {
                if (isKidsMode && !M3UParser.isKidsCategory(c.getCategoryName())) continue;
                if (!isAdultEnabled && M3UParser.isSensitiveCategory(c.getCategoryName())) continue;
                if (hideSensitive && M3UParser.isSensitiveCategory(c.getCategoryName())) continue;
                if (!isSportEnabled && M3UParser.isSportCategory(c.getCategoryName())) continue;
                newList.add(c);
            }
        } else if ("0".equals(category.getId())) {
            FavoriteManager favManager = new FavoriteManager(this);
            for (Channel c : DataManager.getAllChannels()) {
                if (favManager.isFavorite(c.getId())) {
                    if (isKidsMode && !M3UParser.isKidsCategory(c.getCategoryName())) continue;
                    if (!isAdultEnabled && M3UParser.isSensitiveCategory(c.getCategoryName())) continue;
                    if (hideSensitive && M3UParser.isSensitiveCategory(c.getCategoryName())) continue;
                    if (!isSportEnabled && M3UParser.isSportCategory(c.getCategoryName())) continue;
                    newList.add(c);
                }
            }
        } else {
            List<Channel> list = DataManager.getCurrentChannelMap().get(category.getId());
            if (list != null) {
                for (Channel c : list) {
                    if (isKidsMode && !M3UParser.isKidsCategory(c.getCategoryName())) continue;
                    if (!isAdultEnabled && M3UParser.isSensitiveCategory(c.getCategoryName())) continue;
                    if (hideSensitive && M3UParser.isSensitiveCategory(c.getCategoryName())) continue;
                    if (!isSportEnabled && M3UParser.isSportCategory(c.getCategoryName())) continue;
                    newList.add(c);
                }
            }
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
            binding.testBannerPlayer.setVisibility(View.GONE);
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
                
                binding.testBannerPlayer.setVisibility(View.VISIBLE);
                binding.testTimerPlayer.setText("Test: " + timeLeft);

                int color;
                if (remaining < 60) {
                    color = Color.RED;
                    binding.testWarningPlayer.setVisibility(View.VISIBLE);
                    binding.testWarningPlayer.setText("⚠️ Test vaxtı bitir!");
                } else if (remaining < 300) {
                    color = Color.parseColor("#FFA500"); // Orange
                    binding.testWarningPlayer.setVisibility(View.VISIBLE);
                    binding.testWarningPlayer.setText("Test müddəti az qalıb");
                } else {
                    color = Color.parseColor("#D4AF37"); // Gold
                    binding.testWarningPlayer.setVisibility(View.GONE);
                }
                
                binding.testTimerPlayer.setTextColor(color);
                binding.testTitlePlayer.setTextColor(color);
            }

            @Override
            public void onFinish() {
                binding.testTimerPlayer.setText("Test: 00:00");
                binding.testBannerPlayer.setVisibility(View.GONE);
                Toast.makeText(PlayerActivity.this, "Test müddəti bitdi!", Toast.LENGTH_LONG).show();
                finish();
            }
        }.start();
    }

    private String formatTimeForTest(int totalSeconds) {
        int hours = totalSeconds / 3600;
        int minutes = (totalSeconds % 3600) / 60;
        int seconds = totalSeconds % 60;
        return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (testCountDownTimer != null) {
            testCountDownTimer.cancel();
        }
        if (exoPlayer != null) {
            exoPlayer.release();
            exoPlayer = null;
        }
    }
}
