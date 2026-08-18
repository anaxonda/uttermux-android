package io.uttermux.android

import io.uttermux.android.config.Languages
import io.uttermux.android.router.LanguageDetector
import org.junit.Assert.*
import org.junit.Test

class LanguagesTest {
    @Test fun normalizesBcp47() { assertEquals("fr-FR", Languages.normalized("FR_fr")) }
    @Test fun baseLanguageMatchesLocale() { assertTrue(Languages.matches("fr", "fr-CA")); assertFalse(Languages.matches("de", "fr-FR")) }
    @Test fun grokIncludesFrenchAndPortugueseVariants() { assertTrue("fr" in Languages.grok); assertTrue("pt-BR" in Languages.grok) }
    @Test fun convertsAndroidIso3Locales() { assertEquals("en-US",Languages.fromAndroid("eng","USA"));assertEquals("fr-FR",Languages.fromAndroid("fra","FRA")) }
    @Test fun detectsLanguageWithoutNetwork() {
        assertEquals("fr", LanguageDetector.detect("Les livres sont dans une maison avec des fenêtres."))
        assertEquals("de", LanguageDetector.detect("Das ist ein Buch mit der Geschichte."))
        assertEquals("ja", LanguageDetector.detect("これは日本語の文章です。"))
        assertEquals("en-US", LanguageDetector.detect("A short ambiguous sentence."))
    }
}
