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

@Composable fun BenchmarkPage(onStatus:(String)->Unit){
    val app=UtterMuxApp.instance;val runner=remember{BenchmarkRunner(app)};val scope=rememberCoroutineScope()
    var revision by remember{mutableIntStateOf(0)};val voices=remember(revision){runner.installedArtifacts()}
    var active by remember{mutableStateOf<String?>(null)};var cancel by remember{mutableStateOf<AtomicBoolean?>(null)};var pending by remember{mutableStateOf<BenchmarkOutcome?>(null)};var progress by remember{mutableStateOf("Choose an installed local model.")}
    LazyColumn(Modifier.fillMaxSize().padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
        item{Text("Tune this device",style=MaterialTheme.typography.headlineSmall);Text("Benchmarks each installed artifact independently. Performance measurements cannot judge voice quality.")}
        if(voices.isEmpty())item{Card{Text("No local model is installed.",Modifier.padding(12.dp))}}
        items(voices,key={runner.artifactId(it)}){voice->val artifact=runner.artifactId(voice);val latest=runner.latest(artifact);Card{Column(Modifier.fillMaxWidth().padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
            Text(voice.model,style=MaterialTheme.typography.titleMedium);Text(listOf(voice.library,voice.modelVersion,voice.quantization).filter(String::isNotBlank).joinToString(" · "),style=MaterialTheme.typography.bodySmall)
            val fingerprint=app.models.artifactFingerprint(artifact);Text("Active tuning: "+app.settings.tunedThreads(artifact,fingerprint).takeIf{it>0}?.let{"$it threads · Tuned"}.orEmpty().ifBlank{app.settings.engineThreads.takeIf{it>0}?.let{"$it threads · Manual"}?:"Automatic"})
            if(latest!=null)Text("Last result: ${latest.optString("classification")} · ${latest.optJSONObject("winner")?.optDouble("medianRtf")} RTF",style=MaterialTheme.typography.labelSmall)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Button(enabled=active==null,onClick={val signal=AtomicBoolean();cancel=signal;active=artifact;scope.launch{runCatching{withContext(Dispatchers.Default){runner.run(voice,signal){message->scope.launch{progress=message}}}}.onSuccess{pending=it;progress="Review ${it.classification}: ${"%.3f".format(it.winner.medianRtf)} RTF, ${it.winner.threads} threads"}.onFailure{progress=if(it is InterruptedException)"Benchmark cancelled" else "Benchmark failed: ${it.message}"};active=null;cancel=null;revision++}}){Text(if(active==artifact)"Running…" else "Benchmark")};OutlinedButton(enabled=active==null,onClick={scope.launch{runCatching{PreviewController.play(voice.id){signal->app.router.synthesizeExact(voice.id,"UtterMux variant comparison preview.",voice.locale.toLanguageTag(),1f,signal)}}.onFailure{onStatus("Preview failed: ${it.message}")}}}){Text("Preview")};if(app.settings.tunedThreads(artifact,fingerprint)>0)TextButton(enabled=active==null,onClick={app.settings.setTunedThreads(artifact,0);app.providers.forEach{it.trimMemory()};revision++}){Text("Reset")}}
        }}}
        item{Text(progress,style=MaterialTheme.typography.bodySmall);if(active!=null)OutlinedButton(onClick={cancel?.set(true)}){Text("Cancel benchmark")}}
    }
    pending?.let{result->AlertDialog(onDismissRequest={pending=null},title={Text("Apply tuned profile?")},text={Text("${result.voice.model}: ${result.winner.threads} threads, RTF ${"%.3f".format(result.winner.medianRtf)}, ${result.classification}. This does not assess speech quality or change the model variant.")},confirmButton={Button(onClick={app.settings.setTunedThreads(result.artifactId,result.winner.threads,result.artifactFingerprint);app.providers.forEach{it.trimMemory()};pending=null;revision++;onStatus("Applied tuning for ${result.voice.model}")}){Text("Apply")}},dismissButton={TextButton(onClick={pending=null}){Text("Keep current")}})}
}
