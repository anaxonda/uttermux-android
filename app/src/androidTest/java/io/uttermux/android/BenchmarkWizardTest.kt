package io.uttermux.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.uttermux.android.benchmark.BenchmarkRunner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(AndroidJUnit4::class)
class BenchmarkWizardTest {
    @Test fun syntheticArtifactProducesReviewableAndApplicableResult() {
        val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as UtterMuxApp
        val runner=BenchmarkRunner(app);val voice=runner.installedArtifacts().single{it.provider=="test-host"}
        val result=runner.run(voice,AtomicBoolean()){}
        assertEquals(3,result.report.let{org.json.JSONObject(it.readText()).getInt("schemaVersion")})
        assertTrue(result.candidates.isNotEmpty());assertTrue(result.winner.threads>=1)
        app.settings.setTunedThreads(result.artifactId,result.winner.threads,result.artifactFingerprint)
        assertEquals(result.winner.threads,app.settings.tunedThreads(result.artifactId,result.artifactFingerprint))
        app.settings.setTunedThreads(result.artifactId,0)
    }
}
