package io.uttermux.android

import android.util.Log
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.uttermux.android.config.AudioData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.Locale

/** Explicit, opt-in device tests. These download hundreds of megabytes and are
 * intentionally not part of the ordinary connected test suite. */
@RunWith(AndroidJUnit4::class)
class LargeModelIntegrationTest {
    private val app get()=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as UtterMuxApp

    @Test fun pocketDownloadAndExactVoiceSynthesis(){
        val id="sherpa-onnx-pocket-tts-int8-2026-01-26"
        install(id)
        val voice=app.router.voices.first{it.model=="Pocket TTS INT8"}
        val began=android.os.SystemClock.elapsedRealtime()
        val audio=app.router.synthesizeExact(voice.id,"Pocket is now speaking through its licensed Alba reference voice.","en-US",1f,AtomicBoolean())
        verify("Pocket",audio,began)
    }

    @Test fun mossDownloadAndExactVoiceSynthesis(){
        val id="moss-tts-nano-100m-onnx"
        install(id)
        val voice=app.router.voices.first{it.provider=="moss"&&it.name.startsWith("Ava")}
        val began=android.os.SystemClock.elapsedRealtime()
        val audio=app.router.synthesizeExact(voice.id,"MOSS is speaking on this Android phone.","en-US",1f,AtomicBoolean())
        verify("MOSS",audio,began)
    }

    @Test fun installedPocketAndMossCompleteThroughAndroidSystemApi(){
        listOf(
            app.router.voices.first{it.model=="Pocket TTS INT8"},
            app.router.voices.first{it.provider=="moss"&&it.name.startsWith("Ava")},
        ).forEach(::speakThroughAndroid)
    }

    @Test fun pocketQualityAndWarmRequestBenchmark(){
        val voice=app.router.voices.first{it.model=="Pocket TTS INT8"};assertTrue(app.router.isAvailable(voice));val old=app.settings.pocketNumSteps
        try{
            for(steps in 3..5){
                app.settings.pocketNumSteps=steps;val began=android.os.SystemClock.elapsedRealtime()
                val audio=app.router.synthesizeExact(voice.id,"A short Pocket benchmark measures startup delay and speech generation speed.","en-US",1f,AtomicBoolean())
                verify("Pocket-$steps-steps",audio,began)
            }
        }finally{app.settings.pocketNumSteps=old}
    }

    @Test fun pocketSequentialStreamRequests(){
        val voice=app.router.voices.first{it.model=="Pocket TTS INT8"};assertTrue(app.router.isAvailable(voice));val provider=app.providers.first{it.id==voice.provider};provider.warm(voice);val old=app.settings.pocketNumSteps
        try{for(steps in 3..5){app.settings.pocketNumSteps=steps;repeat(3){index->
            val began=android.os.SystemClock.elapsedRealtime();var firstAt=0L;var previousAt=0L;var previousAudioMs=0L;var worstDeficitMs=Long.MIN_VALUE;var chunks=0;var bytes=0
            provider.stream(provider.prepare(voice,"en-US"),"Section ${index+1}. This resembles a short document-reader paragraph.",1f,1f,AtomicBoolean()){chunk->
                val now=android.os.SystemClock.elapsedRealtime()
                if(firstAt==0L)firstAt=now
                if(previousAt>0)worstDeficitMs=maxOf(worstDeficitMs,now-previousAt-previousAudioMs)
                previousAt=now;previousAudioMs=(chunk.pcm16.size/2L*1000/chunk.sampleRate);chunks++;bytes+=chunk.pcm16.size;true
            }
            assertTrue(bytes>0);Log.i("UtterMuxLargeTest","Pocket steps=$steps request=${index+1}: first PCM ${firstAt-began}ms, complete ${android.os.SystemClock.elapsedRealtime()-began}ms, chunks=$chunks, worst callback deficit=${worstDeficitMs.coerceAtLeast(0)}ms")
        }}}finally{app.settings.pocketNumSteps=old}
    }

