package io.uttermux.android.provider

import io.uttermux.android.config.*
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class ElevenLabsProvider(private val secure: SecureStore) : TtsProvider {
    private val languages = setOf("ar","bg","cs","da","de","el","en","es","fi","fil","fr","hi","hr","hu","id","it","ja","ko","ms","nl","no","pl","pt","ro","ru","sk","sv","ta","tr","uk","vi","zh")
    private val builtins = listOf("pqHfZKP75CvOlQylNhV4" to "Bill")
    override val voices = builtins.map { (id,name) -> VoiceRecord("elevenlabs/$id@en-US", "$name · ElevenLabs", Locale.US, ProviderKind.ELEVENLABS, "eleven_flash_v2_5", languages, true) }
    override fun synthesize(voice: VoiceRecord, text: String, language: String, speed: Float, cancelled: AtomicBoolean): AudioData {
        val key = secure.get("elevenlabs"); require(key.isNotBlank()) { "ElevenLabs API key is not configured" }
        val external = voice.id.substringAfter('/').substringBefore('@')
        val body = JSONObject().put("text", text).put("model_id", "eleven_flash_v2_5")
            .put("language_code", Languages.normalized(language).substringBefore('-'))
            .put("voice_settings", JSONObject().put("speed", speed.coerceIn(.7f, 1.2f)))
        if (cancelled.get()) throw InterruptedException()
        return AudioData(24000, HttpAudio.post("https://api.elevenlabs.io/v1/text-to-speech/$external/stream?output_format=pcm_24000", body,
            mapOf("xi-api-key" to key, "Accept" to "audio/pcm")))
    }
}
