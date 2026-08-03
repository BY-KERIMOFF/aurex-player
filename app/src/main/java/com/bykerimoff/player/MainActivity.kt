package com.bykerimoff.player

import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Shader
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.util.UnstableApi
import com.bykerimoff.player.api.ApiClient
import com.bykerimoff.player.api.ApiResponse
import com.bykerimoff.player.databinding.ActivityMainBinding
import com.bykerimoff.player.utils.M3UParser
import com.bykerimoff.player.utils.MacUtils
import com.bykerimoff.player.adapters.CurrencyAdapter
import com.bykerimoff.player.adapters.ResumeAdapter
import com.bykerimoff.player.models.Channel
import com.bykerimoff.player.utils.CurrencyManager
import com.bykerimoff.player.utils.DataManager
import com.bykerimoff.player.utils.ResumeManager
import com.bykerimoff.player.utils.SecurityUtils
import com.bykerimoff.player.utils.UpdateManager
import com.bykerimoff.player.utils.XMLTVParser
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var retryCount = 0
    private val MAX_RETRIES = 3
    private var deviceMac: String? = null
    private var isSplashFinished = false
    private var pendingAuthResponse: ApiResponse? = null
    
    private var testCountDownTimer: android.os.CountDownTimer? = null
    
    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Təhlükəsizlik yoxlanışı (Ekran yükləndikdən sonra çağırılmalıdır)
        if (checkSecurity()) return

        val prefs = getSharedPreferences("neoplay_prefs", Context.MODE_PRIVATE)
        val lockEnabled = prefs.getBoolean("app_lock_enabled", false)
        val isAlreadyUnlocked = intent.getBooleanExtra("is_unlocked", false)

        if (lockEnabled && !isAlreadyUnlocked) {
            startActivity(Intent(this, LockActivity::class.java))
            finish()
            return
        }

        com.bykerimoff.player.utils.WallpaperManager.applyWallpaper(this, binding.ivAppBackground)

        // Elan ayarını yaddaşdan yüklə
        DataManager.setShowAnnouncementGlobal(prefs.getBoolean("show_announcement_global", true))

        deviceMac = MacUtils.getMacAddress(this)

        // DNS Ayarı
        val dns = prefs.getString("dns_type", "system") ?: "system"
        val manualUrl = prefs.getString("dns_manual_url", "")
        com.bykerimoff.player.utils.NetworkUtils.setDnsType(dns, manualUrl)

        // Yeniləməni yoxla
        UpdateManager(this).checkForUpdates()

        // Qlobal loqo bazasını yüklə
        com.bykerimoff.player.utils.LogoManager.loadLogoDatabase()

        // EPG Sinxronizasiyasını başlat
        XMLTVParser.syncDefaultSources()

        startSplashAnimation()
        setupListeners()
        startAuthProcess()
    }

    override fun onResume() {
        super.onResume()
        if (checkSecurity()) return
        
        loadResumeList()

        if (isSplashFinished) {
            binding.loadingLayout.visibility = View.GONE
            binding.dashboardLayout.visibility = View.VISIBLE
        }
    }

    private fun checkSecurity(): Boolean {
        // Developer/Release fərqi qoymadan VPN və Proxy-ni həmişə yoxla
        if (SecurityUtils.isVpnActive(this)) {
            showSecurityError("VPN istifadəsi qadağandır! Zəhmət olmasa VPN-i söndürüb yenidən cəhd edin.")
            return true
        }
        if (SecurityUtils.isSnifferAppInstalled(this)) {
            showSecurityError("Cihazda şəbəkə tutucu (Sniffer) proqram aşkar edildi! Təhlükəsizlik üçün həmin proqramı silin.")
            return true
        }
        if (SecurityUtils.isProxyActive()) {
            showSecurityError("Proxy (Proksi) bağlantısı aşkar edildi! Şəbəkə tənzimləmələrini yoxlayın.")
            return true
        }

        // VPN və Proxy yoxlanışı kifayətdir (USB Debugging və Debugger TV Box uyğunluğu üçün söndürüldü)
        return false
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
        
        // 5 saniyə sonra proqramı avtomatik bağla
        Handler(Looper.getMainLooper()).postDelayed({ finishAffinity() }, 5000)
    }

    private fun startSplashAnimation() {
        // Arxa fon şəkli üçün premium qızılı abstrakt fon yükləyək
        val premiumSplashUrl = "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?q=80&w=1920"
        com.bumptech.glide.Glide.with(this)
            .load(premiumSplashUrl)
            .placeholder(R.drawable.app_background)
            .centerCrop()
            .into(binding.ivSplashBg)

        // Ken Burns Effect: Arxa fonu yavaşca böyüdək
        binding.ivSplashBg.scaleX = 1.0f
        binding.ivSplashBg.scaleY = 1.0f
        binding.ivSplashBg.animate()
            .scaleX(1.15f)
            .scaleY(1.15f)
            .setDuration(4000)
            .setInterpolator(android.view.animation.LinearInterpolator())
            .start()

        val slideUpText = AnimationUtils.loadAnimation(this, R.anim.slide_up_fade)
        slideUpText.duration = 2000

        binding.tvEnjoyWatching.visibility = View.VISIBLE
        binding.tvEnjoyWatching.startAnimation(slideUpText)
        
        // Shimmer effektini başlat
        applyShimmerEffect(binding.tvEnjoyWatching)

        // Failsafe: Əgər animasiya ilişib qalsa, 3.5 saniyə sonra dashboard-u aç
        Handler(Looper.getMainLooper()).postDelayed({
            if (!isSplashFinished) {
                isSplashFinished = true
                val prefs = getSharedPreferences("neoplay_prefs", Context.MODE_PRIVATE)
                showDashboard(prefs.getBoolean("is_vod_enabled", true), prefs.getBoolean("is_series_enabled", true))
                checkPendingResponse()
            }
        }, 3500)

        slideUpText.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation) {}

            override fun onAnimationEnd(animation: Animation) {
                // Animasiyalar tam bitdi
                isSplashFinished = true
                
                // Dashboard-u dərhal göstər (auth bitməsə belə)
                val prefs = getSharedPreferences("neoplay_prefs", Context.MODE_PRIVATE)
                val isVod = prefs.getBoolean("is_vod_enabled", true)
                val isSeries = prefs.getBoolean("is_series_enabled", true)
                showDashboard(isVod, isSeries)
                
                checkPendingResponse()
            }

            override fun onAnimationRepeat(animation: Animation) {}
        })
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

        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
        }

        // Search düyməsi üçün Live TV-yə yönləndirmə
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
                    handleFailure("İnternet bağlantısı yoxdur və ya serverə qoşulmaq mümkün olmadı.\n\nXəta: ${t.localizedMessage ?: t.message ?: "Naməlum"}")
                }
            }
        })
    }

    private fun handleAuthResponse(response: ApiResponse) {
        // Auth cavabı gəldi, yükləmə ikonunu gizlət
        binding.pbDashboardLoading.visibility = View.GONE

        if (!isSplashFinished) {
            pendingAuthResponse = response
            return
        }

        val status = response.status ?: "error"
        val message = response.message ?: "M3U faylı tapılmadı! Zəhmət olmasa dilerinizlə əlaqə saxlayın."

        if ("success".equals(status, ignoreCase = true)) {
            val isTest = response.isTestMode
            val remaining = response.getTestRemainingSeconds()
            android.util.Log.d("MainActivity", "Auth Success. TestMode: $isTest, Remaining: $remaining")
            
            val m3uUrl = response.m3uUrl
            if (m3uUrl.isNullOrEmpty()) {
                showError("Xəta", message)
                return
            }

            val expiry = response.expiryDate

            val isVod = response.isVodEnabled
            val isSeries = response.isSeriesEnabled

            // Bütün məlumatları yadda saxla
            getSharedPreferences("neoplay_prefs", Context.MODE_PRIVATE)
                .edit()
                .putString("expiry_date", expiry)
                .putString("playlist_type", response.playlistType)
                .putString("m3u_url", m3uUrl)
                .putString("xtream_host", if (response.xtream != null) response.xtream.host else "")
                .putString("xtream_user", if (response.xtream != null) response.xtream.username else "")
                .putString("xtream_pass", if (response.xtream != null) response.xtream.password else "")
                .putBoolean("is_vod_enabled", isVod)
                .putBoolean("is_series_enabled", isSeries)
                .putBoolean("is_adult_enabled", response.isAdultEnabled)
                .putLong("test_expire_time", if (isTest) System.currentTimeMillis() + (remaining * 1000) else 0L)
                .apply()

            if (!expiry.isNullOrBlank() && !expiry.equals("null", ignoreCase = true)) {
                binding.tvExpiryInfo.text = "Abunəlik bitir: $expiry"
                binding.tvExpiryInfo.visibility = View.VISIBLE

                getSharedPreferences("neoplay_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .putString("expiry_date", expiry)
                    .apply()
            } else {
                binding.tvExpiryInfo.visibility = View.GONE
            }

            // Test Rejimi Yoxlaması
            if (isTest) {
                val remainingInt = remaining.toInt()
                val countdown = response.countdown ?: formatTime(remainingInt)
                showTestCountdown(remainingInt, countdown, response.warning, response.warningLevel)
                android.widget.Toast.makeText(this, "Test Rejimi Aktivdir ($remainingInt san.)", android.widget.Toast.LENGTH_LONG).show()
            } else {
                hideTestCountdown()
            }

            showDashboard(isVod, isSeries)
            loadAndCheckPlaylist()

            // Yenilənmə yoxlanışını və avtomatik başlatmanı idarə et
            handleAutoStart() 
        } else if ("expired".equals(status, ignoreCase = true)) {
            val expireDate = response.expiryDate ?: ""
            showError("Abunəlik Bitib", "⏳ Abunəlik bitib!\nTarix: $expireDate")
        } else if ("blocked".equals(status, ignoreCase = true)) {
            val blockMsg = response.message ?: "🚫 Cihaz bloklanıb!"
            showBlockedDialog(blockMsg)
        } else if ("not_found".equals(status, ignoreCase = true)) {
            showError("Aktiv Edilməyib", "❌ Bu MAC ünvanı aktiv edilməyib.")
        } else if ("app_global_closed".equals(status, ignoreCase = true)) {
            showError("Tətbiq Bağlanıb", message + "\n\n" + (response.detail ?: ""))
        } else if ("app_closed".equals(status, ignoreCase = true)) {
            showError("Giriş Bağlanıb", message + "\n\n" + (response.detail ?: ""))
        } else if ("app_expired".equals(status, ignoreCase = true)) {
            showError("Müddət Bitib", message + "\n\n" + (response.detail ?: ""))
        } else {
            showError("Xəta", message)
        }
    }

    private fun handleAutoStart() {
        val prefs = getSharedPreferences("neoplay_prefs", Context.MODE_PRIVATE)
        val autoStart = prefs.getBoolean("auto_start_last_channel", true)
        val lastChannelUrl = prefs.getString("last_channel_url", "")

        // Yalnız auto-start aktivdirsə VƏ əvvəllər kanala baxılıbsa başlat
        if (!autoStart || lastChannelUrl.isNullOrEmpty()) return

        val handler = Handler(Looper.getMainLooper())
        val checkInterval = 200L
        val maxWaitTime = 3000L
        var waitedTime = 0L

        val checkRunnable = object : Runnable {
            override fun run() {
                if (UpdateManager.isCheckFinished) {
                    // Yoxlanış bitdi, indi qərar verək
                    if (!UpdateManager.isUpdateFound) {
                        val intent = Intent(this@MainActivity, LiveTvActivity::class.java)
                        intent.putExtra("auto_start", true)
                        startActivity(intent)
                    } else {
                        // Yenilənmə var, avtomatik başlatmanı skip edirik ki, dialog görünsün
                    }
                } else if (waitedTime < maxWaitTime) {
                    waitedTime += checkInterval
                    handler.postDelayed(this, checkInterval)
                } else {
                    // Gözləmə müddəti bitdi (timeout), ehtiyat olaraq kanalı açırıq
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
        val prefs = getSharedPreferences("neoplay_prefs", Context.MODE_PRIVATE)
        val type = prefs.getString("playlist_type", "m3u")
        val m3uUrl = prefs.getString("m3u_url", "http://kanal65.xyz/by-kerimoff-player/playlist.m3u")
        val isVodEnabled = prefs.getBoolean("is_vod_enabled", true)
        val isSeriesEnabled = prefs.getBoolean("is_series_enabled", true)

        if ("xtream".equals(type, ignoreCase = true)) {
            runOnUiThread {
                binding.cardMovies.visibility = if (isVodEnabled) View.VISIBLE else View.GONE
                binding.cardSeries.visibility = if (isSeriesEnabled) View.VISIBLE else View.GONE

                var weightSum = 2.0f
                if (isVodEnabled) weightSum += 1.0f
                if (isSeriesEnabled) weightSum += 1.0f
                binding.cardsContainer.weightSum = weightSum
            }
            return
        }

        val executor = Executors.newSingleThreadExecutor()
        executor.execute {
            var hasVod = false
            try {
                val url = URL(m3uUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 10000
                connection.readTimeout = 10000

                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                var line: String?
                var count = 0
                while (reader.readLine().also { line = it } != null && count < 2000) {
                    val trimmedLine = line!!.trim()
                    if (!trimmedLine.startsWith("#") && trimmedLine.isNotEmpty()) {
                        if (M3UParser.isVodChannel(trimmedLine)) {
                            hasVod = true
                            break
                        }
                        count++
                    }
                }
                reader.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }

            val finalHasVod = hasVod
            runOnUiThread {
                val finalShowMovies = finalHasVod && isVodEnabled
                val finalShowSeries = finalHasVod && isSeriesEnabled

                binding.cardMovies.visibility = if (finalShowMovies) View.VISIBLE else View.GONE
                binding.cardSeries.visibility = if (finalShowSeries) View.VISIBLE else View.GONE

                var weightSum = 2.0f
                if (finalShowMovies) weightSum += 1.0f
                if (finalShowSeries) weightSum += 1.0f
                binding.cardsContainer.weightSum = weightSum
            }
        }
    }

    private fun showDashboard(isVodEnabled: Boolean, isSeriesEnabled: Boolean) {
        // Bütün maneələri dərhal təmizlə
        binding.loadingLayout.clearAnimation()
        binding.loadingLayout.visibility = View.GONE
        binding.loadingLayout.elevation = 0f
        
        if (binding.dashboardLayout.visibility == View.VISIBLE) {
            updateDashboardCards(isVodEnabled, isSeriesEnabled)
            return
        }

        // Premium Brendinq tətbiq et
        applyPremiumBranding(binding.tvAppTitle)

        val sdf = SimpleDateFormat("EEEE, d MMMM", Locale("az"))
        binding.tvDate.text = sdf.format(Date())

        updateDashboardCards(isVodEnabled, isSeriesEnabled)

        binding.dashboardLayout.visibility = View.VISIBLE
        binding.errorOverlay.visibility = View.GONE

        // Fokusun Canlı TV-yə verilməsi
        binding.cardLiveTv.requestFocus()

        loadWeather()
        loadResumeList()
        loadCurrencies()
    }

    private fun loadResumeList() {
        val list = ResumeManager.getResumeList(this)
        runOnUiThread {
            if (list.isNotEmpty()) {
                binding.resumeSection.visibility = View.VISIBLE
                val adapter = ResumeAdapter(list) { item ->
                    val channel = Channel(item.id, item.name, item.logoUrl, item.streamUrl, item.categoryName)
                    
                    val prefs = getSharedPreferences("neoplay_prefs", Context.MODE_PRIVATE)
                    if (prefs.getBoolean("use_external_player", false)) {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW)
                            intent.setDataAndType(android.net.Uri.parse(channel.getStreamUrl()), "video/*")
                            intent.putExtra("title", channel.name)
                            startActivity(Intent.createChooser(intent, "Pleyer seçin"))
                        } catch (e: Exception) {
                            Toast.makeText(this, "Xarici pleyer açılmadı", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        DataManager.setCurrentChannelList(listOf(channel))
                        val intent = Intent(this, PlayerActivity::class.java)
                        intent.putExtra("channel_index", 0)
                        intent.putExtra("resume_position", item.position)
                        startActivity(intent)
                    }
                }
                binding.rvResume.adapter = adapter
            } else {
                binding.resumeSection.visibility = View.GONE
            }
        }
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
        
        // Premium Metalik Qızılı Qradiyent
        val goldGradient = LinearGradient(
            0f, 0f, 0f, textView.textSize,
            intArrayOf(
                Color.parseColor("#FFE700"), // Light Gold
                Color.parseColor("#FFD700"), // Golden
                Color.parseColor("#B8860B"), // Dark Gold/Bronze
                Color.parseColor("#8B6508")  // Deep Bronze
            ),
            floatArrayOf(0f, 0.4f, 0.7f, 1f),
            Shader.TileMode.CLAMP
        )
        
        textView.paint.shader = goldGradient
        
        // Kölgə və Parıltı
        textView.setShadowLayer(15f, 0f, 0f, Color.parseColor("#80FFD700"))
        
        textView.invalidate()
    }

    private fun updateDashboardCards(isVodEnabled: Boolean, isSeriesEnabled: Boolean) {
        binding.cardMovies.visibility = if (isVodEnabled) View.VISIBLE else View.GONE
        binding.cardSeries.visibility = if (isSeriesEnabled) View.VISIBLE else View.GONE

        var weightSum = 2.0f
        if (isVodEnabled) weightSum += 1.0f
        if (isSeriesEnabled) weightSum += 1.0f
        binding.cardsContainer.weightSum = weightSum
    }

    private fun loadWeather() {
        com.bykerimoff.player.utils.WeatherManager.fetchWeather(object : com.bykerimoff.player.utils.WeatherManager.WeatherCallback {
            override fun onSuccess(temp: String, weatherCode: Int) {
                binding.weatherLayout.visibility = View.VISIBLE
                binding.tvTemperature.text = temp
                binding.tvWeatherEmoji.text = com.bykerimoff.player.utils.WeatherManager.getWeatherEmoji(weatherCode)
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
            val builder = android.app.AlertDialog.Builder(this)
            builder.setTitle("⛔ Cihaz Bloklanıb")
            builder.setMessage("$message\n\nZəhmət olmasa dilerinizlə əlaqə saxlayın.")
            builder.setCancelable(false)
            builder.setPositiveButton("Bağla") { _, _ ->
                finish() // Proqramı bağla
            }

            builder.setIcon(android.R.drawable.ic_dialog_alert)

            val dialog = builder.create()
            dialog.show()

            // Mesaj rəngini qırmızı et
            val msgView = dialog.findViewById<android.widget.TextView>(android.R.id.message)
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
        
        testCountDownTimer = object : android.os.CountDownTimer(seconds.toLong() * 1000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val remaining = (millisUntilFinished / 1000).toInt()
                val timeText = formatTime(remaining)
                binding.testCountdown.text = "Test: $timeText"
                
                val color: Int
                if (remaining < 60) {
                    color = Color.RED
                } else if (remaining < 300) {
                    color = Color.parseColor("#FFA500") // Orange
                } else {
                    color = Color.parseColor("#D4AF37") // Gold
                }
                
                binding.testCountdown.setTextColor(color)
                binding.testTitle.setTextColor(color)

                // 5 dəqiqədən az qaldıqda marqatla
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
                
                android.app.AlertDialog.Builder(this@MainActivity)
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
}
