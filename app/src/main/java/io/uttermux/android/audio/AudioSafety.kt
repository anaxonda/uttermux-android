package io.uttermux.android.audio

import kotlin.math.abs

object AudioSafety {
    fun requireSafe(samples:FloatArray,sampleRate:Int){
        var railRun=0;val maximum=(sampleRate/50).coerceAtLeast(64)
        for(sample in samples){
            require(sample.isFinite()){ "Model produced non-finite audio" }
            railRun=if(abs(sample)>=.999f)railRun+1 else 0
            require(railRun<maximum){ "Model produced unsafe rail-pinned audio" }
        }
    }
}
