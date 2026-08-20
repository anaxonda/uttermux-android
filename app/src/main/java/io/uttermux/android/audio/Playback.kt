package io.uttermux.android.audio

import android.media.*
import android.util.Log
import io.uttermux.android.config.AudioData
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/** Owns direct app/KOReader playback. Android system TTS does not use this class. */
object Playback {
    enum class State { CREATED, BUFFERING, PLAYING, PAUSED, FINISHED, STOPPED, ERROR }
    private val serial=ReentrantLock(true)
    private val active=AtomicReference<StreamSession?>()

    class StreamSession internal constructor(
        private val sampleRate:Int,
        private val startupMs:()->Int,
        val cancelled:AtomicBoolean,
        private val next:(Long)->ByteArray?,
        private val generationDone:()->Boolean,
        private val onStarted:()->Unit,
        private val onProgress:(Long)->Unit,
        private val onUnderrun:()->Unit,
        private val onState:(State)->Unit,
    ) {
        private val monitor=Object()
        private val paused=AtomicBoolean()
        private val finished=AtomicBoolean()
        @Volatile private var player:AudioTrack?=null
        @Volatile var state:State=State.CREATED;private set
        private fun state(next:State){state=next;Log.d("UtterMuxPlayback","session=${System.identityHashCode(this)} state=$next");onState(next)}
        fun pause(){
            if(finished.get()||cancelled.get())return
            paused.set(true);runCatching{player?.pause()};state(State.PAUSED)
        }
        fun resume():Boolean {
            if(finished.get()||cancelled.get())return false
            paused.set(false);synchronized(monitor){monitor.notifyAll()};runCatching{player?.play()};state(State.PLAYING)
            return true
        }
        fun stop(){
            if(finished.getAndSet(true))return
            cancelled.set(true);paused.set(false);synchronized(monitor){monitor.notifyAll()}
            // Only the owner thread releases AudioTrack. Releasing it here races
            // playbackHeadPosition and caused the KOReader process crash.
            runCatching{player?.pause()};state(State.STOPPED)
        }
        private fun awaitResume():Boolean {
            synchronized(monitor){while(paused.get()&&!cancelled.get())monitor.wait(250)}
            return !cancelled.get()
        }
        fun run(){
            Log.d("UtterMuxPlayback","session=${System.identityHashCode(this)} worker started")
            val previous=active.getAndSet(this);if(previous!==this)previous?.stop()
            serial.withLock {
                Log.d("UtterMuxPlayback","session=${System.identityHashCode(this)} acquired audio owner")
                if(cancelled.get())return@withLock
                val pending=mutableListOf<ByteArray>();var buffered=0;state(State.BUFFERING)
                // A zero startup reserve means "start with the first chunk", not
                // "start without asking the producer for audio". Preview playback
                // deliberately uses a zero reserve, so always fetch at least one
                // chunk before deciding that the stream is empty.
                val startupBytes=(sampleRate*2L*startupMs()/1000L).coerceAtLeast(0L)
                while((buffered==0||buffered<startupBytes)&&!cancelled.get()){
                    val chunk=next(100)?:if(generationDone())break else continue
                    if(chunk.isEmpty())break
                    pending+=chunk;buffered+=chunk.size
                }
                if(buffered==0){state(if(cancelled.get())State.STOPPED else State.FINISHED);finished.set(true);return@withLock}
                Log.d("UtterMuxPlayback","session=${System.identityHashCode(this)} startup bytes=$buffered")
                val attributes=AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
                val format=AudioFormat.Builder().setSampleRate(sampleRate).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()
                val bufferSize=maxOf(4096,AudioTrack.getMinBufferSize(sampleRate,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT))
                val track=AudioTrack(attributes,format,bufferSize,AudioTrack.MODE_STREAM,AudioManager.AUDIO_SESSION_ID_GENERATE);player=track
                var writtenFrames=0L
                fun playedFrames()=runCatching{track.playbackHeadPosition.toLong() and 0xffffffffL}.getOrDefault(0L)
                fun write(chunk:ByteArray):Boolean{
                    var offset=0
                    while(offset<chunk.size&&!cancelled.get()){
                        if(!awaitResume())return false
                        val wanted=minOf(4096,chunk.size-offset)
                        val count=track.write(chunk,offset,wanted,AudioTrack.WRITE_BLOCKING)
                        // AudioTrack may return zero or an error when pause()
                        // interrupts a blocking write. That is not end-of-stream:
                        // wait for resume and retry the same bytes.
                        if(count<=0){
                            if(paused.get()){if(!awaitResume())return false;continue}
                            return false
                        }
                        offset+=count;writtenFrames+=count/2;onProgress(playedFrames().coerceAtMost(writtenFrames))
                    }
                    return !cancelled.get()
                }
                try {
                    check(track.state==AudioTrack.STATE_INITIALIZED){"Could not initialize Android audio output"}
                    track.play();state(State.PLAYING);onStarted()
                    for(chunk in pending)if(!write(chunk))return@withLock
                    while(!cancelled.get()){
                        if(!awaitResume())break
                        val chunk=next(100)
                        if(chunk==null){if(generationDone())break else{onUnderrun();continue}}
                        if(chunk.isEmpty()||!write(chunk))break
                    }
                    while(!cancelled.get()&&playedFrames()<writtenFrames){
                        if(!awaitResume())break
                        val remaining=(writtenFrames-playedFrames()).coerceAtLeast(0)
                        if(remaining==0L)break
                        Thread.sleep(minOf(20L,(remaining*1000/sampleRate).coerceAtLeast(1)))
                        onProgress(playedFrames().coerceAtMost(writtenFrames))
                    }
                    onProgress(playedFrames().coerceAtMost(writtenFrames))
                    if(!cancelled.get())state(State.FINISHED)
                } catch(error:Throwable){
                    if(!cancelled.get()){state(State.ERROR);throw error}
                } finally {
                    finished.set(true);player=null
                    runCatching{track.stop()};track.release();active.compareAndSet(this,null)
                }
            }
        }
    }

    fun streamSession(sampleRate:Int,startupMs:()->Int,cancelled:AtomicBoolean,next:(Long)->ByteArray?,generationDone:()->Boolean,
        onStarted:()->Unit={},onProgress:(Long)->Unit={},onUnderrun:()->Unit={},onState:(State)->Unit={}):StreamSession =
        StreamSession(sampleRate,startupMs,cancelled,next,generationDone,onStarted,onProgress,onUnderrun,onState)

    fun playStream(sampleRate:Int,startupMs:()->Int,cancelled:AtomicBoolean,next:(Long)->ByteArray?,generationDone:()->Boolean,
        onStarted:()->Unit={},onProgress:(Long)->Unit={},onUnderrun:()->Unit={}) =
        streamSession(sampleRate,startupMs,cancelled,next,generationDone,onStarted,onProgress,onUnderrun).run()

    fun play(audio:AudioData,onStarted:()->Unit={}){
        require(audio.pcm16.isNotEmpty()){ "No audio was generated" }
        val delivered=AtomicBoolean();val cancelled=AtomicBoolean()
        streamSession(audio.sampleRate,{0},cancelled,{if(delivered.compareAndSet(false,true))audio.pcm16 else ByteArray(0)},{delivered.get()},onStarted).run()
    }
    fun stop(){active.getAndSet(null)?.stop()}
}
