package io.uttermux.android.config

enum class StreamStrategy { DIRECT_STREAM, SEGMENTED_LOCAL, CODEC_ADAPTIVE, BUFFERED }

data class AudioFormatSpec(val sampleRate: Int = 24_000, val channels: Int = 1)

data class TextRange(val start: Int, val endExclusive: Int)

data class AudioChunk(
    val pcm16: ByteArray,
    val sampleRate: Int,
    val range: TextRange,
    val sequence: Int,
    val generatedNanos: Long = 0,
)

data class PreparedSession(
    val voice: VoiceRecord,
    val language: String,
    val format: AudioFormatSpec = AudioFormatSpec(),
    val strategy: StreamStrategy,
)

data class RoutingSession(val language:String,val candidates:List<PreparedSession>) {
    val primary:PreparedSession get()=candidates.first()
}
