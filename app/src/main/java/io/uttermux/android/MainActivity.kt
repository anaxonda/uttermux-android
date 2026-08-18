package io.uttermux.android

import android.Manifest
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.uttermux.android.audio.CompressedAudioDecoder
import io.uttermux.android.audio.Playback
import io.uttermux.android.config.*
import io.uttermux.android.provider.HttpAudio
import io.uttermux.android.service.KoReaderServerService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : ComponentActivity() {
    private val notifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        if (android.os.Build.VERSION.SDK_INT >= 33) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        val app = UtterMuxApp.instance
        if (intent.getBooleanExtra("enable_koreader", false)) app.settings.koReaderEnabled = true
        if (app.settings.koReaderEnabled) startForegroundService(Intent(this, KoReaderServerService::class.java))
        setContent {
            var theme by remember { mutableStateOf(app.settings.theme) }
            val dark = when(theme) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
            MaterialTheme(colorScheme = if(dark) darkColorScheme() else lightColorScheme()) {
                ManagerScreen(theme) { value -> app.settings.theme=value; theme=value }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun ManagerScreen(theme:String,onTheme:(String)->Unit) {
    val app = UtterMuxApp.instance; val scope=rememberCoroutineScope()
    var defaultVoice by remember { mutableStateOf(app.settings.defaultVoice) }
    var grokKey by remember { mutableStateOf(app.secure.get("grok")) }
    var elevenKey by remember { mutableStateOf(app.secure.get("elevenlabs")) }
    var query by remember { mutableStateOf("") }; var provider by remember { mutableStateOf("all") }
    var language by remember { mutableStateOf("all") }; var model by remember { mutableStateOf("all") }
    var status by remember { mutableStateOf("Loading provider catalogs…") }; var koReader by remember { mutableStateOf(app.settings.koReaderEnabled) }
    var revision by remember { mutableIntStateOf(0) }
    fun refresh() { scope.launch { val errors=withContext(Dispatchers.IO){app.refreshCatalogs()}; revision++; status=if(errors.isEmpty()) "Catalogs refreshed" else errors.joinToString() } }
    LaunchedEffect(Unit) { refresh() }
    val allVoices = remember(revision) { app.router.voices }
    val languages = remember(allVoices) { listOf("all")+allVoices.flatMap{it.languages}.map(Languages::normalized).distinct().sorted() }
    val models = remember(allVoices,provider,language) { listOf("all")+allVoices.filter{(provider=="all"||it.provider.name.equals(provider,true))&&(language=="all"||it.languages.any{l->Languages.matches(l,language)})}.map{it.model}.distinct().sorted() }
    val voices = allVoices.filter { voice ->
        (provider=="all"||voice.provider.name.equals(provider,true)) &&
        (language=="all"||voice.languages.any{Languages.matches(it,language)}) && (model=="all"||voice.model==model) &&
        (query.isBlank()||listOf(voice.name,voice.id,voice.model,voice.description,voice.languages.joinToString()).any{it.contains(query,true)})
    }
    Scaffold(topBar={TopAppBar(title={Text("UtterMux")})}) { padding ->
        LazyColumn(Modifier.padding(padding).padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            item { Text("Linux-style routing for Android and KOReader",style=MaterialTheme.typography.titleMedium) }
            item { Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Button(onClick={app.startActivity(Intent("com.android.settings.TTS_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))}){Text("Android TTS settings")}
                OutlinedButton(onClick={refresh()}){Text("Refresh catalogs")}
            } }
            item { Selector("Theme",theme,listOf("system","light","dark"),onTheme) }
            item { OutlinedTextField(grokKey,{grokKey=it},Modifier.fillMaxWidth(),label={Text("Grok API key")},singleLine=true) }
            item { OutlinedTextField(elevenKey,{elevenKey=it},Modifier.fillMaxWidth(),label={Text("ElevenLabs API key")},singleLine=true) }
            item { Button(onClick={
                app.secure.put("grok",grokKey.trim());app.secure.put("elevenlabs",elevenKey.trim());status="Keys encrypted; refreshing…";refresh()
            }){Text("Save keys and refresh")}}
            item { Row(verticalAlignment=Alignment.CenterVertically) {
                Switch(koReader,{enabled->koReader=enabled;app.settings.koReaderEnabled=enabled;val intent=Intent(app,KoReaderServerService::class.java);if(enabled)app.startForegroundService(intent)else app.stopService(intent)})
                Spacer(Modifier.width(8.dp));Column{Text("KOReader server · localhost:5000");Text(if(koReader)"Enabled" else "Disabled",style=MaterialTheme.typography.bodySmall)}
            } }
            item { OutlinedTextField(query,{query=it},Modifier.fillMaxWidth(),label={Text("Search voice, accent, language, or model")},singleLine=true) }
            item { Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)){Selector("Provider",provider,listOf("all","grok","elevenlabs","edge","sherpa")){provider=it;model="all"}}
                Box(Modifier.weight(1f)){Selector("Language",language,languages){language=it;model="all"}}
            } }
            item { Selector("Model",model,models){model=it} }
            item { Text("$status · ${voices.size} of ${allVoices.size} voices",style=MaterialTheme.typography.bodySmall) }
            items(voices,key={it.id}) { voice ->
                val localId=voice.downloadId.ifBlank{voice.takeIf{it.provider==ProviderKind.SHERPA}?.id?.split('/')?.getOrNull(1).orEmpty()}
                var installed by remember(voice.id,revision){mutableStateOf(localId.isBlank()||runCatching{app.models.installed(localId)}.getOrDefault(false))}
                Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)){Text(voice.name,style=MaterialTheme.typography.titleSmall);Text("${voice.provider.name.lowercase()} · ${voice.model} · ${voice.languages.joinToString()}",style=MaterialTheme.typography.bodySmall);if(voice.description.isNotBlank())Text(voice.description,style=MaterialTheme.typography.bodySmall)}
                        RadioButton(voice.id==defaultVoice,{app.settings.defaultVoice=voice.id;defaultVoice=voice.id;status="Default: ${voice.name}"})
                    }
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
                        if(localId.isNotBlank()) AssistChip(onClick={},label={Text(when{installed->"Installed";voice.downloadable->"Not installed";else->"Preview only"})})
                        if(localId.isNotBlank()&&!installed&&voice.downloadable) TextButton(onClick={
                            status="Downloading $localId…";scope.launch{runCatching{withContext(Dispatchers.IO){app.models.install(localId){}}}.onSuccess{installed=true;status="Installed $localId"}.onFailure{status=it.message?:"Install failed"}}
                        }){Text("Download")}
                        TextButton(enabled=installed||voice.previewUrl.isNotBlank(),onClick={
                            status="Previewing ${voice.name}…";scope.launch{runCatching{withContext(Dispatchers.IO){
                                val audio=if(voice.previewUrl.isNotBlank()&&(localId.isBlank()||!installed))CompressedAudioDecoder.mp3(app,HttpAudio.get(voice.previewUrl)) else app.router.synthesize(voice.id,sample(voice.locale.language),voice.locale.toLanguageTag(),1f,AtomicBoolean())
                                Playback.play(audio)
                            }}.onSuccess{status="Previewed ${voice.name}"}.onFailure{status="Preview failed: ${it.message}"}}
                        }){Text("Preview")}
                    }
                } }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun Selector(label:String,value:String,options:List<String>,onSelect:(String)->Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded,{expanded=it}) {
        OutlinedTextField(value,{ },Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),readOnly=true,label={Text(label)},trailingIcon={ExposedDropdownMenuDefaults.TrailingIcon(expanded)})
        ExposedDropdownMenu(expanded,{expanded=false}) { options.forEach { option -> DropdownMenuItem(text={Text(option)},onClick={onSelect(option);expanded=false}) } }
    }
}

private fun sample(language:String)=when(language){"fr"->"Bonjour. Voici un aperçu de cette voix avec UtterMux.";"de"->"Hallo. Dies ist eine Vorschau dieser Stimme mit UtterMux.";"es"->"Hola. Esta es una muestra de esta voz con UtterMux.";"it"->"Ciao. Questa è un'anteprima della voce.";"pt"->"Olá. Esta é uma prévia desta voz.";else->"This is an UtterMux voice preview."}
