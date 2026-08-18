package io.uttermux.android.provider

import io.uttermux.android.config.AudioData
import io.uttermux.android.config.VoiceRecord
import java.util.concurrent.atomic.AtomicBoolean

interface TtsProvider {
    val voices: List<VoiceRecord>
    fun synthesize(voice: VoiceRecord, text: String, language: String, speed: Float, cancelled: AtomicBoolean): AudioData
}
