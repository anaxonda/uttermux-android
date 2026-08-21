package io.uttermux.android.benchmark

import android.content.Context
import android.os.Build
import android.os.Debug
import android.os.PowerManager
import android.os.SystemClock
import io.uttermux.android.BuildConfig
import io.uttermux.android.UtterMuxApp
import io.uttermux.android.config.AudioChunk
import io.uttermux.android.config.VoiceRecord
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

data class CandidateSummary(val threads:Int,val medianRtf:Double,val medianFirstAudioMs:Double,val peakPssMb:Int,val underruns:Int,val stable:Boolean)
data class BenchmarkOutcome(val artifactId:String,val artifactFingerprint:String,val voice:VoiceRecord,val candidates:List<CandidateSummary>,val winner:CandidateSummary,val classification:String,val report:File)
object BenchmarkPolicy {
    // Four is the safe ceiling for the standard phone sweep. Android reports
    // heterogeneous efficiency/performance cores as peers; testing 6/8 cores
    // can make a heavy ONNX graph dramatically slower and starve the UI.
    fun threadCandidates(cores:Int)=listOf(1,2,3,4).filter{it<=cores.coerceAtLeast(1)}.ifEmpty{listOf(1)}
    fun winner(candidates:List<CandidateSummary>):CandidateSummary {val valid=candidates.filter{it.stable};require(valid.isNotEmpty());val fastest=valid.minOf{it.medianRtf};return valid.filter{it.medianRtf<=fastest*1.05}.minBy{it.threads}}
    fun classification(value:CandidateSummary)=when{value.underruns>0->"marginal";value.medianRtf<=.85->"reader-ready";value.medianRtf<=1.0->"marginal";else->"too-slow"}
}

class BenchmarkRunner(private val app:UtterMuxApp) {
    private val store=File(app.filesDir,"benchmarks").apply{mkdirs()}
    fun artifactId(voice:VoiceRecord)=voice.downloadId.ifBlank{voice.modelVersion.ifBlank{voice.model}}
    fun installedArtifacts()=app.router.availableVoices.filter{!it.networkRequired}.distinctBy(::artifactId).sortedWith(compareBy({it.library},{it.model},{it.quantization}))
    fun latest(artifactId:String):JSONObject?=store.listFiles()?.filter{it.name.startsWith(safe(artifactId)+"-")&&it.extension=="json"}?.maxByOrNull(File::lastModified)?.let{runCatching{JSONObject(it.readText())}.getOrNull()}

    fun run(voice:VoiceRecord,cancelled:AtomicBoolean,progress:(String)->Unit):BenchmarkOutcome {
        val artifact=artifactId(voice);require(artifact.isNotBlank()){"Voice has no stable artifact ID"}
        val provider=app.providers.first{it.id==voice.provider};val fingerprint=app.models.artifactFingerprint(artifact)
        val cores=Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        val candidates=BenchmarkPolicy.threadCandidates(cores)
        val summaries=mutableListOf<CandidateSummary>();val allRuns=JSONArray()
        try {
            for(threads in candidates){
                if(cancelled.get())throw InterruptedException();checkThermal()
                progress("$artifact: $threads thread${if(threads==1)"" else "s"}")
                app.settings.setBenchmarkThreads(artifact,threads);provider.trimMemory()
                val runs=mutableListOf<Run>();for(index in 0 until 3){
                    if(cancelled.get())throw InterruptedException();if(index==0)provider.trimMemory()
                    runs+=measure(provider,voice,cancelled,index==0)
                    if(index==1&&summaries.isNotEmpty()&&runs.map{it.rtf}.average()>summaries.minOf{it.medianRtf}*1.25)break
                }
                val rtf=runs.map{it.rtf}.sorted().let{it[it.size/2]};val first=runs.map{it.firstMs}.sorted().let{it[it.size/2]}
                val summary=CandidateSummary(threads,rtf,first,runs.maxOf{it.pssMb},runs.sumOf{it.underruns},runs.all{it.valid});summaries+=summary
                allRuns.put(JSONObject().put("threads",threads).put("runs",JSONArray(runs.map{it.json()})))
            }
        } finally {app.settings.setBenchmarkThreads(artifact,0);provider.trimMemory()}
        val winner=runCatching{BenchmarkPolicy.winner(summaries)}.getOrElse{throw IllegalStateException("Every benchmark candidate failed")}
        val classification=BenchmarkPolicy.classification(winner)
        val model=runCatching{app.models.model(artifact)}.getOrNull();val artifactHash=fingerprint
        val report=JSONObject().put("schemaVersion",3).put("createdAt",System.currentTimeMillis()).put("application",JSONObject().put("name","UtterMux").put("version",BuildConfig.VERSION_NAME))
            .put("artifact",JSONObject().put("id",artifact).put("sha256",artifactHash).put("family",model?.family?:voice.library).put("quantization",voice.quantization))
            .put("voice",voice.id).put("language",voice.locale.toLanguageTag()).put("machine",JSONObject().put("platform","android").put("architecture",Build.SUPPORTED_ABIS.firstOrNull()).put("model",Build.MODEL).put("soc",if(Build.VERSION.SDK_INT>=31)Build.SOC_MODEL else "unknown").put("logicalCores",cores).put("totalRamMb",io.uttermux.android.config.HardwareAdvisor.detect(app).totalRamMb).put("inferenceProvider","CPU"))
            .put("candidates",allRuns).put("winner",JSONObject().put("threads",winner.threads).put("medianRtf",winner.medianRtf).put("medianFirstAudioMs",winner.medianFirstAudioMs).put("peakPssMb",winner.peakPssMb).put("underruns",winner.underruns)).put("classification",classification)
            .put("note","Performance metrics do not measure speech quality. Review and listen before applying or changing variants.")
        val file=File(store,"${safe(artifact)}-${System.currentTimeMillis()}.json");file.writeText(report.toString(2))
        return BenchmarkOutcome(artifact,fingerprint,voice,summaries,winner,classification,file)
    }

