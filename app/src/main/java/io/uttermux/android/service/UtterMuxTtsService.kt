package io.uttermux.android.service

import android.media.AudioFormat
import android.speech.tts.*
import android.util.Log
import io.uttermux.android.UtterMuxApp
import io.uttermux.android.audio.PcmTransform
import io.uttermux.android.config.*
import io.uttermux.android.diagnostics.Diagnostics
import io.uttermux.android.provider.ProviderException
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.ArrayDeque
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class UtterMuxTtsService : TextToSpeechService() {
    private data class CatalogSnapshot(val revision:Long,val voices:MutableList<Voice>,val locales:Set<String>)
    private val active=AtomicReference<AtomicBoolean?>()
    private val warmExecutor=Executors.newSingleThreadExecutor()
    private val warming=ConcurrentHashMap.newKeySet<String>()
    private val catalogLock=Any()
    @Volatile private var catalog:CatalogSnapshot?=null
    private val app get()=UtterMuxApp.instance
    private val router get()=app.router

    private fun snapshot():CatalogSnapshot {
        val revision=app.voiceDataRevision.get()
        catalog?.takeIf{it.revision==revision}?.let{return it}
        return synchronized(catalogLock){
            catalog?.takeIf{it.revision==revision}?:buildSnapshot(revision).also{catalog=it}
        }
    }

    private fun buildSnapshot(revision:Long):CatalogSnapshot {
        val began=System.nanoTime();val ready=router.availableVoices
        val locales=ready.flatMap{it.languages}.map(Languages::normalized).distinct().sorted()
        val automatic=locales.map{tag->
            val candidates=router.candidates("uttermux:auto@$tag",tag)
            Voice("uttermux:auto@$tag",Locale.forLanguageTag(tag),Voice.QUALITY_HIGH,Voice.LATENCY_NORMAL,
                candidates.firstOrNull()?.networkRequired?:true,setOf("uttermux:auto"))
        }
        val readyById=ready.associateBy{it.id}
        val selected=(listOf(app.settings.defaultVoice)+app.settings.configuredRouteVoices()).distinct()
            .mapNotNull(readyById::get).map{voice->
                val latency=if(voice.performanceClass=="fast")Voice.LATENCY_LOW else Voice.LATENCY_NORMAL
                Voice(voice.id,voice.locale,Voice.QUALITY_HIGH,latency,voice.networkRequired,voice.capabilities)
            }
        return CatalogSnapshot(revision,(automatic+selected).distinctBy{it.name}.toMutableList(),locales.toSet()).also{
            Log.i("UtterMuxTTS","built system voice snapshot: ${it.voices.size} voices in ${(System.nanoTime()-began)/1_000_000}ms (${ready.size} ready)")
        }
    }

    private fun requestedTag(lang:String?,country:String?)=Languages.fromAndroid(lang,country)
    private fun matchingLocale(tag:String):String? {
        val normalized=Languages.normalized(tag);val locales=snapshot().locales
        return normalized.takeIf(locales::contains)?:locales.firstOrNull{Languages.matches(it,normalized)}
    }
    override fun onIsLanguageAvailable(lang:String?,country:String?,variant:String?):Int {
        val requested=requestedTag(lang,country);val matched=matchingLocale(requested)?:return TextToSpeech.LANG_NOT_SUPPORTED
        return when {
            !variant.isNullOrBlank()&&matched.equals(requested,true)->TextToSpeech.LANG_COUNTRY_AVAILABLE
            !country.isNullOrBlank()&&matched.equals(requested,true)->TextToSpeech.LANG_COUNTRY_AVAILABLE
            else->TextToSpeech.LANG_AVAILABLE
        }
    }
    override fun onLoadLanguage(lang:String?,country:String?,variant:String?)=onIsLanguageAvailable(lang,country,variant)
    override fun onGetLanguage():Array<String> {
        val locale=router.effectiveDefault()?.locale?:Locale.US
        return arrayOf(runCatching{locale.isO3Language}.getOrDefault(locale.language),runCatching{locale.isO3Country}.getOrDefault(locale.country),locale.variant)
    }
    override fun onGetVoices():MutableList<Voice> = snapshot().voices.toMutableList()
    override fun onIsValidVoiceName(voiceName:String?):Int =
        if(voiceName!=null&&snapshot().voices.any{it.name==voiceName})TextToSpeech.SUCCESS else TextToSpeech.ERROR
    override fun onLoadVoice(voiceName:String?):Int = onIsValidVoiceName(voiceName).also{result->
        if(result==TextToSpeech.SUCCESS&&!voiceName.isNullOrBlank()&&warming.add(voiceName))warmExecutor.execute{
            try{VoiceActivity.status("warming");router.warm(voiceName)}finally{warming.remove(voiceName);VoiceActivity.status("idle")}
        }
    }
    override fun onGetDefaultVoiceNameFor(lang:String?,country:String?,variant:String?):String {
        val requested=requestedTag(lang,country);val matched=matchingLocale(requested)?:requested
        return "uttermux:auto@$matched"
    }
    override fun onStop(){active.getAndSet(null)?.set(true)}

    override fun onSynthesizeText(request:SynthesisRequest,callback:SynthesisCallback) {
        val signal=AtomicBoolean();active.getAndSet(signal)?.set(true)
        var callbackStarted=false;var errorSent=false
        fun errorOnce(code:Int){if(!errorSent){errorSent=true;callback.error(code)}}
        val locale=requestedTag(request.language,request.country)
        val text=request.charSequenceText?.toString().orEmpty()
        val diagnostic=Diagnostics.request("system ${request.voiceName} $locale chars=${text.length}")
        val began=System.nanoTime();var emittedFrames=0;var firstAudio=true
        try {
            if(text.isBlank())return
            val pitch=(request.pitch/100f).coerceIn(.5f,2f)
            val providerSpeed=(request.speechRate/100f/pitch).coerceIn(.5f,2f)
            val route=router.prepare(request.voiceName,text,locale)
            val configured=app.settings.defaultVoice
            val effective=router.effectiveDefault()?.id.orEmpty()
            VoiceActivity.defaults(configured,effective)
            VoiceActivity.speaking(route.primary.voice.id,route.language,"Android system TTS",
                if(configured.substringBefore('@')!=route.primary.voice.id.substringBefore('@'))"Configured voice unavailable or language fallback" else "")
            val controller=app.adaptiveBuffers.controller(route.primary.voice.id)
            data class PendingAudio(val pcm:ByteArray,val range:TextRange)
            val pending=ArrayDeque<PendingAudio>();var pendingBytes=0;var deliveryStarted=false
            Diagnostics.record(diagnostic,"routed","${route.primary.voice.id} ${route.primary.strategy}")
            if(callback.start(24_000,AudioFormat.ENCODING_PCM_16BIT,1)!=TextToSpeech.SUCCESS){signal.set(true);errorOnce(TextToSpeech.ERROR_OUTPUT);return}
            callbackStarted=true;Diagnostics.record(diagnostic,"callback-start","${(System.nanoTime()-began)/1_000_000}ms")
            var lastRange:TextRange?=null
            fun deliver(item:PendingAudio):Boolean {
                if(item.range!=lastRange){callback.rangeStart(emittedFrames,item.range.start.coerceIn(0,text.length),item.range.endExclusive.coerceIn(0,text.length));lastRange=item.range}
                var offset=0;val maximum=callback.maxBufferSize.coerceAtLeast(2)
                while(offset<item.pcm.size&&!signal.get()){
                    val size=minOf(maximum,item.pcm.size-offset)
                    if(callback.audioAvailable(item.pcm,offset,size)!=TextToSpeech.SUCCESS){signal.set(true);return false}
                    offset+=size
                }
                emittedFrames+=item.pcm.size/2;return !signal.get()
            }
            fun flushPending():Boolean{while(pending.isNotEmpty())if(!deliver(pending.removeFirst()))return false;pendingBytes=0;return true}
            router.stream(route,text,providerSpeed,pitch,signal,onCandidate={chosen->VoiceActivity.speaking(chosen.id,route.language,"Android system TTS",if(chosen.id!=route.primary.voice.id)"Primary voice failed; using fallback" else "")}){chunk->
                if(signal.get())return@stream false
                require(chunk.pcm16.isNotEmpty()&&chunk.pcm16.size%2==0){"Provider returned invalid PCM16 audio"}
                val pitched=PcmTransform.pitchPcm16(chunk.pcm16,pitch)
                val pcm=PcmTransform.resamplePcm16(pitched,chunk.sampleRate,24_000)
                controller.record(chunk.generatedNanos,pcm.size/2.0/24_000.0)
                if(firstAudio){firstAudio=false;Diagnostics.record(diagnostic,"first-audio","${(System.nanoTime()-began)/1_000_000}ms")}
                val item=PendingAudio(pcm,chunk.range)
                if(!deliveryStarted){pending+=item;pendingBytes+=pcm.size;val target=24_000*2*controller.startupMillis()/1000;if(pendingBytes>=target){deliveryStarted=true;if(!flushPending())return@stream false}}
                else if(!deliver(item))return@stream false
                !signal.get()
            }
            if(!signal.get())flushPending()
        } catch(_:InterruptedException){signal.set(true)}
        catch(error:Throwable){
            Log.e("UtterMuxTTS","Synthesis failed for $locale/${request.voiceName}: ${error.message}",error)
            Diagnostics.record(diagnostic,"error",error.message.orEmpty());if(!signal.get())errorOnce(errorCode(error))
        } finally {
            Diagnostics.record(diagnostic,"complete","${(System.nanoTime()-began)/1_000_000}ms frames=$emittedFrames cancelled=${signal.get()}")
            // error() and done() are both terminal Android TTS callbacks. Never
            // send both for one request; strict clients reject that lifecycle.
            if(!errorSent)callback.done()
            active.compareAndSet(signal,null);VoiceActivity.idle()
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
    override fun onDestroy(){active.getAndSet(null)?.set(true);warmExecutor.shutdownNow();super.onDestroy()}
}
