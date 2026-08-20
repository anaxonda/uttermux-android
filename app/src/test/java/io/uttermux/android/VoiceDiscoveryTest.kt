package io.uttermux.android

import io.uttermux.android.config.ProviderIds
import io.uttermux.android.config.VoiceRecord
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class VoiceDiscoveryTest {
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
        val shown=VoiceDiscovery.filter(entries,VoiceFilters(language="French",library="kokoro",locality="on-device",gender="female"))
        assertEquals(1,shown.size);assertEquals("Kokoro",shown.single().library)
        assertTrue(VoiceDiscovery.filter(entries,VoiceFilters(library="edge",locality="on-device")).isEmpty())
    }
}
