package io.uttermux.android

import io.uttermux.android.config.ProviderIds
import io.uttermux.android.config.VoiceRecord
import io.uttermux.android.config.koReaderBindAddress
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class VoiceDiscoveryTest {
    @Test fun numeric_piper_speakers_are_searchable_but_not_discovery_suggestions(){
        assertFalse(VoiceDiscovery.usefulVoiceSuggestion("00737 · Piper"))
        assertFalse(VoiceDiscovery.usefulVoiceSuggestion("speaker-12 · Piper"))
        assertTrue(VoiceDiscovery.usefulVoiceSuggestion("Alan Low · Piper"))
        assertTrue(VoiceDiscovery.usefulVoiceSuggestion("Bella · Kokoro"))
    }
    private fun voice(provider:String,model:String,language:String="en-US",network:Boolean=false,gender:String="female",performance:String="fast")=
        VoiceRecord("$provider/id@$language","Example",Locale.forLanguageTag(language),provider,model,setOf(language),network,gender=gender,performanceClass=performance)

    @Test fun exposesModelFamilyInsteadOfSherpaImplementation(){
        assertEquals("Kokoro",VoiceDiscovery.service(voice(ProviderIds.SHERPA,"Kokoro 82M"),"Local / sherpa-onnx"))
        assertEquals("Piper",VoiceDiscovery.service(voice(ProviderIds.SHERPA,"Piper medium"),"Local / sherpa-onnx"))
        assertEquals("ElevenLabs",VoiceDiscovery.service(voice("elevenlabs","Flash",network=true),"ElevenLabs"))
    }

    @Test fun combinesIndependentUserFacingFacets(){
        val entries=listOf(
            VoiceDiscovery.index(voice(ProviderIds.SHERPA,"Kokoro 82M","fr-FR",false,"female","balanced"),true,"Local / sherpa-onnx"),
            VoiceDiscovery.index(voice("edge","Edge Neural","fr-FR",true,"male","cloud"),true,"Edge"),
        )
        val shown=VoiceDiscovery.filter(entries,VoiceFilters(language="fr-FR",library="Kokoro",locality="on-device",gender="female"))
        assertEquals(1,shown.size);assertEquals("Kokoro",shown.single().library)
        assertTrue(VoiceDiscovery.filter(entries,VoiceFilters(library="Edge",locality="on-device")).isEmpty())
        assertEquals(1,VoiceDiscovery.filter(entries,VoiceFilters(locality="offline")).size)
        assertEquals("edge",VoiceDiscovery.filter(entries,VoiceFilters(locality="online")).single().voice.provider)
    }

    @Test fun selectedFacetsAreExactRatherThanSubstringQueries(){
        val entries=listOf(
            VoiceDiscovery.index(voice("edge","Edge Neural","ar"),true,"Edge"),
            VoiceDiscovery.index(voice("edge","Edge Neural","ar-EG"),true,"Edge"),
        )
        assertEquals(listOf("ar"),VoiceDiscovery.filter(entries,VoiceFilters(language="ar")).single().voice.languages.toList())
    }

    @Test fun globalSearchSpansVoiceServiceModelLanguageAndAccent(){
        val bill=VoiceRecord("elevenlabs/bill@en-US","Bill · ElevenLabs",Locale.US,"elevenlabs","eleven_flash_v2_5",setOf("en-US"),true,accent="American",library="ElevenLabs",modelVersion="Flash 2.5")
        val alba=VoiceRecord("sherpa/pocket/alba@en-GB","Alba",Locale.UK,ProviderIds.SHERPA,"Pocket INT8",setOf("en-GB"),false,accent="British",library="Pocket",modelVersion="INT8")
        val entries=listOf(VoiceDiscovery.index(bill,true,"ElevenLabs"),VoiceDiscovery.index(alba,true,"Local models"))
        assertEquals(listOf("Bill · ElevenLabs"),VoiceDiscovery.filter(entries,VoiceFilters(query="bill elevenlabs")).map{it.voice.name})
        assertEquals(listOf("Alba"),VoiceDiscovery.filter(entries,VoiceFilters(query="pocket british")).map{it.voice.name})
        assertEquals(listOf("Bill · ElevenLabs"),VoiceDiscovery.filter(entries,VoiceFilters(query="english flash")).map{it.voice.name})
    }

    @Test fun favoritesAreAnIndependentFilter(){
        val entries=listOf(
            VoiceDiscovery.index(voice(ProviderIds.SHERPA,"Kokoro"),true,"Kokoro"),
            VoiceDiscovery.index(voice("edge","Edge",network=true),true,"Edge"),
        )
        val favorite=entries.last().voice.id
        assertEquals(listOf(favorite),VoiceDiscovery.filter(entries,VoiceFilters(
            favoritesOnly=true,favoriteIds=setOf(favorite))).map{it.voice.id})
    }

    @Test fun koreaderLanModeIsExplicitlyWildcardBound(){
        assertEquals("127.0.0.1",koReaderBindAddress(false))
        assertEquals("0.0.0.0",koReaderBindAddress(true))
    }
}
