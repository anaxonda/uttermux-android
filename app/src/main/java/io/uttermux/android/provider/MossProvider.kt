package io.uttermux.android.provider

import io.uttermux.android.audio.PcmTransform
import io.uttermux.android.config.*
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class MossProvider(private val manager:ModelManager):TtsProvider {
    override val id="moss";override val descriptor=ProviderDescriptor(id,"Local / MOSS ONNX",network=false)
    private val modelId="moss-tts-nano-100m-onnx"
    private val presets=listOf(
        Triple("Junhao","zh-CN","male"),Triple("Zhiming","zh-CN","male"),Triple("Xiaoyu","zh-CN","female"),
        Triple("Ava","en-US","female"),Triple("Bella","en-US","female"),Triple("Adam","en-US","male"),Triple("Nathan","en-US","male"),
        Triple("Soyo","ja-JP","female"),Triple("Saki","ja-JP","female"),Triple("Mei","ja-JP","female"),
    )
    private val supportedLanguages=setOf("zh","en","de","es","fr","ja","it","hu","ko","ru","fa","ar","pl","pt","cs","da","sv","el","tr")
    override val voices=presets.map{(name,locale,gender)->VoiceRecord("$id/$modelId/$name@$locale","$name · MOSS Nano",Locale.forLanguageTag(locale),id,"MOSS-TTS-Nano 100M ONNX",setOf(locale),false,
        "~760 MB download · multilingual · official built-in preset · Apache-2.0",downloadId=modelId,approxSizeMb=760,license="Apache-2.0",capabilities=setOf("streaming","multilingual"),gender=gender,accent=locale,quantization="FP32",estimatedRamMb=1400,performanceClass="heavy",attribution="Official OpenMOSS built-in preset").copy(languages=supportedLanguages)}
    override val availableVoices get()=if(manager.installed(modelId))voices else emptyList()
    override fun isAvailable(voice:VoiceRecord)=manager.installed(modelId)
    override fun strategy(voice:VoiceRecord)=StreamStrategy.CODEC_ADAPTIVE
    @Volatile private var runtime:MossRuntime?=null
    private fun engine():MossRuntime=synchronized(this){runtime?:MossRuntime(File(manager.root,modelId)).also{runtime=it}}
    override fun warm(voice:VoiceRecord){if(manager.installed(modelId))engine()}
    override fun stream(session:PreparedSession,text:String,speed:Float,pitch:Float,cancelled:AtomicBoolean,emit:(AudioChunk)->Boolean){
        require(manager.installed(modelId)){"Download MOSS-TTS-Nano first"};val range=TextRange(0,text.length);var sequence=0
        engine().stream(text,session.voice.id.substringAfter("$modelId/").substringBefore('@'),cancelled){chunk->
            var pcm=PcmTransform.floatToPcm16(chunk.samples);if(speed!=1f)pcm=PcmTransform.resamplePcm16(pcm,chunk.sampleRate,(chunk.sampleRate/speed).toInt().coerceAtLeast(8000))
            emit(AudioChunk(pcm,chunk.sampleRate,range,sequence++,chunk.generatedNanos))
        }
    }
}
