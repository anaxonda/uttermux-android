package io.uttermux.android.config

import android.app.ActivityManager
import android.content.Context
import android.os.Build

data class DeviceHardware(
    val architecture:String,
    val logicalCores:Int,
    val totalRamMb:Int,
    val availableRamMb:Int,
    val inferenceProviders:List<String> = listOf("CPU"),
)

data class ModelAdvice(val label:String,val reason:String)

object HardwareAdvisor {
    fun detect(context:Context):DeviceHardware {
        val memory=ActivityManager.MemoryInfo()
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(memory)
        return DeviceHardware(
            Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown",
            Runtime.getRuntime().availableProcessors().coerceAtLeast(1),
            (memory.totalMem / 1_048_576L).toInt(),
            (memory.availMem / 1_048_576L).toInt(),
        )
    }

    fun recommend(context:Context,voice:VoiceRecord)=recommend(detect(context),voice.estimatedRamMb,voice.performanceClass,voice.networkRequired)

    fun recommend(hardware:DeviceHardware,estimatedRamMb:Int,performanceClass:String,networkRequired:Boolean):ModelAdvice {
        if(networkRequired)return ModelAdvice("Cloud","Runs remotely; local CPU and model RAM are not limiting factors.")
        if(estimatedRamMb>0&&estimatedRamMb>hardware.totalRamMb*0.70)return ModelAdvice("Not recommended","Estimated model RAM exceeds 70% of this device's total memory.")
        if(estimatedRamMb>0&&estimatedRamMb>hardware.availableRamMb*0.75)return ModelAdvice("Memory pressure","It may work, but little currently available RAM would remain for the reading app.")
        return when(performanceClass.lowercase()){
            "fast","tiny"->ModelAdvice("Recommended","Expected to be responsive with CPU inference on this device.")
            "balanced","medium"->ModelAdvice("Likely suitable","Expected to work, though initial model loading can be noticeable.")
            "heavy","slow"->ModelAdvice("May be slow","CPU-only synthesis may not keep pace with continuous reading.")
            else->ModelAdvice("Compatibility unknown","No measured performance profile is available for this model variant.")
        }
    }
}
