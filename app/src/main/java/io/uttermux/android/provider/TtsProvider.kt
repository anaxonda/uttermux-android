package io.uttermux.android.provider

import io.uttermux.android.config.AudioData
import io.uttermux.android.config.ProviderKind
import io.uttermux.android.config.VoiceRecord
import java.util.concurrent.atomic.AtomicBoolean

interface TtsProvider {
    val kind: ProviderKind
    val voices: List<VoiceRecord>
    fun isAvailable(voice: VoiceRecord): Boolean = true
    fun synthesize(voice: VoiceRecord, text: String, language: String, speed: Float, cancelled: AtomicBoolean): AudioData
    fun stream(voice: VoiceRecord, text: String, language: String, speed: Float, pitch: Float,
               cancelled: AtomicBoolean, emit: (AudioData) -> Boolean) {
        emit(synthesize(voice, text, language, speed, cancelled))
    }
}
