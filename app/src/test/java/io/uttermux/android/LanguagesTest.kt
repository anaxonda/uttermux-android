package io.uttermux.android

import io.uttermux.android.config.Languages
import org.junit.Assert.*
import org.junit.Test

class LanguagesTest {
    @Test fun normalizesBcp47() { assertEquals("fr-FR", Languages.normalized("FR_fr")) }
    @Test fun baseLanguageMatchesLocale() { assertTrue(Languages.matches("fr", "fr-CA")); assertFalse(Languages.matches("de", "fr-FR")) }
    @Test fun grokIncludesFrenchAndPortugueseVariants() { assertTrue("fr" in Languages.grok); assertTrue("pt-BR" in Languages.grok) }
}
