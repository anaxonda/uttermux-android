package io.uttermux.android

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

@RunWith(AndroidJUnit4::class)
class SystemTtsCompatibilityTest {
    @Test fun syntheticUtteranceCompletesThroughAndroidApi() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val initialized=CountDownLatch(1);val initStatus=AtomicInteger(TextToSpeech.ERROR)
        lateinit var tts:TextToSpeech
        tts=TextToSpeech(context,{status->initStatus.set(status);initialized.countDown()},context.packageName)
        try {
            assertTrue("TTS initialization timed out",initialized.await(10,TimeUnit.SECONDS))
            assertEquals(TextToSpeech.SUCCESS,initStatus.get())
            assertTrue(tts.voices.any{it.name.startsWith("uttermux:auto@en")})
            assertTrue(tts.setLanguage(Locale.US)>=TextToSpeech.LANG_AVAILABLE)
            val finished=CountDownLatch(1);val error=AtomicInteger(0)
            tts.setOnUtteranceProgressListener(object:UtteranceProgressListener(){
                override fun onStart(id:String?)=Unit
                override fun onDone(id:String?){finished.countDown()}
                override fun onError(id:String?,code:Int){error.set(code);finished.countDown()}
                @Deprecated("legacy") override fun onError(id:String?){error.set(TextToSpeech.ERROR);finished.countDown()}
            })
            assertEquals(TextToSpeech.SUCCESS,tts.speak("This is a synthetic UtterMux compatibility test.",TextToSpeech.QUEUE_FLUSH,null,"compat"))
            assertTrue("Synthetic speech did not finish",finished.await(30,TimeUnit.SECONDS))
            assertEquals("Synthetic speech failed",0,error.get())
        } finally { tts.stop();tts.shutdown() }
    }

    @Test fun secondLocalUtteranceUsesWarmEngine(){
        val context=ApplicationProvider.getApplicationContext<Context>();val initialized=CountDownLatch(1)
        lateinit var tts:TextToSpeech;tts=TextToSpeech(context,{initialized.countDown()},context.packageName)
        try{
            assertTrue(initialized.await(10,TimeUnit.SECONDS));assertTrue(tts.setLanguage(Locale.UK)>=TextToSpeech.LANG_AVAILABLE)
            fun speak(id:String,text:String):Long{
                val started=CountDownLatch(1);val finished=CountDownLatch(1);val began=android.os.SystemClock.elapsedRealtime();val first=AtomicLong(-1);val error=AtomicInteger()
                tts.setOnUtteranceProgressListener(object:UtteranceProgressListener(){
                    override fun onStart(value:String?){first.compareAndSet(-1,android.os.SystemClock.elapsedRealtime()-began);started.countDown()}
                    override fun onDone(value:String?){finished.countDown()}
                    override fun onError(value:String?,code:Int){error.set(code);started.countDown();finished.countDown()}
                    @Deprecated("legacy")override fun onError(value:String?){error.set(TextToSpeech.ERROR);started.countDown();finished.countDown()}
                })
                assertEquals(TextToSpeech.SUCCESS,tts.speak(text,TextToSpeech.QUEUE_FLUSH,null,id));assertTrue("$id did not start",started.await(12,TimeUnit.SECONDS));assertTrue("$id did not finish",finished.await(30,TimeUnit.SECONDS));assertEquals(0,error.get());return first.get()
            }
            val cold=speak("cold","This first sentence establishes the local voice engine.")
            val warm=speak("warm","The warm engine should begin this sentence promptly.")
            assertTrue("Warm first audio was ${warm}ms",warm in 0..5000)
            assertTrue("Warm startup $warm ms regressed beyond cold startup $cold ms",warm<=cold+750)
        }finally{tts.stop();tts.shutdown()}
    }
}
