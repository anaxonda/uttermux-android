package io.uttermux.android.service

import android.app.*
import android.content.Intent
import android.os.IBinder
import io.uttermux.android.MainActivity
import io.uttermux.android.R
import io.uttermux.android.UtterMuxApp
import io.uttermux.android.audio.*
import io.uttermux.android.config.*
import io.uttermux.android.diagnostics.Diagnostics
import org.json.JSONObject
import java.io.*
import java.net.*
import java.security.MessageDigest
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean

class KoReaderServerService:Service(){
    enum class ClipState{PREPARING,READY,BUFFERING,PLAYING,PAUSED,FINISHED,STOPPED,ERROR}
    data class StreamClip(
        val handle:String,val text:String,val speed:Float,val language:String,val routeRevision:Long,val route:RoutingSession,
        val queue:PcmChunkQueue=PcmChunkQueue(24_000),val cancelled:AtomicBoolean=AtomicBoolean(),
        val createdAt:Long=System.currentTimeMillis(),@Volatile var generatedFrames:Long=0,@Volatile var playedFrames:Long=0,
        @Volatile var generationDone:Boolean=false,@Volatile var error:String="",@Volatile var state:ClipState=ClipState.PREPARING,
        @Volatile var playback:Playback.StreamSession?=null,
    ){val queuedSeconds get()=(generatedFrames-playedFrames).coerceAtLeast(0)/24_000.0}

