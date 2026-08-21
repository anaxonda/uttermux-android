package io.uttermux.android.provider

import android.content.Context
import io.uttermux.android.audio.CompressedAudioDecoder
import io.uttermux.android.config.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class PlayHtProvider(private val context:Context,private val secure:SecureStore):TtsProvider {
    override val id=ProviderIds.PLAYHT
    override val descriptor=ProviderDescriptor(id,"PlayHT",credentialFields=listOf(CredentialField("playht","API key"),CredentialField("playht_user","User ID",false)))
    @Volatile private var catalog=listOf(VoiceRecord("playht/default@en-US","Default · PlayHT",Locale.US,id,"Play3.0-mini",setOf("en-US"),true))
    override val voices get()=catalog
    override fun isAvailable(voice:VoiceRecord)=secure.get("playht").isNotBlank()&&secure.get("playht_user").isNotBlank()
    override fun strategy(voice:VoiceRecord)=StreamStrategy.BUFFERED
    override fun refresh(){
        val key=secure.get("playht");val user=secure.get("playht_user");if(key.isBlank()||user.isBlank())return
        val value=String(HttpAudio.get("https://api.play.ht/api/v2/voices",mapOf("AUTHORIZATION" to key,"X-USER-ID" to user)))
        val array=runCatching{JSONArray(value)}.getOrElse{JSONObject(value).optJSONArray("voices")?:JSONArray()}
        val found=(0 until array.length()).map{array.getJSONObject(it)}.map{
            val locale=it.optString("language_code","en-US")
            VoiceRecord("playht/${java.net.URLEncoder.encode(it.optString("id"),"UTF-8")}@$locale","${it.optString("name","Voice")} · PlayHT",Locale.forLanguageTag(locale),id,it.optString("voice_engine","Play3.0-mini"),setOf(locale),true,listOf(it.optString("accent"),it.optString("gender")).filter(String::isNotBlank).joinToString(" · "),it.optString("sample"))
        };if(found.isNotEmpty())catalog=found
    }
    override fun stream(session:PreparedSession,text:String,speed:Float,pitch:Float,cancelled:AtomicBoolean,emit:(AudioChunk)->Boolean){
        val key=secure.get("playht");val user=secure.get("playht_user");require(key.isNotBlank()&&user.isNotBlank()){ "PlayHT credentials are not configured" }
        val voice=java.net.URLDecoder.decode(session.voice.id.substringAfter('/').substringBefore('@'),"UTF-8")
        val body=JSONObject().put("text",text).put("voice",voice).put("voice_engine",session.voice.model).put("output_format","wav").put("sample_rate",24000).put("speed",speed.coerceIn(.1f,5f)).put("language",CloudContracts.playHtLanguage(session.language))
        val bytes=HttpAudio.postRaw("https://api.play.ht/api/v2/tts/stream",body.toString().toByteArray(),"application/json",mapOf("AUTHORIZATION" to key,"X-USER-ID" to user,"Accept" to "audio/wav"),cancelled)
        val audio=CompressedAudioDecoder.decode(context,bytes,"wav");emit(AudioChunk(audio.pcm16,audio.sampleRate,TextRange(0,text.length),0))
    }
}

class ResembleProvider(private val context:Context,private val secure:SecureStore):TtsProvider {
    override val id=ProviderIds.RESEMBLE
    override val descriptor=ProviderDescriptor(id,"Resemble AI",credentialFields=listOf(CredentialField("resemble","API token"),CredentialField("resemble_voice","Voice UUID",false),CredentialField("resemble_project","Project UUID",false)))
    override val voices get()=secure.get("resemble_voice").split(',','\n').map(String::trim).filter(String::isNotBlank).map{VoiceRecord("resemble/$it@en-US","$it · Resemble",Locale.US,id,"Resemble",setOf("en"),true)}
    override fun isAvailable(voice:VoiceRecord)=secure.get("resemble").isNotBlank()&&voices.isNotEmpty()
    override fun strategy(voice:VoiceRecord)=StreamStrategy.BUFFERED
    override fun stream(session:PreparedSession,text:String,speed:Float,pitch:Float,cancelled:AtomicBoolean,emit:(AudioChunk)->Boolean){
        val key=secure.get("resemble");require(key.isNotBlank()){ "Resemble token is not configured" }
        val body=JSONObject().put("voice_uuid",session.voice.id.substringAfter('/').substringBefore('@')).put("data",text).put("precision","PCM_16").put("sample_rate",24000)
        secure.get("resemble_project").takeIf(String::isNotBlank)?.let{body.put("project_uuid",it)}
        val bytes=HttpAudio.postRaw("https://f.cluster.resemble.ai/stream",body.toString().toByteArray(),"application/json",mapOf("Authorization" to "Bearer $key"),cancelled)
        val audio=CompressedAudioDecoder.decode(context,bytes,"wav");emit(AudioChunk(audio.pcm16,audio.sampleRate,TextRange(0,text.length),0))
    }
}

