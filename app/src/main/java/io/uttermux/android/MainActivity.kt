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
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import io.uttermux.android.audio.CompressedAudioDecoder
import io.uttermux.android.audio.Playback
import io.uttermux.android.config.*
import io.uttermux.android.diagnostics.Diagnostics
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

private enum class Page(val label:String){VOICES("Voices"),SETTINGS("Settings")}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun UtterMuxManager(theme:String,onTheme:(String)->Unit){
    val app=UtterMuxApp.instance;val scope=rememberCoroutineScope();var page by remember{mutableStateOf(Page.VOICES)};var revision by remember{mutableIntStateOf(0)};var status by remember{mutableStateOf("Ready")}
    fun refresh(){scope.launch{status="Refreshing catalogs…";val errors=withContext(Dispatchers.IO){app.refreshCatalogs()};revision++;status=if(errors.isEmpty())"Catalogs refreshed" else errors.joinToString()}}
    LaunchedEffect(Unit){refresh()}
    Scaffold(topBar={TopAppBar(title={Column{Text("UtterMux");Text(status,style=MaterialTheme.typography.labelSmall)}})},bottomBar={NavigationBar{
        NavigationBarItem(selected=page==Page.VOICES,onClick={page=Page.VOICES},icon={Text("◉")},label={Text("Voices")})
        NavigationBarItem(selected=page==Page.SETTINGS,onClick={page=Page.SETTINGS},icon={Text("⚙")},label={Text("Settings")})
    }}){padding->Box(Modifier.padding(padding)){when(page){
        Page.VOICES->VoicesPage(revision,{revision++},{status=it})
        Page.SETTINGS->SettingsPage(revision,theme,onTheme,{refresh()},{revision++},{status=it})
    }}}
}

