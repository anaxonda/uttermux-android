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