    @Test fun pocketStopsAfterRejectedFirstChunk(){
        val voice=app.router.voices.first{it.model=="Pocket TTS INT8"};assertTrue(app.router.isAvailable(voice));val provider=app.providers.first{it.id==voice.provider}
        provider.warm(voice);val began=android.os.SystemClock.elapsedRealtime();var chunks=0
        provider.stream(provider.prepare(voice,"en-US"),"This deliberately long Pocket request must stop as soon as its first audio chunk is rejected by the Android caller, rather than finishing the entire sentence in the background.",1f,1f,AtomicBoolean()){
            chunks++;false
        }
        val elapsed=android.os.SystemClock.elapsedRealtime()-began
        assertEquals("Cancellation must stop after the rejected callback",1,chunks)
        assertTrue("Pocket callback cancellation took ${elapsed}ms",elapsed<3_000)
        Log.i("UtterMuxLargeTest","Pocket callback cancellation completed in ${elapsed}ms")
    }

    @Test fun installedConventionalSherpaVoiceStillSynthesizes(){
        val voice=app.router.availableVoices.firstOrNull{it.provider=="sherpa"&&it.model!="Pocket TTS INT8"}
            ?:return
        val began=android.os.SystemClock.elapsedRealtime()
        val audio=app.router.synthesizeExact(voice.id,"The conventional local speech engine still works after the native streaming update.",voice.locale.toLanguageTag(),1f,AtomicBoolean())
        verify(voice.name,audio,began)
    }

    private fun install(id:String){
        if(app.models.installed(id)){Log.i("UtterMuxLargeTest","$id already installed");return}
        val began=android.os.SystemClock.elapsedRealtime()
        app.models.install(id,{Log.i("UtterMuxLargeTest",it)})
        assertTrue(app.models.installed(id));Log.i("UtterMuxLargeTest","Installed $id in ${android.os.SystemClock.elapsedRealtime()-began}ms")
    }
    private fun verify(name:String,audio:AudioData,began:Long){
        assertTrue("$name sample rate ${audio.sampleRate}",audio.sampleRate>=16_000)
        assertEquals("$name PCM must be 16-bit aligned",0,audio.pcm16.size%2)
        assertTrue("$name returned too little audio",audio.pcm16.size>audio.sampleRate)
        Log.i("UtterMuxLargeTest","$name synthesized ${audio.pcm16.size/2.0/audio.sampleRate}s in ${android.os.SystemClock.elapsedRealtime()-began}ms")
    }
    private fun speakThroughAndroid(voice:io.uttermux.android.config.VoiceRecord){
        assertTrue("${voice.name} is not installed",app.router.isAvailable(voice));val old=app.settings.defaultVoice;app.settings.defaultVoice=voice.id
        val initialized=CountDownLatch(1);lateinit var tts:TextToSpeech;tts=TextToSpeech(app,{initialized.countDown()},app.packageName)
        try{
            assertTrue(initialized.await(10,TimeUnit.SECONDS));assertTrue(tts.setLanguage(Locale.US)>=TextToSpeech.LANG_AVAILABLE)
            val finished=CountDownLatch(1);val error=AtomicInteger();val began=android.os.SystemClock.elapsedRealtime()
            tts.setOnUtteranceProgressListener(object:UtteranceProgressListener(){
                override fun onStart(id:String?)=Unit;override fun onDone(id:String?){finished.countDown()}
                override fun onError(id:String?,code:Int){error.set(code);finished.countDown()}
                @Deprecated("legacy")override fun onError(id:String?){error.set(TextToSpeech.ERROR);finished.countDown()}
            })
            assertEquals(TextToSpeech.SUCCESS,tts.speak("System TTS is speaking with ${voice.name}.",TextToSpeech.QUEUE_FLUSH,null,"large-${voice.provider}"))
            assertTrue("${voice.name} system request timed out",finished.await(60,TimeUnit.SECONDS));assertEquals("${voice.name} system request failed",0,error.get())
            Log.i("UtterMuxLargeTest","${voice.name} completed through Android in ${android.os.SystemClock.elapsedRealtime()-began}ms")
        }finally{tts.stop();tts.shutdown();app.settings.defaultVoice=old}
    }
}
