package io.uttermux.android.provider

import android.content.Context
import com.k2fsa.sherpa.onnx.*
import io.uttermux.android.R
import io.uttermux.android.config.*
import org.json.JSONArray
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class SherpaProvider(context:Context, val manager:ModelManager=ModelManager(context)) : TtsProvider {
    private data class Spec(val voice:VoiceRecord,val model:String,val speaker:Int)
    private val specs=mutableListOf(
        Spec(VoiceRecord("sherpa/vits-inflect-en-nano-v2/default@en-US","Inflect Nano",Locale.US,ProviderKind.SHERPA,"VITS",setOf("en-US"),false),"vits-inflect-en-nano-v2",0),
        Spec(VoiceRecord("sherpa/kitten-nano-en-v0_8-int8/expr-voice-3-m@en-US","Kitten Voice 3 Male",Locale.US,ProviderKind.SHERPA,"Kitten",setOf("en-US"),false),"kitten-nano-en-v0_8-int8",2),
        Spec(VoiceRecord("sherpa/kokoro-multi-lang-v1_0/am-adam@en-US","Kokoro Adam",Locale.US,ProviderKind.SHERPA,"Kokoro",setOf("en-US"),false),"kokoro-multi-lang-v1_0",11),
    )
    init {
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
                val voice = VoiceRecord("sherpa/$modelId/$speakerName@$localeTag", "$display · Piper", Locale.forLanguageTag(localeTag), ProviderKind.SHERPA,
                    "Piper ${item.getString("quality")}", setOf(localeTag), false, description, sampleUrl, modelId, downloadable)
                specs += Spec(voice, modelId, sid)
            }
        }
    }
    override val voices get()=specs.map{it.voice}
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
    private fun formatBytes(value:Long):String = if(value<=0) "preview only" else "${value/1024/1024} MB"
}
