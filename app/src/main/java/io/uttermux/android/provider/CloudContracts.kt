package io.uttermux.android.provider

import io.uttermux.android.config.Languages
import java.net.URI

/** Pure provider-contract helpers. Keep protocol spelling out of UI and routing code. */
object CloudContracts {
    fun configured(provider:String,value:(String)->String):Boolean=when(provider){
        "edge"->true
        "elevenlabs"->value("elevenlabs").isNotBlank()
        "grok"->value("grok").isNotBlank()
        "openai"->value("openai").isNotBlank()&&validHttps(value("openai_endpoint").ifBlank{"https://api.openai.com"})
        "azure"->value("azure").isNotBlank()&&(value("azure_endpoint").isBlank()||validHttps(value("azure_endpoint")))
        "qwen"->value("qwen").isNotBlank()
        "deepgram"->value("deepgram").isNotBlank()
        "cartesia"->value("cartesia").isNotBlank()
        "playht"->value("playht").isNotBlank()&&value("playht_user").isNotBlank()
        "resemble"->value("resemble").isNotBlank()&&value("resemble_voice").isNotBlank()
        "custom"->validHttps(value("custom_endpoint"))
        "google"->if(value("google_auth_mode").ifBlank{"direct"}=="proxy") validHttps(value("google_proxy")) else value("google_api_key").isNotBlank()
        "aws"->when(value("aws_auth_mode").ifBlank{"direct"}){
            "proxy"->validHttps(value("aws_proxy"));"cognito"->value("aws_identity_pool").isNotBlank()
            else->value("aws_access_key").isNotBlank()&&value("aws_secret_key").isNotBlank()
        }
        else->false
    }
    fun qwenLanguage(tag:String):String=languageName(tag)
    fun playHtLanguage(tag:String):String=languageName(tag).lowercase()
    fun languageName(tag:String):String=when(Languages.normalized(tag).substringBefore('-')){
        "ar"->"Arabic";"bn"->"Bengali";"bg"->"Bulgarian";"ca"->"Catalan";"cs"->"Czech"
        "da"->"Danish";"de"->"German";"el"->"Greek";"en"->"English";"es"->"Spanish"
        "fi"->"Finnish";"fr"->"French";"he"->"Hebrew";"hi"->"Hindi";"hr"->"Croatian"
        "hu"->"Hungarian";"id"->"Indonesian";"it"->"Italian";"ja"->"Japanese";"ko"->"Korean"
        "ms"->"Malay";"nl"->"Dutch";"no","nb"->"Norwegian";"pl"->"Polish";"pt"->"Portuguese"
        "ro"->"Romanian";"ru"->"Russian";"sk"->"Slovak";"sv"->"Swedish";"ta"->"Tamil"
        "te"->"Telugu";"th"->"Thai";"tr"->"Turkish";"uk"->"Ukrainian";"vi"->"Vietnamese"
        "zh"->"Chinese";else->"Auto"
    }
    fun azurePath(endpoint:String,region:String,path:String):String {
        val clean=endpoint.trim().trimEnd('/')
        return if(clean.isNotBlank())"$clean/tts/cognitiveservices/$path"
        else "https://${region.ifBlank{"eastus"}}.tts.speech.microsoft.com/cognitiveservices/$path"
    }
    fun requireHttps(value:String,label:String):String {
        val parsed=runCatching{URI(value)}.getOrNull()
        require(parsed?.scheme.equals("https",true)&&!parsed?.host.isNullOrBlank()){ "$label must be an HTTPS URL" }
        return value.trimEnd('/')
    }
    fun validHttps(value:String)=runCatching{val uri=URI(value);uri.scheme.equals("https",true)&&!uri.host.isNullOrBlank()}.getOrDefault(false)
}
