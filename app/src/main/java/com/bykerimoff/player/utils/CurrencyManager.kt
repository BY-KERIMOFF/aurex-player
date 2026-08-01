package com.bykerimoff.player.utils

import android.util.Log
import com.bykerimoff.player.models.CurrencyItem
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import androidx.media3.common.util.UnstableApi
import java.io.IOException
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.*

@UnstableApi
object CurrencyManager {
    private const val TAG = "CurrencyManager"

    fun fetchCurrencies(callback: (List<CurrencyItem>) -> Unit) {
        val sdf = SimpleDateFormat("dd.MM.yyyy", Locale.US)
        val today = sdf.format(Date())
        val url = "https://www.cbar.az/currencies/$today.xml"

        val client = NetworkUtils.getSafeOkHttpClient()
        val request = okhttp3.Request.Builder()
            .url(url)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Failed to fetch currencies: ${e.message}")
                callback(emptyList())
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val body = response.body?.string()
                    if (!body.isNullOrEmpty()) {
                        val items = parseCbarXml(body)
                        callback(items)
                    } else {
                        callback(emptyList())
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Parsing error: ${e.message}")
                    callback(emptyList())
                }
            }
        })
    }

    private fun parseCbarXml(xml: String): List<CurrencyItem> {
        val items = mutableListOf<CurrencyItem>()
        val factory = XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()
        parser.setInput(StringReader(xml))

        var eventType = parser.eventType
        var currentCode = ""
        var currentName = ""
        var currentNominal = ""
        var currentValue = ""
        
        // Göstərmək istədiyimiz əsas valyutalar
        val targetCodes = listOf("USD", "EUR", "RUB", "TRY", "GBP", "GEL")

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    if (parser.name == "Valute") {
                        currentCode = parser.getAttributeValue(null, "Code")
                    } else if (parser.name == "Nominal") {
                        currentNominal = parser.nextText()
                    } else if (parser.name == "Name") {
                        currentName = parser.nextText()
                    } else if (parser.name == "Value") {
                        currentValue = parser.nextText()
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "Valute") {
                        if (targetCodes.contains(currentCode)) {
                            items.add(CurrencyItem(currentCode, currentName, currentNominal, currentValue))
                        }
                    }
                }
            }
            eventType = parser.next()
        }
        return items
    }
}
