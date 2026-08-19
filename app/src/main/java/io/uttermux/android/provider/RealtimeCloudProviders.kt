package io.uttermux.android.provider

import android.content.Context
import android.util.Base64
import io.uttermux.android.audio.CompressedAudioDecoder
import io.uttermux.android.config.*
import okhttp3.*
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

private fun awaitSocket(cancelled:AtomicBoolean,done:CountDownLatch,socket:WebSocket,error:AtomicReference<Throwable?>,seconds:Long=60){
    val deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(seconds)
    while(!done.await(50,TimeUnit.MILLISECONDS)){
        if(cancelled.get()){socket.cancel();throw InterruptedException()}
        if(System.nanoTime()>deadline){socket.cancel();error("TTS WebSocket timed out")}
    }
    error.get()?.let{throw it}
}

class QwenProvider(private val secure:SecureStore):TtsProvider {
    override val id=ProviderIds.QWEN
    override val descriptor=ProviderDescriptor(id,"Qwen / DashScope",credentialFields=listOf(CredentialField("qwen","API key"),CredentialField("qwen_region","Region",false,"singapore"),CredentialField("qwen_workspace","Workspace ID",false)))
    private val names=listOf("Cherry","Serena","Ethan","Chelsie")
    override val voices=names.map{VoiceRecord("qwen/$it@zh-CN","$it · Qwen",Locale.CHINA,id,"qwen3-tts-flash-realtime",setOf("zh","en","fr","de","es","it","pt","ja","ko","ru"),true,capabilities=setOf("streaming","multilingual"))}
    override fun isAvailable(voice:VoiceRecord)=secure.get("qwen").isNotBlank()
    override fun stream(session:PreparedSession,text:String,speed:Float,pitch:Float,cancelled:AtomicBoolean,emit:(AudioChunk)->Boolean){
        val key=secure.get("qwen");require(key.isNotBlank()){ "Qwen API key is not configured" }
        val workspace=secure.get("qwen_workspace");val region=secure.get("qwen_region").ifBlank{"singapore"}
        val host=when{workspace.isNotBlank()&&region.equals("beijing",true)->"$workspace.cn-beijing.maas.aliyuncs.com";workspace.isNotBlank()->"$workspace.ap-southeast-1.maas.aliyuncs.com";region.equals("beijing",true)->"dashscope.aliyuncs.com";else->"dashscope-intl.aliyuncs.com"}
        val request=Request.Builder().url("wss://$host/api-ws/v1/realtime?model=qwen3-tts-flash-realtime").header("Authorization","Bearer $key").apply{if(workspace.isNotBlank())header("X-DashScope-WorkSpace",workspace)}.build()
        val done=CountDownLatch(1);val failure=AtomicReference<Throwable?>();var sequence=0;val range=TextRange(0,text.length)
        lateinit var socket:WebSocket
        socket=HttpAudio.client.newWebSocket(request,object:WebSocketListener(){
            override fun onOpen(webSocket:WebSocket,response:Response){
                webSocket.send(JSONObject().put("type","session.update").put("session",JSONObject().put("mode","commit").put("voice",session.voice.id.substringAfter('/').substringBefore('@')).put("response_format","pcm").put("sample_rate",24000).put("speed",speed)).toString())
                webSocket.send(JSONObject().put("type","input_text_buffer.append").put("text",text).toString())
                webSocket.send(JSONObject().put("type","input_text_buffer.commit").toString())
            }
            override fun onMessage(webSocket:WebSocket,textMessage:String){
                val event=JSONObject(textMessage);when(event.optString("type")){
                    "response.audio.delta"->{val pcm=Base64.decode(event.getString("delta"),Base64.DEFAULT);if(!emit(AudioChunk(pcm,24000,range,sequence++))){cancelled.set(true);webSocket.cancel()}}
                    "response.audio.done"->webSocket.send(JSONObject().put("type","session.finish").toString())
                    "session.finished"->{done.countDown();webSocket.close(1000,null)}
                    "error"->{failure.set(IllegalStateException(event.toString()));done.countDown()}
                }
            }
            override fun onFailure(webSocket:WebSocket,t:Throwable,response:Response?){failure.set(t);done.countDown()}
            override fun onClosed(webSocket:WebSocket,code:Int,reason:String){done.countDown()}
        })
        awaitSocket(cancelled,done,socket,failure)
    }
}