@Composable private fun VoicesPage(revision:Int,onChanged:()->Unit,onStatus:(String)->Unit){
    val app=UtterMuxApp.instance
    var voiceSearch by remember{mutableStateOf("")};var languageSearch by remember{mutableStateOf("")};var providerSearch by remember{mutableStateOf("")};var modelSearch by remember{mutableStateOf("")}
    var locality by remember{mutableStateOf("all")};var readiness by remember{mutableStateOf("all")};var defaultVoice by remember(revision){mutableStateOf(app.settings.defaultVoice)}
    val effectiveDefault=remember(revision,defaultVoice){app.router.effectiveDefault()};val configuredReady=remember(revision,defaultVoice){app.router.voice(defaultVoice)?.let(app.router::isAvailable)==true}
    val all=remember(revision){app.router.voices};val providerNames=remember(revision){app.router.providerDescriptors.associate{it.id to it.name}};fun contains(value:String,query:String)=query.isBlank()||value.contains(query,true)
    val shown=all.filter{voice->
        val ready=app.router.isAvailable(voice)
        contains(listOf(voice.name,voice.accent,voice.gender,voice.description).joinToString(" "),voiceSearch)&&
            contains(voice.languages.joinToString(" "){Languages.searchableName(it)},languageSearch)&&contains("${voice.provider} ${providerNames[voice.provider].orEmpty()}",providerSearch)&&contains(voice.model,modelSearch)&&
            (locality=="all"||(locality=="offline"&&!voice.networkRequired)||(locality=="online"&&voice.networkRequired))&&
            (readiness=="all"||(readiness=="ready"&&ready)||(readiness=="downloadable"&&!ready&&voice.downloadable))
    }.sortedWith(compareBy<VoiceRecord>({it.model},{it.name}))
    LazyColumn(Modifier.fillMaxSize().padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
        if(app.router.availableVoices.isEmpty())item{Card{Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Text("No voice is installed or configured",style=MaterialTheme.typography.titleMedium);Text("Download an offline voice below or configure an online provider in Settings. UtterMux intentionally bundles no voice model.");Button(onClick={app.startActivity(Intent("com.android.settings.TTS_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))}){Text("Android TTS settings")}}}}
        if(!configuredReady&&effectiveDefault!=null)item{Card{Text("Configured default is unavailable. Currently using ${effectiveDefault.name}; the saved preference will be restored automatically if its provider becomes available.",Modifier.padding(12.dp))}}
        item{Text("Find a voice",style=MaterialTheme.typography.titleMedium)}
        item{SearchField("Voice or accent",voiceSearch){voiceSearch=it}}
        item{SearchField("Language",languageSearch){languageSearch=it}}
        item{SearchField("Provider",providerSearch){providerSearch=it}}
        item{SearchField("Model family or variant",modelSearch){modelSearch=it}}
        item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Box(Modifier.weight(1f)){Selector("Location",locality,listOf("all","offline","online")){locality=it}};Box(Modifier.weight(1f)){Selector("Availability",readiness,listOf("all","ready","downloadable")){readiness=it}}}}
        item{Text("${shown.size} of ${all.size} voices",style=MaterialTheme.typography.bodySmall)}
        items(shown,key={it.id}){voice->VoiceCard(voice,voice.id==(effectiveDefault?.id?:defaultVoice),{app.settings.defaultVoice=voice.id;defaultVoice=voice.id;Thread{app.router.warm(voice.id)}.start();onStatus("Default: ${voice.name}")},{onChanged()},{onStatus(it)})}
    }
}

@Composable private fun SearchField(label:String,value:String,onValue:(String)->Unit)=OutlinedTextField(value,onValue,Modifier.fillMaxWidth().heightIn(min=56.dp),label={Text(label)},singleLine=true)

@Composable private fun VoiceCard(voice:VoiceRecord,selected:Boolean,onDefault:()->Unit,onChanged:()->Unit,onStatus:(String)->Unit){
    val app=UtterMuxApp.instance;val scope=rememberCoroutineScope();val localId=voice.downloadId.ifBlank{voice.takeIf{it.provider==ProviderIds.SHERPA}?.id?.split('/')?.getOrNull(1).orEmpty()}
    var installed by remember(voice.id){mutableStateOf(localId.isBlank()&&app.router.isAvailable(voice)||localId.isNotBlank()&&runCatching{app.models.installed(localId)}.getOrDefault(false))}
    val ready=if(localId.isNotBlank())installed else app.router.isAvailable(voice);val canRemotePreview=voice.previewUrl.isNotBlank();val canPreview=ready||canRemotePreview
    Card(Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(7.dp)){
        Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(voice.name,style=MaterialTheme.typography.titleSmall);Text("${voice.provider} · ${voice.model} · ${voice.languages.joinToString()}",style=MaterialTheme.typography.bodySmall);if(voice.description.isNotBlank())Text(voice.description,style=MaterialTheme.typography.bodySmall)};RadioButton(selected,onClick=onDefault,enabled=ready)}
        val facts=listOf(voice.quantization,voice.approxSizeMb.takeIf{it>0}?.let{"$it MB"}.orEmpty(),voice.estimatedRamMb.takeIf{it>0}?.let{"~$it MB RAM"}.orEmpty(),voice.performanceClass.takeUnless{it=="unknown"}.orEmpty(),voice.license).filter(String::isNotBlank)
        if(facts.isNotEmpty())Text(facts.joinToString(" · "),style=MaterialTheme.typography.labelSmall)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically){
            AssistChip(onClick={},label={Text(if(ready)"Ready" else if(voice.downloadable&&localId.isNotBlank())"Downloadable" else "Setup required")})
            TextButton(enabled=canPreview,onClick={scope.launch{onStatus("Previewing ${voice.name}…");runCatching{withContext(Dispatchers.IO){
                val audio=if(!ready&&canRemotePreview)CompressedAudioDecoder.decode(app,HttpAudio.get(voice.previewUrl),voice.previewUrl.substringAfterLast('.',"audio"))else app.router.synthesizeExact(voice.id,previewText(voice.locale.language),voice.locale.toLanguageTag(),1f,AtomicBoolean());Playback.play(audio)
            }}.onSuccess{onStatus("Previewed ${voice.name}")}.onFailure{onStatus("Preview unavailable: ${it.message}")}}}){Text(if(canPreview)"Preview" else "Install to preview")}
        }
        if(localId.isNotBlank())Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
            if(!installed&&voice.downloadable)Button(onClick={scope.launch{onStatus("Downloading $localId…");runCatching{withContext(Dispatchers.IO){app.models.install(localId)}}.onSuccess{installed=true;onChanged();onStatus("Installed ${voice.name}")}.onFailure{onStatus("Install failed: ${it.message}")}}}){Text("Download")}
            if(installed)OutlinedButton(onClick={if(app.models.delete(localId)){installed=false;if(selected)app.settings.defaultVoice="uttermux:auto@en";onChanged();onStatus("Deleted ${voice.model}")}}){Text("Delete model")}
        }
        if(voice.attribution.isNotBlank())Text(voice.attribution,style=MaterialTheme.typography.labelSmall)
    }}
}

