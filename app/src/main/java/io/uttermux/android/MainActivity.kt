package io.uttermux.android

import android.Manifest
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.uttermux.android.audio.CompressedAudioDecoder
import io.uttermux.android.audio.Playback
import io.uttermux.android.catalog.BundledCatalog
import io.uttermux.android.config.*
import io.uttermux.android.diagnostics.Diagnostics
import io.uttermux.android.download.ModelDownloads
import io.uttermux.android.provider.HttpAudio
import io.uttermux.android.service.KoReaderServerService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity:ComponentActivity(){
    private val notifications=registerForActivityResult(ActivityResultContracts.RequestPermission()){}
    override fun onCreate(savedInstanceState:android.os.Bundle?){super.onCreate(savedInstanceState)
        if(android.os.Build.VERSION.SDK_INT>=33)notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        val app=UtterMuxApp.instance;if(intent.getBooleanExtra("enable_koreader",false))app.settings.koReaderEnabled=true
        if(app.settings.koReaderEnabled)startForegroundService(Intent(this,KoReaderServerService::class.java))
        setContent{var theme by remember{mutableStateOf(app.settings.theme)};val dark=when(theme){"dark"->true;"light"->false;else->androidx.compose.foundation.isSystemInDarkTheme()}
            MaterialTheme(colorScheme=if(dark)darkColorScheme()else lightColorScheme()){UtterMuxManager(theme){app.settings.theme=it;theme=it}}}
    }
}

private enum class Page(val label:String){VOICES("Voices"),ROUTES("Routes"),MODELS("Models"),PROVIDERS("Providers"),SETTINGS("Settings"),DIAGNOSTICS("Diagnostics")}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun UtterMuxManager(theme:String,onTheme:(String)->Unit){
    val app=UtterMuxApp.instance;val scope=rememberCoroutineScope();var page by remember{mutableStateOf(Page.VOICES)};var revision by remember{mutableIntStateOf(0)};var status by remember{mutableStateOf("Ready")}
    fun refresh(){scope.launch{status="Refreshing catalogs…";val errors=withContext(Dispatchers.IO){app.refreshCatalogs()};revision++;status=if(errors.isEmpty())"Catalogs refreshed" else errors.joinToString()}}
    LaunchedEffect(Unit){refresh()}
    Scaffold(topBar={TopAppBar(title={Column{Text("UtterMux");Text(status,style=MaterialTheme.typography.labelSmall)}})},bottomBar={NavigationBar{Page.entries.forEach{item->NavigationBarItem(selected=page==item,onClick={page=item},icon={Text(when(item){Page.VOICES->"◉";Page.ROUTES->"⇄";Page.MODELS->"↓";Page.PROVIDERS->"☁";Page.SETTINGS->"⚙";Page.DIAGNOSTICS->"≡"})},label={Text(item.label)})}}}){padding->
        Box(Modifier.padding(padding)){when(page){
            Page.VOICES->VoicesPage(revision,{revision++},{status=it})
            Page.ROUTES->RoutesPage(revision,{status=it})
            Page.MODELS->ModelsPage(revision,{revision++},{status=it})
            Page.PROVIDERS->ProvidersPage(revision,{refresh()},{status=it})
            Page.SETTINGS->SettingsPage(theme,onTheme,{status=it})
            Page.DIAGNOSTICS->DiagnosticsPage()
        }}
    }
}

