package io.uttermux.android.router

/** Small, offline detector used when a caller supplies no useful locale. */
object LanguageDetector {
    private val words = mapOf(
        "fr" to setOf("avec", "dans", "des", "est", "les", "pas", "pour", "que", "une"),
        "de" to setOf("aber", "das", "der", "die", "ein", "ist", "mit", "nicht", "und"),
        "es" to setOf("con", "del", "el", "en", "es", "las", "los", "para", "que", "una"),
        "it" to setOf("che", "con", "del", "della", "gli", "il", "non", "per", "una"),
        "pt" to setOf("com", "de", "do", "dos", "em", "não", "para", "que", "uma"),
        "nl" to setOf("dat", "de", "een", "en", "het", "met", "niet", "van", "voor"),
    )

    fun detect(text: String, fallback: String = "en-US"): String {
        if (text.any { it in '\u3040'..'\u30ff' }) return "ja"
        if (text.any { it in '\uac00'..'\ud7af' }) return "ko"
        if (text.any { it in '\u0600'..'\u06ff' }) return "ar"
        if (text.any { it in '\u0400'..'\u04ff' }) return "ru"
        if (text.any { it in '\u4e00'..'\u9fff' }) return "zh"
        val tokens = Regex("[\\p{L}']+").findAll(text.lowercase()).map { it.value }.toList()
        val scored = words.mapValues { (_, markers) -> tokens.count(markers::contains) }
        val best = scored.maxByOrNull { it.value }
        return if (best != null && best.value >= 2) best.key else fallback
    }
}
