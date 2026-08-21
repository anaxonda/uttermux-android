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
import java.net.Socket
import org.json.JSONObject
import com.qwen.tts.studio.engine.QwenEngine
import java.io.File

/** Explicit, opt-in device tests. These download hundreds of megabytes and are
 * intentionally not part of the ordinary connected test suite. */
@RunWith(AndroidJUnit4::class)
@OptInDeviceTest
class LargeModelIntegrationTest {
    private val app get()=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as UtterMuxApp

    @Test fun qwenDownloadAndDirectCloneBenchmark(){
        val id="qwen3-tts-0.6b-base-q4km";install(id)
        val modelRoot=File(app.models.root,id)
        val reference=File(app.models.root,"sherpa-onnx-pocket-tts-int8-2026-01-26/presets/alba-casual.wav")
        assertTrue("Pocket Alba reference is required for this controlled clone benchmark",reference.isFile)
        QwenEngine().use{engine->
            engine.setCpuThreads(4)
            val loadStart=android.os.SystemClock.elapsedRealtime()
            assertTrue(engine.lastError(),engine.loadModels(modelRoot.absolutePath,"qwen-talker-0.6b-base-Q4_K_M.gguf"))
            val loadedMs=android.os.SystemClock.elapsedRealtime()-loadStart
            val embedding=File(app.cacheDir,"qwen-benchmark-speaker.json")
            val prepareStart=android.os.SystemClock.elapsedRealtime()
            assertTrue(engine.lastError(),engine.extractSpeakerEmbedding(reference.absolutePath,embedding.absolutePath))
            val preparedMs=android.os.SystemClock.elapsedRealtime()-prepareStart
            assertTrue("Prepared Qwen speaker embedding is empty",embedding.length()>0)
            repeat(2){run->
                var frames=0L;var sampleRate=0;var firstMs=-1L
                val began=android.os.SystemClock.elapsedRealtime()
                val result=engine.stream("UtterMux measures Qwen synthesis speed on this Android device.",speakerEmbedding=embedding.absolutePath,
                    params=QwenEngine.NativeParams(languageId=2050,maxAudioTokens=128)){samples,rate,_,_,_,_,_,_,_,_->
                    if(firstMs<0)firstMs=android.os.SystemClock.elapsedRealtime()-began
                    frames+=samples.size;sampleRate=rate;true
                }
                val wallMs=android.os.SystemClock.elapsedRealtime()-began
                assertTrue(result.errorMsg,result.success);assertTrue(frames>0&&sampleRate>0)
                val seconds=frames.toDouble()/sampleRate
                Log.i("UtterMuxBenchmark","Qwen load=${loadedMs}ms prepare=${preparedMs}ms run=${run+1} first=${firstMs}ms wall=${wallMs}ms audio=${"%.3f".format(seconds)}s rtf=${"%.3f".format(wallMs/1000.0/seconds)} pssKb=${android.os.Debug.getPss()}")
            }
            embedding.delete()
        }
    }

    @Test fun pocketDownloadAndExactVoiceSynthesis(){
        val id="sherpa-onnx-pocket-tts-int8-2026-01-26"
        install(id)
        val voice=app.router.voices.first{it.model=="Pocket TTS INT8"}
        val began=android.os.SystemClock.elapsedRealtime()
        val audio=app.router.synthesizeExact(voice.id,"Pocket is now speaking through its licensed Alba reference voice.","en-US",1f,AtomicBoolean())
        verify("Pocket",audio,began)
    }

    @Test fun kokoroFp32DownloadAndExactVoiceSynthesis(){
        val id="kokoro-multi-lang-v1_1";install(id)
        val voice=app.router.voices.first{it.downloadId==id&&it.locale.toLanguageTag()=="en-US"}
        val began=android.os.SystemClock.elapsedRealtime();val audio=app.router.synthesizeExact(voice.id,"Kokoro is speaking through the supported full precision model.","en-US",1f,AtomicBoolean())
        verify("Kokoro FP32",audio,began)
    }

