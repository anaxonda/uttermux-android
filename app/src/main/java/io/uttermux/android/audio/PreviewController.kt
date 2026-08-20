package io.uttermux.android.audio

import io.uttermux.android.config.AudioData
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

data class PreviewState(
    val voiceId:String="",
    val phase:String="idle",
    val message:String="",
)

/** Serializes previews across every screen and cancels generation as well as audio. */
object PreviewController {
    private val generation=AtomicLong()
    private val activeCancellation=AtomicReference<AtomicBoolean?>()
    private val mutableState=MutableStateFlow(PreviewState())
    val state=mutableState.asStateFlow()

    suspend fun play(voiceId:String,produce:(AtomicBoolean)->AudioData)=withContext(Dispatchers.IO){
        val token=generation.incrementAndGet()
        activeCancellation.getAndSet(null)?.set(true)
        Playback.stop()
        val cancelled=AtomicBoolean()
        activeCancellation.set(cancelled)
        mutableState.value=PreviewState(voiceId,"loading","Generating preview")
        try{
            val audio=produce(cancelled)
            check(!cancelled.get()&&generation.get()==token){"Preview cancelled"}
            Playback.play(audio){if(generation.get()==token)mutableState.value=PreviewState(voiceId,"playing","Playing preview")}
            if(!cancelled.get()&&generation.get()==token)mutableState.value=PreviewState(voiceId,"completed","Preview completed")
        }catch(error:Throwable){
            if(generation.get()==token){
                mutableState.value=if(cancelled.get())PreviewState(voiceId,"stopped","Preview stopped")
                    else PreviewState(voiceId,"error",error.message?:"Preview failed")
            }
            if(!cancelled.get())throw error
        }finally{
            activeCancellation.compareAndSet(cancelled,null)
        }
    }

    fun stop(){
        generation.incrementAndGet()
        val voice=mutableState.value.voiceId
        activeCancellation.getAndSet(null)?.set(true)
        Playback.stop()
        mutableState.value=PreviewState(voice,"stopped","Preview stopped")
    }
}
