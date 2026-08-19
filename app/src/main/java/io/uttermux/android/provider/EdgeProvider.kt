package io.uttermux.android.provider

import android.content.Context
import android.text.TextUtils
import io.uttermux.android.audio.CompressedAudioDecoder
import io.uttermux.android.config.*
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONArray
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class EdgeProvider(private val context: Context) : TtsProvider {
    override val kind=ProviderKind.EDGE
    @Volatile private var catalog = listOf(
        VoiceRecord("edge/en-US-AriaNeural@en-US", "Aria · Edge", Locale.US, ProviderKind.EDGE, "Edge", setOf("en-US"), true),
        VoiceRecord("edge/fr-FR-DeniseNeural@fr-FR", "Denise · Edge", Locale.FRANCE, ProviderKind.EDGE, "Edge", setOf("fr-FR"), true),
    )
    override val voices get() = catalog
    override val availableVoices get()=voices
    fun refresh() {
        val url = "https://speech.platform.bing.com/consumer/speech/synthesize/readaloud/voices/list?trustedclienttoken=$TOKEN"
        val array = JSONArray(String(HttpAudio.get(url, mapOf("User-Agent" to USER_AGENT)), Charsets.UTF_8))
        val found = (0 until array.length()).map { index ->
            val item = array.getJSONObject(index); val locale = item.getString("Locale").replace('_', '-')
            VoiceRecord("edge/${item.getString("ShortName")}@$locale", "${item.optString("FriendlyName", item.getString("ShortName"))} · Edge",
                Locale.forLanguageTag(locale), ProviderKind.EDGE, "Edge Neural", setOf(locale), true, item.optString("Gender"))
        }
        if (found.isNotEmpty()) catalog = found
    }
    override fun synthesize(voice: VoiceRecord, text: String, language: String, speed: Float, cancelled: AtomicBoolean): AudioData {
        return synthesizeWithPitch(voice,text,language,speed,1f,cancelled)
    }
    override fun stream(voice:VoiceRecord,text:String,language:String,speed:Float,pitch:Float,cancelled:AtomicBoolean,emit:(AudioData)->Boolean) {
        // Pitch is applied uniformly to emitted PCM by the Android service.
        emit(synthesizeWithPitch(voice,text,language,speed,1f,cancelled))
    }
    private fun synthesizeWithPitch(voice: VoiceRecord, text: String, language: String, speed: Float, pitch:Float, cancelled: AtomicBoolean): AudioData {
        val connectionId = UUID.randomUUID().toString().replace("-", "")
        val url = "$WSS&ConnectionId=$connectionId&Sec-MS-GEC=${gec()}&Sec-MS-GEC-Version=1-143.0.3650.75"
        val done = CountDownLatch(1); val audio = ByteArrayOutputStream(); val failure = AtomicReference<Throwable?>()
        val request = Request.Builder().url(url)
            .header("User-Agent", USER_AGENT).header("Origin", "chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold")
            .header("Pragma", "no-cache").header("Cache-Control", "no-cache")
            .header("Cookie", "muid=${UUID.randomUUID().toString().replace("-", "").uppercase()};").build()
        val socket = HttpAudio.client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                val timestamp = timestamp()
                ws.send("X-Timestamp:$timestamp\r\nContent-Type:application/json; charset=utf-8\r\nPath:speech.config\r\n\r\n" +
                    "{\"context\":{\"synthesis\":{\"audio\":{\"metadataoptions\":{\"sentenceBoundaryEnabled\":\"false\",\"wordBoundaryEnabled\":\"false\"},\"outputFormat\":\"audio-24khz-48kbitrate-mono-mp3\"}}}}\r\n")
                val rate = ((speed.coerceIn(.5f, 2f) - 1f) * 100).toInt().let { if (it >= 0) "+$it%" else "$it%" }
                val pitchPercent=((pitch.coerceIn(.5f,2f)-1f)*100).toInt().let{if(it>=0)"+$it%" else "$it%"}
                val name = voice.id.substringAfter('/').substringBefore('@')
                val ssml = "<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='$language'><voice name='$name'><prosody pitch='$pitchPercent' rate='$rate' volume='+0%'>${TextUtils.htmlEncode(text)}</prosody></voice></speak>"
                ws.send("X-RequestId:${UUID.randomUUID().toString().replace("-", "")}\r\nContent-Type:application/ssml+xml\r\nX-Timestamp:${timestamp}Z\r\nPath:ssml\r\n\r\n$ssml")
            }
            override fun onMessage(ws: WebSocket, bytes: ByteString) {
                val data = bytes.toByteArray(); if (data.size < 2) return
                val header = ((data[0].toInt() and 255) shl 8) or (data[1].toInt() and 255)
                if (header + 2 <= data.size) audio.write(data, header + 2, data.size - header - 2)
            }
            override fun onMessage(ws: WebSocket, text: String) { if (text.substringBefore("\r\n\r\n").contains("Path:turn.end")) { done.countDown(); ws.close(1000, null) } }
            override fun onFailure(ws: WebSocket, error: Throwable, response: Response?) { failure.set(error); done.countDown() }
        })
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60)
        while (!done.await(100, TimeUnit.MILLISECONDS)) {
            if (cancelled.get()) { socket.cancel(); throw InterruptedException() }
            if (System.nanoTime() > deadline) { socket.cancel(); error("Edge synthesis timed out") }
        }
        failure.get()?.let { throw it }
        require(audio.size() > 0) { "Edge returned no audio" }
        return CompressedAudioDecoder.mp3(context, audio.toByteArray())
    }

    private fun timestamp() = SimpleDateFormat("EEE MMM dd yyyy HH:mm:ss 'GMT+0000 (Coordinated Universal Time)'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date())
    private fun gec(): String {
        val seconds = System.currentTimeMillis() / 1000L
        val ticks = (seconds + 11644473600L - (seconds + 11644473600L) % 300L) * 10_000_000L
        return MessageDigest.getInstance("SHA-256").digest("$ticks$TOKEN".toByteArray()).joinToString("") { "%02X".format(it) }
    }
    companion object {
        private const val TOKEN = "6A5AA1D4EAFF4E9FB37E23D68491D6F4"
        private const val WSS = "wss://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1?TrustedClientToken=$TOKEN"
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36 Edg/143.0.0.0"
    }
}
