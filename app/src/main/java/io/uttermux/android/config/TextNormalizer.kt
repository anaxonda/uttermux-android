package io.uttermux.android.config

import java.text.Normalizer

object TextNormalizer {
    private val negativeContraction=Regex("(?i)\\b(?:aren't|can't|couldn't|didn't|doesn't|don't|hadn't|hasn't|haven't|isn't|mustn't|shouldn't|wasn't|weren't|won't|wouldn't)\\b")
    private val expandedNegatives=mapOf(
        "aren't" to "are not","can't" to "cannot","couldn't" to "could not","didn't" to "did not",
        "doesn't" to "does not","don't" to "do not","hadn't" to "had not","hasn't" to "has not",
        "haven't" to "have not","isn't" to "is not","mustn't" to "must not","shouldn't" to "should not",
        "wasn't" to "was not","weren't" to "were not","won't" to "will not","wouldn't" to "would not",
    )
    /** Normalization for whole-section readers where source offsets are owned by the caller. */
    fun readerText(value:String):String = Normalizer.normalize(value,Normalizer.Form.NFKC)
        .replace(Regex("(?is)<\\s*/?\\s*speak(?:\\s[^>]*)?>")," ")
        .replace(Regex("(?is)<[^>]+>")," ")
        .replace("&nbsp;"," ",true).replace("&amp;","&",true)
        .replace("&lt;","<",true).replace("&gt;",">",true).replace("&quot;","\"",true)
        .replace('\u00a0',' ').replace('\u202f',' ')
        .replace('‘','\'').replace('’','\'').replace('“','\"').replace('”','\"')
        .replace('–','-').replace('—','-').replace("…","...")
        .replace(Regex("[\\p{Cc}&&[^\\n\\t]]")," ")
        .replace(Regex("\\s+")," ").trim()

    fun diagnosticSnippet(value:String,limit:Int=180)=value.take(limit)
        .replace("\\","\\\\").replace("\n","\\n").replace("\r","\\r").replace("\t","\\t")

    /** Work around contraction artifacts in the affected local frontends. */
    fun modelText(value:String,engine:String):String {
        if(engine !in setOf("vits","pocket"))return value
        return negativeContraction.replace(value){match->
            val expanded=expandedNegatives.getValue(match.value.lowercase())
            if(match.value.first().isUpperCase())expanded.replaceFirstChar(Char::uppercase)else expanded
        }
    }
}
