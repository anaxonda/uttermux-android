package io.uttermux.android.service

import android.media.AudioFormat
import android.speech.tts.*
import io.uttermux.android.UtterMuxApp
import io.uttermux.android.config.Languages
import android.util.Log
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class UtterMuxTtsService : TextToSpeechService() {
    @Volatile private var cancelled = AtomicBoolean()
    private val router get() = UtterMuxApp.instance.router
    override fun onIsLanguageAvailable(lang: String?, country: String?, variant: String?): Int {
        val tag = Languages.fromAndroid(lang,country)
        if (router.voices.none { v -> v.languages.any { Languages.matches(it, tag) } }) {
            return TextToSpeech.LANG_NOT_SUPPORTED
        }
        return when {
            !variant.isNullOrBlank() -> TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE
            !country.isNullOrBlank() -> TextToSpeech.LANG_COUNTRY_AVAILABLE
            else -> TextToSpeech.LANG_AVAILABLE
        }
    }
    override fun onLoadLanguage(lang: String?, country: String?, variant: String?): Int = onIsLanguageAvailable(lang, country, variant)
    override fun onGetLanguage(): Array<String> = arrayOf("eng", "USA", "")
    override fun onGetVoices(): MutableList<Voice> {
        val auto = Voice("uttermux:auto", Locale.US, Voice.QUALITY_HIGH, Voice.LATENCY_NORMAL, true, emptySet())
        return (listOf(auto) + router.voices.map { Voice(it.id, it.locale, Voice.QUALITY_HIGH, Voice.LATENCY_NORMAL, it.networkRequired, emptySet()) }).toMutableList()
    }
    override fun onIsValidVoiceName(voiceName: String?): Int = if (voiceName == "uttermux:auto" || router.voice(voiceName.orEmpty()) != null) TextToSpeech.SUCCESS else TextToSpeech.ERROR
    override fun onLoadVoice(voiceName: String?): Int = onIsValidVoiceName(voiceName)
    override fun onGetDefaultVoiceNameFor(lang: String?, country: String?, variant: String?): String = "uttermux:auto"
    override fun onStop() { cancelled.set(true) }
    override fun onSynthesizeText(request: SynthesisRequest, callback: SynthesisCallback) {
        val signal = AtomicBoolean(); cancelled = signal
        val locale = Languages.fromAndroid(request.language,request.country)
        try {
            val audio = router.synthesize(request.voiceName, request.charSequenceText.toString(), locale, request.speechRate / 100f, signal)
            if (signal.get()) { callback.error(TextToSpeech.ERROR_SYNTHESIS); return }
            if (callback.start(audio.sampleRate, AudioFormat.ENCODING_PCM_16BIT, 1) != TextToSpeech.SUCCESS) return
            var offset = 0
            while (offset < audio.pcm16.size && !signal.get()) {
                val size = minOf(callback.maxBufferSize, audio.pcm16.size - offset)
                if (callback.audioAvailable(audio.pcm16, offset, size) != TextToSpeech.SUCCESS) break
                offset += size
            }
            if (signal.get()) callback.error(TextToSpeech.ERROR_SYNTHESIS) else callback.done()
        } catch (_: InterruptedException) { callback.error(TextToSpeech.ERROR_SYNTHESIS) }
        catch (error: Throwable) { Log.e("UtterMuxTTS","Synthesis failed for $locale/${request.voiceName}: ${error.message}",error);callback.error(TextToSpeech.ERROR_SYNTHESIS) }
    }
}
