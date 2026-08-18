package io.uttermux.android.config

import android.content.Context

class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    var defaultVoice: String
        get() = prefs.getString("default_voice", "grok/eve@en-US")!!
        set(value) { prefs.edit().putString("default_voice", value).apply() }
    var koReaderEnabled: Boolean
        get() = prefs.getBoolean("koreader_enabled", false)
        set(value) { prefs.edit().putBoolean("koreader_enabled", value).apply() }
    var theme: String
        get() = prefs.getString("theme", "system")!!
        set(value) { prefs.edit().putString("theme", value).apply() }
    fun route(language: String): String = prefs.getString("route.${Languages.normalized(language)}", "")!!
    fun setRoute(language: String, voice: String) = prefs.edit().putString("route.${Languages.normalized(language)}", voice).apply()
}