    private var server:ServerSocket?=null
    private val requestPool=Executors.newFixedThreadPool(4)
    private val workPool=Executors.newFixedThreadPool(3)
    private val cache=object:LinkedHashMap<String,StreamClip>(16,.75f,true){
        override fun removeEldestEntry(eldest:MutableMap.MutableEntry<String,StreamClip>?):Boolean{
            val remove=size>12;if(remove)eldest?.value?.let(::cancel);return remove
        }
    }
    override fun onCreate(){
        super.onCreate();val manager=getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("koreader",getString(R.string.koreader_channel),NotificationManager.IMPORTANCE_LOW))
        val pending=PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE)
        startForeground(5000,Notification.Builder(this,"koreader").setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
            .setContentTitle("UtterMux for KOReader").setContentText("Listening on localhost:5000").setContentIntent(pending).build())
        server=ServerSocket().apply{reuseAddress=true;bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"),5000))}
        requestPool.execute(::acceptLoop)
    }
    private fun acceptLoop(){while(true)try{server?.accept()?.let{socket->socket.soTimeout=5_000;requestPool.execute{handle(socket)}}?:return}catch(_:IOException){return}}
    private fun handle(socket:Socket)=socket.use{connection->
        try{
            val input=BufferedInputStream(connection.getInputStream());val output=BufferedOutputStream(connection.getOutputStream())
            val header=readHeader(input);val first=header.lineSequence().first().split(' ');val method=first[0];val path=first[1].substringBefore('?')
            val length=Regex("(?im)^Content-Length:\\s*(\\d+)").find(header)?.groupValues?.get(1)?.toInt()?:0
            val body=if(length>0)String(readBytes(input,length),Charsets.UTF_8)else "{}";val json=JSONObject(body)
            when{
                method=="GET"&&path=="/voices"->respond(output,200,voices(),"application/json")
                method=="GET"&&path=="/health"->respond(output,200,JSONObject().put("ok",true).put("protocol",2).put("routeRevision",app.voiceDataRevision.get()).put("sessions",synchronized(cache){cache.size}).toString(),"application/json")
                method=="POST"&&path=="/"->{
                    val text=TextNormalizer.readerText(json.optString("text"));require(text.isNotEmpty()){ "No text provided" }
                    val speed=(1.0/json.optDouble("length_scale",1.0)).toFloat();val language=json.optString("language","auto")
                    val revision=app.voiceDataRevision.get();val route=app.router.prepare("uttermux:auto",text,language)
                    val handle=digest("$text\u0000$speed\u0000$language")
                    synchronized(cache){
                        cache[handle]?.takeIf{it.routeRevision==revision&&it.state !in setOf(ClipState.STOPPED,ClipState.ERROR)}
                            ?:create(handle,text,speed,language,revision,route).also{cache[handle]?.let(::cancel);cache[handle]=it}
                    }
                    respond(output,200,handle)
                }
                method=="POST"&&path in setOf("/play","/resume")->{val clip=fresh(json.getString("handle"));startOrResume(clip);respond(output,200,"")}
                method=="POST"&&path=="/pause"->{pause(find(json.getString("handle")));respond(output,200,"")}
                method=="POST"&&path=="/stop"->{findOrNull(json.optString("handle"))?.let(::cancel);respond(output,200,"")}
                method=="POST"&&path=="/remaining"->{
                    val clip=find(json.getString("handle"));val remaining=if(clip.state in setOf(ClipState.FINISHED,ClipState.STOPPED,ClipState.ERROR))0.0 else clip.queuedSeconds.coerceAtLeast(.02)
                    respond(output,200,JSONObject().put("started",clip.state !in setOf(ClipState.PREPARING,ClipState.READY)).put("remaining",remaining)
                        .put("buffered",clip.queuedSeconds).put("generating",!clip.generationDone).put("paused",clip.state==ClipState.PAUSED)
                        .put("state",clip.state.name.lowercase()).put("routeRevision",clip.routeRevision).put("error",clip.error).toString(),"application/json")
                }
                else->respond(output,404,"Not found")
            }
        }catch(error:Throwable){runCatching{respond(BufferedOutputStream(connection.getOutputStream()),500,error.message?:"Server error")}}
    }
    private val app get()=UtterMuxApp.instance
    private fun create(handle:String,text:String,speed:Float,language:String,revision:Long,route:RoutingSession)=
        StreamClip(handle,text,speed,language,revision,route).also(::generate)
    private fun find(handle:String)=findOrNull(handle)?:error("Unknown handle")
    private fun findOrNull(handle:String)=synchronized(cache){cache[handle]}
    private fun fresh(handle:String):StreamClip=synchronized(cache){
        val existing=cache[handle]?:error("Unknown handle");val revision=app.voiceDataRevision.get()
        if(existing.routeRevision==revision)return@synchronized existing
        cache.values.filter{it.routeRevision!=revision}.forEach(::cancel)
        val route=app.router.prepare("uttermux:auto",existing.text,existing.language)
        create(handle,existing.text,existing.speed,existing.language,revision,route).also{cache[handle]=it}
    }
    private fun startOrResume(clip:StreamClip)=synchronized(clip){
        when(clip.state){
            ClipState.PLAYING,ClipState.BUFFERING->return
            ClipState.PAUSED->{
                check(clip.playback?.resume()==true){"Paused playback session has already ended"}
                clip.state=ClipState.PLAYING
                return
            }
            ClipState.FINISHED->return
            ClipState.STOPPED,ClipState.ERROR->error("Handle is no longer playable")
            else->Unit
        }
        val controller=app.adaptiveBuffers.controller(clip.route.primary.voice.id)
        val session=Playback.streamSession(24_000,{controller.startupMillis()},clip.cancelled,{timeout->clip.queue.poll(timeout)},{clip.generationDone},
            onStarted={clip.state=ClipState.PLAYING;VoiceActivity.speaking(clip.route.primary.voice.id,clip.route.language,"KOReader")},
            onProgress={clip.playedFrames=it},onUnderrun={controller.recordUnderrun()},onState={state->clip.state=when(state){
                Playback.State.BUFFERING->ClipState.BUFFERING;Playback.State.PLAYING->ClipState.PLAYING;Playback.State.PAUSED->ClipState.PAUSED
                Playback.State.FINISHED->ClipState.FINISHED;Playback.State.STOPPED->ClipState.STOPPED;Playback.State.ERROR->ClipState.ERROR
                else->clip.state
            }})
        clip.playback=session;clip.state=ClipState.BUFFERING
        workPool.execute{try{session.run()}catch(error:Throwable){clip.error=error.message.orEmpty();clip.state=ClipState.ERROR}finally{if(clip.state!=ClipState.PAUSED)VoiceActivity.idle()}}
    }
    private fun pause(clip:StreamClip)=synchronized(clip){
        if(clip.state in setOf(ClipState.BUFFERING,ClipState.PLAYING,ClipState.PAUSED)){
            clip.playback?.pause()
            clip.state=ClipState.PAUSED
        }
    }
    private fun generate(clip:StreamClip){workPool.execute{
        val diagnostic=Diagnostics.request("koreader ${clip.handle} chars=${clip.text.length}")
        try{
            val controller=app.adaptiveBuffers.controller(clip.route.primary.voice.id)
            app.router.stream(clip.route,clip.text,clip.speed,1f,clip.cancelled,onCandidate={chosen->VoiceActivity.speaking(chosen.id,clip.route.language,"KOReader",if(chosen.id!=clip.route.primary.voice.id)"Primary voice failed; using fallback" else "")}){chunk->
                val pcm=PcmTransform.resamplePcm16(chunk.pcm16,chunk.sampleRate,24_000);controller.record(chunk.generatedNanos,pcm.size/2.0/24_000.0);clip.generatedFrames+=pcm.size/2
                while(!clip.cancelled.get())if(clip.queue.offer(pcm,100,clip.cancelled))return@stream true
                false
            }
        }catch(error:Throwable){if(!clip.cancelled.get()){clip.error=error.message.orEmpty();clip.state=ClipState.ERROR;Diagnostics.record(diagnostic,"error",clip.error)}}
        finally{clip.generationDone=true;if(clip.state==ClipState.PREPARING)clip.state=ClipState.READY;Diagnostics.record(diagnostic,"generated","frames=${clip.generatedFrames}")}
    }}
    private fun cancel(clip:StreamClip){clip.cancelled.set(true);clip.playback?.stop();clip.queue.clear();clip.state=ClipState.STOPPED}
    private fun voices():String{val groups=JSONObject();app.router.voices.forEach{voice->val key=voice.locale.toLanguageTag().replace('-','_');val array=groups.optJSONArray(key)?:org.json.JSONArray().also{groups.put(key,it)};array.put(voice.id)};return groups.toString()}
    private fun readHeader(input:InputStream):String{val bytes=ByteArrayOutputStream();var state=0;while(bytes.size()<32768){val b=input.read();if(b<0)break;bytes.write(b);state=when{state==0&&b==13->1;state==1&&b==10->2;state==2&&b==13->3;state==3&&b==10->4;b==13->1;else->0};if(state==4)break};return String(bytes.toByteArray(),Charsets.US_ASCII)}
    private fun readBytes(input:InputStream,length:Int):ByteArray{val result=ByteArray(length);var offset=0;while(offset<length){val count=input.read(result,offset,length-offset);if(count<0)throw EOFException("Request body ended early");offset+=count};return result}
    private fun respond(output:BufferedOutputStream,code:Int,body:String,type:String="text/plain"){val data=body.toByteArray();output.write("HTTP/1.1 $code ${if(code==200)"OK" else "Error"}\r\nContent-Type: $type\r\nContent-Length: ${data.size}\r\nConnection: close\r\n\r\n".toByteArray());output.write(data);output.flush()}
    private fun digest(value:String)=MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).take(10).joinToString(""){"%02x".format(it)}
    override fun onDestroy(){server?.close();synchronized(cache){cache.values.forEach(::cancel);cache.clear()};requestPool.shutdownNow();workPool.shutdownNow();Playback.stop();super.onDestroy()}
    override fun onBind(intent:Intent?):IBinder?=null
}
