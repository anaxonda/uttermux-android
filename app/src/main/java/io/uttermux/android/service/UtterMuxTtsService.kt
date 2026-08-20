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
import java.util.concurrent.Executors
import java.util.concurrent.ConcurrentHashMap
import java.util.ArrayDeque

class UtterMuxTtsService : TextToSpeechService() {
    @Volatile private var cancelled = AtomicBoolean()
    private val router get() = UtterMuxApp.instance.router
    private val warmExecutor=Executors.newSingleThreadExecutor()
    private val warming=ConcurrentHashMap.newKeySet<String>()
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
        val began=System.nanoTime()
        val ready=router.availableVoices
        // Android clients do not need the entire searchable provider catalog.
        // Returning hundreds or thousands of Voice parcelables makes some
        // readers perform expensive synchronous setup on their main thread.
        // Expose compact language routers plus only user-selected concrete
        // voices; the manager remains the place to browse every provider voice.
        val languages=ready.asSequence().flatMap{it.languages.asSequence()}
            .map(Languages::normalized).map{it.substringBefore('-')}.distinct().sorted().toList()
        val automatic=languages.map { language ->
            val candidates=router.candidates("uttermux:auto@$language",language)
            Voice("uttermux:auto@$language",Locale.forLanguageTag(language),Voice.QUALITY_HIGH,Voice.LATENCY_NORMAL,
                candidates.isEmpty()||candidates.all{it.networkRequired},emptySet())
        }
        val readyById=ready.associateBy{it.id}
        val selected=(listOf(UtterMuxApp.instance.settings.defaultVoice)+UtterMuxApp.instance.settings.configuredRouteVoices())
            .distinct().mapNotNull(readyById::get)
            .map { Voice(it.id,it.locale,Voice.QUALITY_HIGH,Voice.LATENCY_NORMAL,it.networkRequired,emptySet()) }
        return (automatic+selected).distinctBy{it.name}.toMutableList().also{
            Log.i("UtterMuxTTS","system voice catalog: ${it.size} voices in ${(System.nanoTime()-began)/1_000_000}ms (${ready.size} ready in manager)")
        }
    }
    override fun onIsValidVoiceName(voiceName: String?): Int = if (voiceName?.startsWith("uttermux:auto") == true || router.availableVoices.any{it.id==voiceName}) TextToSpeech.SUCCESS else TextToSpeech.ERROR
    override fun onLoadVoice(voiceName: String?): Int = onIsValidVoiceName(voiceName).also{result->
        if(result==TextToSpeech.SUCCESS&&!voiceName.isNullOrBlank()&&warming.add(voiceName))warmExecutor.execute{
            try{router.warm(voiceName)}finally{warming.remove(voiceName)}
        }
    }
    override fun onGetDefaultVoiceNameFor(lang: String?, country: String?, variant: String?): String = "uttermux:auto@${Languages.fromAndroid(lang,country).substringBefore('-')}"
    override fun onDestroy(){warmExecutor.shutdownNow();super.onDestroy()}
    override fun onStop() { cancelled.set(true) }
    override fun onSynthesizeText(request: SynthesisRequest, callback: SynthesisCallback) {
        val signal = AtomicBoolean(); cancelled = signal
        val terminal=AtomicBoolean()
        fun doneOnce(){if(terminal.compareAndSet(false,true))callback.done()}
        fun errorOnce(code:Int){if(terminal.compareAndSet(false,true))callback.error(code)}
        val locale = Languages.fromAndroid(request.language,request.country)
        val text=request.charSequenceText?.toString().orEmpty()
        val diagnostic=Diagnostics.request("${request.voiceName} $locale chars=${text.length}")
        val began=System.nanoTime();var emittedFrames=0;var firstAudio=true
        try {
            if(text.isBlank()){doneOnce();return}
            val pitch=(request.pitch/100f).coerceIn(.5f,2f)
            // Resampling changes pitch and speed together; compensate provider
            // speed first so the final duration still follows speechRate.
            val providerSpeed=(request.speechRate/100f/pitch).coerceIn(.5f,2f)
            val route=router.prepare(request.voiceName,text,locale)
            val controller=UtterMuxApp.instance.adaptiveBuffers.controller(route.primary.voice.id)
            data class PendingAudio(val pcm:ByteArray,val range:io.uttermux.android.config.TextRange)
            val pending=ArrayDeque<PendingAudio>();var pendingBytes=0;var deliveryStarted=false
            Diagnostics.record(diagnostic,"routed","${route.primary.voice.id} ${route.primary.strategy}")
            if(callback.start(24_000,AudioFormat.ENCODING_PCM_16BIT,1)!=TextToSpeech.SUCCESS){signal.set(true);errorOnce(TextToSpeech.ERROR_OUTPUT);return}
            Diagnostics.record(diagnostic,"callback-start","${(System.nanoTime()-began)/1_000_000}ms")
            fun deliver(item:PendingAudio):Boolean {
                callback.rangeStart(emittedFrames,item.range.start.coerceIn(0,text.length),item.range.endExclusive.coerceIn(0,text.length))
                var offset=0;val maximum=callback.maxBufferSize.coerceAtLeast(2)
                while(offset<item.pcm.size&&!signal.get()){
                    val size=minOf(maximum,item.pcm.size-offset)
                    if(callback.audioAvailable(item.pcm,offset,size)!=TextToSpeech.SUCCESS){signal.set(true);return false}
                    offset+=size
                }
                emittedFrames+=item.pcm.size/2
                return !signal.get()
            }
            fun flushPending():Boolean {while(pending.isNotEmpty())if(!deliver(pending.removeFirst()))return false;pendingBytes=0;return true}
            router.stream(route,text,providerSpeed,pitch,signal){chunk->
                if(signal.get())return@stream false
                if(chunk.pcm16.isEmpty()||chunk.pcm16.size%2!=0)throw IllegalArgumentException("Provider returned invalid PCM16 audio")
                val pitched=PcmTransform.pitchPcm16(chunk.pcm16,pitch)
                val pcm=PcmTransform.resamplePcm16(pitched,chunk.sampleRate,24_000)
                controller.record(chunk.generatedNanos,pcm.size/2.0/24_000.0)
                if(firstAudio){firstAudio=false;Diagnostics.record(diagnostic,"first-audio","${(System.nanoTime()-began)/1_000_000}ms")}
                val item=PendingAudio(pcm,chunk.range)
                if(!deliveryStarted){
                    pending+=item;pendingBytes+=pcm.size
                    val targetBytes=24_000*2*controller.startupMillis()/1000
                    if(pendingBytes>=targetBytes){deliveryStarted=true;if(!flushPending())return@stream false}
                }else if(!deliver(item))return@stream false
                if(!signal.get()&&deliveryStarted&&chunk.generatedNanos>pcm.size/2.0/24_000.0*1_000_000_000L){
                    controller.recordUnderrun()
                }
                !signal.get()
            }
            if(!signal.get()&&!flushPending())return
        } catch (_:InterruptedException){signal.set(true)}
        catch(error:Throwable){
            Log.e("UtterMuxTTS","Synthesis failed for $locale/${request.voiceName}: ${error.message}",error)
            Diagnostics.record(diagnostic,"error",error.message.orEmpty())
            if(!signal.get())errorOnce(errorCode(error))
        } finally {
            Diagnostics.record(diagnostic,"complete","${(System.nanoTime()-began)/1_000_000}ms frames=$emittedFrames cancelled=${signal.get()}")
            doneOnce()
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