    @Test fun kittenDownloadAndExactVoiceSynthesis(){
        val id="kitten-nano-en-v0_8-int8";install(id)
        val voice=app.router.voices.first{it.downloadId==id};val began=android.os.SystemClock.elapsedRealtime()
        verify("Kitten INT8",app.router.synthesizeExact(voice.id,"Kitten is restored and ready for Android applications.","en-US",1f,AtomicBoolean()),began)
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

    @Test fun pocketAndKokoroOptimizationMatrix(){
        val prefs=app.getSharedPreferences("settings",android.content.Context.MODE_PRIVATE)
        val oldSteps=prefs.getInt("pocket_num_steps_v3",2);val oldThreads=prefs.getInt("engine_threads",0)
        val pocket=app.router.voices.first{it.model=="Pocket TTS INT8"};val kokoro=app.router.voices.first{it.downloadId=="kokoro-multi-lang-v1_1"&&it.locale.toLanguageTag()=="en-US"}
        assertTrue(app.router.isAvailable(pocket));assertTrue(app.router.isAvailable(kokoro))
        val text="A controlled benchmark measures sustained local speech generation on this phone."
        try{
            for((voice,steps) in listOf(pocket to 1,pocket to 2,pocket to 3,kokoro to 0))for(threads in listOf(1,2,4)){
                prefs.edit().putInt("engine_threads",threads).putInt("pocket_num_steps_v3",steps.coerceAtLeast(1)).commit()
                app.providers.forEach{it.trimMemory()}
                val began=android.os.SystemClock.elapsedRealtime();val audio=app.router.synthesizeExact(voice.id,text,"en-US",1f,AtomicBoolean())
                val wall=android.os.SystemClock.elapsedRealtime()-began;val seconds=audio.pcm16.size/2.0/audio.sampleRate;val rtf=wall/1000.0/seconds
                Log.i("UtterMuxOptimization","model=${voice.model} steps=$steps threads=$threads wallMs=$wall audio=${"%.3f".format(seconds)} rtf=${"%.3f".format(rtf)} pssKb=${android.os.Debug.getPss()}")
                assertTrue("${voice.model} returned invalid audio",audio.pcm16.size>audio.sampleRate&&audio.pcm16.size%2==0)
            }
        }finally{prefs.edit().putInt("pocket_num_steps_v3",oldSteps).putInt("engine_threads",oldThreads).commit();app.providers.forEach{it.trimMemory()}}
    }

    @Test fun pocketAndKokoroSustainedThermalMatrix(){
        val prefs=app.getSharedPreferences("settings",android.content.Context.MODE_PRIVATE)
        val oldSteps=prefs.getInt("pocket_num_steps_v3",2);val oldThreads=prefs.getInt("engine_threads",0)
        val pocket=app.router.voices.first{it.model=="Pocket TTS INT8"};val kokoro=app.router.voices.first{it.downloadId=="kokoro-multi-lang-v1_1"&&it.locale.toLanguageTag()=="en-US"}
        val passages=listOf(
            "The first passage establishes a warm model for sustained reading.",
            "A second passage measures whether generation remains ahead of playback.",
            "Repeated requests reveal heat and scheduling behavior hidden by one benchmark.",
            "Document readers require stable throughput across every submitted section.",
            "The final passage records sustained performance rather than cold startup alone.")
        try{
            for((voice,steps,threads) in listOf(Triple(pocket,1,2),Triple(pocket,2,2),Triple(kokoro,0,2),Triple(kokoro,0,4))){
                prefs.edit().putInt("engine_threads",threads).putInt("pocket_num_steps_v3",steps.coerceAtLeast(1)).commit();app.providers.forEach{it.trimMemory()}
                passages.forEachIndexed{index,text->
                    val began=android.os.SystemClock.elapsedRealtime();val audio=app.router.synthesizeExact(voice.id,text,"en-US",1f,AtomicBoolean())
                    val wall=android.os.SystemClock.elapsedRealtime()-began;val seconds=audio.pcm16.size/2.0/audio.sampleRate
                    Log.i("UtterMuxThermal","model=${voice.model} steps=$steps threads=$threads run=${index+1} wallMs=$wall audio=${"%.3f".format(seconds)} rtf=${"%.3f".format(wall/1000.0/seconds)} pssKb=${android.os.Debug.getPss()}")
                    assertTrue("${voice.model} returned invalid audio",audio.pcm16.size>audio.sampleRate&&audio.pcm16.size%2==0)
                }
            }
        }finally{prefs.edit().putInt("pocket_num_steps_v3",oldSteps).putInt("engine_threads",oldThreads).commit();app.providers.forEach{it.trimMemory()}}
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

    @Test fun pocketRepeatedClauseStreamingDiagnostic(){
        val voice=app.router.voices.first{it.model=="Pocket TTS INT8"};assertTrue(app.router.isAvailable(voice))
        val text="MISTER HANEDA WAS senior to Mister Omochi, who was senior to Mister Saito, who was senior to Miss Mori, who was senior to me, I was senior to no one."
        val provider=app.providers.first{it.id==voice.provider};var frames=0L
        provider.stream(provider.prepare(voice,"en-US"),text,1f,1f,AtomicBoolean()){frames+=it.pcm16.size/2;true}
        assertTrue(frames>0);Log.i("UtterMuxLargeTest","Pocket repeated-clause diagnostic emitted $frames frames")
    }

    @Test fun koReaderPocketBridgeCompletesAndReplaysSameText(){
        val voice=app.router.voices.first{it.model=="Pocket TTS INT8"};assertTrue(app.router.isAvailable(voice))
        val old=app.settings.defaultVoice;app.settings.defaultVoice=voice.id
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        context.startForegroundService(android.content.Intent(context,io.uttermux.android.service.KoReaderServerService::class.java))
        try{
            Thread.sleep(750)
            repeat(2){pass->
                val handle=post("/",JSONObject().put("text","The same Pocket bridge section must play fully every time.").put("language","en-US").put("length_scale",1.0).toString())
                post("/play",JSONObject().put("handle",handle).toString())
                val deadline=android.os.SystemClock.elapsedRealtime()+20_000;var remaining=Double.MAX_VALUE;var error=""
                while(android.os.SystemClock.elapsedRealtime()<deadline){
                    val state=JSONObject(post("/remaining",JSONObject().put("handle",handle).toString()));remaining=state.getDouble("remaining");error=state.optString("error")
                    if(remaining==0.0)break;Thread.sleep(100)
                }
                assertTrue("KOReader pass ${pass+1} error: $error",error.isBlank());assertEquals("KOReader pass ${pass+1} did not complete",0.0,remaining,0.0)
            }
        }finally{context.stopService(android.content.Intent(context,io.uttermux.android.service.KoReaderServerService::class.java));app.settings.defaultVoice=old}
    }

    @Test fun koReaderPocketBridgePausesAndResumes(){
        val voice=app.router.voices.first{it.model=="Pocket TTS INT8"};assertTrue(app.router.isAvailable(voice))
        val old=app.settings.defaultVoice;app.settings.defaultVoice=voice.id
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        context.startForegroundService(android.content.Intent(context,io.uttermux.android.service.KoReaderServerService::class.java))
        try{
            Thread.sleep(750)
            val text="Pocket Alba reads a sufficiently long section so the bridge can pause after playback starts, preserve the active stream, and continue from that same request when playback resumes."
            val handle=post("/",JSONObject().put("text",text).put("language","en-US").put("length_scale",1.0).toString())
            val body=JSONObject().put("handle",handle).toString();post("/play",body)
            awaitState(handle,setOf("playing"),20_000)
            post("/pause",body);assertEquals("paused",awaitState(handle,setOf("paused"),2_000).getString("state"))
            Thread.sleep(350);assertEquals("Pause must remain stable until resume","paused",state(handle).getString("state"))
            post("/play",body);awaitState(handle,setOf("playing","finished"),3_000)
            val finished=awaitState(handle,setOf("finished"),20_000)
            assertTrue("KOReader pause/resume error: ${finished.optString("error")}",finished.optString("error").isBlank())
        }finally{context.stopService(android.content.Intent(context,io.uttermux.android.service.KoReaderServerService::class.java));app.settings.defaultVoice=old}
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
    private fun post(path:String,body:String):String{
        return Socket("127.0.0.1",5000).use{socket->
            socket.soTimeout=20_000;val bytes=body.toByteArray();val output=socket.getOutputStream()
            output.write("POST $path HTTP/1.1\r\nHost: 127.0.0.1\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray());output.write(bytes);output.flush()
            val response=socket.getInputStream().bufferedReader().readText();assertTrue(response.startsWith("HTTP/1.1 200"));response.substringAfter("\r\n\r\n")
        }
    }
    private fun state(handle:String)=JSONObject(post("/remaining",JSONObject().put("handle",handle).toString()))
    private fun awaitState(handle:String,expected:Set<String>,timeoutMs:Long):JSONObject{
        val deadline=android.os.SystemClock.elapsedRealtime()+timeoutMs
        var latest=state(handle)
        while(android.os.SystemClock.elapsedRealtime()<deadline&&latest.optString("state") !in expected){Thread.sleep(100);latest=state(handle)}
        assertTrue("Expected $expected but bridge was ${latest.optString("state")}: ${latest.optString("error")}",latest.optString("state") in expected)
        return latest
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
