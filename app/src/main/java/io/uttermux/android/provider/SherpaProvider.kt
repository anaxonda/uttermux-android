package io.uttermux.android.provider

import android.content.Context
import com.k2fsa.sherpa.onnx.*
import io.uttermux.android.R
import io.uttermux.android.audio.PcmTransform
import io.uttermux.android.audio.CompressedAudioDecoder
import io.uttermux.android.audio.TextSegmenter
import io.uttermux.android.config.*
import org.json.JSONArray
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class SherpaProvider(private val context:Context, val manager:ModelManager=ModelManager(context)) : TtsProvider {
    private val settings=AppSettings(context)
    private class ChunkCallback(val block:(FloatArray)->Int):Function1<FloatArray,Int> {
        override fun invoke(samples:FloatArray):Int=block(samples)
    }
    override val id=ProviderIds.SHERPA
    override val descriptor=ProviderDescriptor(id,"Local / sherpa-onnx",network=false)
    private data class Spec(val voice:VoiceRecord,val model:String,val speaker:Int,val referenceFile:String="")
    private val specs=mutableListOf(
        Spec(VoiceRecord("sherpa/vits-inflect-en-nano-v2/default@en-US","Inflect Nano",Locale.US,ProviderIds.SHERPA,"VITS",setOf("en-US"),false),"vits-inflect-en-nano-v2",0),
        Spec(VoiceRecord("sherpa/vits-inflect-en-micro-v2/default@en-US","Inflect Micro",Locale.US,ProviderIds.SHERPA,"VITS",setOf("en-US"),false,
            "43 MB · Apache-2.0","","vits-inflect-en-micro-v2",true,approxSizeMb=43,license="Apache-2.0"),"vits-inflect-en-micro-v2",0),
        Spec(VoiceRecord("sherpa/matcha-icefall-en_US-ljspeech/ljspeech@en-US","LJSpeech · Matcha",Locale.US,ProviderIds.SHERPA,"Matcha",setOf("en-US"),false,
            "77 MB · female · MIT","https://github.com/HayaiApp/HayaiTTS-samples/releases/download/samples-3/matcha-icefall-en_US-ljspeech.mp3","matcha-icefall-en_US-ljspeech",true,approxSizeMb=77,license="MIT"),"matcha-icefall-en_US-ljspeech",0),
    )
    private val supertonicLanguages=setOf("ar","bg","hr","cs","da","nl","en-US","et","fi","fr-FR","de-DE","el","hi","hu","id","ja-JP","it-IT","ko-KR","lv","lt","pl","pt","ro","ru","sk","es-ES","sl","sv","tr","uk","vi")
    init {
        val kittenVoices=listOf(
            Triple("expr-voice-2-m","Jasper", "male"),Triple("expr-voice-2-f","Bella","female"),
            Triple("expr-voice-3-m","Bruno","male"),Triple("expr-voice-3-f","Luna","female"),
            Triple("expr-voice-4-m","Hugo","male"),Triple("expr-voice-4-f","Rosie","female"),
            Triple("expr-voice-5-m","Leo","male"),Triple("expr-voice-5-f","Kiki","female"),
        )
        kittenVoices.forEachIndexed{sid,(key,name,gender)->
            specs+=Spec(VoiceRecord("sherpa/kitten-nano-en-v0_8-int8/$key@en-US","$name · Kitten Nano",Locale.US,ProviderIds.SHERPA,"Kitten Nano 0.8 INT8",setOf("en-US"),false,
                "~31 MB · tiny CPU model · Apache-2.0",downloadId="kitten-nano-en-v0_8-int8",approxSizeMb=31,license="Apache-2.0",gender=gender,quantization="INT8",estimatedRamMb=120,performanceClass="fast"),"kitten-nano-en-v0_8-int8",sid)
        }
        val kokoroNames=listOf(
            "af_alloy","af_aoede","af_bella","af_heart","af_jessica","af_kore","af_nicole","af_nova","af_river","af_sarah","af_sky",
            "am_adam","am_echo","am_eric","am_fenrir","am_liam","am_michael","am_onyx","am_puck","am_santa",
            "bf_alice","bf_emma","bf_isabella","bf_lily","bm_daniel","bm_fable","bm_george","bm_lewis",
            "ef_dora","em_alex","ff_siwis","hf_alpha","hf_beta","hm_omega","hm_psi","if_sara","im_nicola",
            "jf_alpha","jf_gongitsune","jf_nezumi","jf_tebukuro","jm_kumo","pf_dora","pm_alex","pm_santa",
            "zf_xiaobei","zf_xiaoni","zf_xiaoxiao","zf_xiaoyi","zm_yunjian","zm_yunxi","zm_yunxia","zm_yunyang",
        )
        fun kokoroLocale(key:String)=when(key.first()){'a'->"en-US";'b'->"en-GB";'e'->"es-ES";'f'->"fr-FR";'h'->"hi-IN";'i'->"it-IT";'j'->"ja-JP";'p'->"pt-BR";'z'->"zh-CN";else->"en-US"}
        kokoroNames.forEachIndexed{sid,key->
            val locale=kokoroLocale(key);val display=key.substringAfter('_').replaceFirstChar(Char::uppercase)
            val gender=if(key[1]=='f')"female" else "male"
            specs+=Spec(VoiceRecord("sherpa/kokoro-multi-lang-v1_0/${key.replace('_','-')}@$locale","$display · Kokoro",Locale.forLanguageTag(locale),ProviderIds.SHERPA,"Kokoro 82M",setOf(locale),false,
                "~350 MB package · $gender · Apache-2.0",downloadId="kokoro-multi-lang-v1_0",approxSizeMb=350,license="Apache-2.0",gender=gender,quantization="FP32",estimatedRamMb=650,performanceClass="balanced"),"kokoro-multi-lang-v1_0",sid)
        }
        listOf(
            Triple("alba-casual","Alba Casual","presets/alba-casual.wav"),Triple("alba-announcer","Alba Announcer","presets/alba-announcer.wav"),
            Triple("alba-merchant","Alba Merchant","presets/alba-merchant.wav"),Triple("alba-moment","Alba · A Moment By","presets/alba-moment.wav"),
        ).forEach{(key,name,file)->
            val model="sherpa-onnx-pocket-tts-int8-2026-01-26"
            specs+=Spec(VoiceRecord("sherpa/$model/$key@en-US","$name · Pocket",Locale.US,ProviderIds.SHERPA,"Pocket TTS INT8",setOf("en-US"),false,
                "~176 MB · licensed preset · CC BY 4.0",downloadId=model,approxSizeMb=176,license="CC BY 4.0",gender="female",quantization="INT8",estimatedRamMb=420,performanceClass="balanced",attribution="Voice performed by Alba MacKenna; source: Kyutai tts-voices"),model,0,file)
        }
        val supertonicModel="sherpa-onnx-supertonic-3-tts-int8-2026-05-11"
        val supertonicSampleGroups=listOf(0,3,3,0,3,0,0,3,2,1)
        (0 until 10).forEach { sid ->
            val preview="https://github.com/HayaiApp/HayaiTTS-samples/releases/download/samples-${supertonicSampleGroups[sid]}/$supertonicModel"+"__sid${sid}__en-US.mp3"
            specs+=Spec(VoiceRecord("sherpa/$supertonicModel/style-${sid+1}@en-US","Style ${sid+1} · Supertonic 3",Locale.US,ProviderIds.SHERPA,"Supertonic 3",supertonicLanguages,false,
                "129 MB · 31 languages · OpenRAIL","$preview",supertonicModel,true,experimental=true,approxSizeMb=129,license="OpenRAIL",capabilities=setOf("multilingual","streaming")),supertonicModel,sid)
        }
        val catalog = JSONArray(context.resources.openRawResource(R.raw.piper_catalog).bufferedReader().use { it.readText() })
        for (i in 0 until catalog.length()) {
            val item = catalog.getJSONObject(i); val key = item.getString("key"); val modelId = "vits-piper-$key"
            val localeTag = item.getString("language").replace('_', '-'); val speakers = item.getInt("speakers")
            val downloadUrl = item.optString("download_url"); val checksum=item.optString("sha256"); val modelFile = item.getString("model_file")
            val downloadable=downloadUrl.isNotBlank()&&checksum.isNotBlank()
            if (downloadable) manager.register(LocalModel(modelId,"vits",downloadUrl,checksum,modelFile))
            val named = item.getJSONObject("speaker_ids"); val speakerPairs = if (named.length() > 0)
                named.keys().asSequence().map { it to named.getInt(it) }.toList()
            else (0 until speakers.coerceAtLeast(1)).map { (if (speakers > 1) "speaker-$it" else item.getString("name")) to it }
            speakerPairs.forEach { (speakerName, sid) ->
                val display = speakerName.replace('_',' ').replaceFirstChar(Char::uppercase)
                val description = "${item.getString("language_name")} · ${item.optString("country")} · ${item.getString("quality")} · ${formatBytes(item.optLong("download_size"))}"
                val sampleUrl=item.optString("sample_url").replace("speaker_0.mp3","speaker_${sid}.mp3")
                val voice = VoiceRecord("sherpa/$modelId/$speakerName@$localeTag", "$display · Piper", Locale.forLanguageTag(localeTag), ProviderIds.SHERPA,
                    "Piper ${item.getString("quality")}", setOf(localeTag), false, description, sampleUrl, modelId, downloadable,
                    approxSizeMb=(item.optLong("download_size")/1024/1024).toInt(),license=item.optString("license","Model-specific"),quantization="ONNX",performanceClass="fast")
                specs += Spec(voice, modelId, sid)
            }
        }
    }
    override val voices get()=specs.map{it.voice}
    override val availableVoices get()=manager.installedIds().let{installed->specs.filter{it.model in installed}.map{it.voice}}
    override fun isAvailable(voice:VoiceRecord)=specs.firstOrNull{it.voice.id==voice.id}?.let{runCatching{manager.installed(it.model)}.getOrDefault(false)}==true
    private val engines=object:LinkedHashMap<String,OfflineTts>(3,.75f,true){ override fun removeEldestEntry(e:MutableMap.MutableEntry<String,OfflineTts>?):Boolean { val remove=size>settings.modelCacheSize;if(remove)e?.value?.release();return remove } }
    private data class Reference(val samples:FloatArray,val sampleRate:Int)
    private val references=mutableMapOf<String,Reference>()
    private fun engineKey(spec:Spec)="${spec.model}@${spec.voice.locale.toLanguageTag()}"
    private fun engine(spec:Spec):OfflineTts=synchronized(engines){engines.getOrPut(engineKey(spec)){create(manager.model(spec.model),File(manager.root,spec.model),spec.voice.locale.toLanguageTag())}}
    override fun strategy(voice:VoiceRecord):StreamStrategy=specs.firstOrNull{it.voice.id==voice.id}?.let{manager.model(it.model).engine}
        .let{if(it=="pocket"||it=="zipvoice")StreamStrategy.CODEC_ADAPTIVE else StreamStrategy.SEGMENTED_LOCAL}
    override fun warm(voice:VoiceRecord){specs.firstOrNull{it.voice.id==voice.id}?.takeIf{manager.installed(it.model)}?.let(::engine)}
    override fun synthesize(voice:VoiceRecord,text:String,language:String,speed:Float,cancelled:AtomicBoolean):AudioData {
        val spec=specs.first{it.voice.id==voice.id};require(manager.installed(spec.model)){"Download ${spec.model} first"}
        val tts=engine(spec)
        if(cancelled.get())throw InterruptedException()
        val generated=tts.generateWithConfig(text,generationConfig(spec,speed))
        val bytes=ByteArray(generated.samples.size*2);generated.samples.forEachIndexed{i,value->val sample=(value.coerceIn(-1f,1f)*32767).toInt();bytes[i*2]=sample.toByte();bytes[i*2+1]=(sample shr 8).toByte()}
        return AudioData(generated.sampleRate,bytes)
    }
    override fun stream(session:PreparedSession,text:String,speed:Float,pitch:Float,cancelled:AtomicBoolean,emit:(AudioChunk)->Boolean) {
        val voice=session.voice
        val spec=specs.first{it.voice.id==voice.id};require(manager.installed(spec.model)){"Download ${spec.model} first"}
        val tts=engine(spec);var sequence=0
        for(segment in TextSegmenter.split(text)) {
            if(cancelled.get())throw InterruptedException()
            val began=System.nanoTime()
            if(manager.model(spec.model).engine=="vits") {
                val generated=tts.generateWithConfig(segment.text,generationConfig(spec,speed))
                val audio=PcmTransform.trimSilence(pcm(generated.samples),generated.sampleRate)
                if(audio.isNotEmpty()&&!emit(AudioChunk(audio,generated.sampleRate,segment.range,sequence++,System.nanoTime()-began)))return
            } else {
                var callbackUsed=false;var chunkBegan=began
                val generated=tts.generateWithConfig(segment.text,generationConfig(spec,speed),ChunkCallback{samples->
                    callbackUsed=true;val now=System.nanoTime();val elapsed=now-chunkBegan;chunkBegan=now
                    if(cancelled.get()||!emit(AudioChunk(pcm(samples),tts.sampleRate(),segment.range,sequence++,elapsed)))0 else 1
                })
                if(cancelled.get())throw InterruptedException()
                if(!callbackUsed&&!emit(AudioChunk(pcm(generated.samples),generated.sampleRate,segment.range,sequence++,System.nanoTime()-began)))return
            }
        }
    }
    private fun pcm(samples:FloatArray):ByteArray {
        val bytes=ByteArray(samples.size*2);samples.forEachIndexed{i,value->val sample=(value.coerceIn(-1f,1f)*32767).toInt();bytes[i*2]=sample.toByte();bytes[i*2+1]=(sample shr 8).toByte()};return bytes
    }
    private fun generationConfig(spec:Spec,speed:Float):GenerationConfig {
        if(spec.referenceFile.isBlank())return GenerationConfig(speed=speed,sid=spec.speaker)
        val reference=synchronized(references){references.getOrPut("${spec.model}/${spec.referenceFile}"){
            val file=File(File(manager.root,spec.model),spec.referenceFile);require(file.isFile){"Pocket preset audio is missing; reinstall the model"}
            val audio=CompressedAudioDecoder.decode(context,file.readBytes(),"wav")
            Reference(FloatArray(audio.pcm16.size/2){i->
                val lo=audio.pcm16[i*2].toInt() and 255;val hi=audio.pcm16[i*2+1].toInt();((hi shl 8 or lo).toShort()/32768f)
            },audio.sampleRate)
        }}
        return GenerationConfig(speed=speed,sid=spec.speaker,referenceAudio=reference.samples,referenceSampleRate=reference.sampleRate,numSteps=settings.pocketNumSteps)
    }
    private fun create(model:LocalModel,root:File,language:String):OfflineTts {
        fun path(name:String)=if(name.isBlank())"" else File(root,name).absolutePath
        val config=OfflineTtsModelConfig(numThreads=4)
        when(model.engine){
            "vits"->config.vits=OfflineTtsVitsModelConfig(model=path(model.model),tokens=path(model.tokens),dataDir=path(model.dataDir))
            "matcha"->config.matcha=OfflineTtsMatchaModelConfig(acousticModel=path(model.model),vocoder=path(model.secondaryFile),tokens=path(model.tokens),dataDir=path(model.dataDir),lexicon=path(model.lexicon))
            "kokoro"->{
                val lexicons=listOf("lexicon-us-en.txt","lexicon-gb-en.txt","lexicon-zh.txt").map{File(root,it)}.filter(File::isFile).joinToString(","){it.absolutePath}
                config.kokoro=OfflineTtsKokoroModelConfig(model=path(model.model),voices=path(model.voices),tokens=path(model.tokens),dataDir=path(model.dataDir),lexicon=lexicons,lang=language.lowercase())
            }
            "kitten"->config.kitten=OfflineTtsKittenModelConfig(model=path(model.model),voices=path(model.voices),tokens=path(model.tokens),dataDir=path(model.dataDir))
            "zipvoice"->config.zipvoice=OfflineTtsZipVoiceModelConfig(tokens=path("tokens.txt"),encoder=path("encoder.int8.onnx"),decoder=path("decoder.int8.onnx"),vocoder=path("vocos_24khz.onnx"),dataDir=path("espeak-ng-data"),lexicon=path("lexicon.txt"))
            "pocket"->config.pocket=OfflineTtsPocketModelConfig(lmFlow=path("lm_flow.int8.onnx"),lmMain=path("lm_main.int8.onnx"),encoder=path("encoder.onnx"),decoder=path("decoder.int8.onnx"),textConditioner=path("text_conditioner.onnx"),vocabJson=path("vocab.json"),tokenScoresJson=path("token_scores.json"))
            "supertonic"->config.supertonic=OfflineTtsSupertonicModelConfig(durationPredictor=path("duration_predictor.int8.onnx"),textEncoder=path("text_encoder.int8.onnx"),vectorEstimator=path("vector_estimator.int8.onnx"),vocoder=path("vocoder.int8.onnx"),ttsJson=path("tts.json"),unicodeIndexer=path("unicode_indexer.bin"),voiceStyle=path("voice.bin"))
        }
        return OfflineTts(OfflineTtsConfig(model=config,maxNumSentences=1))
    }
    private fun formatBytes(value:Long):String = if(value<=0) "preview only" else "${value/1024/1024} MB"
}
