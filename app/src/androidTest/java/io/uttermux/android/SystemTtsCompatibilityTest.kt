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
}
