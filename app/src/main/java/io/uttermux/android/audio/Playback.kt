package io.uttermux.android.audio

import android.media.*
import android.os.Handler
import android.os.Looper
import io.uttermux.android.config.AudioData
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object Playback {
    @Volatile private var track: AudioTrack? = null
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
    fun stop() { runCatching { track?.pause(); track?.flush(); track?.release() }; track = null }
}
