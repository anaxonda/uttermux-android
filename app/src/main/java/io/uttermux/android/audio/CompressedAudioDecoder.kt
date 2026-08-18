package io.uttermux.android.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import io.uttermux.android.config.AudioData
import java.io.ByteArrayOutputStream

object CompressedAudioDecoder {
    fun mp3(context: Context, bytes: ByteArray): AudioData {
        val file = kotlin.io.path.createTempFile(context.cacheDir.toPath(), "edge-", ".mp3").toFile()
        try {
            file.writeBytes(bytes)
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(file.absolutePath)
                val track = (0 until extractor.trackCount).firstOrNull {
                    extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
                } ?: error("Edge returned no audio track")
                extractor.selectTrack(track)
                val format = extractor.getTrackFormat(track)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: error("Edge audio has no codec")
                val codec = MediaCodec.createDecoderByType(mime)
                try {
                    codec.configure(format, null, null, 0); codec.start()
                    val output = ByteArrayOutputStream(); val info = MediaCodec.BufferInfo()
                    var inputEnded = false; var outputEnded = false; var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    while (!outputEnded) {
                        if (!inputEnded) {
                            val index = codec.dequeueInputBuffer(10_000)
                            if (index >= 0) {
                                val buffer = codec.getInputBuffer(index)!!
                                val size = extractor.readSampleData(buffer, 0)
                                if (size < 0) { codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputEnded = true }
                                else { codec.queueInputBuffer(index, 0, size, extractor.sampleTime, 0); extractor.advance() }
                            }
                        }
                        val index = codec.dequeueOutputBuffer(info, 10_000)
                        if (index >= 0) {
                            codec.getOutputBuffer(index)?.let { buffer ->
                                buffer.position(info.offset); buffer.limit(info.offset + info.size)
                                val chunk = ByteArray(info.size); buffer.get(chunk); output.write(chunk)
                            }
                            outputEnded = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                            codec.releaseOutputBuffer(index, false)
                        } else if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                            sampleRate = codec.outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        }
                    }
                    return AudioData(sampleRate, output.toByteArray())
                } finally { runCatching { codec.stop() }; codec.release() }
            } finally { extractor.release() }
        } finally { file.delete() }
    }
}
