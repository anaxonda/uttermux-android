package io.uttermux.android.provider

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.Uri
import io.uttermux.android.audio.CompressedAudioDecoder
import io.uttermux.android.audio.PcmTransform
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

data class PocketProfile(val id:String,val name:String,val file:String,val language:String="en-US")

class PocketProfileStore(private val context:Context) {
    private val prefs=context.getSharedPreferences("pocket_profiles",Context.MODE_PRIVATE)
    private val root=File(context.filesDir,"voice-profiles/pocket").apply{mkdirs()}
    fun profiles():List<PocketProfile>{val data=prefs.getString("profiles","[]")?:"[]";return runCatching{val a=JSONArray(data);(0 until a.length()).map{i->a.getJSONObject(i).let{PocketProfile(it.getString("id"),it.getString("name"),it.getString("file"),it.optString("language","en-US"))}}}.getOrDefault(emptyList())}
    private fun save(items:List<PocketProfile>){prefs.edit().putString("profiles",JSONArray().also{a->items.forEach{p->a.put(JSONObject().put("id",p.id).put("name",p.name).put("file",p.file).put("language",p.language))}}.toString()).apply()}
    fun import(uri:Uri,name:String):PocketProfile {
        val bytes=requireNotNull(context.contentResolver.openInputStream(uri)){"Cannot open recording"}.use{it.readBytes()}
        val audio=CompressedAudioDecoder.decode(context,bytes,uri.lastPathSegment?.substringAfterLast('.',"audio")?:"audio")
        return add(name,audio.pcm16,audio.sampleRate)
    }
    @Suppress("MissingPermission")
    fun record(name:String,durationMs:Int=8_000):PocketProfile {
        val rate=24_000;val min=AudioRecord.getMinBufferSize(rate,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT).coerceAtLeast(4096)
        val recorder=AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION,rate,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,min*2)
        require(recorder.state==AudioRecord.STATE_INITIALIZED){"Microphone could not be initialized"}
        val target=rate*2*durationMs/1000;val output=java.io.ByteArrayOutputStream(target);val buffer=ByteArray(min)
        try{recorder.startRecording();while(output.size()<target){val n=recorder.read(buffer,0,min);if(n>0)output.write(buffer,0,minOf(n,target-output.size()))}}finally{runCatching{recorder.stop()};recorder.release()}
        return add(name,output.toByteArray(),rate)
    }
    private fun add(rawName:String,pcm:ByteArray,sampleRate:Int):PocketProfile {
        val name=rawName.trim().ifBlank{"My Pocket voice"};val id=UUID.randomUUID().toString();val normalized=PcmTransform.trimSilence(PcmTransform.resamplePcm16(pcm,sampleRate,24_000),24_000)
        require(normalized.size>=24_000*2){"Reference needs at least one second of clear speech"}
        val file=File(root,"$id.wav");writeWav(file,normalized,24_000)
        return PocketProfile(id,name,file.absolutePath).also{save(profiles()+it)}
    }
    fun delete(id:String):Boolean {val items=profiles();val profile=items.firstOrNull{it.id==id}?:return false;File(profile.file).delete();save(items.filterNot{it.id==id});return true}
    private fun writeWav(file:File,pcm:ByteArray,rate:Int)=FileOutputStream(file).use{out->
        fun le(value:Int,count:Int){repeat(count){out.write(value shr (8*it) and 0xff)}}
        out.write("RIFF".toByteArray());le(36+pcm.size,4);out.write("WAVEfmt ".toByteArray());le(16,4);le(1,2);le(1,2);le(rate,4);le(rate*2,4);le(2,2);le(16,2);out.write("data".toByteArray());le(pcm.size,4);out.write(pcm)
    }
}
