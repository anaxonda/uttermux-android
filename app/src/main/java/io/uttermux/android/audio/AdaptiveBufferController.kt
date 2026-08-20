package io.uttermux.android.audio

import io.uttermux.android.config.AppSettings
import kotlin.math.ceil
import java.util.concurrent.ConcurrentHashMap

object AdaptiveBufferPolicy {
    fun startupMillis(profile:String,manual:Int,rtf:Double,underruns:Int):Int {
        if(profile=="manual"&&manual>0)return manual.coerceIn(80,5000)
        val base=when(profile){"low"->100;"smooth"->700;else->300}
        val pressure=when{rtf<.55->0;rtf<.9->200;else->ceil((rtf-0.75)*1000).toInt()}
        return (base+pressure+underruns.coerceAtMost(4)*150).coerceIn(80,3000)
    }
}

class AdaptiveBufferController(private val settings:AppSettings) {
    private var rtfEwma=0.6;private var samples=0;private var underruns=0;private var stableSamples=0
    @Synchronized
    fun record(generationNanos:Long,audioSeconds:Double) {
        if(generationNanos<=0||audioSeconds<=0)return
        val rtf=(generationNanos/1e9/audioSeconds).coerceIn(.05,8.0)
        rtfEwma=if(samples++==0)rtf else rtfEwma*.75+rtf*.25
        if(++stableSamples>=8&&underruns>0){underruns--;stableSamples=0}
    }
    @Synchronized fun recordUnderrun(){underruns++;stableSamples=0}
    @Synchronized
    fun startupMillis():Int {
        return AdaptiveBufferPolicy.startupMillis(settings.latencyProfile,settings.manualStartupMs,rtfEwma,underruns)
    }
    @Synchronized fun snapshot()="rtf=${"%.2f".format(rtfEwma)}, startup=${startupMillis()}ms, underruns=$underruns"
}

/** Keeps learned throughput for the life of the app instead of resetting it
 * for every KOReader/PROCESS_TEXT request. */
class AdaptiveBufferRegistry(private val settings:AppSettings) {
    private val controllers=ConcurrentHashMap<String,AdaptiveBufferController>()
    fun controller(key:String)=controllers.getOrPut(key.ifBlank{"default"}){AdaptiveBufferController(settings)}
    fun snapshot()=controllers.entries.sortedBy{it.key}.joinToString("\n"){"${it.key}: ${it.value.snapshot()}"}
}
