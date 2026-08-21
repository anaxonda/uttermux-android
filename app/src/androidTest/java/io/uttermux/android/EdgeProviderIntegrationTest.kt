package io.uttermux.android

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.uttermux.android.provider.EdgeProvider
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(AndroidJUnit4::class)
class EdgeProviderIntegrationTest {
    @OptInDeviceTest
    @Test fun edgeCatalogAndSynthesisReturnPlayablePcm(){
        val provider=EdgeProvider(ApplicationProvider.getApplicationContext<Context>())
        provider.refresh();assertTrue(provider.voices.size>100)
        val voice=provider.voices.first{it.id.contains("en-US")}
        val audio=provider.synthesize(voice,"This is an Edge streaming integration test.","en-US",1f,AtomicBoolean())
        assertTrue(audio.sampleRate>=16_000);assertTrue(audio.pcm16.size>audio.sampleRate)
    }
}
