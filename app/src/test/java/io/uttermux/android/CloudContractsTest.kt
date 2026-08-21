package io.uttermux.android

import io.uttermux.android.provider.CloudContracts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudContractsTest {
    @Test fun qwen_maps_bcp47_to_documented_language_name(){assertEquals("French",CloudContracts.qwenLanguage("fr-FR"));assertEquals("Auto",CloudContracts.qwenLanguage("und"))}
    @Test fun playht_uses_documented_lowercase_language_name(){assertEquals("portuguese",CloudContracts.playHtLanguage("pt-BR"))}
    @Test fun azure_resource_and_regional_paths_are_distinct(){
        assertEquals("https://demo.cognitiveservices.azure.com/tts/cognitiveservices/voices/list",CloudContracts.azurePath("https://demo.cognitiveservices.azure.com","","voices/list"))
        assertEquals("https://eastus.tts.speech.microsoft.com/cognitiveservices/v1",CloudContracts.azurePath("","eastus","v1"))
    }
    @Test fun custom_endpoint_requires_https(){assertThrows(IllegalArgumentException::class.java){CloudContracts.requireHttps("http://example.test/tts","Endpoint")}}
    @Test fun configured_requires_the_selected_auth_mode(){
        val values=mutableMapOf("aws_auth_mode" to "direct","aws_access_key" to "id")
        assertFalse(CloudContracts.configured("aws"){values[it].orEmpty()})
        values["aws_secret_key"]="secret"
        assertTrue(CloudContracts.configured("aws"){values[it].orEmpty()})
        assertFalse(CloudContracts.configured("custom"){if(it=="custom_endpoint")"http://localhost" else ""})
    }
}
