package com.bykerimoff.player

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bykerimoff.player.databinding.ActivitySpeedTestBinding
import com.bykerimoff.player.utils.SpeedTestManager
import com.bykerimoff.player.utils.WallpaperManager
import kotlinx.coroutines.launch
import java.util.Locale

class SpeedTestActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySpeedTestBinding
    private var isTesting = false
    private var maxMbps = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySpeedTestBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WallpaperManager.applyWallpaper(this, binding.ivBackground)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnStartTest.setOnClickListener {
            if (!isTesting) startTest()
        }

        binding.btnStartTest.requestFocus()
    }

    private fun startTest() {
        isTesting = true
        binding.btnStartTest.isEnabled = false
        binding.btnStartTest.text = "TEST GEDİR..."
        binding.tvRecommendation.text = "Şəbəkə yoxlanılır..."
        
        resetUi()

        lifecycleScope.launch {
            // 1. Ping Test
            val ping = SpeedTestManager.measurePing()
            runOnUiThread {
                binding.tvPing.text = if (ping > 0) "$ping ms" else "Xəta"
            }

            // 2. Download Test
            val result = SpeedTestManager.measureDownloadSpeed { currentMbps ->
                updateSpeedUi(currentMbps)
            }

            isTesting = false
            binding.btnStartTest.isEnabled = true
            binding.btnStartTest.text = "YENİDƏN BAŞLAT"
            
            showFinalResults(result)
        }
    }

    private fun resetUi() {
        maxMbps = 0.0
        binding.pbSpeedGauge.progress = 0
        binding.tvCurrentSpeed.text = "0.0"
        binding.tvPing.text = "-- ms"
        binding.tvMaxSpeed.text = "-- Mbps"
        
        val gray = Color.parseColor("#80FFFFFF")
        binding.tvQualitySD.setTextColor(gray)
        binding.tvQualityHD.setTextColor(gray)
        binding.tvQualityFHD.setTextColor(gray)
        binding.tvQuality4K.setTextColor(gray)
    }

    private fun updateSpeedUi(mbps: Double) {
        if (mbps > maxMbps) {
            maxMbps = mbps
            binding.tvMaxSpeed.text = String.format(Locale.US, "%.1f Mbps", maxMbps)
        }
        
        binding.tvCurrentSpeed.text = String.format(Locale.US, "%.1f", mbps)
        
        // Gauge max is 100 Mbps for visual representation
        val progress = (mbps).toInt().coerceAtMost(100)
        binding.pbSpeedGauge.progress = progress

        updateQualityIndicators(mbps)
    }

    private fun updateQualityIndicators(mbps: Double) {
        val gold = Color.parseColor("#FFD700")
        val gray = Color.parseColor("#80FFFFFF")

        binding.tvQualitySD.setTextColor(if (mbps >= 2.0) gold else gray)
        binding.tvQualityHD.setTextColor(if (mbps >= 5.0) gold else gray)
        binding.tvQualityFHD.setTextColor(if (mbps >= 10.0) gold else gray)
        binding.tvQuality4K.setTextColor(if (mbps >= 25.0) gold else gray)
    }

    private fun showFinalResults(finalMbps: Double) {
        updateSpeedUi(finalMbps)
        
        val recommendation = when {
            finalMbps >= 25.0 -> "Mükəmməl! 4K yayım üçün tam uyğundur. ✅"
            finalMbps >= 10.0 -> "Çox yaxşı! FHD kanalları rahat izləyə bilərsiniz. ✅"
            finalMbps >= 5.0 -> "Yaxşı. HD yayım üçün kifayətdir. ⚠️"
            finalMbps >= 2.0 -> "Zəif. Yalnız SD kanallarda stabil ola bilər. ⚠️"
            else -> "Çox zəif internet! Donmalar qaçılmazdır. ❌"
        }
        
        binding.tvRecommendation.text = recommendation
        binding.tvRecommendation.setTextColor(if (finalMbps >= 5.0) Color.WHITE else Color.RED)
        
        Toast.makeText(this, "Test başa çatdı", Toast.LENGTH_SHORT).show()
    }
}
