package io.uttermux.android.config

import java.util.Locale

enum class ProviderKind { GROK, ELEVENLABS, EDGE, SHERPA }

data class VoiceRecord(
    val id: String,
    val name: String,
    val locale: Locale,
    val provider: ProviderKind,
    val model: String,
    val languages: Set<String>,
    val networkRequired: Boolean,
)

data class AudioData(val sampleRate: Int, val pcm16: ByteArray)

object Languages {
    val grok = setOf("en", "ar-EG", "ar-SA", "ar-AE", "bn", "zh", "fr", "de", "hi", "id", "it", "ja", "ko", "pt-BR", "pt-PT", "ru", "es-MX", "es-ES", "tr", "vi")
    fun normalized(value: String): String = value.replace('_', '-').let {
        val parts = it.split('-', limit = 2)
        if (parts.size == 1) parts[0].lowercase() else "${parts[0].lowercase()}-${parts[1].uppercase()}"
    }
    fun matches(capability: String, requested: String): Boolean {
        val a = normalized(capability); val b = normalized(requested)
        return a == b || a.substringBefore('-') == b.substringBefore('-')
    }
}
