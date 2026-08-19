package io.uttermux.android

import android.app.Application
import android.util.Log
import android.content.Intent
import android.speech.tts.TextToSpeech
import io.uttermux.android.config.*
import io.uttermux.android.provider.*
import io.uttermux.android.router.VoiceRouter
import kotlinx.coroutines.*

class UtterMuxApp : Application() {
    lateinit var secure: SecureStore; lateinit var settings: AppSettings; lateinit var router: VoiceRouter; lateinit var models: ModelManager
    lateinit var providers:List<TtsProvider>;private set
    override fun onCreate() {
        super.onCreate(); instance = this
        secure = SecureStore(this); settings = AppSettings(this)
        models=ModelManager(this)
        providers=listOf(
            GrokProvider(secure),ElevenLabsProvider(secure),EdgeProvider(this),SherpaProvider(this,models),
            AzureProvider(secure),QwenProvider(secure),OpenAiProvider(secure),DeepgramProvider(secure),CartesiaProvider(secure),
            PlayHtProvider(this,secure),ResembleProvider(this,secure),
            ProxyPcmProvider(ProviderIds.GOOGLE,"Google Cloud TTS",secure,"google",listOf("en-US-Chirp3-HD-Charon" to "en-US","fr-FR-Chirp3-HD-Aoede" to "fr-FR"),"OAuth/ADC credentials remain in the optional proxy."),
            ProxyPcmProvider(ProviderIds.AWS,"Amazon Polly",secure,"aws",listOf("Joanna" to "en-US","Amy" to "en-GB","Lea" to "fr-FR"),"Use a proxy or temporary AWS credentials rather than a permanent secret on the phone."),
            CustomPcmProvider(secure),
        )
        router=VoiceRouter(settings,providers)
        CoroutineScope(SupervisorJob()+Dispatchers.IO).launch { refreshCatalogs();router.warm(settings.defaultVoice) }
    }
    fun refreshCatalogs(): List<String> {
        val errors=mutableListOf<String>()
        providers.forEach{provider->runCatching{provider.refresh()}.onFailure{errors+="${provider.descriptor.name}: ${it.message}"}}
        Log.i("UtterMux", "catalog refresh: providers=${providers.size} voices=${router.voices.size} errors=${errors.joinToString()}")
        sendBroadcast(Intent(TextToSpeech.Engine.ACTION_TTS_DATA_INSTALLED).setPackage(packageName))
        return errors
    }
    companion object { lateinit var instance: UtterMuxApp; private set }
}
