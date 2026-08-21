package io.uttermux.android

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.uttermux.android.audio.PreviewController
import io.uttermux.android.benchmark.BenchmarkOutcome
import io.uttermux.android.benchmark.BenchmarkRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

@Composable fun BenchmarkPage(focusArtifact:String="",onStatus:(String)->Unit){
    val app=UtterMuxApp.instance;val runner=remember{BenchmarkRunner(app)};val scope=rememberCoroutineScope()
    var revision by remember{mutableIntStateOf(0)};val voices=remember(revision,focusArtifact){runner.installedArtifacts().sortedBy{if(runner.artifactId(it)==focusArtifact)0 else 1}}
    var active by remember{mutableStateOf<String?>(null)};var cancel by remember{mutableStateOf<AtomicBoolean?>(null)};var pending by remember{mutableStateOf<BenchmarkOutcome?>(null)};var progress by remember{mutableStateOf("Choose an installed local model.")}
    LazyColumn(Modifier.fillMaxSize().padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
        item{Text("Test and tune local models",style=MaterialTheme.typography.headlineSmall);Text("Preview checks that a voice works and sounds correct. Benchmark measures startup, throughput, memory, and CPU thread choices for each installed artifact; it cannot judge voice quality.")}
        if(voices.isEmpty())item{Card{Text("No local model is installed.",Modifier.padding(12.dp))}}
        items(voices,key={runner.artifactId(it)}){voice->val artifact=runner.artifactId(voice);val latest=runner.latest(artifact);val latestWinner=latest?.optJSONObject("winner");val preview by PreviewController.state.collectAsState();val previewActive=preview.voiceId==voice.id&&preview.phase in setOf("loading","playing");Card{Column(Modifier.fillMaxWidth().padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
            Text(voice.model,style=MaterialTheme.typography.titleMedium);Text(listOf(voice.library,voice.modelVersion,voice.quantization).filter(String::isNotBlank).joinToString(" · "),style=MaterialTheme.typography.bodySmall)
            val fingerprint=app.models.artifactFingerprint(artifact);Text("Active tuning: "+app.settings.tunedThreads(artifact,fingerprint).takeIf{it>0}?.let{"$it threads · Tuned"}.orEmpty().ifBlank{app.settings.engineThreads.takeIf{it>0}?.let{"$it threads · Manual"}?:"Automatic"})
            if(latestWinner!=null)Text("Last result: ${latest.optString("classification")} · ${latestWinner.optInt("threads")} threads · RTF ${"%.3f".format(latestWinner.optDouble("medianRtf"))} · first PCM ${latestWinner.optDouble("medianFirstAudioMs").toInt()} ms · peak ${latestWinner.optInt("peakPssMb")} MB${latestWinner.optInt("underruns").takeIf{it>0}?.let{" · $it underruns"}.orEmpty()}",style=MaterialTheme.typography.labelSmall)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Button(enabled=active==null,onClick={val signal=AtomicBoolean();cancel=signal;active=artifact;scope.launch{runCatching{withContext(Dispatchers.Default){runner.run(voice,signal){message->scope.launch{progress=message}}}}.onSuccess{pending=it;progress="Review ${it.classification}: ${"%.3f".format(it.winner.medianRtf)} RTF, ${it.winner.threads} threads"}.onFailure{progress=if(it is InterruptedException)"Benchmark cancelled" else "Benchmark failed: ${it.message}"};active=null;cancel=null;revision++}}){Text(if(active==artifact)"Running…" else "Benchmark")};OutlinedButton(enabled=active==null,onClick={if(previewActive)PreviewController.stop()else scope.launch{runCatching{PreviewController.play(voice.id){signal->app.router.synthesizeExact(voice.id,"UtterMux variant comparison preview.",voice.locale.toLanguageTag(),1f,signal)}}.onFailure{onStatus("Preview failed: ${it.message}")}}}){Text(if(previewActive)"Stop" else "Preview")};if(app.settings.tunedThreads(artifact,fingerprint)>0)TextButton(enabled=active==null,onClick={app.settings.setTunedThreads(artifact,0);app.providers.forEach{it.trimMemory()};revision++}){Text("Reset")}}
            if(preview.voiceId==voice.id&&preview.phase!="idle")Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){if(preview.phase=="loading")CircularProgressIndicator(Modifier.size(18.dp),strokeWidth=2.dp);Text(preview.message,style=MaterialTheme.typography.labelSmall)}
        }}}
        item{Text(progress,style=MaterialTheme.typography.bodySmall);if(active!=null)OutlinedButton(onClick={cancel?.set(true)}){Text("Cancel benchmark")}}
    }
    pending?.let{result->AlertDialog(onDismissRequest={pending=null},title={Text("Apply tuned profile?")},text={Column(verticalArrangement=Arrangement.spacedBy(6.dp)){Text("${result.voice.model} · ${result.classification}");Text("Recommended: ${result.winner.threads} threads\nRTF: ${"%.3f".format(result.winner.medianRtf)}\nMedian first PCM: ${result.winner.medianFirstAudioMs.toInt()} ms\nPeak process memory: ${result.winner.peakPssMb} MB\nUnderruns: ${result.winner.underruns}");HorizontalDivider();Text("Candidates",style=MaterialTheme.typography.titleSmall);result.candidates.forEach{candidate->Text("${candidate.threads} thread${if(candidate.threads==1)"" else "s"}: RTF ${"%.3f".format(candidate.medianRtf)}, first ${candidate.medianFirstAudioMs.toInt()} ms, ${candidate.peakPssMb} MB, ${candidate.underruns} underruns",style=MaterialTheme.typography.bodySmall)};Text("Performance metrics do not assess speech quality or change the model variant.",style=MaterialTheme.typography.labelSmall)}},confirmButton={Button(onClick={app.settings.setTunedThreads(result.artifactId,result.winner.threads,result.artifactFingerprint);app.providers.forEach{it.trimMemory()};pending=null;revision++;onStatus("Applied tuning for ${result.voice.model}")}){Text("Apply")}},dismissButton={TextButton(onClick={pending=null}){Text("Keep current")}})}
}
