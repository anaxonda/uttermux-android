package io.uttermux.android

import io.uttermux.android.audio.AudioSafety
import org.junit.Test

class AudioSafetyTest {
    @Test fun acceptsNormalSpeechLikeSamples(){AudioSafety.requireSafe(FloatArray(2400){if(it%2==0).2f else -.2f},24_000)}
    @Test(expected=IllegalArgumentException::class) fun rejectsRailPinnedOutput(){AudioSafety.requireSafe(FloatArray(1000){1f},24_000)}
    @Test(expected=IllegalArgumentException::class) fun rejectsNonFiniteOutput(){AudioSafety.requireSafe(floatArrayOf(Float.NaN),24_000)}
}
