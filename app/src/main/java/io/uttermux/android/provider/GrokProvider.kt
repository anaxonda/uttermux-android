package io.uttermux.android.provider

import io.uttermux.android.config.*
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class GrokProvider(private val secure: SecureStore) : TtsProvider {
    override val id=ProviderIds.GROK
    override val descriptor=ProviderDescriptor(id,"xAI / Grok",credentialFields=listOf(CredentialField("grok","API key")))
    @Volatile private var keyAvailable=false
    @Volatile private var keyCheckedAt=0L
    private fun configured():Boolean {
        val now=android.os.SystemClock.elapsedRealtime()
        if(now-keyCheckedAt>1000)synchronized(this){if(now-keyCheckedAt>1000){keyAvailable=secure.get("grok").isNotBlank();keyCheckedAt=now}}
        return keyAvailable
    }
    private val ids = listOf("altair","ara","atlas","aurora","carina","castor","celeste","cosmo","eve","helios","helix","iris","kepler","leo","liora","lumen","luna","lux","naksh","orion","perseus","rex","rigel","sal","sirius","ursa","zagan","zenith")
    @Volatile private var catalog = ids.map { voiceId -> VoiceRecord("grok/$voiceId@en-US", "${voiceId.replaceFirstChar(Char::uppercase)} · Grok", Locale.US, ProviderIds.GROK, "xAI TTS", Languages.grok, true) }
    override val voices get()=catalog
    override fun isAvailable(voice: VoiceRecord) = configured()
    override val availableVoices get()=if(configured())voices else emptyList()
    override fun refresh(){
        val key=secure.get("grok");if(key.isBlank())return
        val root=JSONObject(String(HttpAudio.get("https://api.x.ai/v1/tts/voices",mapOf("Authorization" to "Bearer $key"))))
        val array=root.optJSONArray("voices")?:return;val found=(0 until array.length()).mapNotNull{i->
            val item=array.getJSONObject(i);val voiceId=item.optString("voice_id",item.optString("id"));if(voiceId.isBlank())null else VoiceRecord("grok/$voiceId@en-US","${item.optString("name",voiceId.replaceFirstChar(Char::uppercase))} · Grok",Locale.US,id,"xAI TTS",Languages.grok,true,item.optString("gender"))
        };if(found.isNotEmpty())catalog=found
    }
    override fun synthesize(voice: VoiceRecord, text: String, language: String, speed: Float, cancelled: AtomicBoolean): AudioData {
        val key = secure.get("grok"); require(key.isNotBlank()) { "Grok API key is not configured" }
        val body = JSONObject().put("text", text).put("voice_id", voice.id.substringAfter('/').substringBefore('@'))
            .put("language", "auto").put("speed", speed.coerceIn(.7f, 1.5f)).put("text_normalization", true)
            .put("output_format", JSONObject().put("codec", "pcm").put("sample_rate", 24000))
        if (cancelled.get()) throw InterruptedException()
        return AudioData(24000, HttpAudio.post("https://api.x.ai/v1/tts", body, mapOf("Authorization" to "Bearer $key", "Accept" to "audio/pcm"), cancelled))
    }
    override fun stream(session:PreparedSession,text:String,speed:Float,pitch:Float,cancelled:AtomicBoolean,emit:(AudioChunk)->Boolean) {
        val voice=session.voice
        val key=secure.get("grok");require(key.isNotBlank()){ "Grok API key is not configured" }
        val body=JSONObject().put("text",text).put("voice_id",voice.id.substringAfter('/').substringBefore('@'))
            .put("language","auto").put("speed",speed.coerceIn(.7f,1.5f)).put("text_normalization",true)
            .put("output_format",JSONObject().put("codec","pcm").put("sample_rate",24000))
        var sequence=0;val range=TextRange(0,text.length)
        HttpAudio.postStream("https://api.x.ai/v1/tts",body,mapOf("Authorization" to "Bearer $key","Accept" to "audio/pcm"),cancelled){emit(AudioChunk(it,24000,range,sequence++))}
    }
}
