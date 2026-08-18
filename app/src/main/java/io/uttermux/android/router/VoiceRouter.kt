package io.uttermux.android.router

import io.uttermux.android.config.*
import io.uttermux.android.provider.TtsProvider
import java.util.concurrent.atomic.AtomicBoolean

class VoiceRouter(private val settings: AppSettings, providers: List<TtsProvider>) {
    private val providers = providers.associateBy { it.voices.firstOrNull()?.provider }
    val voices = providers.flatMap { it.voices }
    fun voice(id: String): VoiceRecord? = voices.firstOrNull { it.id == id || it.id.substringBefore('@') == id.substringBefore('@') }
    fun candidates(requestedVoice: String?, language: String): List<VoiceRecord> {
        val result = mutableListOf<VoiceRecord>()
        fun add(id: String?) { id?.takeIf(String::isNotBlank)?.let(::voice)?.let { if (it !in result) result += it } }
        if (!requestedVoice.isNullOrBlank() && requestedVoice != "uttermux:auto") add(requestedVoice)
        add(settings.defaultVoice)
        add(settings.route(language))
        voices.filter { record -> record.languages.any { Languages.matches(it, language) } }.forEach { if (it !in result) result += it }
        return result
    }
    fun synthesize(requestedVoice: String?, text: String, language: String, speed: Float, cancelled: AtomicBoolean): AudioData {
        var failure: Throwable? = null
        for (voice in candidates(requestedVoice, language)) {
            if (cancelled.get()) throw InterruptedException()
            try { return providers.getValue(voice.provider).synthesize(voice, text, language, speed, cancelled) }
            catch (error: Throwable) { failure = error }
        }
        throw RuntimeException("No voice could synthesize $language", failure)
    }
}
