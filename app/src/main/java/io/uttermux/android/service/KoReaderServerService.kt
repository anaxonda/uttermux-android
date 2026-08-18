package io.uttermux.android.service

import android.app.*
import android.content.Intent
import android.os.IBinder
import io.uttermux.android.MainActivity
import io.uttermux.android.R
import io.uttermux.android.UtterMuxApp
import io.uttermux.android.audio.Playback
import io.uttermux.android.config.AudioData
import org.json.JSONObject
import java.io.*
import java.net.*
import java.security.MessageDigest
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean

class KoReaderServerService : Service() {
    private var server: ServerSocket? = null
    private val pool = Executors.newCachedThreadPool()
    private val cache = object : LinkedHashMap<String, Clip>(24, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Clip>?) = size > 20
    }
    data class Clip(val audio: AudioData, @Volatile var startedAt: Long = 0) {
        val duration get() = audio.pcm16.size / 2.0 / audio.sampleRate
    }
    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("koreader", getString(R.string.koreader_channel), NotificationManager.IMPORTANCE_LOW))
        val pending = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        startForeground(5000, Notification.Builder(this, "koreader").setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
            .setContentTitle("UtterMux for KOReader").setContentText("Listening on localhost:5000").setContentIntent(pending).build())
        server = ServerSocket().apply { reuseAddress = true; bind(InetSocketAddress(InetAddress.getLoopbackAddress(), 5000)) }
        pool.execute { acceptLoop() }
    }
    private fun acceptLoop() {
        while (true) try { server?.accept()?.let { socket -> pool.execute { handle(socket) } } ?: return } catch (_: IOException) { return }
    }
    private fun handle(socket: Socket) = socket.use {
        try {
            val input = BufferedInputStream(it.getInputStream()); val output = BufferedOutputStream(it.getOutputStream())
            val header = readHeader(input); val first = header.lineSequence().first().split(' ')
            val method = first[0]; val path = first[1].substringBefore('?')
            val length = Regex("(?im)^Content-Length:\\s*(\\d+)").find(header)?.groupValues?.get(1)?.toInt() ?: 0
            val body = if (length > 0) input.readNBytes(length).toString(Charsets.UTF_8) else "{}"
            val json = JSONObject(body)
            when {
                method == "GET" && path == "/voices" -> respond(output, 200, voices(), "application/json")
                method == "POST" && path == "/" -> {
                    val text = json.optString("text").trim(); require(text.isNotEmpty()) { "No text provided" }
                    val requested = json.optString("voice").takeIf(String::isNotBlank)
                    val speed = (1.0 / json.optDouble("length_scale", 1.0)).toFloat()
                    val language = json.optString("language", "en-US")
                    val key = digest("$text\u0000$requested\u0000$speed\u0000$language")
                    synchronized(cache) { if (!cache.containsKey(key)) cache[key] = Clip(UtterMuxApp.instance.router.synthesize(requested, text, language, speed, AtomicBoolean())) }
                    respond(output, 200, key)
                }
                method == "POST" && path == "/play" -> {
                    val clip = synchronized(cache) { cache[json.getString("handle")] } ?: error("Unknown handle")
                    clip.startedAt = System.nanoTime(); pool.execute { Playback.play(clip.audio) }; respond(output, 200, "")
                }
                method == "POST" && path == "/stop" -> { Playback.stop(); synchronized(cache) { cache[json.optString("handle")]?.startedAt = 0 }; respond(output, 200, "") }
                method == "POST" && path == "/remaining" -> {
                    val clip = synchronized(cache) { cache[json.getString("handle")] } ?: error("Unknown handle")
                    val elapsed = if (clip.startedAt == 0L) 0.0 else (System.nanoTime() - clip.startedAt) / 1e9
                    respond(output, 200, JSONObject().put("started", clip.startedAt != 0L).put("remaining", (clip.duration - elapsed).coerceAtLeast(0.0)).toString(), "application/json")
                }
                else -> respond(output, 404, "Not found")
            }
        } catch (error: Throwable) { runCatching { respond(BufferedOutputStream(it.getOutputStream()), 500, error.message ?: "Server error") } }
    }
    private fun voices(): String {
        val groups = JSONObject()
        UtterMuxApp.instance.router.voices.forEach { voice ->
            val key = voice.locale.toLanguageTag().replace('-', '_'); val array = groups.optJSONArray(key) ?: org.json.JSONArray().also { groups.put(key, it) }
            array.put(voice.id)
        }
        return groups.toString()
    }
    private fun readHeader(input: InputStream): String {
        val bytes = ByteArrayOutputStream(); var state = 0
        while (bytes.size() < 32768) { val b = input.read(); if (b < 0) break; bytes.write(b); state = when { state == 0 && b == 13 -> 1; state == 1 && b == 10 -> 2; state == 2 && b == 13 -> 3; state == 3 && b == 10 -> 4; b == 13 -> 1; else -> 0 }; if (state == 4) break }
        return bytes.toString(Charsets.US_ASCII)
    }
    private fun respond(output: BufferedOutputStream, code: Int, body: String, type: String = "text/plain") {
        val data = body.toByteArray(); output.write("HTTP/1.1 $code ${if (code == 200) "OK" else "Error"}\r\nContent-Type: $type\r\nContent-Length: ${data.size}\r\nConnection: close\r\n\r\n".toByteArray()); output.write(data); output.flush()
    }
    private fun digest(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).take(10).joinToString("") { "%02x".format(it) }
    override fun onDestroy() { server?.close(); pool.shutdownNow(); Playback.stop(); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null
}
