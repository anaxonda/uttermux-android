package io.uttermux.android

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.uttermux.android.audio.CompressedAudioDecoder
import io.uttermux.android.provider.HttpAudio
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PiperPreviewTest {
    @Test fun officialLessacSampleDecodesOnDevice() {
        val url = "https://huggingface.co/rhasspy/piper-voices/resolve/main/en/en_US/lessac/medium/samples/speaker_0.mp3"
        val encoded = HttpAudio.get(url)
        val audio = CompressedAudioDecoder.mp3(ApplicationProvider.getApplicationContext(), encoded)
        assertTrue(audio.sampleRate >= 16_000)
        assertTrue(audio.pcm16.size > audio.sampleRate * 2)
    }
}
