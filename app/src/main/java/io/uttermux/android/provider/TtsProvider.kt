package io.uttermux.android.provider

import io.uttermux.android.config.AudioData
import io.uttermux.android.config.AudioChunk
import io.uttermux.android.config.PreparedSession
import io.uttermux.android.config.ProviderDescriptor
import io.uttermux.android.config.StreamStrategy
import io.uttermux.android.config.TextRange
import io.uttermux.android.config.VoiceRecord
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean

interface TtsProvider {
    val id:String
    val descriptor:ProviderDescriptor get()=ProviderDescriptor(id,id.replaceFirstChar(Char::uppercase))
    val voices: List<VoiceRecord>
    val availableVoices: List<VoiceRecord> get() = voices.filter(::isAvailable)
    fun isAvailable(voice: VoiceRecord): Boolean = true
    fun refresh() {}
    fun strategy(voice:VoiceRecord)=if(voice.networkRequired)StreamStrategy.DIRECT_STREAM else StreamStrategy.SEGMENTED_LOCAL
    fun prepare(voice:VoiceRecord,language:String)=PreparedSession(voice,language,strategy=strategy(voice))
    fun warm(voice:VoiceRecord) {}
    fun trimMemory() {}
    fun stream(session:PreparedSession,text:String,speed:Float,pitch:Float,cancelled:AtomicBoolean,emit:(AudioChunk)->Boolean)
    fun synthesize(voice: VoiceRecord, text: String, language: String, speed: Float, cancelled: AtomicBoolean): AudioData {
        val output=ByteArrayOutputStream();var rate=24_000;var sequence=0
        stream(prepare(voice,language),text,speed,1f,cancelled){chunk->rate=chunk.sampleRate;sequence=chunk.sequence;output.write(chunk.pcm16);!cancelled.get()}
        if(sequence<0)throw InterruptedException()
        return AudioData(rate,output.toByteArray())
    }
}
