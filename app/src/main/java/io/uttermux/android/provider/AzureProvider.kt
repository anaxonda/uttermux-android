package io.uttermux.android.provider

import android.text.TextUtils
import io.uttermux.android.config.*
import org.json.JSONArray
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class AzureProvider(private val secure:SecureStore):TtsProvider {
    override val id=ProviderIds.AZURE
    override val descriptor=ProviderDescriptor(id,"Azure Speech",credentialFields=listOf(CredentialField("azure","Speech resource key"),CredentialField("azure_region","Region",false,"eastus"),CredentialField("azure_endpoint","Resource endpoint",false)))
    @Volatile private var catalog=listOf(voice("en-US-JennyNeural","en-US"),voice("fr-FR-DeniseNeural","fr-FR"))
    override val voices get()=catalog
    override fun isAvailable(voice:VoiceRecord)=secure.get("azure").isNotBlank()&&runCatching{val endpoint=secure.get("azure_endpoint");endpoint.isBlank()||CloudContracts.requireHttps(endpoint,"Azure resource endpoint").isNotBlank()}.getOrDefault(false)
    private fun endpoint(path:String)=CloudContracts.azurePath(secure.get("azure_endpoint"),secure.get("azure_region"),path)
    private fun voice(name:String,locale:String,details:String="")=VoiceRecord("azure/$name@$locale","${name.substringAfterLast('-').removeSuffix("Neural")} · Azure",Locale.forLanguageTag(locale),id,"Azure Neural",setOf(locale),true,details)
    override fun refresh(){
        val key=secure.get("azure");if(key.isBlank())return
        val array=JSONArray(String(HttpAudio.get(endpoint("voices/list"),mapOf("Ocp-Apim-Subscription-Key" to key))))
        val found=(0 until array.length()).map{array.getJSONObject(it)}.map{voice(it.getString("ShortName"),it.getString("Locale"),it.optString("Gender"))}
        if(found.isNotEmpty())catalog=found
    }
    override fun stream(session:PreparedSession,text:String,speed:Float,pitch:Float,cancelled:AtomicBoolean,emit:(AudioChunk)->Boolean){
        val key=secure.get("azure");require(key.isNotBlank()){ "Azure key is not configured" }
        val name=session.voice.id.substringAfter('/').substringBefore('@');val rate=((speed-1f)*100).toInt().let{if(it>=0)"+$it%" else "$it%"}
        val ssml="<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='${session.language}'><voice name='$name'><prosody rate='$rate'>${TextUtils.htmlEncode(text)}</prosody></voice></speak>"
        var sequence=0;val range=TextRange(0,text.length)
        HttpAudio.postStreamRaw(endpoint("v1"),ssml.toByteArray(),"application/ssml+xml",mapOf("Ocp-Apim-Subscription-Key" to key,"X-Microsoft-OutputFormat" to "raw-24khz-16bit-mono-pcm","User-Agent" to "UtterMux"),cancelled){emit(AudioChunk(it,24000,range,sequence++))}
    }
}
