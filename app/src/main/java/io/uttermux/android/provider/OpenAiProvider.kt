package io.uttermux.android.provider

import io.uttermux.android.config.*
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class OpenAiProvider(private val secure:SecureStore):TtsProvider {
    override val id=ProviderIds.OPENAI
    override val descriptor=ProviderDescriptor(id,"OpenAI",credentialFields=listOf(CredentialField("openai","API key"),CredentialField("openai_endpoint","Endpoint",false,"https://api.openai.com"),CredentialField("openai_model","Model",false,"gpt-4o-mini-tts")))
    private val names=listOf("alloy","ash","ballad","coral","echo","fable","nova","onyx","sage","shimmer","verse","marin","cedar")
    private val supported=setOf("af","ar","hy","az","be","bs","bg","ca","zh","hr","cs","da","nl","en","et","fi","fr","gl","de","el","he","hi","hu","is","id","it","ja","kn","kk","ko","lv","lt","mk","ms","mr","mi","ne","no","fa","pl","pt","ro","ru","sr","sk","sl","es","sw","sv","tl","ta","th","tr","uk","ur","vi","cy")
    override val voices=names.map{VoiceRecord("openai/$it@en-US","${it.replaceFirstChar(Char::uppercase)} · OpenAI",Locale.US,id,"OpenAI TTS",supported,true,capabilities=setOf("streaming","multilingual"))}
    override fun isAvailable(voice:VoiceRecord)=CloudContracts.configured(id){secure.get(it)}
    override fun stream(session:PreparedSession,text:String,speed:Float,pitch:Float,cancelled:AtomicBoolean,emit:(AudioChunk)->Boolean) {
        val key=secure.get("openai");require(key.isNotBlank()){ "OpenAI API key is not configured" }
        val endpoint=CloudContracts.requireHttps(secure.get("openai_endpoint").ifBlank{"https://api.openai.com"},"OpenAI endpoint")
        val model=secure.get("openai_model").ifBlank{"gpt-4o-mini-tts"}
        val body=JSONObject().put("model",model).put("voice",session.voice.id.substringAfter('/').substringBefore('@')).put("input",text).put("response_format","pcm").put("speed",speed.coerceIn(.25f,4f))
        var sequence=0;val range=TextRange(0,text.length)
        HttpAudio.postStream("$endpoint/v1/audio/speech",body,mapOf("Authorization" to "Bearer $key","Accept" to "audio/pcm"),cancelled){emit(AudioChunk(it,24000,range,sequence++))}
    }
}
