package io.uttermux.android

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import io.uttermux.android.audio.Playback
import io.uttermux.android.audio.PcmTransform
import io.uttermux.android.audio.PcmChunkQueue
import kotlinx.coroutines.*
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
                val app=UtterMuxApp.instance;val queue=PcmChunkQueue(24_000);val done=AtomicBoolean()
                val route=app.router.prepare("uttermux:auto",text,"auto");val controller=app.adaptiveBuffers.controller(route.primary.voice.id)
                val producer=async{
                    try{app.router.stream(route,text,1f,1f,cancelled){chunk->
                        val pcm=PcmTransform.resamplePcm16(chunk.pcm16,chunk.sampleRate,24_000)
                        controller.record(chunk.generatedNanos,pcm.size/2.0/24_000.0)
                        while(!cancelled.get())if(queue.offer(pcm,100,cancelled))return@stream true
                        false
                    }}finally{done.set(true);queue.offer(ByteArray(0),100)}
                }
                Playback.playStream(24_000,controller.startupMillis(),cancelled,{timeout->queue.poll(timeout)},{done.get()},onUnderrun={controller.recordUnderrun()})
                producer.await()
            } }
                .onSuccess { }
                .onFailure { Toast.makeText(this@ProcessTextActivity, it.message ?: "TTS failed", Toast.LENGTH_LONG).show() }
            finish()
        }
    }
    override fun onDestroy() { cancelled.set(true);scope.cancel();Playback.stop();super.onDestroy() }
}