    private data class Run(val cold:Boolean,val firstMs:Double,val wallMs:Double,val audioSeconds:Double,val rtf:Double,val pssMb:Int,val underruns:Int,val valid:Boolean){fun json()=JSONObject().put("cold",cold).put("firstAudioMs",firstMs).put("wallMs",wallMs).put("audioSeconds",audioSeconds).put("rtf",rtf).put("peakPssMb",pssMb).put("underruns",underruns).put("valid",valid)}
    private fun measure(provider:io.uttermux.android.provider.TtsProvider,voice:VoiceRecord,cancelled:AtomicBoolean,cold:Boolean):Run {
        val text=passage(voice.locale.language);val began=SystemClock.elapsedRealtimeNanos();var first=0L;var audio=0.0;var buffered=0.0;var underruns=0;var valid=true
        provider.stream(provider.prepare(voice,voice.locale.toLanguageTag()),text,1f,1f,cancelled){chunk:AudioChunk->
            val now=SystemClock.elapsedRealtimeNanos();if(first==0L)first=now else {val playback=(now-first)/1e9;if(playback>buffered+.30)underruns++}
            if(chunk.sampleRate<=0||chunk.pcm16.isEmpty()||chunk.pcm16.size%2!=0)valid=false
            val seconds=if(chunk.sampleRate>0)chunk.pcm16.size/2.0/chunk.sampleRate else 0.0;audio+=seconds;buffered+=seconds;!cancelled.get()
        }
        val end=SystemClock.elapsedRealtimeNanos();require(first>0&&audio>0){"Provider returned no audio"};val wall=(end-began)/1e6
        return Run(cold,(first-began)/1e6,wall,audio,wall/1000.0/audio,(Debug.getPss()/1024).toInt(),underruns,valid)
    }
    private fun checkThermal(){if(Build.VERSION.SDK_INT>=29){val status=(app.getSystemService(Context.POWER_SERVICE) as PowerManager).currentThermalStatus;if(status>=PowerManager.THERMAL_STATUS_MODERATE)throw IllegalStateException("Device is thermally throttled; let it cool before benchmarking")}}
    private fun passage(language:String)=when(language.lowercase()){"fr"->"UtterMux mesure le démarrage, la continuité et la vitesse de cette voix. Plusieurs phrases permettent de vérifier une lecture soutenue avec une ponctuation naturelle.";"de"->"UtterMux misst Startzeit, Kontinuität und Geschwindigkeit dieser Stimme. Mehrere Sätze prüfen das anhaltende Lesen mit natürlicher Zeichensetzung.";"es"->"UtterMux mide el inicio, la continuidad y la velocidad de esta voz. Varias frases permiten comprobar una lectura sostenida con puntuación natural.";else->"UtterMux measures startup latency, continuity, and sustained synthesis speed for this installed voice. Several sentences provide enough natural punctuation to expose stalls while keeping every candidate directly comparable."}
    private fun safe(value:String)=value.replace(Regex("[^A-Za-z0-9._-]"),"-")
}
