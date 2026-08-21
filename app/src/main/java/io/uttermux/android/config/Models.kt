package io.uttermux.android.config

import java.util.Locale

object ProviderIds {
    const val GROK="grok";const val ELEVENLABS="elevenlabs";const val EDGE="edge";const val SHERPA="sherpa";const val QWEN_LOCAL="qwen-local"
    const val AZURE="azure";const val GOOGLE="google";const val QWEN="qwen";const val OPENAI="openai"
    const val DEEPGRAM="deepgram";const val AWS="aws";const val CARTESIA="cartesia";const val PLAYHT="playht"
    const val RESEMBLE="resemble";const val CUSTOM="custom"
}

data class CredentialField(
    val key:String,val label:String,val secret:Boolean=true,val placeholder:String="",
    val choices:List<String> = emptyList(),
)
data class ProviderDescriptor(
    val id:String,val name:String,val network:Boolean=true,val experimental:Boolean=false,
    val credentialFields:List<CredentialField> = emptyList(),val note:String="",
)

data class ModelCatalogEntry(
    val id:String,val family:String,val title:String,val languages:Set<String>,val status:String,
    val approxSizeMb:Int=0,val license:String="",val description:String="",val sourceUrl:String="",
)

/** Provider-independent catalog records. VoiceRecord remains the flattened,
 * Android-facing projection used by the existing provider API. */
data class VoiceDefinition(
    val providerId:String,
    val speakerId:String,
    val displayName:String,
    val languages:Set<String>,
    val accent:String="",
    val gender:String="",
    val license:String="",
    val attribution:String="",
)

data class ModelVariant(
    val id:String,
    val family:String,
    val displayName:String,
    val networkRequired:Boolean,
    val quantization:String="",
    val downloadSizeMb:Int=0,
    val estimatedRamMb:Int=0,
    val performanceClass:String="unknown",
    val capabilities:Set<String> = emptySet(),
)

data class VoiceProfile(
    val id:String,
    val name:String,
    val language:String,
    val engine:String,
    val modelVersion:String,
    val referenceFile:String,
    val createdAt:Long,
    val localOnly:Boolean=true,
    val speakerEmbeddingFile:String="",
    val iclPromptFile:String="",
    val referenceText:String="",
)

data class VoiceChoice(
    val definition:VoiceDefinition,
    val variant:ModelVariant,
    val locale:String,
) {
    val stableId="${definition.providerId}/${variant.id}/${definition.speakerId}@${Languages.normalized(locale)}"
}

data class VoiceRecord(
    val id: String,
    val name: String,
    val locale: Locale,
    val provider: String,
    val model: String,
    val languages: Set<String>,
    val networkRequired: Boolean,
    val description: String = "",
    val previewUrl: String = "",
    val downloadId: String = "",
    val downloadable: Boolean = true,
    val status:String = "available",
    val experimental:Boolean = false,
    val approxSizeMb:Int = 0,
    val license:String = "",
    val capabilities:Set<String> = emptySet(),
    val accent:String = "",
    val gender:String = "",
    val quantization:String = "",
    val estimatedRamMb:Int = 0,
    val performanceClass:String = "unknown",
    val attribution:String = "",
    val sourceUrl:String = "",
    val library:String = "",
    val modelVersion:String = "",
    val costClass:String = "free",
)

data class AudioData(val sampleRate: Int, val pcm16: ByteArray)

object Languages {
    val grok = setOf("en", "ar-EG", "ar-SA", "ar-AE", "bn", "zh", "fr", "de", "hi", "id", "it", "ja", "ko", "pt-BR", "pt-PT", "ru", "es-MX", "es-ES", "tr", "vi")
    fun normalized(value: String): String = value.replace('_', '-').let {
        val parts = it.split('-', limit = 2)
        if (parts.size == 1) parts[0].lowercase() else "${parts[0].lowercase()}-${parts[1].uppercase()}"
    }
    fun matches(capability: String, requested: String): Boolean {
        val a = normalized(capability); val b = normalized(requested)
        return a == b || a.substringBefore('-') == b.substringBefore('-')
    }
    fun searchableName(value:String):String {
        val tag=normalized(value);val locale=Locale.forLanguageTag(tag)
        return listOf(tag,locale.getDisplayLanguage(Locale.ENGLISH),locale.getDisplayLanguage(),locale.getDisplayCountry(Locale.ENGLISH),locale.getDisplayCountry()).filter(String::isNotBlank).distinct().joinToString(" ")
    }
    private val iso3Languages by lazy { Locale.getISOLanguages().associateBy { runCatching { Locale.Builder().setLanguage(it).build().isO3Language.lowercase() }.getOrDefault(it) } }
    private val iso3Countries by lazy { Locale.getISOCountries().associateBy { runCatching { Locale.Builder().setRegion(it).build().isO3Country.uppercase() }.getOrDefault(it) } }
    fun fromAndroid(language:String?,country:String?):String {
        val rawLanguage=language.orEmpty().lowercase().ifBlank{"en"}
        val lang=if(rawLanguage.length==3) iso3Languages[rawLanguage]?:rawLanguage else rawLanguage
        val rawCountry=country.orEmpty().uppercase()
        val region=when{rawCountry.length==3->iso3Countries[rawCountry]?:rawCountry;else->rawCountry}
        return normalized(listOf(lang,region.takeIf(String::isNotBlank)).filterNotNull().joinToString("-"))
    }
}
