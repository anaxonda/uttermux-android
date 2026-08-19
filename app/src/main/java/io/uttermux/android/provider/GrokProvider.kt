package io.uttermux.android.provider

import io.uttermux.android.config.*
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class GrokProvider(private val secure: SecureStore) : TtsProvider {
    override val kind=ProviderKind.GROK
    @Volatile private var keyAvailable=false
    @Volatile private var keyCheckedAt=0L
    private fun configured():Boolean {
        val now=android.os.SystemClock.elapsedRealtime()
        if(now-keyCheckedAt>1000)synchronized(this){if(now-keyCheckedAt>1000){keyAvailable=secure.get("grok").isNotBlank();keyCheckedAt=now}}
        return keyAvailable
    }
    private val ids = listOf("altair","ara","atlas","aurora","carina","castor","celeste","cosmo","eve","helios","helix","iris","kepler","leo","liora","lumen","luna","lux","naksh","orion","perseus","rex","rigel","sal","sirius","ursa","zagan","zenith")
    override val voices = ids.map { id -> VoiceRecord("grok/$id@en-US", "${id.replaceFirstChar(Char::uppercase)} · Grok", Locale.US, ProviderKind.GROK, "xAI TTS", Languages.grok, true) }
    override fun isAvailable(voice: VoiceRecord) = configured()
    override val availableVoices get()=if(configured())voices else emptyList()
    override fun synthesize(voice: VoiceRecord, text: String, language: String, speed: Float, cancelled: AtomicBoolean): AudioData {
        val key = secure.get("grok"); require(key.isNotBlank()) { "Grok API key is not configured" }
        val body = JSONObject().put("text", text).put("voice_id", voice.id.substringAfter('/').substringBefore('@'))
            .put("language", "auto").put("speed", speed.coerceIn(.7f, 1.5f)).put("text_normalization", true)
            .put("output_format", JSONObject().put("codec", "pcm").put("sample_rate", 24000))
        if (cancelled.get()) throw InterruptedException()
        return AudioData(24000, HttpAudio.post("https://api.x.ai/v1/tts", body, mapOf("Authorization" to "Bearer $key", "Accept" to "audio/pcm"), cancelled))
    }
    override fun stream(voice:VoiceRecord,text:String,language:String,speed:Float,pitch:Float,cancelled:AtomicBoolean,emit:(AudioData)->Boolean) {
        val key=secure.get("grok");require(key.isNotBlank()){ "Grok API key is not configured" }
        val body=JSONObject().put("text",text).put("voice_id",voice.id.substringAfter('/').substringBefore('@'))
            .put("language","auto").put("speed",speed.coerceIn(.7f,1.5f)).put("text_normalization",true)
            .put("output_format",JSONObject().put("codec","pcm").put("sample_rate",24000))
        HttpAudio.postStream("https://api.x.ai/v1/tts",body,mapOf("Authorization" to "Bearer $key","Accept" to "audio/pcm"),cancelled){emit(AudioData(24000,it))}
    }
}
