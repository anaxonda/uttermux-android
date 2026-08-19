package io.uttermux.android

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import io.uttermux.android.audio.Playback
import io.uttermux.android.audio.AdaptiveBufferController
import io.uttermux.android.audio.PcmTransform
import kotlinx.coroutines.*
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class ProcessTextActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val cancelled=AtomicBoolean()
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val text = when (intent.action) {
            Intent.ACTION_PROCESS_TEXT -> intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
            Intent.ACTION_SEND -> intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
            else -> null
        }.orEmpty().trim()
        if (text.isBlank()) { finish(); return }
        scope.launch {
            runCatching { withContext(Dispatchers.IO) {
                val app=UtterMuxApp.instance;val queue=LinkedBlockingQueue<ByteArray>(64);val done=AtomicBoolean()
                val producer=async{
                    try{val route=app.router.prepare("uttermux:auto",text,"auto");app.router.stream(route,text,1f,1f,cancelled){chunk->
                        val pcm=PcmTransform.resamplePcm16(chunk.pcm16,chunk.sampleRate,24_000)
                        while(!cancelled.get())if(queue.offer(pcm,100,TimeUnit.MILLISECONDS))return@stream true
                        false
                    }}finally{done.set(true);queue.offer(ByteArray(0))}
                }
                Playback.playStream(24_000,AdaptiveBufferController(app.settings).startupMillis(),cancelled,{timeout->queue.poll(timeout,TimeUnit.MILLISECONDS)},{done.get()})
                producer.await()
            } }
                .onSuccess { }
                .onFailure { Toast.makeText(this@ProcessTextActivity, it.message ?: "TTS failed", Toast.LENGTH_LONG).show() }
            finish()
        }
    }
    override fun onDestroy() { cancelled.set(true);scope.cancel();Playback.stop();super.onDestroy() }
}
