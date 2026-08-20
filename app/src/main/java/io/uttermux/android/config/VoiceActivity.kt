package io.uttermux.android.config

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class VoiceActivityState(
    val configuredDefault:String="",
    val effectiveDefault:String="",
    val activeVoice:String="",
    val language:String="",
    val client:String="",
    val status:String="idle",
    val fallbackReason:String="",
)

object VoiceActivity {
    private val mutable=MutableStateFlow(VoiceActivityState())
    val state:StateFlow<VoiceActivityState> = mutable.asStateFlow()
    fun defaults(configured:String,effective:String)=mutable.value.let{
        mutable.value=it.copy(configuredDefault=configured,effectiveDefault=effective)
    }
    fun speaking(voice:String,language:String,client:String,fallback:String="")=mutable.value.let{
        mutable.value=it.copy(activeVoice=voice,language=language,client=client,status="speaking",fallbackReason=fallback)
    }
    fun status(value:String)=mutable.value.let{mutable.value=it.copy(status=value)}
    fun idle()=mutable.value.let{mutable.value=it.copy(activeVoice="",client="",status="idle",fallbackReason="")}
}
