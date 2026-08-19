package io.uttermux.android

import io.uttermux.android.audio.PcmTransform
import org.junit.Assert.*
import org.junit.Test

class PcmTransformTest {
    private fun pcm(vararg samples:Int)=ByteArray(samples.size*2).also { bytes ->
        samples.forEachIndexed { i,value -> bytes[i*2]=value.toByte();bytes[i*2+1]=(value shr 8).toByte() }
    }

    @Test fun unityPitchLeavesPcmUntouched() {
        val input=pcm(-1000,0,1000)
        assertSame(input,PcmTransform.pitchPcm16(input,1f))
    }

    @Test fun higherPitchShortensAndLowerPitchLengthensPcm() {
        val input=pcm(0,1000,2000,3000,4000,5000,6000,7000)
        assertEquals(input.size/2,PcmTransform.pitchPcm16(input,2f).size)
        assertEquals(input.size*2,PcmTransform.pitchPcm16(input,.5f).size)
    }

    @Test fun pitchOutputRemainsPcm16Aligned() {
        assertEquals(0,PcmTransform.pitchPcm16(pcm(0,100,200,300,400),1.37f).size%2)
    }
}
