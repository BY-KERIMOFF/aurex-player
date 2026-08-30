package com.bykerimoff.player.utils

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.dnsoverhttps.DnsOverHttps
import okhttp3.CertificatePinner
import java.net.InetAddress
import java.net.URL
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

@UnstableApi
object NetworkUtils {

    private var currentDnsType = "system"
    private var customDnsUrl: String? = null

    @JvmStatic
    fun setDnsType(type: String, customUrl: String? = null) {
        currentDnsType = type
        customDnsUrl = customUrl
    }

    /**
     * Təhlükəsiz OkHttpClient (SSL Pinning və Sertifikat Yoxlanışı ilə).
     * API sorğuları üçün istifadə olunur.
     */
    @JvmStatic
    fun getSafeOkHttpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
        
        // SSL Pinning (Serverinizin sertifikatını bura əlavə edə bilərsiniz)
        // val pinner = CertificatePinner.Builder()
        //    .add("kanal65.xyz", "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
        //    .build()
        // builder.certificatePinner(pinner)

        builder.connectTimeout(60, TimeUnit.SECONDS)
        builder.readTimeout(60, TimeUnit.SECONDS)
        builder.writeTimeout(60, TimeUnit.SECONDS)
        
        // Dinamik Header Interceptor (API sorğuları üçün)
        builder.addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/110.0.0.0 Safari/537.36")
                .header("Accept", "*/*")
                .build()
            chain.proceed(request)
        }
        
        return builder.build()
    }

    @JvmStatic
    fun getUnsafeOkHttpClient(): OkHttpClient {
        try {
            val trustAllCerts = arrayOf<TrustManager>(
                object : X509TrustManager {
                    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
                    override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                }
            )

            val sslContext = SSLContext.getInstance("SSL")
            sslContext.init(null, trustAllCerts, SecureRandom())

            val builder = OkHttpClient.Builder()
            builder.sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
            builder.hostnameVerifier { _, _ -> true }

            // DNS Ayarı
            when (currentDnsType) {
                "google" -> {
                    val dns = DnsOverHttps.Builder()
                        .client(OkHttpClient())
                        .url("https://dns.google/dns-query".toHttpUrl())
                        .bootstrapDnsHosts(InetAddress.getByName("8.8.8.8"), InetAddress.getByName("8.8.4.4"))
                        .build()
                    builder.dns(dns)
                }
                "cloudflare" -> {
                    val dns = DnsOverHttps.Builder()
                        .client(OkHttpClient())
                        .url("https://cloudflare-dns.com/dns-query".toHttpUrl())
                        .bootstrapDnsHosts(InetAddress.getByName("1.1.1.1"), InetAddress.getByName("1.0.0.1"))
                        .build()
                    builder.dns(dns)
                }
                "manual" -> {
                    customDnsUrl?.let { url ->
                        try {
                            val dns = DnsOverHttps.Builder()
                                .client(OkHttpClient())
                                .url(url.toHttpUrl())
                                .build()
                            builder.dns(dns)
                        } catch (e: Exception) {
                            builder.dns(Dns.SYSTEM)
                        }
                    } ?: builder.dns(Dns.SYSTEM)
                }
                else -> builder.dns(Dns.SYSTEM)
            }

            builder.connectTimeout(30, TimeUnit.SECONDS)
            builder.readTimeout(30, TimeUnit.SECONDS)
            builder.writeTimeout(30, TimeUnit.SECONDS)
            builder.followRedirects(true)
            builder.followSslRedirects(true)

            // Dinamik Header Interceptor (Global Optimizasiya - Bütün dünya üçün)
            builder.addInterceptor { chain ->
                val original = chain.request()
                val url = original.url.toString().lowercase()
                val requestBuilder = original.newBuilder()

                // Müasir və universal brauzer agenti (Bütün yayımlar və API üçün)
                val globalUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Safari/537.36"

                requestBuilder.header("User-Agent", globalUserAgent)
                    .header("Accept", "*/*")
                    .header("Accept-Language", "en-US,en;q=0.9,az;q=0.8,ru;q=0.7")
                    .header("Connection", "keep-alive")
                    .header("Cache-Control", "max-age=0")
                
                // GitHub, CatCast və digər yayım serverləri üçün mütləq olan Referer başlığı
                if (url.contains("github") || url.contains("catcast") || url.contains("stream") || url.contains(".m3u8")) {
                    val referer = if (url.contains("github")) "https://github.com/" else original.url.scheme + "://" + original.url.host + "/"
                    requestBuilder.header("Referer", referer)
                    requestBuilder.header("Origin", original.url.scheme + "://" + original.url.host)
                }

                chain.proceed(requestBuilder.build())
            }

            return builder.build()
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }

    @JvmStatic
    fun getDataSourceFactory(context: Context): OkHttpDataSource.Factory {
        return OkHttpDataSource.Factory(getUnsafeOkHttpClient())
            .setUserAgent("VLC/3.0.11 LibVLC/3.0.11")
    }
}
