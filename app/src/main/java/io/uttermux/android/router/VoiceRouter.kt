package io.uttermux.android.router

import io.uttermux.android.config.*
import io.uttermux.android.provider.TtsProvider
import io.uttermux.android.audio.PcmTransform
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean

class VoiceRouter(private val settings: AppSettings, providers: List<TtsProvider>) {
    private val providers = providers.associateBy(TtsProvider::id)
    val providerDescriptors get()=providers.values.map(TtsProvider::descriptor).sortedBy{it.name}
    val voices get() = providers.values.flatMap { it.voices }
    val availableVoices get() = providers.values.flatMap(TtsProvider::availableVoices)
    fun voice(id: String): VoiceRecord? = voices.firstOrNull { it.id == id || it.id.substringBefore('@') == id.substringBefore('@') }
    fun candidates(requestedVoice: String?, language: String): List<VoiceRecord> {
        val result = mutableListOf<VoiceRecord>()
        val explicit=!requestedVoice.isNullOrBlank()&&!requestedVoice.startsWith("uttermux:auto")
        fun add(id: String?,requireLanguage:Boolean=true) { id?.takeIf(String::isNotBlank)?.let(::voice)?.let {
            if((!requireLanguage||it.languages.any{tag->Languages.matches(tag,language)})&&it !in result)result+=it
        } }
        if(explicit)add(requestedVoice,false)
        add(settings.defaultVoice)
        settings.routeChain(language).forEach(::add)
        add(settings.route(language))
        // Local voices are safe implicit fallbacks. Metered network providers must
        // be placed explicitly in the language route chain.
        availableVoices.filter { !it.networkRequired&&it.languages.any { tag -> Languages.matches(tag, language) } }.forEach { if (it !in result) result += it }
        return result.filter { providers[it.provider]?.isAvailable(it) == true }
    }
    fun prepare(requestedVoice:String?,text:String,language:String):RoutingSession {
        val auto=requestedVoice?.startsWith("uttermux:auto")!=false
        val effectiveLanguage = if (language.isBlank() || language == "und" || language == "auto" || requestedVoice?.startsWith("uttermux:auto") == true)
            LanguageDetector.detect(text, language.takeUnless { it.isBlank() || it == "und" || it == "auto" } ?: "en-US")
        else language
        val prepared=candidates(requestedVoice,effectiveLanguage).mapNotNull{voice->runCatching{providers.getValue(voice.provider).prepare(voice,effectiveLanguage)}.getOrNull()}
        require(prepared.isNotEmpty()){ "No voice is ready for $effectiveLanguage" }
        return RoutingSession(effectiveLanguage,prepared)
    }
    fun stream(route:RoutingSession,text:String,speed:Float,pitch:Float,cancelled:AtomicBoolean,emit:(AudioChunk)->Boolean) {
        var failure:Throwable?=null
        for(session in route.candidates) {
            if(cancelled.get()) throw InterruptedException()
            var emitted=false
            try {
                providers.getValue(session.voice.provider).stream(session,text,speed,pitch,cancelled) { chunk -> emitted=true;emit(chunk) }
                return
            } catch(error:Throwable) {
                if(emitted || cancelled.get()) throw error
                failure=error
            }
        }
        throw RuntimeException("No voice could synthesize ${route.language}",failure)
    }
    fun stream(requestedVoice:String?,text:String,language:String,speed:Float,pitch:Float,cancelled:AtomicBoolean,emit:(AudioChunk)->Boolean)=
        stream(prepare(requestedVoice,text,language),text,speed,pitch,cancelled,emit)
    fun synthesize(requestedVoice:String?,text:String,language:String,speed:Float,cancelled:AtomicBoolean):AudioData {
        val output=ByteArrayOutputStream();val route=prepare(requestedVoice,text,language)
        stream(route,text,speed,1f,cancelled){chunk->output.write(PcmTransform.resamplePcm16(chunk.pcm16,chunk.sampleRate,24_000));true}
        return AudioData(24_000,output.toByteArray())
    }
    fun warm(voiceId:String?){voiceId?.let(::voice)?.let{voice->providers[voice.provider]?.warm(voice)}}
}
