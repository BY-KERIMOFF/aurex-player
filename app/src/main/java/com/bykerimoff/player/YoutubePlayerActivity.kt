package com.bykerimoff.player

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.webkit.*
import androidx.appcompat.app.AppCompatActivity
import com.bykerimoff.player.databinding.ActivityYoutubePlayerBinding

class YoutubePlayerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityYoutubePlayerBinding

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityYoutubePlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val videoId = intent.getStringExtra("video_id") ?: return

        setupWebView(videoId)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView(videoId: String) {
        val webView = binding.webViewYoutube
        
        // Masaüstü User-Agent (YouTube-u aldatmaq üçün ən vacib hissə)
        val desktopUA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        
        webView.settings.javaScriptEnabled = true
        webView.settings.mediaPlaybackRequiresUserGesture = false
        webView.settings.userAgentString = desktopUA
        webView.settings.domStorageEnabled = true
        webView.settings.useWideViewPort = true
        webView.settings.loadWithOverviewMode = true
        
        // Hardware acceleration
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (newProgress == 100) {
                    binding.pbPlayerLoading.visibility = View.GONE
                }
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                return false // Linklərin içəridə açılmasını təmin et
            }
        }

        // YouTube IFrame HTML kodu
        val html = """
            <!DOCTYPE html>
            <html>
            <body style="margin:0;padding:0;background:black;overflow:hidden;">
                <div id="player"></div>
                <script src="https://www.youtube.com/iframe_api"></script>
                <script>
                    var player;
                    function onYouTubeIframeAPIReady() {
                        player = new YT.Player('player', {
                            height: '100%',
                            width: '100%',
                            videoId: '$videoId',
                            playerVars: {
                                'autoplay': 1,
                                'controls': 1,
                                'rel': 0,
                                'showinfo': 0,
                                'ecver': 2,
                                'origin': 'https://www.youtube.com'
                            },
                            events: {
                                'onReady': onPlayerReady
                            }
                        });
                    }
                    function onPlayerReady(event) {
                        event.target.playVideo();
                    }
                </script>
            </body>
            </html>
        """.trimIndent()

        // HTML-i yükləyirik, Referer olaraq youtube.com göstəririk
        webView.loadDataWithBaseURL("https://www.youtube.com", html, "text/html", "UTF-8", null)
    }

    override fun onDestroy() {
        binding.webViewYoutube.destroy()
        super.onDestroy()
    }

    override fun onBackPressed() {
        if (binding.webViewYoutube.canGoBack()) {
            binding.webViewYoutube.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