@Composable private fun VoicesPage(revision:Int,onChanged:()->Unit,onStatus:(String)->Unit){
    val app=UtterMuxApp.instance;val scope=rememberCoroutineScope();var query by remember{mutableStateOf("")};var provider by remember{mutableStateOf("all")};var language by remember{mutableStateOf("all")};var model by remember{mutableStateOf("all")};var locality by remember{mutableStateOf("all")};var defaultVoice by remember(revision){mutableStateOf(app.settings.defaultVoice)}
    val all=remember(revision){app.router.voices};val providers=listOf("all")+all.map{it.provider}.distinct().sorted();val languages=listOf("all")+all.flatMap{it.languages}.map(Languages::normalized).distinct().sorted()
    val models=listOf("all")+all.filter{provider=="all"||it.provider==provider}.map{it.model}.distinct().sorted()
    val shown=all.filter{voice->(provider=="all"||voice.provider==provider)&&(language=="all"||voice.languages.any{Languages.matches(it,language)})&&(model=="all"||voice.model==model)&&(locality=="all"||(locality=="local"&&!voice.networkRequired)||(locality=="cloud"&&voice.networkRequired))&&(query.isBlank()||listOf(voice.name,voice.id,voice.model,voice.description,voice.languages.joinToString(),voice.status,voice.license).any{it.contains(query,true)})}
    LazyColumn(Modifier.fillMaxSize().padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
        item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Button(onClick={app.startActivity(Intent("com.android.settings.TTS_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))}){Text("Android TTS settings")};OutlinedButton(onClick=onChanged){Text("Refresh view")}}}
        item{OutlinedTextField(query,{query=it},Modifier.fillMaxWidth(),label={Text("Search voice, accent, language, model, or status")},singleLine=true)}
        item{Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){Box(Modifier.weight(1f)){Selector("Provider",provider,providers){provider=it;model="all"}};Box(Modifier.weight(1f)){Selector("Language",language,languages){language=it}}}}
        item{Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){Box(Modifier.weight(1f)){Selector("Model",model,models){model=it}};Box(Modifier.weight(1f)){Selector("Location",locality,listOf("all","local","cloud")){locality=it}}}}
        item{Text("${shown.size} of ${all.size} voices",style=MaterialTheme.typography.bodySmall)}
        items(shown,key={it.id}){voice->VoiceCard(voice,voice.id==defaultVoice,{app.settings.defaultVoice=voice.id;defaultVoice=voice.id;Thread{app.router.warm(voice.id)}.start();onStatus("Default: ${voice.name}")},{onChanged()},{onStatus(it)})}
    }
}

@Composable private fun VoiceCard(voice:VoiceRecord,selected:Boolean,onDefault:()->Unit,onChanged:()->Unit,onStatus:(String)->Unit){
    val app=UtterMuxApp.instance;val scope=rememberCoroutineScope();val localId=voice.downloadId.ifBlank{voice.takeIf{it.provider==ProviderIds.SHERPA}?.id?.split('/')?.getOrNull(1).orEmpty()};var installed by remember(voice.id){mutableStateOf(localId.isBlank()||runCatching{app.models.installed(localId)}.getOrDefault(false))}
    Card(Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){
        Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(voice.name,style=MaterialTheme.typography.titleSmall);Text("${voice.provider} · ${voice.model} · ${voice.languages.joinToString()}",style=MaterialTheme.typography.bodySmall);if(voice.description.isNotBlank())Text(voice.description,style=MaterialTheme.typography.bodySmall)};RadioButton(selected,onClick=onDefault)}
        Row(horizontalArrangement=Arrangement.spacedBy(6.dp),verticalAlignment=Alignment.CenterVertically){
            AssistChip(onClick={},label={Text(when{installed->"Ready";voice.downloadable&&localId.isNotBlank()->"Installable";voice.status!="available"->voice.status;else->"Preview only"})})
            if(voice.experimental)AssistChip(onClick={},label={Text("Experimental")})
            if(localId.isNotBlank()&&!installed&&voice.downloadable)TextButton(onClick={scope.launch{onStatus("Downloading $localId…");runCatching{withContext(Dispatchers.IO){app.models.install(localId)}}.onSuccess{installed=true;onStatus("Installed $localId");onChanged()}.onFailure{onStatus("Install failed: ${it.message}")}}}){Text("Download")}
            TextButton(enabled=installed||voice.previewUrl.isNotBlank()||(voice.networkRequired&&app.router.availableVoices.any{it.id==voice.id}),onClick={scope.launch{onStatus("Previewing ${voice.name}…");runCatching{withContext(Dispatchers.IO){
                val audio=if(voice.previewUrl.isNotBlank()&&!installed)CompressedAudioDecoder.decode(app,HttpAudio.get(voice.previewUrl),voice.previewUrl.substringAfterLast('.',"audio"))else app.router.synthesize(voice.id,previewText(voice.locale.language),voice.locale.toLanguageTag(),1f,AtomicBoolean());Playback.play(audio)
            }}.onSuccess{onStatus("Previewed ${voice.name}")}.onFailure{onStatus("Preview unavailable: ${it.message}")}}}){Text("Preview")}
        }
    }}
}