class DeepgramProvider(private val secure:SecureStore):TtsProvider {
    override val id=ProviderIds.DEEPGRAM
    override val descriptor=ProviderDescriptor(id,"Deepgram",credentialFields=listOf(CredentialField("deepgram","API key")))
    private val models=listOf("aura-2-thalia-en","aura-2-apollo-en","aura-2-asteria-en","aura-2-orion-en","aura-2-luna-en","aura-2-zeus-en")
    override val voices=models.map{VoiceRecord("deepgram/$it@en-US","${it.substringAfter("aura-2-").substringBefore('-').replaceFirstChar(Char::uppercase)} · Deepgram",Locale.US,id,"Aura 2",setOf("en-US"),true,capabilities=setOf("streaming"))}
    override fun isAvailable(voice:VoiceRecord)=secure.get("deepgram").isNotBlank()
    override fun stream(session:PreparedSession,text:String,speed:Float,pitch:Float,cancelled:AtomicBoolean,emit:(AudioChunk)->Boolean){
        val key=secure.get("deepgram");require(key.isNotBlank()){ "Deepgram key is not configured" };val model=session.voice.id.substringAfter('/').substringBefore('@')
        val request=Request.Builder().url("wss://api.deepgram.com/v1/speak?model=$model&encoding=linear16&sample_rate=24000").header("Authorization","Token $key").build()
        val done=CountDownLatch(1);val failure=AtomicReference<Throwable?>();var sequence=0;val range=TextRange(0,text.length)
        lateinit var socket:WebSocket
        socket=HttpAudio.client.newWebSocket(request,object:WebSocketListener(){
            override fun onOpen(webSocket:WebSocket,response:Response){webSocket.send(JSONObject().put("type","Speak").put("text",text).toString());webSocket.send(JSONObject().put("type","Flush").toString())}
            override fun onMessage(webSocket:WebSocket,bytes:ByteString){if(!emit(AudioChunk(bytes.toByteArray(),24000,range,sequence++))){cancelled.set(true);webSocket.cancel()}}
            override fun onMessage(webSocket:WebSocket,textMessage:String){if(JSONObject(textMessage).optString("type")=="Flushed"){webSocket.send(JSONObject().put("type","Close").toString());done.countDown()}}
            override fun onFailure(webSocket:WebSocket,t:Throwable,response:Response?){failure.set(t);done.countDown()}
            override fun onClosed(webSocket:WebSocket,code:Int,reason:String){done.countDown()}
        });awaitSocket(cancelled,done,socket,failure)
    }
}

class CartesiaProvider(private val secure:SecureStore):TtsProvider {
    override val id=ProviderIds.CARTESIA
    override val descriptor=ProviderDescriptor(id,"Cartesia",credentialFields=listOf(CredentialField("cartesia","API key")))
    @Volatile private var catalog=listOf(VoiceRecord("cartesia/694f9389-aac1-45b6-b726-9d9369183238@en-US","Default · Cartesia",Locale.US,id,"Sonic 3",setOf("en-US"),true))
    override val voices get()=catalog
    override fun isAvailable(voice:VoiceRecord)=secure.get("cartesia").isNotBlank()
    override fun refresh(){
        val key=secure.get("cartesia");if(key.isBlank())return
        val json=JSONObject(String(HttpAudio.get("https://api.cartesia.ai/voices?limit=100&expand%5B%5D=preview_file_url",mapOf("Authorization" to "Bearer $key","Cartesia-Version" to "2026-03-01"))))
        val array=json.getJSONArray("data");val found=(0 until array.length()).map{array.getJSONObject(it)}.filter{it.optBoolean("is_public")||it.optBoolean("is_owner")}.map{
            val locale=listOf(it.optString("language","en"),it.optString("country").takeIf(String::isNotBlank)).filterNotNull().joinToString("-")
            VoiceRecord("cartesia/${it.getString("id")}@$locale","${it.optString("name","Voice")} · Cartesia",Locale.forLanguageTag(locale),id,"Sonic 3",setOf(locale),true,it.optString("description"),it.optString("preview_file_url"),capabilities=setOf("streaming","timestamps"))
        };if(found.isNotEmpty())catalog=found
    }
    override fun stream(session:PreparedSession,text:String,speed:Float,pitch:Float,cancelled:AtomicBoolean,emit:(AudioChunk)->Boolean){
        val key=secure.get("cartesia");require(key.isNotBlank()){ "Cartesia key is not configured" }
        val request=Request.Builder().url("wss://api.cartesia.ai/tts/websocket?cartesia_version=2026-03-01").header("X-API-Key",key).build()
        val done=CountDownLatch(1);val failure=AtomicReference<Throwable?>();var sequence=0;val range=TextRange(0,text.length);lateinit var socket:WebSocket
        socket=HttpAudio.client.newWebSocket(request,object:WebSocketListener(){
            override fun onOpen(webSocket:WebSocket,response:Response){
                val body=JSONObject().put("model_id","sonic-3").put("transcript",text).put("voice",JSONObject().put("mode","id").put("id",session.voice.id.substringAfter('/').substringBefore('@'))).put("language",session.language.substringBefore('-')).put("context_id",UUID.randomUUID().toString()).put("output_format",JSONObject().put("container","raw").put("encoding","pcm_s16le").put("sample_rate",24000)).put("continue",false)
                webSocket.send(body.toString())
            }
            override fun onMessage(webSocket:WebSocket,textMessage:String){val event=JSONObject(textMessage);when(event.optString("type")){"chunk"->{val pcm=Base64.decode(event.getString("data"),Base64.DEFAULT);if(!emit(AudioChunk(pcm,24000,range,sequence++))){cancelled.set(true);webSocket.cancel()}};"done"->{done.countDown();webSocket.close(1000,null)};"error"->{failure.set(IllegalStateException(event.toString()));done.countDown()}}}
            override fun onFailure(webSocket:WebSocket,t:Throwable,response:Response?){failure.set(t);done.countDown()}
            override fun onClosed(webSocket:WebSocket,code:Int,reason:String){done.countDown()}
        });awaitSocket(cancelled,done,socket,failure)
    }
}
