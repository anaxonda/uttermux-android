package io.uttermux.android.service

import android.app.*
import android.content.Intent
import android.os.IBinder
import io.uttermux.android.MainActivity
import io.uttermux.android.R
import io.uttermux.android.UtterMuxApp
import io.uttermux.android.audio.Playback
import io.uttermux.android.config.AudioData
import io.uttermux.android.audio.PcmTransform
import io.uttermux.android.diagnostics.Diagnostics
import org.json.JSONObject
import java.io.*
import java.net.*
import java.security.MessageDigest
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean

class KoReaderServerService : Service() {
    private var server: ServerSocket? = null
    private val pool = Executors.newCachedThreadPool()
    private val cache = object : LinkedHashMap<String, StreamClip>(24, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, StreamClip>?):Boolean {
            val remove=size>20;if(remove)eldest?.value?.cancelled?.set(true);return remove
        }
    }
    data class StreamClip(
        val queue:BlockingQueue<ByteArray> = LinkedBlockingQueue(64),
        val cancelled:AtomicBoolean = AtomicBoolean(),
        @Volatile var generatedFrames:Long=0,@Volatile var playedFrames:Long=0,
        @Volatile var startedAt:Long=0,@Volatile var generationDone:Boolean=false,
        @Volatile var playbackDone:Boolean=false,@Volatile var error:String="",
    ) {
        val queuedSeconds get()=(generatedFrames-playedFrames).coerceAtLeast(0)/24_000.0
    }
    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("koreader", getString(R.string.koreader_channel), NotificationManager.IMPORTANCE_LOW))
        val pending = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        startForeground(5000, Notification.Builder(this, "koreader").setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
            .setContentTitle("UtterMux for KOReader").setContentText("Listening on localhost:5000").setContentIntent(pending).build())
        // KOReader's compatibility plugin addresses the server as 127.0.0.1.
        // InetAddress.getLoopbackAddress() resolves to ::1 on some Samsung builds.
        server = ServerSocket().apply { reuseAddress = true; bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 5000)) }
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
            val body = if (length > 0) String(readBytes(input, length), Charsets.UTF_8) else "{}"
            val json = JSONObject(body)
            when {
                method == "GET" && path == "/voices" -> respond(output, 200, voices(), "application/json")
                method == "POST" && path == "/" -> {
                    val text = json.optString("text").trim(); require(text.isNotEmpty()) { "No text provided" }
                    val speed = (1.0 / json.optDouble("length_scale", 1.0)).toFloat()
                    val language = json.optString("language", "en-US")
                    // Route through automatic mode so the app-wide default and fallback chain
                    // take precedence over a stale provider voice saved by the KOReader plugin.
                    val key = digest("$text\u0000auto\u0000$speed\u0000$language")
                    val clip=synchronized(cache){cache[key]?:StreamClip().also{cache[key]=it;generate(it,key,text,language,speed)}}
                    respond(output, 200, key)
                }
                method == "POST" && path == "/play" -> {
                    val clip = synchronized(cache) { cache[json.getString("handle")] } ?: error("Unknown handle")
                    clip.startedAt=0;clip.playbackDone=false
                    pool.execute {
                        try { Playback.playStream(24_000,UtterMuxApp.instance.settings.let{io.uttermux.android.audio.AdaptiveBufferController(it).startupMillis()},clip.cancelled,
                            {timeout->clip.queue.poll(timeout,TimeUnit.MILLISECONDS)},{clip.generationDone},
                            {clip.startedAt=System.nanoTime()},{frames->clip.playedFrames+=frames}) }
                        finally { clip.playbackDone=true }
                    }
                    respond(output, 200, "")
                }
                method == "POST" && path == "/stop" -> { Playback.stop(); synchronized(cache) { cache[json.optString("handle")]?.apply { cancelled.set(true);startedAt=0;playbackDone=true } }; respond(output, 200, "") }
                method == "POST" && path == "/remaining" -> {
                    val clip = synchronized(cache) { cache[json.getString("handle")] } ?: error("Unknown handle")
                    val remaining=if(clip.playbackDone)0.0 else clip.queuedSeconds.coerceAtLeast(.02)
                    respond(output,200,JSONObject().put("started",clip.startedAt!=0L).put("remaining",remaining)
                        .put("buffered",clip.queuedSeconds).put("generating",!clip.generationDone).put("error",clip.error).toString(),"application/json")
                }
                method == "GET" && path == "/health" -> respond(output,200,JSONObject().put("ok",true).put("sessions",synchronized(cache){cache.size}).toString(),"application/json")
                else -> respond(output, 404, "Not found")
            }
        } catch (error: Throwable) { runCatching { respond(BufferedOutputStream(it.getOutputStream()), 500, error.message ?: "Server error") } }
    }
    private fun generate(clip:StreamClip,key:String,text:String,language:String,speed:Float) {
        pool.execute {
            val diagnostic=Diagnostics.request("koreader $key chars=${text.length}")
            try {
                val route=UtterMuxApp.instance.router.prepare("uttermux:auto",text,language)
                UtterMuxApp.instance.router.stream(route,text,speed,1f,clip.cancelled){chunk->
                    val pcm=PcmTransform.resamplePcm16(chunk.pcm16,chunk.sampleRate,24_000)
                    clip.generatedFrames+=pcm.size/2
                    while(!clip.cancelled.get())if(clip.queue.offer(pcm,100,TimeUnit.MILLISECONDS))return@stream true
                    false
                }
            } catch(error:Throwable){clip.error=error.message.orEmpty();Diagnostics.record(diagnostic,"error",clip.error)}
            finally {clip.generationDone=true;clip.queue.offer(ByteArray(0));Diagnostics.record(diagnostic,"generated","frames=${clip.generatedFrames}")}
        }
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
        return String(bytes.toByteArray(), Charsets.US_ASCII)
    }
    private fun readBytes(input: InputStream, length: Int): ByteArray {
        val result = ByteArray(length); var offset = 0
        while (offset < length) {
            val count = input.read(result, offset, length - offset)
            if (count < 0) throw EOFException("Request body ended early")
            offset += count
        }
        return result
    }
    private fun respond(output: BufferedOutputStream, code: Int, body: String, type: String = "text/plain") {
        val data = body.toByteArray(); output.write("HTTP/1.1 $code ${if (code == 200) "OK" else "Error"}\r\nContent-Type: $type\r\nContent-Length: ${data.size}\r\nConnection: close\r\n\r\n".toByteArray()); output.write(data); output.flush()
    }
    private fun digest(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).take(10).joinToString("") { "%02x".format(it) }
    override fun onDestroy() { server?.close();synchronized(cache){cache.values.forEach{it.cancelled.set(true)}};pool.shutdownNow(); Playback.stop(); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null
}
