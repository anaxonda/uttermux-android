package io.uttermux.android.provider

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object HttpAudio {
    val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()
    val json = "application/json".toMediaType()
    fun post(url: String, body: JSONObject, headers: Map<String, String>): ByteArray {
        val builder = Request.Builder().url(url).post(body.toString().toRequestBody(json))
        headers.forEach(builder::header)
        client.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) throw ProviderException(response.code, response.body.string().take(1000))
            return response.body.bytes()
        }
    }
}

class ProviderException(val status: Int, detail: String) : RuntimeException("Provider HTTP $status: $detail")
