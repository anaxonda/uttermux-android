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
    val query:String="",
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
    val favoritesOnly:Boolean=false,
    val favoriteIds:Set<String> = emptySet(),
)

object VoiceDiscovery {
    /** Numeric speaker IDs are valid searchable aliases in several Piper
     * packages, but they are not useful zero-query discovery suggestions. */
    fun usefulVoiceSuggestion(name:String):Boolean {
        val speaker=name.substringBefore(" ·").trim()
        return speaker.any(Char::isLetter) && !speaker.matches(Regex("(?i)speaker[- _]?\\d+"))
    }
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
        return entries.asSequence().filter{entry->
            val voice=entry.voice
            (filters.query.isBlank()||filters.query.lowercase().split(Regex("\\s+")).filter(String::isNotBlank).all{
                term->term in listOf(entry.searchableVoice,entry.searchableLanguage,entry.searchableLibrary,
                    entry.searchableModel,entry.searchableAccent).joinToString(" ")
            })&&
                (filters.voice.isBlank()||voice.name.equals(filters.voice,true))&&
                (filters.language.isBlank()||voice.languages.any{it.equals(filters.language,true)})&&
                (filters.library.isBlank()||entry.library.equals(filters.library,true))&&
                (filters.model.isBlank()||entry.model.equals(filters.model,true))&&
                (filters.accent.isBlank()||voice.accent.equals(filters.accent,true))&&
                (filters.locality=="all"||(filters.locality in setOf("on-device","offline")&&!voice.networkRequired)||(filters.locality in setOf("cloud","online")&&voice.networkRequired))&&
                (filters.readiness=="all"||(filters.readiness=="ready"&&entry.ready)||(filters.readiness=="downloadable"&&!entry.ready&&voice.downloadable)||(filters.readiness=="setup"&&!entry.ready&&!voice.downloadable))&&
                (filters.performance=="all"||voice.performanceClass.equals(filters.performance,true))&&
                (filters.gender=="all"||voice.gender.equals(filters.gender,true))&&
                (filters.capability=="all"||filters.capability in voice.capabilities)&&
                (filters.cost=="all"||cost(voice).equals(filters.cost,true))&&
                (!filters.favoritesOnly||voice.id in filters.favoriteIds)
        }.let{sequence->when(filters.sort){
            "smallest"->sequence.sortedWith(compareBy<VoiceSearchEntry>({it.voice.approxSizeMb.takeIf{n->n>0}?:Int.MAX_VALUE},{it.voice.name}))
            "fastest"->sequence.sortedWith(compareBy<VoiceSearchEntry>({when(it.voice.performanceClass){"fast"->0;"balanced"->1;"heavy"->2;else->3}},{it.voice.name}))
            "library"->sequence.sortedWith(compareBy({it.library},{it.model},{it.voice.name}))
            else->sequence.sortedBy{it.voice.name}
        }}.toList()
    }
}
