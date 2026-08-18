package io.uttermux.android.provider

import android.content.Context
import com.k2fsa.sherpa.onnx.*
import io.uttermux.android.config.*
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class SherpaProvider(context:Context) : TtsProvider {
    val manager=ModelManager(context)
    private data class Spec(val voice:VoiceRecord,val model:String,val speaker:Int)
    private val specs=listOf(
        Spec(VoiceRecord("sherpa/vits-piper-en_US-lessac-medium/lessac@en-US","Piper Lessac",Locale.US,ProviderKind.SHERPA,"Piper",setOf("en-US"),false),"vits-piper-en_US-lessac-medium",0),
        Spec(VoiceRecord("sherpa/vits-inflect-en-nano-v2/default@en-US","Inflect Nano",Locale.US,ProviderKind.SHERPA,"VITS",setOf("en-US"),false),"vits-inflect-en-nano-v2",0),
        Spec(VoiceRecord("sherpa/kitten-nano-en-v0_8-int8/expr-voice-3-m@en-US","Kitten Voice 3 Male",Locale.US,ProviderKind.SHERPA,"Kitten",setOf("en-US"),false),"kitten-nano-en-v0_8-int8",2),
        Spec(VoiceRecord("sherpa/kokoro-multi-lang-v1_0/am-adam@en-US","Kokoro Adam",Locale.US,ProviderKind.SHERPA,"Kokoro",setOf("en-US"),false),"kokoro-multi-lang-v1_0",11),
    )
    override val voices=specs.map{it.voice}
    private val engines=object:LinkedHashMap<String,OfflineTts>(3,.75f,true){ override fun removeEldestEntry(e:MutableMap.MutableEntry<String,OfflineTts>?):Boolean { val remove=size>2;if(remove)e?.value?.release();return remove } }
    override fun synthesize(voice:VoiceRecord,text:String,language:String,speed:Float,cancelled:AtomicBoolean):AudioData {
        val spec=specs.first{it.voice.id==voice.id};require(manager.installed(spec.model)){"Download ${spec.model} first"}
        val tts=synchronized(engines){engines.getOrPut(spec.model){create(manager.model(spec.model),File(manager.root,spec.model))}}
        if(cancelled.get())throw InterruptedException()
        val generated=tts.generateWithConfig(text,GenerationConfig(speed=speed,sid=spec.speaker))
        val bytes=ByteArray(generated.samples.size*2);generated.samples.forEachIndexed{i,value->val sample=(value.coerceIn(-1f,1f)*32767).toInt();bytes[i*2]=sample.toByte();bytes[i*2+1]=(sample shr 8).toByte()}
        return AudioData(generated.sampleRate,bytes)
    }
    private fun create(model:LocalModel,root:File):OfflineTts {
        fun path(name:String)=if(name.isBlank())"" else File(root,name).absolutePath
        val config=OfflineTtsModelConfig(numThreads=4)
        when(model.engine){
            "vits"->config.vits=OfflineTtsVitsModelConfig(model=path(model.model),tokens=path(model.tokens),dataDir=path(model.dataDir))
            "kokoro"->config.kokoro=OfflineTtsKokoroModelConfig(model=path(model.model),voices=path(model.voices),tokens=path(model.tokens),dataDir=path(model.dataDir),lexicon=path(model.lexicon),lang="en-us")
            "kitten"->config.kitten=OfflineTtsKittenModelConfig(model=path(model.model),voices=path(model.voices),tokens=path(model.tokens),dataDir=path(model.dataDir))
        }
        return OfflineTts(OfflineTtsConfig(model=config,maxNumSentences=1))
    }
}
