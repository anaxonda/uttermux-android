package io.uttermux.android

import io.uttermux.android.config.Languages
import io.uttermux.android.config.ProviderIds
import io.uttermux.android.config.VoiceRecord

data class VoiceSearchEntry(
    val voice:VoiceRecord,
    val ready:Boolean,
    val service:String,
    val searchableVoice:String,
    val searchableLanguage:String,
    val searchableService:String,
)

data class VoiceFilters(
    val voice:String="",
    val language:String="",
    val service:String="",
    val locality:String="all",
    val readiness:String="all",
    val performance:String="all",
    val gender:String="all",
)

object VoiceDiscovery {
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
        val service=service(voice,providerName)
        return VoiceSearchEntry(
            voice,ready,service,
            listOf(voice.name,voice.accent,voice.gender,voice.description,voice.model,voice.provider,providerName,service,voice.quantization,voice.license,voice.capabilities.joinToString(" ")).joinToString(" ").lowercase(),
            voice.languages.joinToString(" "){Languages.searchableName(it)}.lowercase(),
            "$service ${voice.provider} $providerName ${voice.model}".lowercase(),
        )
    }

    fun filter(entries:List<VoiceSearchEntry>,filters:VoiceFilters):List<VoiceSearchEntry> {
        fun String.has(query:String)=query.isBlank()||contains(query.trim(),true)
        return entries.asSequence().filter{entry->
            val voice=entry.voice
            entry.searchableVoice.has(filters.voice)&&entry.searchableLanguage.has(filters.language)&&entry.searchableService.has(filters.service)&&
                (filters.locality=="all"||(filters.locality=="on-device"&&!voice.networkRequired)||(filters.locality=="cloud"&&voice.networkRequired))&&
                (filters.readiness=="all"||(filters.readiness=="ready"&&entry.ready)||(filters.readiness=="downloadable"&&!entry.ready&&voice.downloadable)||(filters.readiness=="setup"&&!entry.ready&&!voice.downloadable))&&
                (filters.performance=="all"||voice.performanceClass.equals(filters.performance,true))&&
                (filters.gender=="all"||voice.gender.equals(filters.gender,true))
        }.sortedWith(compareBy<VoiceSearchEntry>({it.service},{it.voice.model},{it.voice.name})).toList()
    }
}
