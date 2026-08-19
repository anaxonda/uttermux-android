package io.uttermux.android.provider

import io.uttermux.android.config.*
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class ElevenLabsProvider(private val secure: SecureStore) : TtsProvider {
    override val kind=ProviderKind.ELEVENLABS
    @Volatile private var keyAvailable=false
    @Volatile private var keyCheckedAt=0L
    private fun configured():Boolean {
        val now=android.os.SystemClock.elapsedRealtime()
        if(now-keyCheckedAt>1000)synchronized(this){if(now-keyCheckedAt>1000){keyAvailable=secure.get("elevenlabs").isNotBlank();keyCheckedAt=now}}
        return keyAvailable
    }
    private val languages = setOf("ar","bg","cs","da","de","el","en","es","fi","fil","fr","hi","hr","hu","id","it","ja","ko","ms","nl","no","pl","pt","ro","ru","sk","sv","ta","tr","uk","vi","zh")
    @Volatile private var catalog = listOf(VoiceRecord("elevenlabs/pqHfZKP75CvOlQylNhV4@en-US", "Bill · ElevenLabs", Locale.US, ProviderKind.ELEVENLABS, "eleven_flash_v2_5", languages, true))
    override val voices get() = catalog
    override fun isAvailable(voice: VoiceRecord) = configured()
    fun refresh() {
        val key = secure.get("elevenlabs"); if (key.isBlank()) return
        val found = mutableListOf<VoiceRecord>(); var token: String? = null
        do {
            val suffix = token?.let { "&next_page_token=${java.net.URLEncoder.encode(it, "UTF-8")}" } ?: ""
            val json = JSONObject(String(HttpAudio.get("https://api.elevenlabs.io/v2/voices?page_size=100&sort=name&sort_direction=asc$suffix", mapOf("xi-api-key" to key)), Charsets.UTF_8))
            val array = json.getJSONArray("voices")
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i); val labels = item.optJSONObject("labels") ?: JSONObject()
                val details = listOf(labels.optString("accent"), labels.optString("gender"), item.optString("description")).filter(String::isNotBlank).joinToString(" · ")
                found += VoiceRecord("elevenlabs/${item.getString("voice_id")}@en-US", "${item.optString("name", "Voice")} · ElevenLabs", Locale.US,
                    ProviderKind.ELEVENLABS, "eleven_flash_v2_5", languages, true, details, item.optString("preview_url"))
            }
            token = json.optString("next_page_token").takeIf { json.optBoolean("has_more") && it.isNotBlank() }
        } while (token != null)
        if (found.isNotEmpty()) catalog = found
    }
    override fun synthesize(voice: VoiceRecord, text: String, language: String, speed: Float, cancelled: AtomicBoolean): AudioData {
        val key = secure.get("elevenlabs"); require(key.isNotBlank()) { "ElevenLabs API key is not configured" }
        val external = voice.id.substringAfter('/').substringBefore('@')
        val body = JSONObject().put("text", text).put("model_id", "eleven_flash_v2_5")
            .put("language_code", Languages.normalized(language).substringBefore('-'))
            .put("voice_settings", JSONObject().put("speed", speed.coerceIn(.7f, 1.2f)))
        if (cancelled.get()) throw InterruptedException()
        return AudioData(24000, HttpAudio.post("https://api.elevenlabs.io/v1/text-to-speech/$external/stream?output_format=pcm_24000", body,
            mapOf("xi-api-key" to key, "Accept" to "audio/pcm"), cancelled))
    }
    override fun stream(voice:VoiceRecord,text:String,language:String,speed:Float,pitch:Float,cancelled:AtomicBoolean,emit:(AudioData)->Boolean) {
        val key=secure.get("elevenlabs");require(key.isNotBlank()){ "ElevenLabs API key is not configured" }
        val external=voice.id.substringAfter('/').substringBefore('@')
        val body=JSONObject().put("text",text).put("model_id","eleven_flash_v2_5")
            .put("language_code",Languages.normalized(language).substringBefore('-'))
            .put("voice_settings",JSONObject().put("speed",speed.coerceIn(.7f,1.2f)))
        HttpAudio.postStream("https://api.elevenlabs.io/v1/text-to-speech/$external/stream?output_format=pcm_24000",body,
            mapOf("xi-api-key" to key,"Accept" to "audio/pcm"),cancelled){emit(AudioData(24000,it))}
    }
}
