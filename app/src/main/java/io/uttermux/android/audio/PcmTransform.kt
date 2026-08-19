package io.uttermux.android.audio

/** Small, allocation-bounded PCM transforms used by the Android TTS bridge. */
object PcmTransform {
    fun pitchPcm16(input:ByteArray,factor:Float):ByteArray {
        val pitch=factor.coerceIn(.5f,2f)
        if(kotlin.math.abs(pitch-1f)<.01f||input.size<4)return input
        val samples=input.size/2;val outputSamples=(samples/pitch).toInt().coerceAtLeast(1)
        val output=ByteArray(outputSamples*2)
        fun sample(index:Int):Int { val i=index.coerceIn(0,samples-1)*2;return (input[i].toInt() and 255) or (input[i+1].toInt() shl 8) }
        for(i in 0 until outputSamples) {
            val source=i*pitch;val left=source.toInt().coerceAtMost(samples-1);val right=(left+1).coerceAtMost(samples-1)
            val value=(sample(left)+(sample(right)-sample(left))*(source-left)).toInt().coerceIn(-32768,32767)
            output[i*2]=value.toByte();output[i*2+1]=(value shr 8).toByte()
        }
        return output
    }
}
