package io.uttermux.android

import android.os.Debug
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicBoolean

/** Opt-in, argument-driven device benchmark. It never downloads a model. */
@RunWith(AndroidJUnit4::class)
class ModelBenchmarkTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as UtterMuxApp

    @Test fun benchmarkInstalledVoice() {
        val args = InstrumentationRegistry.getArguments()
        val query = args.getString("voice") ?: error("Pass -e voice <voice-id-or-name>")
        val runs = (args.getString("runs")?.toIntOrNull() ?: 3).coerceIn(1, 10)
        val text = args.getString("text")
            ?: "UtterMux measures local synthesis speed with a fixed technical sentence."
        val voice = app.router.voices.firstOrNull { it.id == query || it.name == query }
            ?: error("Unknown voice: $query")
        assertTrue("Voice is not installed: ${voice.id}", app.router.isAvailable(voice))

        val results = JSONArray()
        repeat(runs) { index ->
            val began = SystemClock.elapsedRealtimeNanos()
            val audio = app.router.synthesizeExact(
                voice.id, text, voice.locale.toLanguageTag(), 1f, AtomicBoolean())
            val wallMs = (SystemClock.elapsedRealtimeNanos() - began) / 1_000_000.0
            val audioSeconds = audio.pcm16.size / 2.0 / audio.sampleRate
            assertTrue("Run ${index + 1} returned no audio", audioSeconds > 0)
            results.put(JSONObject()
                .put("run", index + 1)
                .put("wallMs", wallMs)
                .put("audioSeconds", audioSeconds)
                .put("rtf", wallMs / 1000.0 / audioSeconds)
                .put("pssKb", Debug.getPss()))
        }
        val report = JSONObject()
            .put("schemaVersion", 1)
            .put("device", android.os.Build.MODEL)
            .put("soc", android.os.Build.SOC_MODEL)
            .put("android", android.os.Build.VERSION.RELEASE)
            .put("voice", voice.id)
            .put("model", voice.model)
            .put("characters", text.length)
            .put("runs", results)
            .put("note", "Run 1 includes initialization only if the process did not already cache this model; playback is excluded.")
        Log.i("UtterMuxBenchmark", report.toString())
    }
}
