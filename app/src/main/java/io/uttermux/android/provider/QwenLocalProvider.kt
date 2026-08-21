package io.uttermux.android.provider

import android.content.Context
import com.qwen.tts.studio.engine.QwenEngine
import io.uttermux.android.config.*
import io.uttermux.android.provider.local.LocalRuntime
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

const val QWEN_MODEL="qwen3-tts-0.6b-base-q4km"

class QwenLocalRuntime(private val context:Context,private val manager:ModelManager,private val profiles:PocketProfileStore):LocalRuntime {
    override val id="qwen-gguf"
    override val compiledBackends=setOf("CPU")
    private val lock=Any()
    private var engine:QwenEngine?=null
    private val settings=AppSettings(context)

    override fun supports(voice:VoiceRecord)=voice.provider==ProviderIds.QWEN_LOCAL
    private fun loaded():QwenEngine=synchronized(lock){engine?:QwenEngine().also{created->
        val threads=settings.engineThreads.takeIf{it>0}?:Runtime.getRuntime().availableProcessors().coerceIn(1,4)
        created.setCpuThreads(threads)
        val root=File(manager.root,QWEN_MODEL)
        check(created.loadModels(root.absolutePath,"qwen-talker-0.6b-base-Q4_K_M.gguf")){created.lastError() ?: "Qwen model load failed"}
        engine=created
    }}
    override fun warm(voice:VoiceRecord){if(manager.installed(QWEN_MODEL))loaded()}
    override fun trimMemory(){synchronized(lock){engine?.close();engine=null}}
    override fun stream(session:PreparedSession,text:String,speed:Float,pitch:Float,cancelled:AtomicBoolean,emit:(AudioChunk)->Boolean){
        require(manager.installed(QWEN_MODEL)){"Download Qwen3-TTS 0.6B Base first"}
        val began=System.nanoTime();var sequence=0
        val profileId=session.voice.id.substringAfter("/custom-","").substringBefore('@')
        val reference=profiles.profiles().firstOrNull{it.id==profileId}?.referenceFile
        val result=loaded().stream(text,referenceWav=reference,params=QwenEngine.NativeParams(languageId=languageId(session.language),maxAudioTokens=(text.length*5).coerceIn(128,2048)),callback=QwenEngine.AudioChunkCallback{samples,rate,_,_,_,_,startByte,endByte,alignment,_ ->
            if(cancelled.get())return@AudioChunkCallback false
            val pcm=ByteArray(samples.size*2);samples.forEachIndexed{i,value->val sample=(value.coerceIn(-1f,1f)*32767).toInt();pcm[i*2]=sample.toByte();pcm[i*2+1]=(sample shr 8).toByte()}
            val range=if(alignment>0&&startByte>=0&&endByte>startByte)TextRange(byteToUtf16(text,startByte),byteToUtf16(text,endByte)) else TextRange(0,text.length)
            emit(AudioChunk(pcm,rate,range,sequence++,System.nanoTime()-began))&&!cancelled.get()
        })
        if(cancelled.get())throw InterruptedException()
        check(result.success){result.errorMsg?:loaded().lastError()?:"Qwen synthesis failed"}
    }
    private fun byteToUtf16(text:String,offset:Int):Int {
        if(offset<=0)return 0
        val bytes=text.toByteArray(Charsets.UTF_8);return bytes.copyOfRange(0,offset.coerceAtMost(bytes.size)).toString(Charsets.UTF_8).length
    }
    private fun languageId(tag:String)=when(Languages.normalized(tag).substringBefore('-')){
        "en"->2050;"de"->2053;"es"->2054;"zh"->2055;"ja"->2058;"fr"->2061;"ko"->2064;"ru"->2069;"it"->2070;"pt"->2071;else->-1
    }
}

class QwenLocalProvider(context:Context,private val manager:ModelManager):TtsProvider {
    override val id=ProviderIds.QWEN_LOCAL
    override val descriptor=ProviderDescriptor(id,"Qwen on-device",network=false,experimental=true,note="Large GGUF runtime; benchmark on this device before reader use.")
    private val profiles=PocketProfileStore(context,"qwen-gguf",QWEN_MODEL,"qwen")
    private val runtime=QwenLocalRuntime(context,manager,profiles)
    override val voices get()=profiles.profiles().map{profile->VoiceRecord(
        id="qwen-local/$QWEN_MODEL/custom-${profile.id}@${profile.language}",name="${profile.name} · Qwen clone",
        locale=Locale.forLanguageTag(profile.language),provider=id,model="Qwen3-TTS 0.6B Base Q4_K_M",languages=setOf(profile.language),networkRequired=false,
        description="Private on-device Qwen voice profile · device preview",downloadId=QWEN_MODEL,approxSizeMb=843,license="Apache-2.0",
        capabilities=setOf("streaming","multilingual","voice-cloning","device-preview"),quantization="Q4_K_M",estimatedRamMb=3000,performanceClass="heavy",
        sourceUrl="https://github.com/QwenLM/Qwen3-TTS",library="Qwen3-TTS",modelVersion="0.6B Base")}
    override val availableVoices get()=if(manager.installed(QWEN_MODEL))voices else emptyList()
    override fun isAvailable(voice:VoiceRecord)=manager.installed(QWEN_MODEL)
    override fun strategy(voice:VoiceRecord)=StreamStrategy.CODEC_ADAPTIVE
    override fun warm(voice:VoiceRecord)=runtime.warm(voice)
    override fun trimMemory()=runtime.trimMemory()
    override fun stream(session:PreparedSession,text:String,speed:Float,pitch:Float,cancelled:AtomicBoolean,emit:(AudioChunk)->Boolean)=runtime.stream(session,text,speed,pitch,cancelled,emit)
}
