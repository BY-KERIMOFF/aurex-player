package com.bykerimoff.player;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;

import com.bykerimoff.player.databinding.ActivitySettingsBinding;
import com.bykerimoff.player.utils.MacUtils;
import com.bykerimoff.player.utils.PinDialog;
import com.bykerimoff.player.utils.SleepTimerManager;
import com.bykerimoff.player.utils.WallpaperManager;
import com.bykerimoff.player.utils.ThemeManager;
import com.bykerimoff.player.adapters.WallpaperAdapter;

public class SettingsActivity extends AppCompatActivity {

    private ActivitySettingsBinding binding;
    private SharedPreferences prefs;
    private ExoPlayer backgroundPlayer;

    private final ActivityResultLauncher<String[]> imagePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(),
            uri -> {
                if (uri != null) {
                    try {
                        getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        WallpaperManager.INSTANCE.setCustomImageUri(this, uri.toString());
                        WallpaperManager.INSTANCE.setWallpaperType(this, WallpaperManager.WallpaperType.CUSTOM_IMAGE);
                        WallpaperManager.INSTANCE.applyWallpaper(this, binding.ivAppBackground);
                        initBackgroundVideo();
                        Toast.makeText(this, "Şəkil fon kimi təyin edildi", Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        Toast.makeText(this, "Xəta: Fayl icazəsi alınmadı", Toast.LENGTH_SHORT).show();
                    }
                }
            }
    );

