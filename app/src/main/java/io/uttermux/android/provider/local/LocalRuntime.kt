package io.uttermux.android.provider.local

import io.uttermux.android.config.AudioChunk
import io.uttermux.android.config.PreparedSession
import io.uttermux.android.config.VoiceRecord
import java.util.concurrent.atomic.AtomicBoolean

/** Required behavior for every on-device inference implementation. */
interface LocalRuntime : AutoCloseable {
    val id:String
    val compiledBackends:Set<String>
    fun supports(voice:VoiceRecord):Boolean
    fun warm(voice:VoiceRecord)
    fun stream(session:PreparedSession,text:String,speed:Float,pitch:Float,cancelled:AtomicBoolean,emit:(AudioChunk)->Boolean)
    fun trimMemory()
    override fun close()=trimMemory()
}

class LocalRuntimeRegistry(runtimes:List<LocalRuntime>) {
    private val byId=runtimes.associateBy(LocalRuntime::id)
    fun runtime(id:String)=requireNotNull(byId[id]){"Local runtime is not compiled: $id"}
    fun compiled()=byId.keys
    fun trimMemory()=byId.values.forEach(LocalRuntime::trimMemory)
}
