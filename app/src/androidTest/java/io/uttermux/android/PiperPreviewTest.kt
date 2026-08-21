package io.uttermux.android

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.uttermux.android.audio.CompressedAudioDecoder
import io.uttermux.android.audio.Playback
import io.uttermux.android.config.AudioData
import io.uttermux.android.provider.HttpAudio
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class PiperPreviewTest {
    @Test fun zeroReservePreviewStartsInsteadOfFinishingEmpty(){
        val began=CountDownLatch(1);val finished=CountDownLatch(1)
        Thread({
            Playback.play(AudioData(24_000,ByteArray(24_000/5*2))){began.countDown()}
            finished.countDown()
        },"preview-zero-reserve-test").start()
        assertTrue("Preview never reached AudioTrack",began.await(2,TimeUnit.SECONDS))
        assertTrue("Preview did not drain",finished.await(2,TimeUnit.SECONDS))
    }

    @OptInDeviceTest @Test fun officialLessacSampleDecodesOnDevice() {
        val url = "https://huggingface.co/rhasspy/piper-voices/resolve/main/en/en_US/lessac/medium/samples/speaker_0.mp3"
        val encoded = HttpAudio.get(url)
        val audio = CompressedAudioDecoder.mp3(ApplicationProvider.getApplicationContext(), encoded)
        assertTrue(audio.sampleRate >= 16_000)
        assertTrue(audio.pcm16.size > audio.sampleRate * 2)
    }

    @OptInDeviceTest @Test fun kokoroAndKittenCatalogSamplesDecodeOnDevice(){
        listOf(
            "https://github.com/HayaiApp/HayaiTTS-samples/releases/download/samples-3/kokoro-multi-lang-v1_0__sid0__en-US.mp3",
            "https://github.com/HayaiApp/HayaiTTS-samples/releases/download/samples-0/kitten-nano-en-v0_8-int8__sid0__en-US.mp3",
        ).forEach{url->
            val audio=CompressedAudioDecoder.mp3(ApplicationProvider.getApplicationContext(),HttpAudio.get(url))
            assertTrue("Preview sample rate for $url",audio.sampleRate>=16_000)
            assertTrue("Preview too short for $url",audio.pcm16.size>audio.sampleRate*2)
        }
    }
}
