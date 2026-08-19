package io.uttermux.android.audio

/** Small, allocation-bounded PCM transforms used by the Android TTS bridge. */
object PcmTransform {
    fun resamplePcm16(input:ByteArray,fromRate:Int,toRate:Int):ByteArray {
        if(fromRate==toRate||input.size<4)return input
        require(fromRate>0&&toRate>0)
        val samples=input.size/2
        val outputSamples=(samples.toLong()*toRate/fromRate).toInt().coerceAtLeast(1)
        val output=ByteArray(outputSamples*2)
        fun sample(index:Int):Int { val i=index.coerceIn(0,samples-1)*2;return (input[i].toInt() and 255) or (input[i+1].toInt() shl 8) }
        for(i in 0 until outputSamples) {
            val source=i.toDouble()*fromRate/toRate
            val left=source.toInt().coerceAtMost(samples-1);val right=(left+1).coerceAtMost(samples-1)
            val value=(sample(left)+(sample(right)-sample(left))*(source-left)).toInt().coerceIn(-32768,32767)
            output[i*2]=value.toByte();output[i*2+1]=(value shr 8).toByte()
        }
        return output
    }
    fun trimSilence(input:ByteArray,sampleRate:Int,threshold:Int=96,maxLeadingMs:Int=120,maxTrailingMs:Int=90):ByteArray {
        if(input.size<4)return input
        val count=input.size/2
        fun magnitude(index:Int):Int { val i=index*2;return kotlin.math.abs(((input[i].toInt() and 255) or (input[i+1].toInt() shl 8)).coerceAtLeast(-32767)) }
        val leadingLimit=minOf(count,sampleRate*maxLeadingMs/1000);var first=0
        while(first<leadingLimit&&magnitude(first)<=threshold)first++
        val trailingLimit=maxOf(first,count-sampleRate*maxTrailingMs/1000);var last=count
        while(last>trailingLimit&&magnitude(last-1)<=threshold)last--
        return if(first==0&&last==count)input else input.copyOfRange(first*2,last*2)
    }
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
