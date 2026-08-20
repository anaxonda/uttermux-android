package io.uttermux.android

import android.app.Application
import android.util.Log
import android.content.Intent
import android.speech.tts.TextToSpeech
import io.uttermux.android.config.*
import io.uttermux.android.provider.*
import io.uttermux.android.router.VoiceRouter
import io.uttermux.android.audio.AdaptiveBufferRegistry
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicLong

class UtterMuxApp : Application() {
    lateinit var secure: SecureStore; lateinit var settings: AppSettings; lateinit var router: VoiceRouter; lateinit var models: ModelManager
    lateinit var adaptiveBuffers:AdaptiveBufferRegistry;private set
    lateinit var providers:List<TtsProvider>;private set
    val voiceDataRevision=AtomicLong(1)
    private var settingsListener:android.content.SharedPreferences.OnSharedPreferenceChangeListener?=null
    override fun onCreate() {
        super.onCreate(); instance = this
        secure = SecureStore(this); settings = AppSettings(this)
        models=ModelManager(this);adaptiveBuffers=AdaptiveBufferRegistry(settings)
        providers=listOf(
            GrokProvider(secure),ElevenLabsProvider(secure),EdgeProvider(this),SherpaProvider(this,models),
            AzureProvider(secure),OpenAiProvider(secure),DeepgramProvider(secure),CartesiaProvider(secure),
            PlayHtProvider(this,secure),ResembleProvider(this,secure),
            GoogleCloudProvider(this,secure),AwsPollyProvider(secure),
            CustomPcmProvider(secure),
        )
        router=VoiceRouter(settings,providers)
        settingsListener=settings.registerChangeListener { key ->
            val affectsVoices=key=="default_voice"||key.startsWith("route.")||key.startsWith("route_chain.")
            if(affectsVoices)notifyVoiceDataChanged()
        }
        CoroutineScope(SupervisorJob()+Dispatchers.IO).launch { refreshCatalogs();router.warm(router.effectiveDefault()?.id) }
    }
    fun refreshCatalogs(): List<String> {
        val errors=mutableListOf<String>()
        providers.forEach{provider->runCatching{provider.refresh()}.onFailure{errors+="${provider.descriptor.name}: ${it.message}"}}
        Log.i("UtterMux", "catalog refresh: providers=${providers.size} voices=${router.voices.size} errors=${errors.joinToString()}")
        notifyVoiceDataChanged()
        return errors
    }
    fun notifyVoiceDataChanged(broadcast:Boolean=true){
        voiceDataRevision.incrementAndGet()
        if(broadcast)sendBroadcast(Intent(TextToSpeech.Engine.ACTION_TTS_DATA_INSTALLED).setPackage(packageName))
    }
    override fun onTrimMemory(level:Int){
        super.onTrimMemory(level)
        if(level>=TRIM_MEMORY_RUNNING_LOW)providers.forEach{it.trimMemory()}
    }
    companion object { lateinit var instance: UtterMuxApp; private set }
}
