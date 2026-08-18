package io.uttermux.android

import android.app.Application
import android.util.Log
import io.uttermux.android.config.*
import io.uttermux.android.provider.*
import io.uttermux.android.router.VoiceRouter
import kotlinx.coroutines.*

class UtterMuxApp : Application() {
    lateinit var secure: SecureStore; lateinit var settings: AppSettings; lateinit var router: VoiceRouter; lateinit var models: ModelManager
    private lateinit var elevenLabs: ElevenLabsProvider; private lateinit var edge: EdgeProvider
    override fun onCreate() {
        super.onCreate(); instance = this
        secure = SecureStore(this); settings = AppSettings(this)
        models=ModelManager(this); elevenLabs=ElevenLabsProvider(secure); edge=EdgeProvider(this)
        router = VoiceRouter(settings, listOf(GrokProvider(secure), elevenLabs, edge, SherpaProvider(this,models)))
        CoroutineScope(SupervisorJob()+Dispatchers.IO).launch { refreshCatalogs() }
    }
    fun refreshCatalogs(): List<String> {
        val errors=mutableListOf<String>()
        runCatching { edge.refresh() }.onFailure { errors += "Edge: ${it.message}" }
        runCatching { elevenLabs.refresh() }.onFailure { errors += "ElevenLabs: ${it.message}" }
        Log.i("UtterMux", "catalog refresh: edge=${edge.voices.size} elevenlabs=${elevenLabs.voices.size} errors=${errors.joinToString()}")
        return errors
    }
    companion object { lateinit var instance: UtterMuxApp; private set }
}
