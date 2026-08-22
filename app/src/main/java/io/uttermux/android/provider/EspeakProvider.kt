package io.uttermux.android.provider

import android.content.Context
import com.reecedunn.espeak.SpeechSynthesis
import io.uttermux.android.R
import io.uttermux.android.config.*
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipInputStream

class EspeakProvider(private val context:Context):TtsProvider,SpeechSynthesis.Callback {
    override val id=ProviderIds.ESPEAK
    override val descriptor=ProviderDescriptor(id,"eSpeak NG",network=false,note="Compact offline formant synthesis")
    private val lock=Any();private var emitter:((AudioChunk)->Boolean)?=null;private var cancellation:AtomicBoolean?=null;private var sequence=0;private var textLength=0
    private val engine:SpeechSynthesis
    override val voices:List<VoiceRecord>
    init {
        val parent=File(context.filesDir,"espeak");val data=File(parent,"espeak-ng-data")
        if(!File(data,"phondata").isFile)extract(parent)
        engine=SpeechSynthesis(parent.absolutePath,this)
        val raw=engine.voices();val found=ArrayList<VoiceRecord>();var index=0
        while(index+3<raw.size){
            val language=Languages.normalized(raw[index]);val identifier=raw[index+1];val gender=when(raw[index+2]){"1"->"male";"2"->"female";else->"unspecified"};val locale=Locale.forLanguageTag(language)
            found+=VoiceRecord("espeak/$identifier@$language","${locale.getDisplayName(Locale.ENGLISH)} · eSpeak NG",locale,id,"eSpeak NG",setOf(language),false,
                downloadable=false,status="ready",license="GPL-3.0-or-later",gender=gender,performanceClass="fast",library="eSpeak NG",sourceUrl="https://github.com/espeak-ng/espeak-ng")
            index+=4
        }
        voices=found.distinctBy{it.id}.sortedBy{it.name}
    }
    private fun extract(parent:File){
        parent.mkdirs();val resource=R.raw.espeakdata
        val canonical=parent.canonicalPath+File.separator
        ZipInputStream(context.resources.openRawResource(resource).buffered()).use{zip->while(true){val entry=zip.nextEntry?:break;val target=File(parent,entry.name)
            require(target.canonicalPath.startsWith(canonical)){"invalid eSpeak data path"};if(entry.isDirectory)target.mkdirs() else {target.parentFile?.mkdirs();target.outputStream().use{zip.copyTo(it)}};zip.closeEntry()}}
    }
    override fun strategy(voice:VoiceRecord)=StreamStrategy.DIRECT_STREAM
    override fun stream(session:PreparedSession,text:String,speed:Float,pitch:Float,cancelled:AtomicBoolean,emit:(AudioChunk)->Boolean){synchronized(lock){
        val selector=session.voice.id.removePrefix("espeak/").substringBeforeLast("@");check(engine.setVoice(selector)){"eSpeak NG rejected voice $selector"}
        engine.setRate((175*speed).toInt().coerceIn(80,450));engine.setPitch((50*pitch).toInt().coerceIn(0,100));emitter=emit;cancellation=cancelled;sequence=0;textLength=text.length
        try{if(!engine.synthesize(text)&&!cancelled.get())error("eSpeak NG synthesis failed")}finally{emitter=null;cancellation=null}
    }}
    override fun onAudio(pcm:ByteArray){
        if(cancellation?.get()==true){engine.stop();return}
        val generated=(pcm.size.toLong()/2L)*1_000_000_000L/engine.sampleRate().coerceAtLeast(1)
        if(emitter?.invoke(AudioChunk(pcm,engine.sampleRate(),TextRange(0,textLength),sequence++,generated))==false)engine.stop()
    }
    override fun onComplete()=Unit
    override fun onWord(position:Int,length:Int,frame:Int)=Unit
}
