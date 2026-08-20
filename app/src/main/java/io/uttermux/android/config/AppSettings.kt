package io.uttermux.android.config

import android.content.Context

class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
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
        get()=prefs.getString("latency_profile","balanced")!!
        set(value){prefs.edit().putString("latency_profile",value).apply()}
    var manualStartupMs:Int
        get()=prefs.getInt("manual_startup_ms",300)
        set(value){prefs.edit().putInt("manual_startup_ms",value.coerceIn(0,5000)).apply()}
    var modelCacheSize:Int
        get()=prefs.getInt("model_cache_size",1)
        set(value){prefs.edit().putInt("model_cache_size",value.coerceIn(1,3)).apply()}
    fun route(language: String): String = prefs.getString("route.${Languages.normalized(language)}", "")!!
    fun setRoute(language: String, voice: String) = prefs.edit().putString("route.${Languages.normalized(language)}", voice).apply()
    fun routeChain(language:String):List<String> = prefs.getString("route_chain.${Languages.normalized(language)}","")!!.split('\n').filter(String::isNotBlank)
    fun setRouteChain(language:String,voices:List<String>)=prefs.edit().putString("route_chain.${Languages.normalized(language)}",voices.distinct().joinToString("\n")).apply()
    fun configuredRouteVoices():List<String> = prefs.all.entries.asSequence()
        .filter { it.key.startsWith("route.") || it.key.startsWith("route_chain.") }
        .flatMap { it.value.toString().split('\n').asSequence() }
        .filter(String::isNotBlank).distinct().toList()
}
