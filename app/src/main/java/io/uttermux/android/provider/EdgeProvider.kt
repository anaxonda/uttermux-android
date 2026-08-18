package io.uttermux.android.provider

import io.uttermux.android.config.*
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class EdgeProvider : TtsProvider {
    override val voices = listOf(
        VoiceRecord("edge/en-US-AriaNeural@en-US", "Aria · Edge", Locale.US, ProviderKind.EDGE, "Edge", setOf("en-US"), true),
        VoiceRecord("edge/fr-FR-DeniseNeural@fr-FR", "Denise · Edge", Locale.FRANCE, ProviderKind.EDGE, "Edge", setOf("fr-FR"), true),
    )
    override fun synthesize(voice: VoiceRecord, text: String, language: String, speed: Float, cancelled: AtomicBoolean): AudioData =
        throw UnsupportedOperationException("Edge support is experimental and not available yet")
}