@Composable private fun SettingsPage(revision:Int,theme:String,onTheme:(String)->Unit,onRefresh:()->Unit,onChanged:()->Unit,onStatus:(String)->Unit){
    val app=UtterMuxApp.instance;val clipboard=LocalClipboardManager.current;val values=remember{mutableStateMapOf<String,String>()};var providersOpen by remember{mutableStateOf(false)};var routesOpen by remember{mutableStateOf(false)};var storageOpen by remember{mutableStateOf(false)};var diagnosticsOpen by remember{mutableStateOf(false)}
    var language by remember{mutableStateOf("en-US")};var selected by remember{mutableStateOf("")};var routeRevision by remember{mutableIntStateOf(0)};var chain by remember(language,routeRevision,revision){mutableStateOf(app.settings.routeChain(language))}
    var koReader by remember{mutableStateOf(app.settings.koReaderEnabled)};var profile by remember{mutableStateOf(app.settings.latencyProfile)};var startup by remember{mutableStateOf(app.settings.manualStartupMs.toString())};var cache by remember{mutableStateOf(app.settings.modelCacheSize.toString())};var report by remember{mutableStateOf(Diagnostics.report())}
    LaunchedEffect(revision){app.router.providerDescriptors.flatMap{it.credentialFields}.forEach{values[it.key]=app.secure.get(it.key)}}
    fun saveRoute(next:List<String>){chain=next;app.settings.setRouteChain(language,next);routeRevision++;onStatus("Saved ${Languages.normalized(language)} fallback chain")}
    val choices=app.router.voices.filter{it.languages.any{tag->Languages.matches(tag,language)}}
    val installed=app.models.models.filter{runCatching{app.models.installed(it.id)}.getOrDefault(false)}
    LazyColumn(Modifier.fillMaxSize().padding(12.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
        item{Text("Appearance and playback",style=MaterialTheme.typography.titleMedium)}
        item{Selector("Theme",theme,listOf("system","light","dark"),onTheme)}
        item{Selector("Latency profile",profile,listOf("automatic","low","balanced","smooth","manual")){profile=it;app.settings.latencyProfile=it}}
        item{OutlinedTextField(startup,{startup=it.filter(Char::isDigit)},Modifier.fillMaxWidth(),label={Text("Manual startup buffer (ms)")},singleLine=true)}
        item{OutlinedTextField(cache,{cache=it.filter(Char::isDigit)},Modifier.fillMaxWidth(),label={Text("Loaded model cache (1–3)")},singleLine=true)}
        item{Button(onClick={app.settings.manualStartupMs=startup.toIntOrNull()?:300;app.settings.modelCacheSize=cache.toIntOrNull()?:1;onStatus("Playback settings saved")}){Text("Save playback settings")}}
        item{Row(verticalAlignment=Alignment.CenterVertically){Switch(koReader,{enabled->koReader=enabled;app.settings.koReaderEnabled=enabled;val intent=Intent(app,KoReaderServerService::class.java);if(enabled)app.startForegroundService(intent)else app.stopService(intent)});Spacer(Modifier.width(8.dp));Column{Text("KOReader bridge");Text("127.0.0.1:5000",style=MaterialTheme.typography.bodySmall)}}}

        item{SectionButton("Online providers",providersOpen){providersOpen=!providersOpen}}
        if(providersOpen){
            item{Text("Credentials are encrypted with Android Keystore. Direct cloud synthesis may be billable.",style=MaterialTheme.typography.bodySmall)}
            items(app.router.providerDescriptors,key={"provider-${it.id}"}){provider->Card{Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(7.dp)){Text(provider.name,style=MaterialTheme.typography.titleSmall);if(provider.note.isNotBlank())Text(provider.note,style=MaterialTheme.typography.bodySmall);if(provider.id==ProviderIds.AWS)TextButton(onClick={clipboard.setText(AnnotatedString(AWS_POLLY_POLICY));onStatus("Polly IAM policy copied")}){Text("Copy least-privilege IAM policy")};provider.credentialFields.forEach{field->
                if(field.choices.isNotEmpty())Selector(field.label,values[field.key].orEmpty().ifBlank{field.placeholder},field.choices){values[field.key]=it}
                else OutlinedTextField(values[field.key].orEmpty(),{values[field.key]=it},Modifier.fillMaxWidth(),label={Text(field.label)},placeholder={Text(field.placeholder)},singleLine=true,visualTransformation=if(field.secret)PasswordVisualTransformation()else VisualTransformation.None)
            }}}}
            item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Button(onClick={values.forEach{(key,value)->app.secure.put(key,value.trim())};onStatus("Provider settings encrypted");onRefresh()}){Text("Save and test")};OutlinedButton(onClick=onRefresh){Text("Refresh voices")}}}
        }

        item{SectionButton("Automatic language routing",routesOpen){routesOpen=!routesOpen}}
        if(routesOpen){
            item{Text("The global default is tried first. Paid cloud voices enter automatic fallback only when added here.",style=MaterialTheme.typography.bodySmall)}
            item{OutlinedTextField(language,{language=Languages.normalized(it)},Modifier.fillMaxWidth(),label={Text("BCP-47 language")},singleLine=true)}
            item{Selector("Add voice",selected,listOf("")+choices.map{it.id}){selected=it}}
            item{Button(enabled=selected.isNotBlank()&&selected !in chain,onClick={saveRoute(chain+selected)}){Text("Add to fallback chain")}}
            items(chain,key={"route-$it"}){id->val voice=app.router.voice(id);Column{Text("${chain.indexOf(id)+1}. ${voice?.name?:id}");Row{TextButton(onClick={val i=chain.indexOf(id);if(i>0){val n=chain.toMutableList();n[i]=n[i-1].also{n[i-1]=n[i]};saveRoute(n)}}){Text("Move up")};TextButton(onClick={val i=chain.indexOf(id);if(i in 0 until chain.lastIndex){val n=chain.toMutableList();n[i]=n[i+1].also{n[i+1]=n[i]};saveRoute(n)}}){Text("Move down")};TextButton(onClick={saveRoute(chain-id)}){Text("Remove")}}}}
        }

        item{SectionButton("Downloaded model storage (${installed.size})",storageOpen){storageOpen=!storageOpen}}
        if(storageOpen){
            if(installed.isEmpty())item{Text("No local models installed.")}
            items(installed,key={"installed-${it.id}"}){model->Card{Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(model.id);Text(model.engine,style=MaterialTheme.typography.bodySmall)};TextButton(onClick={if(app.models.delete(model.id)){onChanged();onStatus("Deleted ${model.id}")}}){Text("Delete")}}}}
        }

        item{SectionButton("Diagnostics",diagnosticsOpen){diagnosticsOpen=!diagnosticsOpen}}
        if(diagnosticsOpen){
            item{Text(app.adaptiveBuffers.snapshot().ifBlank{"No adaptive timing samples yet"},style=MaterialTheme.typography.bodySmall)}
            item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Button(onClick={report=Diagnostics.report()}){Text("Refresh")};OutlinedButton(onClick={Diagnostics.clear();report=""}){Text("Clear")}}}
            item{Text(if(report.isBlank())"No requests recorded" else report,style=MaterialTheme.typography.bodySmall)}
        }
    }
}

