package io.uttermux.android.config

import java.util.Locale

object ProviderIds {
    const val GROK="grok";const val ELEVENLABS="elevenlabs";const val EDGE="edge";const val SHERPA="sherpa"
    const val AZURE="azure";const val GOOGLE="google";const val QWEN="qwen";const val OPENAI="openai"
    const val DEEPGRAM="deepgram";const val AWS="aws";const val CARTESIA="cartesia";const val PLAYHT="playht"
    const val RESEMBLE="resemble";const val CUSTOM="custom"
}

data class CredentialField(val key:String,val label:String,val secret:Boolean=true,val placeholder:String="")
data class ProviderDescriptor(
    val id:String,val name:String,val network:Boolean=true,val experimental:Boolean=false,
    val credentialFields:List<CredentialField> = emptyList(),val note:String="",
)

data class ModelCatalogEntry(
    val id:String,val family:String,val title:String,val languages:Set<String>,val status:String,
    val approxSizeMb:Int=0,val license:String="",val description:String="",val sourceUrl:String="",
)

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
    private val iso3Languages by lazy { Locale.getISOLanguages().associateBy { runCatching { Locale(it).isO3Language.lowercase() }.getOrDefault(it) } }
    private val iso3Countries by lazy { Locale.getISOCountries().associateBy { runCatching { Locale("",it).isO3Country.uppercase() }.getOrDefault(it) } }
    fun fromAndroid(language:String?,country:String?):String {
        val rawLanguage=language.orEmpty().lowercase().ifBlank{"en"}
        val lang=if(rawLanguage.length==3) iso3Languages[rawLanguage]?:rawLanguage else rawLanguage
        val rawCountry=country.orEmpty().uppercase()
        val region=when{rawCountry.length==3->iso3Countries[rawCountry]?:rawCountry;else->rawCountry}
        return normalized(listOf(lang,region.takeIf(String::isNotBlank)).filterNotNull().joinToString("-"))
    }
}
