package io.uttermux.android.provider

import io.uttermux.android.audio.PcmTransform
import io.uttermux.android.audio.TextSegmenter
import io.uttermux.android.config.*
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class MossProvider(private val manager:ModelManager):TtsProvider {
    override val id="moss";override val descriptor=ProviderDescriptor(id,"Local / MOSS ONNX",network=false)
    private data class Variant(val id:String,val label:String,val size:Int,val quantization:String,val ram:Int,val performance:String)
    private val variants=listOf(
        Variant("moss-tts-nano-100m-onnx","MOSS-TTS-Nano 100M FP32",728,"FP32",1400,"heavy"),
    )
    private val presets=listOf(
        Triple("Junhao","zh-CN","male"),Triple("Zhiming","zh-CN","male"),Triple("Xiaoyu","zh-CN","female"),
        Triple("Lingyu","zh-CN","female"),Triple("Weiguo","zh-CN","male"),Triple("Yuewen","zh-CN","female"),
        Triple("Ava","en-US","female"),Triple("Bella","en-US","female"),Triple("Adam","en-US","male"),Triple("Nathan","en-US","male"),Triple("Trump","en-US","male"),
        Triple("Soyo","ja-JP","female"),Triple("Saki","ja-JP","female"),Triple("Mei","ja-JP","female"),Triple("Anon","ja-JP",""),
        Triple("Arisa","ja-JP","female"),Triple("Mortis","ja-JP",""),Triple("Umiri","ja-JP","female"),
    )
    private val supportedLanguages=setOf("en","zh","ja","ko","de","fr","ru","pt","es","it","ar","cs","da","el","fi","hi","hu","nl","pl","tr")
    override val voices=variants.flatMap{variant->presets.map{(name,locale,gender)->VoiceRecord("$id/${variant.id}/$name@$locale","$name · ${variant.label}",Locale.forLanguageTag(locale),id,variant.label,setOf(locale),false,
        "~${variant.size} MB download · high-performance phones only · built-in preset · Apache-2.0",downloadId=variant.id,approxSizeMb=variant.size,license="Apache-2.0",capabilities=setOf("streaming","multilingual","experimental"),gender=gender,accent=locale,quantization=variant.quantization,estimatedRamMb=variant.ram,performanceClass=variant.performance,attribution="Official OpenMOSS model") .copy(languages=supportedLanguages)}}
    override val availableVoices get()=voices.filter(::isAvailable)
    override fun isAvailable(voice:VoiceRecord)=manager.installed(voice.downloadId)
    override fun strategy(voice:VoiceRecord)=StreamStrategy.CODEC_ADAPTIVE
    private val runtimes=linkedMapOf<String,MossRuntime>()
    private fun engine(modelId:String):MossRuntime=synchronized(this){runtimes.getOrPut(modelId){MossRuntime(File(manager.root,modelId))}}
    override fun warm(voice:VoiceRecord){if(manager.installed(voice.downloadId))engine(voice.downloadId)}
    override fun trimMemory()=synchronized(this){runtimes.values.forEach{runtime->synchronized(runtime){runCatching{runtime.close()}}};runtimes.clear()}
    override fun stream(session:PreparedSession,text:String,speed:Float,pitch:Float,cancelled:AtomicBoolean,emit:(AudioChunk)->Boolean){
        val modelId=session.voice.downloadId;require(manager.installed(modelId)){"Download ${session.voice.model} first"};var sequence=0;val preset=session.voice.id.substringAfter("$modelId/").substringBefore('@')
        for(segment in TextSegmenter.split(text,firstTarget=180,nextTarget=320,maxChars=420)){
            if(cancelled.get())throw InterruptedException()
            val runtime=engine(modelId);synchronized(runtime){runtime.stream(segment.text,preset,cancelled){chunk->
                var pcm=PcmTransform.floatToPcm16(chunk.samples);if(speed!=1f)pcm=PcmTransform.resamplePcm16(pcm,chunk.sampleRate,(chunk.sampleRate/speed).toInt().coerceAtLeast(8000))
                emit(AudioChunk(pcm,chunk.sampleRate,segment.range,sequence++,chunk.generatedNanos))
            }}
        }
    }
}