class ProxyPcmProvider(
    override val id:String,private val title:String,private val secure:SecureStore,private val settingPrefix:String,
    private val defaults:List<Pair<String,String>>,private val note:String,
):TtsProvider {
    override val descriptor=ProviderDescriptor(id,title,experimental=true,credentialFields=listOf(CredentialField("${settingPrefix}_proxy","Proxy base URL",false),CredentialField("${settingPrefix}_token","Proxy token")),note=note)
    @Volatile private var catalog=defaults.map{(name,locale)->voice(name,locale)}
    override val voices get()=catalog
    private fun voice(name:String,locale:String)=VoiceRecord("$id/$name@$locale","$name · $title",Locale.forLanguageTag(locale),id,title,setOf(locale),true,"Requires the configured UtterMux-compatible proxy",experimental=true)
    override fun isAvailable(voice:VoiceRecord)=secure.get("${settingPrefix}_proxy").isNotBlank()
    override fun refresh(){
        val base=secure.get("${settingPrefix}_proxy").trimEnd('/');if(base.isBlank())return
        val headers=secure.get("${settingPrefix}_token").takeIf(String::isNotBlank)?.let{mapOf("Authorization" to "Bearer $it")}.orEmpty()
        val array=JSONArray(String(HttpAudio.get("$base/v1/voices",headers)))
        val found=(0 until array.length()).map{array.getJSONObject(it)}.map{voice(it.getString("id"),it.optString("language","en-US"))};if(found.isNotEmpty())catalog=found
    }
    override fun stream(session:PreparedSession,text:String,speed:Float,pitch:Float,cancelled:AtomicBoolean,emit:(AudioChunk)->Boolean){
        val base=secure.get("${settingPrefix}_proxy").trimEnd('/');require(base.isNotBlank()){ "$title proxy is not configured" }
        val headers=secure.get("${settingPrefix}_token").takeIf(String::isNotBlank)?.let{mapOf("Authorization" to "Bearer $it")}.orEmpty()
        val body=JSONObject().put("text",text).put("voice",session.voice.id.substringAfter('/').substringBefore('@')).put("language",session.language).put("speed",speed)
        var sequence=0;val range=TextRange(0,text.length);HttpAudio.postStream("$base/v1/synthesize",body,headers,cancelled){emit(AudioChunk(it,24000,range,sequence++))}
    }
}

class CustomPcmProvider(private val secure:SecureStore):TtsProvider {
    override val id=ProviderIds.CUSTOM
    override val descriptor=ProviderDescriptor(id,"Custom streamed PCM",experimental=true,credentialFields=listOf(CredentialField("custom_endpoint","Synthesis endpoint",false),CredentialField("custom_token","Bearer token"),CredentialField("custom_voice","Voice ID",false)),note="POSTs a constrained JSON request and expects 24 kHz PCM16 mono.")
    override val voices get()=secure.get("custom_voice").ifBlank{"default"}.split(',','\n').map(String::trim).filter(String::isNotBlank).map{VoiceRecord("custom/$it@en-US","$it · Custom",Locale.US,id,"Custom PCM",setOf("multilingual"),true,experimental=true)}
    override fun isAvailable(voice:VoiceRecord)=CloudContracts.validHttps(secure.get("custom_endpoint"))
    override fun stream(session:PreparedSession,text:String,speed:Float,pitch:Float,cancelled:AtomicBoolean,emit:(AudioChunk)->Boolean){
        val endpoint=CloudContracts.requireHttps(secure.get("custom_endpoint"),"Custom PCM endpoint")
        val headers=secure.get("custom_token").takeIf(String::isNotBlank)?.let{mapOf("Authorization" to "Bearer $it")}.orEmpty()
        val body=JSONObject().put("text",text).put("voice",session.voice.id.substringAfter('/').substringBefore('@')).put("language",session.language).put("speed",speed)
        var sequence=0;val range=TextRange(0,text.length);HttpAudio.postStream(endpoint,body,headers,cancelled){emit(AudioChunk(it,24000,range,sequence++))}
    }
}
