package io.uttermux.android.audio

import android.media.*
import io.uttermux.android.config.AudioData

object Playback {
    @Volatile private var track: AudioTrack? = null
    fun play(audio: AudioData) {
        stop()
        val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
        val format = AudioFormat.Builder().setSampleRate(audio.sampleRate).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()
        track = AudioTrack(attributes, format, maxOf(audio.pcm16.size, AudioTrack.getMinBufferSize(audio.sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)), AudioTrack.MODE_STREAM, AudioManager.AUDIO_SESSION_ID_GENERATE).also {
            it.play(); it.write(audio.pcm16, 0, audio.pcm16.size, AudioTrack.WRITE_BLOCKING); it.stop(); it.release()
        }
        track = null
    }
    fun stop() { runCatching { track?.pause(); track?.flush(); track?.release() }; track = null }
}
