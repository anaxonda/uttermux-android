package io.uttermux.android

import io.uttermux.android.config.Languages
import io.uttermux.android.config.ProviderIds
import io.uttermux.android.config.VoiceRecord

data class VoiceSearchEntry(
    val voice:VoiceRecord,
    val ready:Boolean,
    val library:String,
    val model:String,
    val searchableVoice:String,
    val searchableLanguage:String,
    val searchableLibrary:String,
    val searchableModel:String,
    val searchableAccent:String,
)

data class VoiceFilters(
    val voice:String="",
    val language:String="",
    val library:String="",
    val model:String="",
    val accent:String="",
    val locality:String="all",
    val readiness:String="all",
    val performance:String="all",
    val gender:String="all",
    val capability:String="all",
    val cost:String="all",
    val sort:String="name",
)

object VoiceDiscovery {
    fun cost(voice:VoiceRecord)=if(voice.costClass!="free")voice.costClass else if(voice.networkRequired&&voice.provider!=ProviderIds.EDGE)"metered" else "free"
    fun service(voice:VoiceRecord,providerName:String):String {
        if(voice.provider==ProviderIds.SHERPA){
            val model=voice.model.lowercase()
            return when {
                "kokoro" in model->"Kokoro"
                "kitten" in model->"Kitten"
                "piper" in model->"Piper"
                "pocket" in model->"Pocket"
                "supertonic" in model->"Supertonic"
                "zipvoice" in model->"ZipVoice"
                "matcha" in model->"Matcha"
                "inflect" in model->"Inflect"
                "vits" in model->"VITS"
                else->voice.model.substringBefore(' ').ifBlank{"Local ONNX"}
            }
        }
        if(voice.provider=="moss")return "MOSS"
        return providerName.substringBefore(" /").substringBefore('/').trim().ifBlank{voice.provider.replaceFirstChar(Char::uppercase)}
    }

    fun index(voice:VoiceRecord,ready:Boolean,providerName:String):VoiceSearchEntry {
        val service=voice.library.ifBlank{service(voice,providerName)}
        val model=voice.modelVersion.ifBlank{voice.model}
        return VoiceSearchEntry(
            voice,ready,service,model,
            listOf(voice.name,voice.accent,voice.gender,voice.description,voice.model,voice.provider,providerName,service,voice.quantization,voice.license,voice.capabilities.joinToString(" ")).joinToString(" ").lowercase(),
            voice.languages.joinToString(" "){Languages.searchableName(it)}.lowercase(),
            "$service ${voice.provider} $providerName".lowercase(),
            "$model ${voice.model} ${voice.quantization}".lowercase(),
            "${voice.accent} ${voice.locale.displayCountry} ${voice.locale.country}".lowercase(),
        )
    }

    fun filter(entries:List<VoiceSearchEntry>,filters:VoiceFilters):List<VoiceSearchEntry> {
        fun String.has(query:String)=query.isBlank()||contains(query.trim(),true)
        return entries.asSequence().filter{entry->
            val voice=entry.voice
            entry.searchableVoice.has(filters.voice)&&entry.searchableLanguage.has(filters.language)&&entry.searchableLibrary.has(filters.library)&&entry.searchableModel.has(filters.model)&&entry.searchableAccent.has(filters.accent)&&
                (filters.locality=="all"||(filters.locality=="on-device"&&!voice.networkRequired)||(filters.locality=="cloud"&&voice.networkRequired))&&
                (filters.readiness=="all"||(filters.readiness=="ready"&&entry.ready)||(filters.readiness=="downloadable"&&!entry.ready&&voice.downloadable)||(filters.readiness=="setup"&&!entry.ready&&!voice.downloadable))&&
                (filters.performance=="all"||voice.performanceClass.equals(filters.performance,true))&&
                (filters.gender=="all"||voice.gender.equals(filters.gender,true))&&
                (filters.capability=="all"||filters.capability in voice.capabilities)&&
                (filters.cost=="all"||cost(voice).equals(filters.cost,true))
        }.let{sequence->when(filters.sort){
            "smallest"->sequence.sortedWith(compareBy<VoiceSearchEntry>({it.voice.approxSizeMb.takeIf{n->n>0}?:Int.MAX_VALUE},{it.voice.name}))
            "fastest"->sequence.sortedWith(compareBy<VoiceSearchEntry>({when(it.voice.performanceClass){"fast"->0;"balanced"->1;"heavy"->2;else->3}},{it.voice.name}))
            "library"->sequence.sortedWith(compareBy({it.library},{it.model},{it.voice.name}))
            else->sequence.sortedBy{it.voice.name}
        }}.toList()
    }
}
