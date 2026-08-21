package io.uttermux.android.config

import android.content.Context
import android.content.SharedPreferences

class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val benchmarkThreads=java.util.concurrent.ConcurrentHashMap<String,Int>()
    var defaultVoice: String
        get() = prefs.getString("default_voice", "uttermux:auto@en")!!
        set(value) { prefs.edit().putString("default_voice", value).apply() }
    var koReaderEnabled: Boolean
        get() = prefs.getBoolean("koreader_enabled", false)
        set(value) { prefs.edit().putBoolean("koreader_enabled", value).apply() }
    var theme: String
        get() = prefs.getString("theme", "system")!!
        set(value) { prefs.edit().putString("theme", value).apply() }
    var latencyProfile:String
        get()=prefs.getString("latency_profile","automatic")!!.let{if(it=="balanced")"automatic" else it}
        set(value){prefs.edit().putString("latency_profile",value).apply()}
    var manualStartupMs:Int
        get()=prefs.getInt("manual_startup_ms",300)
        set(value){prefs.edit().putInt("manual_startup_ms",value.coerceIn(0,5000)).apply()}
    var modelCacheSize:Int
        get()=prefs.getInt("model_cache_size",1)
        set(value){prefs.edit().putInt("model_cache_size",value.coerceIn(1,3)).apply()}
    var pocketNumSteps:Int
        get()=prefs.getInt("pocket_num_steps_v3",2)
        set(value){prefs.edit().putInt("pocket_num_steps_v3",value.coerceIn(1,5)).apply()}
    var engineThreads:Int
        get()=prefs.getInt("engine_threads",0)
        set(value){prefs.edit().putInt("engine_threads",value.takeIf{it in 1..16}?:0).apply()}
    fun tuningFingerprint(artifactId:String)=prefs.getString("tuning.$artifactId.fingerprint","").orEmpty()
    fun tunedThreads(artifactId:String,fingerprint:String=""):Int=if(fingerprint.isNotBlank()&&tuningFingerprint(artifactId)!=fingerprint)0 else prefs.getInt("tuning.$artifactId.threads",0)
    fun modelThreads(artifactId:String)=prefs.getInt("model_override.$artifactId.threads",0)
    fun setModelThreads(artifactId:String,value:Int){val edit=prefs.edit();if(value in 1..16)edit.putInt("model_override.$artifactId.threads",value)else edit.remove("model_override.$artifactId.threads");edit.apply()}
    fun modelPocketSteps(artifactId:String)=prefs.getInt("model_override.$artifactId.pocket_steps",0)
    fun setModelPocketSteps(artifactId:String,value:Int){val edit=prefs.edit();if(value in 1..5)edit.putInt("model_override.$artifactId.pocket_steps",value)else edit.remove("model_override.$artifactId.pocket_steps");edit.apply()}
    fun effectivePocketSteps(artifactId:String)=modelPocketSteps(artifactId).takeIf{it>0}?:pocketNumSteps
    fun effectiveThreads(artifactId:String,fingerprint:String="")=benchmarkThreads[artifactId]?:modelThreads(artifactId).takeIf{it>0}?:tunedThreads(artifactId,fingerprint)
    fun threadSource(artifactId:String,fingerprint:String="")=when{
        benchmarkThreads.containsKey(artifactId)->"Benchmark run"
        modelThreads(artifactId)>0->"Model override"
        tunedThreads(artifactId,fingerprint)>0->"Tuned"
        engineThreads>0->"Global default"
        else->"Automatic"
    }
    fun setBenchmarkThreads(artifactId:String,value:Int){if(value in 1..16)benchmarkThreads[artifactId]=value else benchmarkThreads.remove(artifactId)}
    fun setTunedThreads(artifactId:String,value:Int,fingerprint:String=""){
        val edit=prefs.edit();val key="tuning.$artifactId.threads";val fingerprintKey="tuning.$artifactId.fingerprint"
        if(value in 1..16){edit.putInt(key,value);edit.putString(fingerprintKey,fingerprint)}else{edit.remove(key);edit.remove(fingerprintKey)};edit.apply()
    }
    fun resetModelOverrides(artifactId:String){prefs.edit().remove("model_override.$artifactId.threads").remove("model_override.$artifactId.pocket_steps").apply()}
    var paidPreviewConfirmed:Boolean
        get()=prefs.getBoolean("paid_preview_confirmed",false)
        set(value){prefs.edit().putBoolean("paid_preview_confirmed",value).apply()}
    fun resetAdvanced(){prefs.edit().putString("latency_profile","automatic").putInt("manual_startup_ms",300).putInt("model_cache_size",1).putInt("pocket_num_steps_v3",2).putInt("engine_threads",0).apply()}
    fun route(language: String): String = prefs.getString("route.${Languages.normalized(language)}", "")!!
    fun setRoute(language: String, voice: String) = prefs.edit().putString("route.${Languages.normalized(language)}", voice).apply()
    fun routeChain(language:String):List<String> = prefs.getString("route_chain.${Languages.normalized(language)}","")!!.split('\n').filter(String::isNotBlank)
    fun setRouteChain(language:String,voices:List<String>)=prefs.edit().putString("route_chain.${Languages.normalized(language)}",voices.distinct().joinToString("\n")).apply()
    fun configuredRouteVoices():List<String> = prefs.all.entries.asSequence()
        .filter { it.key.startsWith("route.") || it.key.startsWith("route_chain.") }
        .flatMap { it.value.toString().split('\n').asSequence() }
        .filter(String::isNotBlank).distinct().toList()
    fun registerChangeListener(listener:(String)->Unit):SharedPreferences.OnSharedPreferenceChangeListener {
        val wrapped=SharedPreferences.OnSharedPreferenceChangeListener{_,key->if(key!=null)listener(key)}
        prefs.registerOnSharedPreferenceChangeListener(wrapped)
        return wrapped
    }
}
