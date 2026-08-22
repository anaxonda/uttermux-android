package io.uttermux.android

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.uttermux.android.config.Languages
import io.uttermux.android.provider.EspeakProvider
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EspeakProviderIntegrationTest {
    @Test fun embeddedEngineEnumeratesAndStreamsPcm() {
        val provider=EspeakProvider(ApplicationProvider.getApplicationContext())
        assertTrue("Expected embedded eSpeak voices",provider.voices.size>50)
        val voice=provider.voices.firstOrNull{Languages.matches(it.locale.toLanguageTag(),"en-US")}
            ?:provider.voices.first()
        val session=provider.prepare(voice,"en-US")
        var bytes=0L
        var chunks=0
        provider.stream(session,"This is an embedded eSpeak N G test.",1f,1f,AtomicBoolean()) { chunk ->
            assertTrue(chunk.sampleRate>0)
            assertEquals(0,chunk.pcm16.size%2)
            assertTrue(chunk.generatedNanos>0)
            bytes+=chunk.pcm16.size
            chunks++
            true
        }
        assertTrue("Expected streamed PCM",bytes>1_000)
        assertTrue(chunks>0)
    }
}
