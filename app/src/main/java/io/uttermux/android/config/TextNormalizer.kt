package io.uttermux.android.config

import java.text.Normalizer

object TextNormalizer {
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
}