@Composable private fun SectionButton(label:String,expanded:Boolean,onClick:()->Unit)=OutlinedButton(onClick,Modifier.fillMaxWidth().heightIn(min=48.dp)){Text(if(expanded)"▾ $label" else "▸ $label")}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun Selector(label:String,value:String,options:List<String>,onSelect:(String)->Unit){var expanded by remember{mutableStateOf(false)};ExposedDropdownMenuBox(expanded,{expanded=it}){OutlinedTextField(value,{},Modifier.fillMaxWidth().heightIn(min=56.dp).menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),readOnly=true,label={Text(label)},trailingIcon={ExposedDropdownMenuDefaults.TrailingIcon(expanded)});ExposedDropdownMenu(expanded,{expanded=false}){options.forEach{option->DropdownMenuItem(text={Text(option.ifBlank{"Choose…"})},onClick={onSelect(option);expanded=false})}}}}
private fun previewText(language:String)=when(language){"fr"->"Bonjour. Voici un aperçu de cette voix avec UtterMux.";"de"->"Hallo. Dies ist eine Vorschau dieser Stimme mit UtterMux.";"es"->"Hola. Esta es una muestra de esta voz con UtterMux.";"it"->"Ciao. Questa è un'anteprima della voce.";"pt"->"Olá. Esta é uma prévia desta voz.";"zh"->"你好，这是 UtterMux 语音预览。";else->"This is an UtterMux voice preview."}
private const val AWS_POLLY_POLICY="""{"Version":"2012-10-17","Statement":[{"Effect":"Allow","Action":["polly:DescribeVoices","polly:SynthesizeSpeech"],"Resource":"*"}]}"""
