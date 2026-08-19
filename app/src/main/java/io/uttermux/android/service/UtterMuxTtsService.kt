package io.uttermux.android.service

import android.media.AudioFormat
import android.speech.tts.*
import io.uttermux.android.UtterMuxApp
import io.uttermux.android.config.Languages
import io.uttermux.android.audio.PcmTransform
import io.uttermux.android.diagnostics.Diagnostics
import io.uttermux.android.provider.ProviderException
import android.util.Log
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class UtterMuxTtsService : TextToSpeechService() {
    @Volatile private var cancelled = AtomicBoolean()
    private val router get() = UtterMuxApp.instance.router
    override fun onIsLanguageAvailable(lang: String?, country: String?, variant: String?): Int {
        val tag = Languages.fromAndroid(lang,country)
        if (router.availableVoices.none { v -> v.languages.any { Languages.matches(it, tag) } }) {
            return TextToSpeech.LANG_NOT_SUPPORTED
        }
        return when {
            !variant.isNullOrBlank() -> TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE
            !country.isNullOrBlank() -> TextToSpeech.LANG_COUNTRY_AVAILABLE
            else -> TextToSpeech.LANG_AVAILABLE
        }
    }
    override fun onLoadLanguage(lang: String?, country: String?, variant: String?): Int = onIsLanguageAvailable(lang, country, variant)
    override fun onGetLanguage(): Array<String> = arrayOf("eng", "USA", "")
    override fun onGetVoices(): MutableList<Voice> {
        val ready=router.availableVoices
        val specific=ready.flatMap{it.languages}.map(Languages::normalized)
        val languages=(specific+specific.map{it.substringBefore('-')}).distinct()
        val automatic=languages.map { language ->
            val candidates=router.candidates("uttermux:auto@$language",language)
            Voice("uttermux:auto@$language",Locale.forLanguageTag(language),Voice.QUALITY_HIGH,Voice.LATENCY_NORMAL,
                candidates.isEmpty()||candidates.all{it.networkRequired},emptySet())
        }
        return (automatic + ready.map { Voice(it.id, it.locale, Voice.QUALITY_HIGH, Voice.LATENCY_NORMAL, it.networkRequired, emptySet()) }).toMutableList()
    }
    override fun onIsValidVoiceName(voiceName: String?): Int = if (voiceName?.startsWith("uttermux:auto") == true || router.availableVoices.any{it.id==voiceName}) TextToSpeech.SUCCESS else TextToSpeech.ERROR
    override fun onLoadVoice(voiceName: String?): Int = onIsValidVoiceName(voiceName).also{if(it==TextToSpeech.SUCCESS)Thread{router.warm(voiceName)}.start()}
    override fun onGetDefaultVoiceNameFor(lang: String?, country: String?, variant: String?): String = "uttermux:auto@${Languages.fromAndroid(lang,country)}"
    override fun onStop() { cancelled.set(true) }
    override fun onSynthesizeText(request: SynthesisRequest, callback: SynthesisCallback) {
        val signal = AtomicBoolean(); cancelled = signal
        val locale = Languages.fromAndroid(request.language,request.country)
        val text=request.charSequenceText?.toString().orEmpty()
        val diagnostic=Diagnostics.request("${request.voiceName} $locale chars=${text.length}")
        val began=System.nanoTime();var emittedFrames=0;var firstAudio=true
        try {
            if(text.isBlank()){callback.done();return}
            val pitch=(request.pitch/100f).coerceIn(.5f,2f)
            // Resampling changes pitch and speed together; compensate provider
            // speed first so the final duration still follows speechRate.
            val providerSpeed=(request.speechRate/100f/pitch).coerceIn(.5f,2f)
            val route=router.prepare(request.voiceName,text,locale)
            Diagnostics.record(diagnostic,"routed","${route.primary.voice.id} ${route.primary.strategy}")
            if(callback.start(24_000,AudioFormat.ENCODING_PCM_16BIT,1)!=TextToSpeech.SUCCESS){signal.set(true);return}
            Diagnostics.record(diagnostic,"callback-start","${(System.nanoTime()-began)/1_000_000}ms")
            router.stream(route,text,providerSpeed,pitch,signal){chunk->
                if(signal.get())return@stream false
                if(chunk.pcm16.isEmpty()||chunk.pcm16.size%2!=0)throw IllegalArgumentException("Provider returned invalid PCM16 audio")
                val pitched=PcmTransform.pitchPcm16(chunk.pcm16,pitch)
                val pcm=PcmTransform.resamplePcm16(pitched,chunk.sampleRate,24_000)
                if(firstAudio){firstAudio=false;Diagnostics.record(diagnostic,"first-audio","${(System.nanoTime()-began)/1_000_000}ms")}
                callback.rangeStart(emittedFrames,chunk.range.start.coerceIn(0,text.length),chunk.range.endExclusive.coerceIn(0,text.length))
                var offset=0;val maximum=callback.maxBufferSize.coerceAtLeast(2)
                while(offset<pcm.size&&!signal.get()){
                    val size=minOf(maximum,pcm.size-offset)
                    if(callback.audioAvailable(pcm,offset,size)!=TextToSpeech.SUCCESS){signal.set(true);return@stream false}
                    offset+=size
                }
                emittedFrames+=pcm.size/2
                !signal.get()
            }
        } catch (_:InterruptedException){signal.set(true)}
        catch(error:Throwable){
            Log.e("UtterMuxTTS","Synthesis failed for $locale/${request.voiceName}: ${error.message}",error)
            Diagnostics.record(diagnostic,"error",error.message.orEmpty())
            if(!signal.get())callback.error(errorCode(error))
        } finally {
            Diagnostics.record(diagnostic,"complete","${(System.nanoTime()-began)/1_000_000}ms frames=$emittedFrames cancelled=${signal.get()}")
            if(!callback.hasFinished())callback.done()
        }
    }
    private fun errorCode(error:Throwable):Int {
        val causes=generateSequence(error){it.cause}.toList()
        return when {
            causes.any{it is SocketTimeoutException}->TextToSpeech.ERROR_NETWORK_TIMEOUT
            causes.any{it is ProviderException||it is IOException}->TextToSpeech.ERROR_NETWORK
            causes.any{it is IllegalArgumentException}->TextToSpeech.ERROR_INVALID_REQUEST
            else->TextToSpeech.ERROR_SYNTHESIS
        }
    }
}
