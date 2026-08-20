package io.uttermux.android

import io.uttermux.android.config.TextNormalizer
import org.junit.Assert.*
import org.junit.Test

class TextNormalizerTest {
    @Test fun curlyApostrophesNeverBecomeTrademarkText(){
        val input="It wasn’t clear why she didn’t answer."
        val normalized=TextNormalizer.readerText(input)
        assertEquals("It wasn't clear why she didn't answer.",normalized)
        assertFalse(normalized.contains("TM",true))
    }
    @Test fun stripsSpeakWrapperWithoutSpeakingClosingTag(){
        assertEquals("Hello & goodbye.",TextNormalizer.readerText("<speak>Hello &amp; goodbye.</speak>"))
    }
}
