package io.uttermux.android.provider

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.Uri
import io.uttermux.android.audio.CompressedAudioDecoder
import io.uttermux.android.audio.PcmTransform
import io.uttermux.android.config.AudioData
import io.uttermux.android.config.Languages
import io.uttermux.android.config.VoiceProfile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class PocketProfileStore(
    private val context:Context,
    private val engineId:String="pocket",
    private val profileModelVersion:String=MODEL_VERSION,
    namespace:String=engineId,
) {
    private val prefs=context.getSharedPreferences(if(namespace=="pocket")"pocket_profiles" else "voice_profiles_$namespace",Context.MODE_PRIVATE)
    private val root=File(context.filesDir,"voice-profiles/$namespace").apply{mkdirs()}
    fun profiles():List<VoiceProfile>{val data=prefs.getString("profiles","[]")?:"[]";return runCatching{val a=JSONArray(data);(0 until a.length()).map{i->a.getJSONObject(i).let{o->VoiceProfile(
        o.getString("id"),o.getString("name"),Languages.normalized(o.optString("language","en-US")),o.optString("engine",engineId),o.optString("modelVersion",profileModelVersion),o.optString("referenceFile",o.optString("file")),o.optLong("createdAt",0L),o.optBoolean("localOnly",true),o.optString("speakerEmbeddingFile"),o.optString("iclPromptFile"),o.optString("referenceText"))}}}.getOrDefault(emptyList())}
    private fun save(items:List<VoiceProfile>){prefs.edit().putString("profiles",JSONArray().also{a->items.forEach{p->a.put(JSONObject().put("schemaVersion",2).put("id",p.id).put("name",p.name).put("language",p.language).put("engine",p.engine).put("modelVersion",p.modelVersion).put("referenceFile",p.referenceFile).put("createdAt",p.createdAt).put("localOnly",p.localOnly).put("speakerEmbeddingFile",p.speakerEmbeddingFile).put("iclPromptFile",p.iclPromptFile).put("referenceText",p.referenceText))}}.toString()).apply()}
    fun import(uri:Uri,name:String,language:String="en-US"):VoiceProfile {
        val bytes=requireNotNull(context.contentResolver.openInputStream(uri)){"Cannot open recording"}.use{it.readBytes()}
        val audio=CompressedAudioDecoder.decode(context,bytes,uri.lastPathSegment?.substringAfterLast('.',"audio")?:"audio")
        return add(name,language,audio.pcm16,audio.sampleRate)
    }
    @Suppress("MissingPermission")
    fun record(name:String,language:String="en-US",durationMs:Int=8_000):VoiceProfile {
        val rate=24_000;val min=AudioRecord.getMinBufferSize(rate,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT).coerceAtLeast(4096)
        val recorder=AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION,rate,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,min*2)
        require(recorder.state==AudioRecord.STATE_INITIALIZED){"Microphone could not be initialized"}
        val target=rate*2*durationMs/1000;val output=java.io.ByteArrayOutputStream(target);val buffer=ByteArray(min)
        try{recorder.startRecording();while(output.size()<target){val n=recorder.read(buffer,0,min);if(n>0)output.write(buffer,0,minOf(n,target-output.size()))}}finally{runCatching{recorder.stop()};recorder.release()}
        return add(name,language,output.toByteArray(),rate)
    }
    private fun add(rawName:String,language:String,pcm:ByteArray,sampleRate:Int):VoiceProfile {
        val name=rawName.trim().ifBlank{"My Pocket voice"};val id=UUID.randomUUID().toString();val normalized=PcmTransform.trimSilence(PcmTransform.resamplePcm16(pcm,sampleRate,24_000),24_000)
        require(normalized.size>=24_000*2){"Reference needs at least one second of clear speech"}
        val file=File(root,"$id.wav");writeWav(file,normalized,24_000)
        return VoiceProfile(id,name,Languages.normalized(language).ifBlank{"en-US"},engineId,profileModelVersion,file.absolutePath,System.currentTimeMillis()).also{save(profiles()+it)}
    }
    fun rename(id:String,name:String):Boolean {val trimmed=name.trim();if(trimmed.isBlank())return false;val items=profiles();if(items.none{it.id==id})return false;save(items.map{if(it.id==id)it.copy(name=trimmed)else it});return true}
    fun artifactPath(profile:VoiceProfile,kind:String):File=File(root,"${profile.id}-$kind.bin")
    fun setPreparedArtifacts(id:String,speakerEmbedding:File?=null,iclPrompt:File?=null,referenceText:String=""):VoiceProfile {
        val items=profiles();val current=items.firstOrNull{it.id==id}?:error("Voice profile no longer exists")
        val updated=current.copy(speakerEmbeddingFile=speakerEmbedding?.absolutePath?:current.speakerEmbeddingFile,
            iclPromptFile=iclPrompt?.absolutePath?:current.iclPromptFile,referenceText=referenceText.ifBlank{current.referenceText})
        save(items.map{if(it.id==id)updated else it});return updated
    }
    fun reference(profile:VoiceProfile):AudioData {val bytes=File(profile.referenceFile).readBytes();require(bytes.size>44){"Reference recording is empty"};return AudioData(24_000,bytes.copyOfRange(44,bytes.size))}
    fun delete(id:String):Boolean {val items=profiles();val profile=items.firstOrNull{it.id==id}?:return false;listOf(profile.referenceFile,profile.speakerEmbeddingFile,profile.iclPromptFile).filter{it.isNotBlank()}.forEach{File(it).delete()};save(items.filterNot{it.id==id});return true}
    private fun writeWav(file:File,pcm:ByteArray,rate:Int)=FileOutputStream(file).use{out->
        fun le(value:Int,count:Int){repeat(count){out.write(value shr (8*it) and 0xff)}}
        out.write("RIFF".toByteArray());le(36+pcm.size,4);out.write("WAVEfmt ".toByteArray());le(16,4);le(1,2);le(1,2);le(rate,4);le(rate*2,4);le(2,2);le(16,2);out.write("data".toByteArray());le(pcm.size,4);out.write(pcm)
    }
    companion object {const val MODEL_VERSION="sherpa-onnx-pocket-tts-int8-2026-01-26"}
}
