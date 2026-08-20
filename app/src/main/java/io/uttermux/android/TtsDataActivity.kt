package io.uttermux.android

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import io.uttermux.android.config.Languages
import java.util.Locale

/** Compatibility entry points used by Samsung Settings and older Android TTS clients. */
class TtsDataActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        when(intent.action) {
            TextToSpeech.Engine.ACTION_CHECK_TTS_DATA -> checkData()
            TextToSpeech.Engine.ACTION_GET_SAMPLE_TEXT -> sampleText()
            TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA -> {
                startActivity(Intent(this,MainActivity::class.java));setResult(RESULT_OK);finish()
            }
            else -> { setResult(RESULT_CANCELED);finish() }
        }
    }
    private fun checkData() {
        val voices=UtterMuxApp.instance.router.availableVoices.mapNotNull { voice ->
            runCatching {
                val locale=voice.locale
                listOf(locale.isO3Language,locale.isO3Country.takeIf(String::isNotBlank)).filterNotNull().joinToString("-")
            }.getOrNull()
        }.distinct().toCollection(ArrayList())
        val result=Intent().putStringArrayListExtra(TextToSpeech.Engine.EXTRA_AVAILABLE_VOICES,voices)
            .putStringArrayListExtra(TextToSpeech.Engine.EXTRA_UNAVAILABLE_VOICES,arrayListOf())
        setResult(if(voices.isEmpty())TextToSpeech.Engine.CHECK_VOICE_DATA_FAIL else TextToSpeech.Engine.CHECK_VOICE_DATA_PASS,result);finish()
    }
    private fun sampleText() {
        val language=Languages.fromAndroid(intent.getStringExtra("language"),intent.getStringExtra("country")).substringBefore('-')
        val text=when(language){"fr"->"Ceci est un exemple de synthèse vocale UtterMux.";"de"->"Dies ist ein Beispiel für die UtterMux-Sprachausgabe.";"es"->"Este es un ejemplo de síntesis de voz de UtterMux.";else->"This is an example of UtterMux speech synthesis."}
        setResult(RESULT_OK,Intent().putExtra(TextToSpeech.Engine.EXTRA_SAMPLE_TEXT,text));finish()
    }
}
