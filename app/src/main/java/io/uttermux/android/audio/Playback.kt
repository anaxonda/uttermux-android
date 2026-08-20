package io.uttermux.android.audio

import android.media.*
import android.os.Handler
import android.os.Looper
import io.uttermux.android.config.AudioData
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

object Playback {
    @Volatile private var track: AudioTrack? = null
    @Volatile private var streamCancellation: AtomicBoolean? = null
    fun play(audio: AudioData, onStarted: () -> Unit = {}) {
        stop()
        require(audio.pcm16.isNotEmpty()) { "No audio was generated" }
        val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
        val format = AudioFormat.Builder().setSampleRate(audio.sampleRate).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()
        val finished = CountDownLatch(1)
        val bufferSize = maxOf(4096, AudioTrack.getMinBufferSize(audio.sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT))
        val player = AudioTrack(attributes, format, bufferSize, AudioTrack.MODE_STREAM, AudioManager.AUDIO_SESSION_ID_GENERATE)
        track = player
        try {
            check(player.state == AudioTrack.STATE_INITIALIZED) { "Could not initialize Android audio output" }
            player.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
                override fun onMarkerReached(track: AudioTrack?) { finished.countDown() }
                override fun onPeriodicNotification(track: AudioTrack?) = Unit
            }, Handler(Looper.getMainLooper()))
            player.notificationMarkerPosition = audio.pcm16.size / 2
            player.play()
            onStarted()
            check(player.write(audio.pcm16, 0, audio.pcm16.size, AudioTrack.WRITE_BLOCKING) == audio.pcm16.size) { "Could not load preview audio" }
            val durationMs = audio.pcm16.size * 1000L / 2 / audio.sampleRate
            finished.await(durationMs + 2_000, TimeUnit.MILLISECONDS)
        } finally {
            if (track === player) track = null
            runCatching { player.stop() }; player.release()
        }
    }
    fun playStream(sampleRate:Int,startupMs:()->Int,cancelled:AtomicBoolean,next:(Long)->ByteArray?,generationDone:()->Boolean,
                   onStarted:()->Unit={},onProgress:(Long)->Unit={},onUnderrun:()->Unit={}) {
        stop();val pending=mutableListOf<ByteArray>();var buffered=0
        while(buffered<sampleRate*2*startupMs()/1000&&!cancelled.get()) {
            val chunk=next(100) ?: if(generationDone())break else continue
            if(chunk.isEmpty())break
            pending+=chunk;buffered+=chunk.size
        }
        if(buffered==0)return
        streamCancellation=cancelled
        val attributes=AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
        val format=AudioFormat.Builder().setSampleRate(sampleRate).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()
        val bufferSize=maxOf(4096,AudioTrack.getMinBufferSize(sampleRate,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT))
        val player=AudioTrack(attributes,format,bufferSize,AudioTrack.MODE_STREAM,AudioManager.AUDIO_SESSION_ID_GENERATE);track=player
        try {
            check(player.state==AudioTrack.STATE_INITIALIZED){"Could not initialize Android audio output"}
            player.play();onStarted()
            fun playedFrames()=player.playbackHeadPosition.toLong() and 0xffffffffL
            var writtenFrames=0L
            fun write(chunk:ByteArray):Boolean {
                var offset=0
                while(offset<chunk.size&&!cancelled.get()) {
                    val count=player.write(chunk,offset,chunk.size-offset,AudioTrack.WRITE_BLOCKING)
                    if(count<=0)return false
                    offset+=count;writtenFrames+=count/2;onProgress(playedFrames().coerceAtMost(writtenFrames))
                }
                return !cancelled.get()
            }
            for(chunk in pending)if(!write(chunk))return
            while(!cancelled.get()) {
                val chunk=next(100)
                if(chunk==null){if(generationDone())break else {onUnderrun();continue}}
                if(chunk.isEmpty()||!write(chunk))break
            }
            // A blocking write only transfers PCM into AudioTrack. Keep the track alive until
            // Android has actually presented the queued tail, otherwise the next section clips it.
            val drainDeadline=System.nanoTime()+((writtenFrames-playedFrames()).coerceAtLeast(0)*1_000_000_000L/sampleRate+2_000_000_000L)
            while(!cancelled.get()&&playedFrames()<writtenFrames&&System.nanoTime()<drainDeadline){
                onProgress(playedFrames().coerceAtMost(writtenFrames));Thread.sleep(10)
            }
            onProgress(playedFrames().coerceAtMost(writtenFrames))
        } finally {
            if(streamCancellation===cancelled)streamCancellation=null
            if(track===player)track=null
            runCatching{player.stop()};player.release()
        }
    }
    fun stop() {
        streamCancellation?.set(true);streamCancellation=null
        val current=track;track=null
        runCatching { current?.pause();current?.flush();current?.release() }
    }
}