@Composable private fun RoutesPage(revision:Int,onStatus:(String)->Unit){
    val app=UtterMuxApp.instance;var language by remember{mutableStateOf("en-US")};var selected by remember{mutableStateOf("")};var chain by remember(language,revision){mutableStateOf(app.settings.routeChain(language))};val choices=app.router.voices.filter{it.languages.any{tag->Languages.matches(tag,language)}}
    fun save(next:List<String>){chain=next;app.settings.setRouteChain(language,next);onStatus("Saved ${Languages.normalized(language)} fallback chain")}
    LazyColumn(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
        item{Text("Automatic language fallback",style=MaterialTheme.typography.titleMedium);Text("Paid cloud providers are used automatically only when you add them here.",style=MaterialTheme.typography.bodySmall)}
        item{OutlinedTextField(language,{language=Languages.normalized(it)},Modifier.fillMaxWidth(),label={Text("BCP-47 language")},singleLine=true)}
        item{Selector("Add voice",selected,listOf("")+choices.map{it.id}){selected=it}}
        item{Button(enabled=selected.isNotBlank()&&selected !in chain,onClick={save(chain+selected)}){Text("Add to fallback chain")}}
        items(chain){id->val voice=app.router.voice(id);Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text("${chain.indexOf(id)+1}. ${voice?.name?:id}",Modifier.weight(1f));TextButton(onClick={val i=chain.indexOf(id);if(i>0){val n=chain.toMutableList();n[i]=n[i-1].also{n[i-1]=n[i]};save(n)}}){Text("↑")};TextButton(onClick={val i=chain.indexOf(id);if(i in 0 until chain.lastIndex){val n=chain.toMutableList();n[i]=n[i+1].also{n[i+1]=n[i]};save(n)}}){Text("↓")};TextButton(onClick={save(chain-id)}){Text("Remove")}}}
    }
}

@Composable private fun ModelsPage(revision:Int,onChanged:()->Unit,onStatus:(String)->Unit){
    val app=UtterMuxApp.instance;val scope=rememberCoroutineScope()
    LazyColumn(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
        item{Row(verticalAlignment=Alignment.CenterVertically){Text("Executable local models",Modifier.weight(1f),style=MaterialTheme.typography.titleMedium);OutlinedButton(onClick=onChanged){Text("Refresh")}}}
        items(app.models.models,key={it.id}){model->val installed=runCatching{app.models.installed(model.id)}.getOrDefault(false);Card{Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(model.id);Text("${model.engine} · ${if(installed)"installed" else "not installed"}",style=MaterialTheme.typography.bodySmall)};if(installed)TextButton(onClick={if(app.models.delete(model.id)){onStatus("Deleted ${model.id}");onChanged()}}){Text("Delete")}else Button(onClick={ModelDownloads.enqueue(app,model.id);onStatus("Queued ${model.id}; downloads resume in background")}){Text("Download")}}}}
        item{HorizontalDivider();Text("Research and compatibility catalog",style=MaterialTheme.typography.titleMedium)}
        items(BundledCatalog.researchModels,key={it.id}){model->Card{Column(Modifier.padding(12.dp)){Text(model.title);Text("${model.family} · ${model.status} · ~${model.approxSizeMb} MB · ${model.license}",style=MaterialTheme.typography.bodySmall);Text(model.description,style=MaterialTheme.typography.bodySmall)}}}
    }
}

