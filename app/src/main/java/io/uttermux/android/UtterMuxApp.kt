package io.uttermux.android

import android.app.Application
import io.uttermux.android.config.*
import io.uttermux.android.provider.*
import io.uttermux.android.router.VoiceRouter

class UtterMuxApp : Application() {
    lateinit var secure: SecureStore; lateinit var settings: AppSettings; lateinit var router: VoiceRouter; lateinit var models: ModelManager
    override fun onCreate() {
        super.onCreate(); instance = this
        secure = SecureStore(this); settings = AppSettings(this)
        models=ModelManager(this)
        router = VoiceRouter(settings, listOf(GrokProvider(secure), ElevenLabsProvider(secure), EdgeProvider(this), SherpaProvider(this)))
    }
    companion object { lateinit var instance: UtterMuxApp; private set }
}
