package com.bykerimoff.player

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Shader
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewAnimationUtils
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.bumptech.glide.Glide
import com.bykerimoff.player.adapters.CurrencyAdapter
import com.bykerimoff.player.api.ApiClient
import com.bykerimoff.player.api.ApiResponse
import com.bykerimoff.player.databinding.ActivityMainBinding
import com.bykerimoff.player.utils.*
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var retryCount = 0
    private val MAX_RETRIES = 3
    private var deviceMac: String? = null
    private var isSplashFinished = false
    private var pendingAuthResponse: ApiResponse? = null
    
    private var testCountDownTimer: CountDownTimer? = null
    private var backgroundPlayer: ExoPlayer? = null
    
    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Təhlükəsizlik yoxlanışı
        if (checkSecurity()) return

        val prefs = getSharedPreferences("neoplay_prefs", MODE_PRIVATE)
        
        // Daimi bloklama yoxlanışı
        if (prefs.getBoolean("is_permanently_blocked", false)) {
            showSecurityError("Cihaz təhlükəsizlik səbəbi ilə daimi olaraq bloklanıb.")
            return
        }

        val lockEnabled = prefs.getBoolean("app_lock_enabled", false)
        val isAlreadyUnlocked = intent.getBooleanExtra("is_unlocked", false)

        if (lockEnabled && !isAlreadyUnlocked) {
            startActivity(Intent(this, LockActivity::class.java))
            finish()
            return
        }

        WallpaperManager.applyWallpaper(this, binding.ivAppBackground)
        initBackgroundVideo()

        DataManager.setShowAnnouncementGlobal(prefs.getBoolean("show_announcement_global", true))

        deviceMac = MacUtils.getMacAddress(this)

        val dns = prefs.getString("dns_type", "system") ?: "system"
        val manualUrl = prefs.getString("dns_manual_url", "")
        NetworkUtils.setDnsType(dns, manualUrl)

        UpdateManager(this).checkForUpdates()
        LogoManager.loadLogoDatabase(this)
        XMLTVParser.syncDefaultSources(this)

        startSplashAnimation()
        setupListeners()
        applyThemeColors()
        startAuthProcess()
    }

    private fun applyThemeColors() {
        val color = ThemeManager.getThemeColor(this)
        val colorStateList = ColorStateList.valueOf(color)
        
        binding.pbDashboardLoading.indeterminateTintList = colorStateList
        binding.btnRetry.backgroundTintList = colorStateList
        binding.btnSearch.backgroundTintList = colorStateList
        
        binding.ivLiveTvIcon.imageTintList = colorStateList
        binding.ivMoviesIcon.imageTintList = colorStateList
        binding.ivSeriesIcon.imageTintList = colorStateList
        binding.ivFavoritesIcon.imageTintList = colorStateList
        binding.ivRadioIcon.imageTintList = colorStateList
        binding.ivSpeedTestIcon.imageTintList = colorStateList
        binding.ivKidsModeIcon.imageTintList = colorStateList
        
        binding.tvExpiryInfo.setTextColor(color)
        binding.testTitle.setTextColor(color)
        binding.testCountdown.setTextColor(color)
        binding.macDisplay.setTextColor(color)
        binding.tvKidsModeAction.setTextColor(color)
        
        applyPremiumBranding(binding.tvAppTitle)
    }

    override fun onResume() {
        super.onResume()
        if (checkSecurity()) return
        
        loadResumeList()

        if (isSplashFinished) {
            val prefs = getSharedPreferences("neoplay_prefs", MODE_PRIVATE)
            val isVod = prefs.getBoolean("is_vod_enabled", true)
            val isSeries = prefs.getBoolean("is_series_enabled", true)
            updateDashboardCards(isVod, isSeries)
            
            val expiry = prefs.getString("expiry_date", null)
            if (!expiry.isNullOrBlank() && !expiry.equals("null", ignoreCase = true)) {
                binding.tvExpiryInfo.text = "Abunəlik bitir: $expiry"
                binding.tvExpiryInfo.visibility = View.VISIBLE
            } else {
                binding.tvExpiryInfo.visibility = View.GONE
            }

            WallpaperManager.applyWallpaper(this, binding.ivAppBackground)
            initBackgroundVideo()

            binding.loadingLayout.visibility = View.GONE
            binding.dashboardLayout.visibility = View.VISIBLE
            applyThemeColors()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        backgroundPlayer?.release()
        backgroundPlayer = null
    }

    private fun initBackgroundVideo() {
        val type = WallpaperManager.getWallpaperType(this)
        val videoUri = WallpaperManager.getCustomVideoUri(this)

        if (type == WallpaperManager.WallpaperType.CUSTOM_VIDEO && videoUri != null) {
            binding.ivAppBackground.visibility = View.GONE
            binding.playerViewBackground.visibility = View.VISIBLE
            
            if (backgroundPlayer == null) {
                backgroundPlayer = ExoPlayer.Builder(this).build()
                binding.playerViewBackground.player = backgroundPlayer
                backgroundPlayer?.repeatMode = Player.REPEAT_MODE_ALL
                backgroundPlayer?.volume = 0f
            }
            
            try {
                val mediaItem = MediaItem.fromUri(videoUri)
                backgroundPlayer?.setMediaItem(mediaItem)
                backgroundPlayer?.prepare()
                backgroundPlayer?.play()
            } catch (e: Exception) {
                e.printStackTrace()
                binding.ivAppBackground.visibility = View.VISIBLE
                binding.playerViewBackground.visibility = View.GONE
            }
        } else {
            binding.ivAppBackground.visibility = View.VISIBLE
            binding.playerViewBackground.visibility = View.GONE
            backgroundPlayer?.stop()
            backgroundPlayer?.release()
            backgroundPlayer = null
        }
    }

    private fun checkSecurity(): Boolean {
        val prefs = getSharedPreferences("neoplay_prefs", MODE_PRIVATE)
        val isTampered = SecurityUtils.isSnifferAppInstalled(this) || SecurityUtils.isDebuggerActive()
        
        if (isTampered) {
            val violations = prefs.getInt("security_violations", 0) + 1
            prefs.edit().putInt("security_violations", violations).apply()
            
            if (violations >= 3) {
                triggerPermanentBlock("Kod izləmə cəhdi (Sniffer/Debugger)")
                return true
            }
            
            showSecurityError("Təhlükəsizlik qaydaları pozuldu! Bu hal davam etsə cihazınız daimi bloklanacaq ($violations/3)")
            return true
        }

        if (SecurityUtils.isVpnActive(this)) {
            showSecurityError("VPN istifadəsi qadağandır! Zəhmət olmasa VPN-i söndürüb yenidən cəhd edin.")
            return true
        }
        if (SecurityUtils.isProxyActive()) {
            showSecurityError("Proxy (Proksi) bağlantısı aşkar edildi! Şəbəkə tənzimləmələrini yoxlayın.")
            return true
        }

        if (SecurityUtils.isEmulator()) {
            showSecurityError("Tətbiqin emulator (PC) üzərində işlədilməsi qadağandır!")
            return true
        }
        
        return false
    }

    private fun triggerPermanentBlock(reason: String) {
        val prefs = getSharedPreferences("neoplay_prefs", MODE_PRIVATE)
        prefs.edit().putBoolean("is_permanently_blocked", true).apply()
        
        val blockUrl = "api.php?mac=$deviceMac&action=block_device&reason=" + URLEncoder.encode(reason, "UTF-8")
        ApiClient.getService().blockDevice(blockUrl).enqueue(object : Callback<ResponseBody> {
            override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {}
            override fun onFailure(call: Call<ResponseBody>, t: Throwable) {}
        })

        showSecurityError("CİHAZ BLOKLANDI!\n$reason")
    }

    private fun showSecurityError(message: String) {
        binding.loadingLayout.visibility = View.GONE
        binding.dashboardLayout.visibility = View.GONE
        binding.errorOverlay.visibility = View.VISIBLE

        binding.errorTitle.text = "Təhlükəsizlik Xətası"
        binding.errorTitle.setTextColor(Color.RED)
        binding.errorMessage.text = message
        binding.btnRetry.text = "ÇIXIŞ"
        binding.btnRetry.setOnClickListener { finishAffinity() }
        
        Handler(Looper.getMainLooper()).postDelayed({ finishAffinity() }, 5000)
    }

    private fun startSplashAnimation() {
        val premiumSplashUrl = "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?q=80&w=1920"
        Glide.with(this)
            .load(premiumSplashUrl)
            .placeholder(R.drawable.app_background)
            .centerCrop()
            .into(binding.ivSplashBg)

        binding.ivSplashBg.scaleX = 1.0f
        binding.ivSplashBg.scaleY = 1.0f
        binding.ivSplashBg.animate()
            .scaleX(1.15f)
            .scaleY(1.15f)
            .setDuration(5000)
            .setInterpolator(LinearInterpolator())
            .start()

        val handler = Handler(Looper.getMainLooper())

        // 1. Logo Animasiyası
        binding.ivSplashLogo.visibility = View.VISIBLE
        binding.ivSplashLogo.alpha = 0f
        binding.ivSplashLogo.scaleX = 0.5f
        binding.ivSplashLogo.scaleY = 0.5f
        binding.ivSplashLogo.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(1200)
            .setInterpolator(OvershootInterpolator())
            .start()

        // 2. Title Animasiyası
        handler.postDelayed({
            val slideUp = AnimationUtils.loadAnimation(this, R.anim.slide_up_fade)
            binding.tvEnjoyWatching.visibility = View.VISIBLE
            binding.tvEnjoyWatching.startAnimation(slideUp)
            applyShimmerEffect(binding.tvEnjoyWatching)
        }, 800)

        // 3. Slogan Animasiyası
        handler.postDelayed({
            binding.tvSlogan.visibility = View.VISIBLE
            binding.tvSlogan.alpha = 0f
            binding.tvSlogan.animate()
                .alpha(1f)
                .setDuration(1000)
                .start()
        }, 1800)

        handler.postDelayed({
            if (!isSplashFinished) {
                isSplashFinished = true
                val prefs = getSharedPreferences("neoplay_prefs", MODE_PRIVATE)
                showDashboard(prefs.getBoolean("is_vod_enabled", true), prefs.getBoolean("is_series_enabled", true))
                checkPendingResponse()
            }
        }, 4000)
    }

    private fun checkPendingResponse() {
        pendingAuthResponse?.let {
            handleAuthResponse(it)
            pendingAuthResponse = null
        }
    }

    private fun setupListeners() {
        setupFocusEffect(binding.cardLiveTv)
        setupFocusEffect(binding.cardMovies)
        setupFocusEffect(binding.cardSeries)
        setupFocusEffect(binding.cardFavorites)
        setupFocusEffect(binding.cardRadio)
        setupFocusEffect(binding.cardSpeedTest)
        setupFocusEffect(binding.cardKidsMode)
        setupFocusEffect(binding.btnSettings)
        setupFocusEffect(binding.btnSearch)

        binding.btnRetry.setOnClickListener {
            retryCount = 0
            startAuthProcess()
        }

        binding.cardLiveTv.setOnClickListener {
            startActivity(Intent(this@MainActivity, LiveTvActivity::class.java))
        }

        binding.cardMovies.setOnClickListener {
            val intent = Intent(this@MainActivity, LiveTvActivity::class.java)
            intent.putExtra("filter_category", "VOD_MOVIES")
            startActivity(intent)
        }

        binding.cardSeries.setOnClickListener {
            val intent = Intent(this@MainActivity, LiveTvActivity::class.java)
            intent.putExtra("filter_category", "VOD_SERIES")
            startActivity(intent)
        }

        binding.cardFavorites.setOnClickListener {
            val intent = Intent(this@MainActivity, LiveTvActivity::class.java)
            intent.putExtra("filter_category", "Sevimlilər")
            startActivity(intent)
        }

        binding.cardRadio.setOnClickListener {
            startActivity(Intent(this@MainActivity, RadioActivity::class.java))
        }

        binding.cardSpeedTest.setOnClickListener {
            startActivity(Intent(this@MainActivity, SpeedTestActivity::class.java))
        }

        binding.cardKidsMode.setOnClickListener {
            toggleKidsMode()
        }

        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
        }

        binding.btnSearch.setOnClickListener {
            startActivity(Intent(this@MainActivity, LiveTvActivity::class.java))
        }
    }

    private fun setupFocusEffect(view: View) {
        view.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                v.startAnimation(AnimationUtils.loadAnimation(this, R.anim.scale_up))
                v.elevation = 20f
            } else {
                v.startAnimation(AnimationUtils.loadAnimation(this, R.anim.scale_down))
                v.elevation = 0f
            }
        }
    }

    private fun startAuthProcess() {
        if (!isSplashFinished) {
            showLoading()
        } else {
            binding.pbDashboardLoading.visibility = View.VISIBLE
        }
        checkAuthentication()
    }

    private fun checkAuthentication() {
        val url = "api.php?mac=$deviceMac"
        ApiClient.getService().checkMac(url).enqueue(object : Callback<ApiResponse> {
            override fun onResponse(call: Call<ApiResponse>, response: Response<ApiResponse>) {
                if (response.isSuccessful && response.body() != null) {
                    val result = response.body()!!
                    handleAuthResponse(result)
                } else {
                    handleFailure("Server xətası baş verdi.")
                }
            }

            override fun onFailure(call: Call<ApiResponse>, t: Throwable) {
                if (retryCount < MAX_RETRIES) {
                    retryCount++
                    Handler(Looper.getMainLooper()).postDelayed({ checkAuthentication() }, 5000)
                } else {
                    handleFailure("İnternet bağlantısı yoxdur və ya serverə qoşulmaq mümkün olmadı.")
                }
            }
        })
    }

    private fun handleAuthResponse(response: ApiResponse) {
        binding.pbDashboardLoading.visibility = View.GONE

        if (!isSplashFinished) {
            pendingAuthResponse = response
            return
        }

        val status = response.status ?: "error"
        val message = response.message ?: "M3U faylı tapılmadı!"

        if ("success".equals(status, ignoreCase = true)) {
            val isTest = response.isTestMode
            val remaining = response.getTestRemainingSeconds()
            
            val m3uUrl = response.m3uUrl
            if (m3uUrl.isNullOrEmpty()) {
                showError("Xəta", message)
                return
            }

            val expiry = response.expiryDate
            val isVod = response.isVodEnabled
            val isSeries = response.isSeriesEnabled

            val edit = getSharedPreferences("neoplay_prefs", MODE_PRIVATE).edit()
            edit.putString("expiry_date", expiry)
            edit.putString("playlist_type", response.playlistType)
            edit.putString("m3u_url", m3uUrl)
            edit.putString("xtream_host", if (response.xtream != null) response.xtream.host else "")
            edit.putString("xtream_user", if (response.xtream != null) response.xtream.username else "")
            edit.putString("xtream_pass", if (response.xtream != null) response.xtream.password else "")
            edit.putBoolean("is_vod_enabled", isVod)
            edit.putBoolean("is_series_enabled", isSeries)
            edit.putBoolean("is_adult_enabled", response.isAdultEnabled)
            edit.putBoolean("is_sport_enabled", response.isSportEnabled)
            
            if (response.announcement != null) {
                edit.putBoolean("show_announcement_global", response.isShowAnnouncement)
                edit.putString("announcement_text", response.announcement)
                edit.putString("announcement_color", response.announcementColor)
            }
            
            edit.putLong("test_expire_time", if (isTest) System.currentTimeMillis() + (remaining * 1000) else 0L)
            edit.apply()

            if (!expiry.isNullOrBlank() && !expiry.equals("null", ignoreCase = true)) {
                binding.tvExpiryInfo.text = "Abunəlik bitir: $expiry"
                binding.tvExpiryInfo.visibility = View.VISIBLE
            } else {
                binding.tvExpiryInfo.visibility = View.GONE
            }

            if (isTest) {
                val remainingInt = remaining.toInt()
                val countdown = response.countdown ?: formatTime(remainingInt)
                showTestCountdown(remainingInt, countdown, response.warning, response.warningLevel)
            } else {
                hideTestCountdown()
            }

            showDashboard(isVod, isSeries)
            loadAndCheckPlaylist()
            handleAutoStart() 
        } else if ("expired".equals(status, ignoreCase = true)) {
            showError("Abunəlik Bitib", "⏳ Abunəlik bitib!\nTarix: ${response.expiryDate}")
        } else if ("blocked".equals(status, ignoreCase = true)) {
            showBlockedDialog(response.message ?: "🚫 Cihaz bloklanıb!")
        } else {
            showError("Xəta", message)
        }
    }

    private fun handleAutoStart() {
        val prefs = getSharedPreferences("neoplay_prefs", MODE_PRIVATE)
        val autoStart = prefs.getBoolean("auto_start_last_channel", true)
        val lastChannelUrl = prefs.getString("last_channel_url", "")
        val lastIsVod = prefs.getBoolean("last_is_vod", false)

        if (!autoStart || lastChannelUrl.isNullOrEmpty() || lastIsVod) return

        val handler = Handler(Looper.getMainLooper())
        val checkInterval = 200L
        val maxWaitTime = 3000L
        var waitedTime = 0L

        val checkRunnable = object : Runnable {
            override fun run() {
                if (UpdateManager.isCheckFinished) {
                    if (!UpdateManager.isUpdateFound) {
                        val intent = Intent(this@MainActivity, LiveTvActivity::class.java)
                        intent.putExtra("auto_start", true)
                        startActivity(intent)
                    }
                } else if (waitedTime < maxWaitTime) {
                    waitedTime += checkInterval
                    handler.postDelayed(this, checkInterval)
                } else {
                    val intent = Intent(this@MainActivity, LiveTvActivity::class.java)
                    intent.putExtra("auto_start", true)
                    startActivity(intent)
                }
            }
        }
        handler.post(checkRunnable)
    }

    private fun showLoading() {
        binding.loadingLayout.visibility = View.VISIBLE
        binding.dashboardLayout.visibility = View.GONE
        binding.errorOverlay.visibility = View.GONE
    }

    private fun loadAndCheckPlaylist() {
        val prefs = getSharedPreferences("neoplay_prefs", MODE_PRIVATE)
        val type = prefs.getString("playlist_type", "m3u")
        val isVodEnabled = prefs.getBoolean("is_vod_enabled", true)
        val isSeriesEnabled = prefs.getBoolean("is_series_enabled", true)

        if (!"xtream".equals(type, ignoreCase = true)) {
            runOnUiThread {
                binding.cardMovies.visibility = View.GONE
                binding.cardSeries.visibility = View.GONE
                updateCardsWeightSum()
            }
            return
        }

        runOnUiThread {
            binding.cardMovies.visibility = if (isVodEnabled) View.VISIBLE else View.GONE
            binding.cardSeries.visibility = if (isSeriesEnabled) View.VISIBLE else View.GONE
            updateCardsWeightSum()
        }
    }

    private fun showDashboard(isVodEnabled: Boolean, isSeriesEnabled: Boolean) {
        binding.loadingLayout.clearAnimation()
        
        if (binding.dashboardLayout.visibility == View.VISIBLE) {
            updateDashboardCards(isVodEnabled, isSeriesEnabled)
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val cx = binding.root.width / 2
            val cy = binding.root.height / 2
            val finalRadius = Math.hypot(cx.toDouble(), cy.toDouble()).toFloat()

            binding.dashboardLayout.visibility = View.VISIBLE
            val anim = ViewAnimationUtils.createCircularReveal(
                binding.dashboardLayout, cx, cy, 0f, finalRadius
            )
            anim.duration = 800
            anim.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    binding.loadingLayout.visibility = View.GONE
                }
            })
            anim.start()
        } else {
            binding.loadingLayout.visibility = View.GONE
            binding.dashboardLayout.visibility = View.VISIBLE
        }

        applyPremiumBranding(binding.tvAppTitle)

        val sdf = SimpleDateFormat("EEEE, d MMMM", Locale("az"))
        binding.tvDate.text = sdf.format(Date())

        updateDashboardCards(isVodEnabled, isSeriesEnabled)
        binding.errorOverlay.visibility = View.GONE

        val isKidsMode = getSharedPreferences("neoplay_prefs", MODE_PRIVATE)
            .getBoolean("kids_mode_active", false)
        updateDashboardForKidsMode(isActive = isKidsMode)

        binding.cardLiveTv.requestFocus()

        loadWeather()
        loadCurrencies()
    }

    private fun loadResumeList() {
        binding.resumeSection.visibility = View.GONE
    }

    private fun loadCurrencies() {
        CurrencyManager.fetchCurrencies { items ->
            runOnUiThread {
                if (items.isNotEmpty()) {
                    binding.currencySection.visibility = View.VISIBLE
                    val adapter = CurrencyAdapter(items)
                    binding.rvCurrencies.adapter = adapter
                } else {
                    binding.currencySection.visibility = View.GONE
                }
            }
        }
    }

    private fun applyPremiumBranding(textView: TextView) {
        textView.text = "AUREX PLAYER"
        val primaryColor = ThemeManager.getThemeColor(this)
        val hsv = FloatArray(3)
        Color.colorToHSV(primaryColor, hsv)
        
        hsv[2] = 1.0f 
        val lightColor = Color.HSVToColor(hsv)
        hsv[2] = 0.5f
        val darkColor = Color.HSVToColor(hsv)

        val gradient = LinearGradient(
            0f, 0f, 0f, textView.textSize,
            intArrayOf(lightColor, primaryColor, darkColor, darkColor),
            floatArrayOf(0f, 0.4f, 0.7f, 1f),
            Shader.TileMode.CLAMP
        )
        
        textView.paint.shader = gradient
        textView.setShadowLayer(15f, 0f, 0f, primaryColor)
        textView.invalidate()
    }

    private fun updateDashboardCards(isVodEnabled: Boolean, isSeriesEnabled: Boolean) {
        val prefs = getSharedPreferences("neoplay_prefs", MODE_PRIVATE)
        val type = prefs.getString("playlist_type", "m3u")

        if ("xtream".equals(type, ignoreCase = true)) {
            binding.cardMovies.visibility = if (isVodEnabled) View.VISIBLE else View.GONE
            binding.cardSeries.visibility = if (isSeriesEnabled) View.VISIBLE else View.GONE
        } else {
            binding.cardMovies.visibility = View.GONE
            binding.cardSeries.visibility = View.GONE
        }

        updateCardsWeightSum()
    }

    private fun updateCardsWeightSum() {
        var visibleCount = 2.0f
        if (binding.cardMovies.visibility == View.VISIBLE) visibleCount += 1.0f
        if (binding.cardSeries.visibility == View.VISIBLE) visibleCount += 1.0f
        binding.cardsContainer.weightSum = visibleCount
    }

    private fun loadWeather() {
        WeatherManager.fetchWeather(object : WeatherManager.WeatherCallback {
            override fun onSuccess(temp: String, weatherCode: Int) {
                binding.weatherLayout.visibility = View.VISIBLE
                binding.tvTemperature.text = temp
                binding.tvWeatherEmoji.text = WeatherManager.getWeatherEmoji(weatherCode)
            }

            override fun onFailure(error: String) {
                binding.weatherLayout.visibility = View.GONE
            }
        })
    }

    private fun showError(title: String, message: String) {
        binding.loadingLayout.visibility = View.GONE
        binding.dashboardLayout.visibility = View.GONE
        binding.errorOverlay.visibility = View.VISIBLE

        binding.errorTitle.text = title
        binding.errorMessage.text = message
        binding.macDisplay.text = "MAC: $deviceMac"
    }

    private fun handleFailure(message: String) {
        showError("Bağlantı Xətası", message)
    }

    private fun showTestCountdown(seconds: Int, countdown: String, warning: String?, warningLevel: String?) {
        runOnUiThread {
            binding.testBanner.visibility = View.VISIBLE
            binding.testCountdown.text = "🧪 TEST - $countdown"
            binding.testCountdown.setTextColor(Color.parseColor("#4ade80"))
            binding.testCountdown.setBackgroundColor(Color.parseColor("#1e3a5f"))
            
            if (warning != null) {
                binding.testWarning.visibility = View.VISIBLE
                binding.testWarning.text = warning
                
                val color = when (warningLevel) {
                    "danger" -> Color.parseColor("#ef4444")
                    "warning" -> Color.parseColor("#fbbf24")
                    else -> Color.parseColor("#4ade80")
                }
                binding.testWarning.setTextColor(color)
            } else {
                binding.testWarning.visibility = View.GONE
            }
            startCountDown(seconds)
        }
    }

    private fun showBlockedDialog(message: String) {
        runOnUiThread {
            val builder = AlertDialog.Builder(this)
            builder.setTitle("⛔ Cihaz Bloklanıb")
            builder.setMessage("$message\n\nZəhmət olmasa dilerinizlə əlaqə saxlayın.")
            builder.setCancelable(false)
            builder.setPositiveButton("Bağla") { _, _ -> finish() }
            builder.setIcon(android.R.drawable.ic_dialog_alert)

            val dialog = builder.create()
            dialog.show()

            val msgView = dialog.findViewById<TextView>(android.R.id.message)
            msgView?.setTextColor(Color.parseColor("#ef4444"))
        }
    }

    private fun hideTestCountdown() {
        runOnUiThread {
            binding.testBanner.visibility = View.GONE
            testCountDownTimer?.cancel()
            testCountDownTimer = null
        }
    }

    private fun startCountDown(seconds: Int) {
        testCountDownTimer?.cancel()
        testCountDownTimer = object : CountDownTimer(seconds.toLong() * 1000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val remaining = (millisUntilFinished / 1000).toInt()
                val timeText = formatTime(remaining)
                binding.testCountdown.text = "Test: $timeText"
                
                val color = if (remaining < 60) Color.RED 
                            else if (remaining < 300) Color.parseColor("#FFA500") 
                            else Color.parseColor("#D4AF37")
                
                binding.testCountdown.setTextColor(color)
                binding.testTitle.setTextColor(color)

                if (remaining < 300) {
                    if (binding.testBanner.animation == null) {
                        binding.testBanner.startAnimation(AnimationUtils.loadAnimation(this@MainActivity, R.anim.blink))
                    }
                } else {
                    binding.testBanner.clearAnimation()
                }
            }
            override fun onFinish() {
                binding.testCountdown.text = "⏰ Test bitdi!"
                binding.testCountdown.setTextColor(Color.parseColor("#ef4444"))
                binding.testCountdown.setBackgroundColor(Color.parseColor("#450a0a"))
                
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("🧪 Test Bitdi")
                    .setMessage("Test müddəti bitdi! Zəhmət olmasa dilerinizlə əlaqə saxlayın.")
                    .setCancelable(false)
                    .setPositiveButton("Bağla") { _, _ -> finish() }
                    .show()
            }
        }.start()
    }

    private fun formatTime(seconds: Int): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, secs)
    }

    private fun applyShimmerEffect(textView: TextView) {
        val paint = textView.paint
        val width = paint.measureText(textView.text.toString())
        val shimmerGradient = LinearGradient(
            0f, 0f, width / 2, 0f,
            intArrayOf(textView.currentTextColor, Color.WHITE, textView.currentTextColor),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        paint.shader = shimmerGradient
        val matrix = Matrix()
        val animator = ValueAnimator.ofFloat(0f, width * 2)
        animator.duration = 1500
        animator.repeatCount = ValueAnimator.INFINITE
        animator.addUpdateListener { animation ->
            val translate = animation.animatedValue as Float
            matrix.setTranslate(translate - width, 0f)
            shimmerGradient.setLocalMatrix(matrix)
            textView.invalidate()
        }
        animator.start()
    }

    private fun toggleKidsMode() {
        val prefs = getSharedPreferences("neoplay_prefs", MODE_PRIVATE)
        val isKidsMode = prefs.getBoolean("kids_mode_active", false)

        if (isKidsMode) {
            PinDialog.show(this, object : PinDialog.PinListener {
                override fun onSuccess() {
                    prefs.edit().putBoolean("kids_mode_active", false).apply()
                    Toast.makeText(this@MainActivity, "Uşaq rejimindən çıxıldı", Toast.LENGTH_SHORT).show()
                    updateDashboardForKidsMode(false)
                }
                override fun onCancel() {}
            })
        } else {
            prefs.edit().putBoolean("kids_mode_active", true).apply()
            Toast.makeText(this, "Uşaq rejimi aktiv edildi", Toast.LENGTH_SHORT).show()
            updateDashboardForKidsMode(true)
        }
    }

    private fun updateDashboardForKidsMode(isActive: Boolean) {
        val prefs = getSharedPreferences("neoplay_prefs", MODE_PRIVATE)
        val isVod = prefs.getBoolean("is_vod_enabled", true)
        val isSeries = prefs.getBoolean("is_series_enabled", true)

        if (isActive) {
            binding.cardMovies.visibility = View.GONE
            binding.cardSeries.visibility = View.GONE
            binding.cardRadio.visibility = View.GONE
            binding.cardFavorites.visibility = View.GONE
            binding.btnSettings.visibility = View.GONE
            binding.btnSearch.visibility = View.GONE
            binding.tvKidsModeAction.text = "REJİMDƏN ÇIX"
            binding.tvKidsModeSubtitle.text = "⚠️ Təhlükəsiz Rejim Aktivdir"
            binding.cardsContainer.weightSum = 1.0f
        } else {
            showDashboard(isVod, isSeries)
            binding.cardRadio.visibility = View.VISIBLE
            binding.cardFavorites.visibility = View.VISIBLE
            binding.btnSettings.visibility = View.VISIBLE
            binding.btnSearch.visibility = View.VISIBLE
            binding.tvKidsModeAction.text = "AKTİV ET"
            binding.tvKidsModeSubtitle.text = "Yalnız uşaqlar üçün kontent"
        }
    }
}