@Composable private fun ProvidersPage(revision:Int,onRefresh:()->Unit,onStatus:(String)->Unit){
    val app=UtterMuxApp.instance;val values=remember{mutableStateMapOf<String,String>()};LaunchedEffect(revision){app.router.providerDescriptors.flatMap{it.credentialFields}.forEach{values[it.key]=app.secure.get(it.key)}}
    LazyColumn(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
        item{Text("Provider credentials",style=MaterialTheme.typography.titleMedium);Text("Secrets are encrypted with Android Keystore. Cloud previews and fallback synthesis may be billable.",style=MaterialTheme.typography.bodySmall)}
        items(app.router.providerDescriptors,key={it.id}){provider->Card{Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){Text(provider.name,style=MaterialTheme.typography.titleSmall);if(provider.note.isNotBlank())Text(provider.note,style=MaterialTheme.typography.bodySmall);provider.credentialFields.forEach{field->OutlinedTextField(values[field.key].orEmpty(),{values[field.key]=it},Modifier.fillMaxWidth(),label={Text(field.label)},placeholder={Text(field.placeholder)},singleLine=true,visualTransformation=if(field.secret)PasswordVisualTransformation()else androidx.compose.ui.text.input.VisualTransformation.None)}}}}
        item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Button(onClick={values.forEach{(key,value)->app.secure.put(key,value.trim())};onStatus("Provider settings encrypted");onRefresh()}){Text("Save and test")};OutlinedButton(onClick=onRefresh){Text("Refresh voices")}}}
    }
}

@Composable private fun SettingsPage(theme:String,onTheme:(String)->Unit,onStatus:(String)->Unit){
    val app=UtterMuxApp.instance;var koReader by remember{mutableStateOf(app.settings.koReaderEnabled)};var profile by remember{mutableStateOf(app.settings.latencyProfile)};var startup by remember{mutableStateOf(app.settings.manualStartupMs.toString())};var cache by remember{mutableStateOf(app.settings.modelCacheSize.toString())}
    LazyColumn(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{Selector("Theme",theme,listOf("system","light","dark"),onTheme)}
        item{Row(verticalAlignment=Alignment.CenterVertically){Switch(koReader,{enabled->koReader=enabled;app.settings.koReaderEnabled=enabled;val intent=Intent(app,KoReaderServerService::class.java);if(enabled)app.startForegroundService(intent)else app.stopService(intent)});Spacer(Modifier.width(8.dp));Column{Text("KOReader bridge");Text("127.0.0.1:5000",style=MaterialTheme.typography.bodySmall)}}}
        item{Selector("Latency profile",profile,listOf("automatic","low","balanced","smooth","manual")){profile=it;app.settings.latencyProfile=it}}
        item{Text("Advanced",style=MaterialTheme.typography.titleMedium)}
        item{OutlinedTextField(startup,{startup=it.filter(Char::isDigit)},Modifier.fillMaxWidth(),label={Text("Manual startup buffer (ms)")},singleLine=true)}
        item{OutlinedTextField(cache,{cache=it.filter(Char::isDigit)},Modifier.fillMaxWidth(),label={Text("Loaded model cache (1–3)")},singleLine=true)}
        item{Button(onClick={app.settings.manualStartupMs=startup.toIntOrNull()?:300;app.settings.modelCacheSize=cache.toIntOrNull()?:1;onStatus("Settings saved")}){Text("Save advanced settings")}}
    }
}

@Composable private fun DiagnosticsPage(){var report by remember{mutableStateOf(Diagnostics.report())};LazyColumn(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Button(onClick={report=Diagnostics.report()}){Text("Refresh")};OutlinedButton(onClick={Diagnostics.clear();report=""}){Text("Clear")}}};item{Text("request → routing → callback start → first audio → completion",style=MaterialTheme.typography.bodySmall)};item{Text(if(report.isBlank())"No requests recorded" else report,style=MaterialTheme.typography.bodySmall)}}}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun Selector(label:String,value:String,options:List<String>,onSelect:(String)->Unit){var expanded by remember{mutableStateOf(false)};ExposedDropdownMenuBox(expanded,{expanded=it}){OutlinedTextField(value,{},Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),readOnly=true,label={Text(label)},trailingIcon={ExposedDropdownMenuDefaults.TrailingIcon(expanded)});ExposedDropdownMenu(expanded,{expanded=false}){options.forEach{option->DropdownMenuItem(text={Text(option.ifBlank{"Choose…"})},onClick={onSelect(option);expanded=false})}}}}
private fun previewText(language:String)=when(language){"fr"->"Bonjour. Voici un aperçu de cette voix avec UtterMux.";"de"->"Hallo. Dies ist eine Vorschau dieser Stimme mit UtterMux.";"es"->"Hola. Esta es una muestra de esta voz con UtterMux.";"it"->"Ciao. Questa è un'anteprima della voce.";"pt"->"Olá. Esta é uma prévia desta voz.";else->"This is an UtterMux voice preview."}
