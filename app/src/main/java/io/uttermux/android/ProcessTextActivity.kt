package io.uttermux.android

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import io.uttermux.android.audio.Playback
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean

class ProcessTextActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val text = when (intent.action) {
            Intent.ACTION_PROCESS_TEXT -> intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
            Intent.ACTION_SEND -> intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
            else -> null
        }.orEmpty().trim()
        if (text.isBlank()) { finish(); return }
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { UtterMuxApp.instance.router.synthesize(null, text, "en-US", 1f, AtomicBoolean()) } }
                .onSuccess { withContext(Dispatchers.IO) { Playback.play(it) } }
                .onFailure { Toast.makeText(this@ProcessTextActivity, it.message ?: "TTS failed", Toast.LENGTH_LONG).show() }
            finish()
        }
    }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