    private final ActivityResultLauncher<String[]> videoPickerLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(),
            uri -> {
                if (uri != null) {
                    try {
                        getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        WallpaperManager.INSTANCE.setCustomVideoUri(this, uri.toString());
                        WallpaperManager.INSTANCE.setWallpaperType(this, WallpaperManager.WallpaperType.CUSTOM_VIDEO);
                        initBackgroundVideo();
                        Toast.makeText(this, "Video fon kimi təyin edildi", Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        Toast.makeText(this, "Xəta: Fayl icazəsi alınmadı", Toast.LENGTH_SHORT).show();
                    }
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        prefs = getSharedPreferences("neoplay_prefs", MODE_PRIVATE);

        WallpaperManager.INSTANCE.applyWallpaper(this, binding.ivAppBackground);
        initBackgroundVideo();
        loadInfo();
        setupListeners();
        setupWallpaperList();
    }

    @Override
    protected void onResume() {
        super.onResume();
        initBackgroundVideo();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (backgroundPlayer != null) {
            backgroundPlayer.release();
            backgroundPlayer = null;
        }
    }

    private void initBackgroundVideo() {
        WallpaperManager.WallpaperType type = WallpaperManager.INSTANCE.getWallpaperType(this);
        String videoUriStr = WallpaperManager.INSTANCE.getCustomVideoUri(this);

        if (type == WallpaperManager.WallpaperType.CUSTOM_VIDEO && videoUriStr != null) {
            binding.ivAppBackground.setVisibility(View.GONE);
            binding.playerViewBackground.setVisibility(View.VISIBLE);
            
            if (backgroundPlayer == null) {
                backgroundPlayer = new ExoPlayer.Builder(this).build();
                binding.playerViewBackground.setPlayer(backgroundPlayer);
                backgroundPlayer.setRepeatMode(Player.REPEAT_MODE_ALL);
                backgroundPlayer.setVolume(0f);
            }
            
            try {
                MediaItem mediaItem = MediaItem.fromUri(android.net.Uri.parse(videoUriStr));
                backgroundPlayer.setMediaItem(mediaItem);
                backgroundPlayer.prepare();
                backgroundPlayer.play();
            } catch (Exception e) {
                e.printStackTrace();
                binding.ivAppBackground.setVisibility(View.VISIBLE);
                binding.playerViewBackground.setVisibility(View.GONE);
            }
        } else {
            binding.ivAppBackground.setVisibility(View.VISIBLE);
            binding.playerViewBackground.setVisibility(View.GONE);
            if (backgroundPlayer != null) {
                backgroundPlayer.stop();
                backgroundPlayer.release();
                backgroundPlayer = null;
            }
        }
    }

    private void loadInfo() {
        binding.tvMacAddress.setText("MAC: " + MacUtils.getMacAddress(this));
        
        try {
            android.content.pm.PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            String version = pInfo.versionName + " (" + (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P ? pInfo.getLongVersionCode() : pInfo.versionCode) + ")";
            binding.tvAppVersion.setText("Versiya: " + version);
        } catch (Exception e) {
            binding.tvAppVersion.setText("Versiya: Naməlum");
        }

        String expiry = prefs.getString("expiry_date", null);
        if (expiry != null && !expiry.equalsIgnoreCase("null") && !expiry.isEmpty()) {
            binding.tvExpiryDate.setText("Bitiş Tarixi: " + expiry);
        } else {
            binding.tvExpiryDate.setText("Bitiş Tarixi: Sınırsız / Naməlum");
        }
        
        binding.cbBootOnStartup.setChecked(prefs.getBoolean("boot_on_startup", false));
        binding.cbAutoStartLast.setChecked(prefs.getBoolean("auto_start_last_channel", true));
        binding.cbUseExternalPlayer.setChecked(prefs.getBoolean("use_external_player", false));
        binding.cbDataSaver.setChecked(prefs.getBoolean("data_saver_enabled", false));
        binding.cbSmartBuffer.setChecked(prefs.getBoolean("smart_buffer_enabled", true));
        binding.cbHideSensitive.setChecked(prefs.getBoolean("hide_sensitive_categories", false));
        
        int bufferSec = prefs.getInt("network_buffer_seconds", 5);
        if (bufferSec == 0) binding.rbBuffer0.setChecked(true);
        else if (bufferSec == 10) binding.rbBuffer10.setChecked(true);
        else if (bufferSec == 30) binding.rbBuffer30.setChecked(true);
        else binding.rbBuffer5.setChecked(true);

        binding.etEpgUrl.setText(prefs.getString("manual_epg_url", ""));

        String dns = prefs.getString("dns_type", "system");
        if ("google".equals(dns)) binding.rbDnsGoogle.setChecked(true);
        else if ("cloudflare".equals(dns)) binding.rbDnsCloudflare.setChecked(true);
        else if ("manual".equals(dns)) {
            binding.rbDnsManual.setChecked(true);
            binding.etDnsManualUrl.setVisibility(View.VISIBLE);
        }
        else binding.rbDnsSystem.setChecked(true);
        
        binding.etDnsManualUrl.setText(prefs.getString("dns_manual_url", ""));

        String viewMode = prefs.getString("view_mode", "classic");
        if ("list".equals(viewMode)) binding.rbViewList.setChecked(true);
        else if ("compact".equals(viewMode)) binding.rbViewCompact.setChecked(true);
        else binding.rbViewClassic.setChecked(true);

        String sortMode = prefs.getString("category_sort_mode", "default");
        if ("name".equals(sortMode)) binding.rbSortName.setChecked(true);
        else if ("count".equals(sortMode)) binding.rbSortCount.setChecked(true);
        else binding.rbSortDefault.setChecked(true);

        String pType = prefs.getString("player_type", "exo2");
        if ("standard".equalsIgnoreCase(pType)) {
            binding.rbExoStandard.setChecked(true);
        } else {
            binding.rbExo2.setChecked(true);
        }
    }

    @OptIn(markerClass = UnstableApi.class)
    private void setupListeners() {
        setupFocusEffect(binding.cbBootOnStartup);
        setupFocusEffect(binding.cbAutoStartLast);
        setupFocusEffect(binding.cbUseExternalPlayer);
        setupFocusEffect(binding.rbExoStandard);
        setupFocusEffect(binding.rbExo2);
        setupFocusEffect(binding.btnRefreshData);
        setupFocusEffect(binding.btnBack);
        
        setupFocusEffect(binding.btnTimerOff);
        setupFocusEffect(binding.btnTimer15);
        setupFocusEffect(binding.btnTimer30);
        setupFocusEffect(binding.btnTimer60);
        setupFocusEffect(binding.btnTimer120);
        
        setupFocusEffect(binding.btnUploadImage);
        setupFocusEffect(binding.btnUploadVideo);
        setupFocusEffect(binding.btnResetWallpaper);
        
        setupFocusEffect(binding.themeGold);
        setupFocusEffect(binding.themeBlue);
        setupFocusEffect(binding.themeRed);
        setupFocusEffect(binding.themeGreen);
        setupFocusEffect(binding.themeSilver);
        
        setupFocusEffect(binding.tvAppVersion);
        setupFocusEffect(binding.btnCheckUpdate);
        
        setupFocusEffect(binding.cbAppLock);
        setupFocusEffect(binding.btnChangePin);
        setupFocusEffect(binding.btnPrivacyPolicy);
        
        setupFocusEffect(binding.rbDnsSystem);
        setupFocusEffect(binding.rbDnsGoogle);
        setupFocusEffect(binding.rbDnsCloudflare);
        setupFocusEffect(binding.rbDnsManual);
        setupFocusEffect(binding.etDnsManualUrl);
        setupFocusEffect(binding.rbViewClassic);
        setupFocusEffect(binding.rbViewList);
        setupFocusEffect(binding.rbViewCompact);
        setupFocusEffect(binding.cbDataSaver);
        setupFocusEffect(binding.cbSmartBuffer);
        setupFocusEffect(binding.cbHideSensitive);
        setupFocusEffect(binding.btnSpeedTest);
        
        setupFocusEffect(binding.rbBuffer0);
        setupFocusEffect(binding.rbBuffer5);
        setupFocusEffect(binding.rbBuffer10);
        setupFocusEffect(binding.rbBuffer30);
        
        setupFocusEffect(binding.rbSortDefault);
        setupFocusEffect(binding.rbSortName);
        setupFocusEffect(binding.rbSortCount);

        binding.cbBootOnStartup.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("boot_on_startup", isChecked).apply();
            Toast.makeText(this, "Parametr yadda saxlanıldı", Toast.LENGTH_SHORT).show();
        });

        binding.cbAutoStartLast.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("auto_start_last_channel", isChecked).apply();
            Toast.makeText(this, "Parametr yadda saxlanıldı", Toast.LENGTH_SHORT).show();
        });

        binding.cbUseExternalPlayer.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("use_external_player", isChecked).apply();
            Toast.makeText(this, "Pleyer seçimi yadda saxlanıldı", Toast.LENGTH_SHORT).show();
        });

        binding.cbDataSaver.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("data_saver_enabled", isChecked).apply();
            Toast.makeText(this, "Data Saver: " + (isChecked ? "Aktiv" : "Deaktiv"), Toast.LENGTH_SHORT).show();
        });

        binding.cbSmartBuffer.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("smart_buffer_enabled", isChecked).apply();
            Toast.makeText(this, "Smart Buffer: " + (isChecked ? "Aktiv" : "Deaktiv"), Toast.LENGTH_SHORT).show();
        });

        binding.cbHideSensitive.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("hide_sensitive_categories", isChecked).apply();
            Toast.makeText(this, "Həssas kateqoriyalar " + (isChecked ? "Gizlədildi" : "Görünür"), Toast.LENGTH_SHORT).show();
        });

        binding.cbAppLock.setChecked(prefs.getBoolean("app_lock_enabled", false));
        binding.btnChangePin.setVisibility(binding.cbAppLock.isChecked() ? View.VISIBLE : View.GONE);

        binding.cbAppLock.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked && prefs.getString("app_pin", "2266").equals("2266")) {
                Toast.makeText(this, "Lütfən PİN kodu dəyişməyi unutmayın (Default: 2266)", Toast.LENGTH_LONG).show();
            }
            prefs.edit().putBoolean("app_lock_enabled", isChecked).apply();
            binding.btnChangePin.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });

        binding.btnChangePin.setOnClickListener(v -> showChangePinDialog());

        binding.rgPlayerChoice.setOnCheckedChangeListener((group, checkedId) -> {
            String type = (checkedId == R.id.rbExoStandard) ? "standard" : "exo2";
            prefs.edit().putString("player_type", type).apply();
            Toast.makeText(this, "Seçildi: " + (type.equals("standard") ? "Standart Exo" : "Exo 2 / V2"), Toast.LENGTH_SHORT).show();
        });

        binding.rgDnsChoice.setOnCheckedChangeListener((group, checkedId) -> {
            String dns = "system";
            binding.etDnsManualUrl.setVisibility(View.GONE);
            
            if (checkedId == R.id.rbDnsGoogle) dns = "google";
            else if (checkedId == R.id.rbDnsCloudflare) dns = "cloudflare";
            else if (checkedId == R.id.rbDnsManual) {
                dns = "manual";
                binding.etDnsManualUrl.setVisibility(View.VISIBLE);
                binding.etDnsManualUrl.requestFocus();
            }
            
            prefs.edit().putString("dns_type", dns).apply();
            String manualUrl = binding.etDnsManualUrl.getText().toString().trim();
            com.bykerimoff.player.utils.NetworkUtils.setDnsType(dns, manualUrl);
            Toast.makeText(this, "DNS dəyişdirildi. Tətbiqi yenidən başlatmağınız tövsiyə olunur.", Toast.LENGTH_LONG).show();
        });

        binding.etDnsManualUrl.setOnEditorActionListener((v, actionId, event) -> {
            String url = v.getText().toString().trim();
            prefs.edit().putString("dns_manual_url", url).apply();
            if (binding.rbDnsManual.isChecked()) {
                com.bykerimoff.player.utils.NetworkUtils.setDnsType("manual", url);
            }
            Toast.makeText(this, "Manual DNS URL yadda saxlanıldı", Toast.LENGTH_SHORT).show();
            return false;
        });

        binding.rgViewChoice.setOnCheckedChangeListener((group, checkedId) -> {
            String mode = "classic";
            if (checkedId == R.id.rbViewList) mode = "list";
            else if (checkedId == R.id.rbViewCompact) mode = "compact";
            
            prefs.edit().putString("view_mode", mode).apply();
            Toast.makeText(this, "Görünüş rejimi dəyişdirildi", Toast.LENGTH_SHORT).show();
        });

        binding.btnSpeedTest.setOnClickListener(v -> {
            startActivity(new Intent(this, SpeedTestActivity.class));
        });

        binding.rgBufferChoice.setOnCheckedChangeListener((group, checkedId) -> {
            int seconds = 5;
            if (checkedId == R.id.rbBuffer0) seconds = 0;
            else if (checkedId == R.id.rbBuffer10) seconds = 10;
            else if (checkedId == R.id.rbBuffer30) seconds = 30;
            
            prefs.edit().putInt("network_buffer_seconds", seconds).apply();
            Toast.makeText(this, "Buffer müddəti: " + seconds + " san.", Toast.LENGTH_SHORT).show();
        });

        binding.rgSortChoice.setOnCheckedChangeListener((group, checkedId) -> {
            String mode = "default";
            if (checkedId == R.id.rbSortName) mode = "name";
            else if (checkedId == R.id.rbSortCount) mode = "count";
            
            prefs.edit().putString("category_sort_mode", mode).apply();
            Toast.makeText(this, "Kateqoriya sıralaması dəyişdirildi", Toast.LENGTH_SHORT).show();
        });

        binding.btnRefreshData.setOnClickListener(v -> {
            String manualEpg = binding.etEpgUrl.getText().toString().trim();
            prefs.edit().putString("manual_epg_url", manualEpg).apply();
            
            // Həm daxili mənbələri, həm də manual linki yenilə
            com.bykerimoff.player.utils.XMLTVParser.syncDefaultSources(this);
            if (!manualEpg.isEmpty()) {
                com.bykerimoff.player.utils.XMLTVParser.downloadAndParse(manualEpg);
            }
            Toast.makeText(this, "Bütün EPG mənbələri yenilənir...", Toast.LENGTH_SHORT).show();
        });

        binding.btnPrivacyPolicy.setOnClickListener(v -> {
            String url = "https://github.com/BY-KERIMOFF/NeoPlay-TV/blob/main/PRIVACY_POLICY.md"; // Default placeholder
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setData(android.net.Uri.parse(url));
            startActivity(i);
        });

        binding.btnCheckUpdate.setOnClickListener(v -> {
            Toast.makeText(this, "Yeniləmə yoxlanılır...", Toast.LENGTH_SHORT).show();
            new com.bykerimoff.player.utils.UpdateManager(this).checkForUpdates(true);
        });

        binding.btnTimerOff.setOnClickListener(v -> setSleepTimer(0));
        binding.btnTimer15.setOnClickListener(v -> setSleepTimer(15));
        binding.btnTimer30.setOnClickListener(v -> setSleepTimer(30));
        binding.btnTimer60.setOnClickListener(v -> setSleepTimer(60));
        binding.btnTimer120.setOnClickListener(v -> setSleepTimer(120));

        binding.btnUploadImage.setOnClickListener(v -> imagePickerLauncher.launch(new String[]{"image/*"}));
        binding.btnUploadVideo.setOnClickListener(v -> videoPickerLauncher.launch(new String[]{"video/*"}));
        
        binding.btnResetWallpaper.setOnClickListener(v -> {
            WallpaperManager.INSTANCE.setWallpaperType(this, WallpaperManager.WallpaperType.DEFAULT);
            WallpaperManager.INSTANCE.applyWallpaper(this, binding.ivAppBackground);
            initBackgroundVideo();
            Toast.makeText(this, "Standart fon bərpa edildi", Toast.LENGTH_SHORT).show();
        });

        binding.themeGold.setOnClickListener(v -> updateTheme(ThemeManager.AppTheme.GOLD));
        binding.themeBlue.setOnClickListener(v -> updateTheme(ThemeManager.AppTheme.NEON_BLUE));
        binding.themeRed.setOnClickListener(v -> updateTheme(ThemeManager.AppTheme.RUBY_RED));
        binding.themeGreen.setOnClickListener(v -> updateTheme(ThemeManager.AppTheme.EMERALD_GREEN));
        binding.themeSilver.setOnClickListener(v -> updateTheme(ThemeManager.AppTheme.SILVER));

        binding.btnBack.setOnClickListener(v -> finish());
    }

    private void updateTheme(ThemeManager.AppTheme theme) {
        ThemeManager.INSTANCE.setTheme(this, theme);
        Toast.makeText(this, "Tema dəyişdirildi: " + theme.getNameAz(), Toast.LENGTH_SHORT).show();
        // UI-da bəzi elementləri dərhal yeniləyək
        recreate(); 
    }

    private void setSleepTimer(int minutes) {
        SleepTimerManager manager = SleepTimerManager.getInstance();
        if (minutes == 0) {
            manager.cancelTimer();
            binding.tvCurrentTimerStatus.setText("Status: Qapalı");
            Toast.makeText(this, "Yuxu taymeri ləğv edildi", Toast.LENGTH_SHORT).show();
        } else {
            manager.startTimer(minutes, this);
            binding.tvCurrentTimerStatus.setText("Status: Aktiv (" + minutes + " dəq)");
        }
    }

    private void showChangePinDialog() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert);
        builder.setTitle("PİN Kodu Dəyişdir");
        
        final android.widget.EditText input = new android.widget.EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        input.setHint("Yeni 4 rəqəmli PİN");
        
        builder.setView(input);
        builder.setPositiveButton("YADDA SAXLA", (dialog, which) -> {
            String newPin = input.getText().toString();
            if (newPin.length() == 4) {
                prefs.edit().putString("app_pin", newPin).apply();
                Toast.makeText(this, "PİN kod uğurla dəyişdirildi", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "PİN 4 rəqəmli olmalıdır!", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("LƏĞV ET", null);
        builder.show();
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

    private void setupWallpaperList() {
        int currentIndex = WallpaperManager.INSTANCE.getCurrentWallpaperIndex(this);
        WallpaperAdapter adapter = new WallpaperAdapter(WallpaperManager.INSTANCE.getWallpapers(), currentIndex, index -> {
            WallpaperManager.INSTANCE.setWallpaperType(this, WallpaperManager.WallpaperType.DEFAULT);
            WallpaperManager.INSTANCE.setCurrentWallpaperIndex(this, index);
            WallpaperManager.INSTANCE.applyWallpaper(this, binding.ivAppBackground);
            initBackgroundVideo();
            // Digər ekranlar açılanda yeni fonu görəcək
            Toast.makeText(this, "Arxa fon dəyişdirildi", Toast.LENGTH_SHORT).show();
        });
        binding.rvWallpapers.setAdapter(adapter);
    }
}
