package io.uttermux.android.provider

import io.uttermux.android.config.*
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sin

/** Deterministic PCM used only by the isolated instrumentation application. */
class TestHostProvider:TtsProvider {
    override val id="test-host"
    override val descriptor=ProviderDescriptor(id,"Instrumentation test host",network=false)
    override val voices=listOf(VoiceRecord("test-host/synthetic@en-US","Synthetic test voice",Locale.US,id,"Test PCM",setOf("en-US","en-GB"),false,downloadable=false,downloadId="test-host-synthetic",performanceClass="fast",library="Test host",modelVersion="1"))
    override fun strategy(voice:VoiceRecord)=StreamStrategy.DIRECT_STREAM
    override fun stream(session:PreparedSession,text:String,speed:Float,pitch:Float,cancelled:AtomicBoolean,emit:(AudioChunk)->Boolean){
        val rate=24_000;val frames=rate/3;repeat(2){sequence->
            if(cancelled.get())throw InterruptedException();val pcm=ByteArray(frames*2)
            repeat(frames){i->val sample=(sin(2.0*Math.PI*220*(i+sequence*frames)/rate)*1200).toInt();pcm[i*2]=sample.toByte();pcm[i*2+1]=(sample shr 8).toByte()}
            if(!emit(AudioChunk(pcm,rate,TextRange(0,text.length),sequence,1_000_000)))return
        }
    }
}
