package com.bykerimoff.player

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bykerimoff.player.databinding.ActivityYoutubeBinding
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder

class YoutubeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityYoutubeBinding
    private val client = OkHttpClient()
    private val youtubeList = mutableListOf<YoutubeVideo>()
    private val TAG = "YoutubeActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityYoutubeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnRefresh.setOnClickListener { searchYoutubeLive() }
        binding.rvYoutube.layoutManager = LinearLayoutManager(this)
        
        searchYoutubeLive()
    }

    private fun searchYoutubeLive() {
        binding.pbLoading.visibility = View.VISIBLE
        
        val queries = listOf("türk dizileri canlı yayın", "türk filmleri canlı", "canlı tv izle", "haber canlı")
        val randomQuery = queries.random()
        
        try {
            val encodedQuery = URLEncoder.encode(randomQuery, "UTF-8")
            // sp=EgJAAQ%3D%3D -> Canlı yayım filtri
            val url = "https://www.youtube.com/results?search_query=$encodedQuery&sp=EgJAAQ%3D%3D&hl=tr&gl=TR"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Accept-Language", "tr-TR,tr;q=0.9,en-US;q=0.8,en;q=0.7")
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    runOnUiThread {
                        binding.pbLoading.visibility = View.GONE
                        Toast.makeText(this@YoutubeActivity, "Bağlantı xətası", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    val html = response.body?.string() ?: ""
                    parseYoutubeHtml(html)
                }
            })
        } catch (e: Exception) {
            runOnUiThread { binding.pbLoading.visibility = View.GONE }
        }
    }

    private fun parseYoutubeHtml(html: String) {
        try {
            // YouTube-un fərqli JSON başlıqlarını yoxlayırıq
            val patterns = listOf("var ytInitialData = ", "window[\"ytInitialData\"] = ", "ytInitialData = ")
            var jsonStr: String? = null
            
            for (pattern in patterns) {
                val startIndex = html.indexOf(pattern)
                if (startIndex != -1) {
                    val jsonStart = startIndex + pattern.length
                    // JSON obyektinin sonunu tapmaq üçün (bəzən ; ilə, bəzən pərdə arxasında bitir)
                    var jsonEnd = html.indexOf(";</script>", jsonStart)
                    if (jsonEnd == -1) jsonEnd = html.indexOf("};", jsonStart) + 1
                    
                    if (jsonEnd != -1 && jsonEnd > jsonStart) {
                        jsonStr = html.substring(jsonStart, jsonEnd).trim()
                        if (jsonStr.endsWith(";")) jsonStr = jsonStr.substring(0, jsonStr.length - 1)
                        break
                    }
                }
            }

            if (jsonStr == null) {
                runOnUiThread {
                    binding.pbLoading.visibility = View.GONE
                    Toast.makeText(this@YoutubeActivity, "YouTube cavab vermədi", Toast.LENGTH_SHORT).show()
                }
                return
            }

            val json = JSONObject(jsonStr)
            youtubeList.clear()
            findVideosRecursively(json)

            runOnUiThread {
                binding.pbLoading.visibility = View.GONE
                binding.rvYoutube.adapter = YoutubeAdapter(youtubeList)
                if (youtubeList.isEmpty()) {
                    Toast.makeText(this@YoutubeActivity, "Canlı yayım tapılmadı, təkrar cəhd edin", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Parsing error", e)
            runOnUiThread { 
                binding.pbLoading.visibility = View.GONE
                Toast.makeText(this@YoutubeActivity, "Məlumat oxunmadı", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun findVideosRecursively(obj: Any) {
        when (obj) {
            is JSONObject -> {
                if (obj.has("videoRenderer")) {
                    val videoRenderer = obj.getJSONObject("videoRenderer")
                    try {
                        val videoId = videoRenderer.getString("videoId")
                        val title = videoRenderer.getJSONObject("title").getJSONArray("runs").getJSONObject(0).getString("text")
                        val channel = videoRenderer.getJSONObject("longBylineText").getJSONArray("runs").getJSONObject(0).getString("text")
                        val thumbnail = videoRenderer.getJSONObject("thumbnail").getJSONArray("thumbnails").getJSONObject(0).getString("url")
                        
                        if (youtubeList.none { it.id == videoId }) {
                            youtubeList.add(YoutubeVideo(videoId, title, channel, thumbnail))
                        }
                    } catch (e: Exception) {}
                } else {
                    val keys = obj.keys()
                    while (keys.hasNext()) {
                        findVideosRecursively(obj.get(keys.next()))
                    }
                }
            }
            is JSONArray -> {
                for (i in 0 until obj.length()) {
                    findVideosRecursively(obj.get(i))
                }
            }
        }
    }

    data class YoutubeVideo(val id: String, val title: String, val channel: String, val thumbnail: String)

    inner class YoutubeAdapter(private val videos: List<YoutubeVideo>) : RecyclerView.Adapter<YoutubeAdapter.ViewHolder>() {
        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val ivThumbnail: ImageView = view.findViewById(R.id.ivThumbnail)
            val tvTitle: TextView = view.findViewById(R.id.tvTitle)
            val tvChannel: TextView = view.findViewById(R.id.tvChannel)
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_youtube, parent, false))
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val video = videos[position]
            holder.tvTitle.text = video.title
            holder.tvChannel.text = video.channel
            Glide.with(this@YoutubeActivity).load(video.thumbnail).into(holder.ivThumbnail)
            holder.itemView.setOnClickListener {
                val intent = Intent(this@YoutubeActivity, YoutubePlayerActivity::class.java)
                intent.putExtra("video_id", video.id)
                startActivity(intent)
            }
        }
        override fun getItemCount() = videos.size
    }
}
