package io.uttermux.android

import android.Manifest
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import kotlinx.coroutines.delay
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
    val app=UtterMuxApp.instance;val scope=rememberCoroutineScope();var page by rememberSaveable{mutableStateOf(Page.VOICES)};var revision by remember{mutableIntStateOf(0)};var status by remember{mutableStateOf("Ready")};var voiceCatalog by remember{mutableStateOf<VoiceCatalogUi?>(null)}
    LaunchedEffect(revision){voiceCatalog=withContext(Dispatchers.Default){buildVoiceCatalog(app)}}
    fun refresh(){scope.launch{status="Refreshing catalogs…";val errors=withContext(Dispatchers.IO){app.refreshCatalogs()};revision++;status=if(errors.isEmpty())"Catalogs refreshed" else errors.joinToString()}}
    Scaffold(topBar={TopAppBar(title={Column{Text("UtterMux");Text(status,style=MaterialTheme.typography.labelSmall)}})},bottomBar={NavigationBar{
        NavigationBarItem(selected=page==Page.VOICES,onClick={page=Page.VOICES},icon={Text("◉")},label={Text("Voices")})
        NavigationBarItem(selected=page==Page.SETTINGS,onClick={page=Page.SETTINGS},icon={Text("⚙")},label={Text("Settings")})
    }}){padding->Box(Modifier.padding(padding)){when(page){
        Page.VOICES->VoicesPage(revision,voiceCatalog,{revision++},{status=it})
        Page.SETTINGS->SettingsPage(revision,theme,onTheme,{refresh()},{revision++},{status=it})
    }}}
}

private data class VoiceCatalogUi(val entries:List<VoiceSearchEntry>,val languages:List<Suggestion>,val libraries:List<Suggestion>,val models:List<Suggestion>,val performances:List<String>,val genders:List<String>,val capabilities:List<String>,val effectiveDefault:VoiceRecord?)
private fun buildVoiceCatalog(app:UtterMuxApp):VoiceCatalogUi {
    val all=app.router.voices;val readyIds=app.router.availableVoices.mapTo(hashSetOf()){it.id};val providerNames=app.router.providerDescriptors.associate{it.id to it.name}
    val entries=all.map{VoiceDiscovery.index(it,it.id in readyIds,providerNames[it.provider].orEmpty())}
    val languages=all.flatMap{it.languages}.distinct().sortedWith(compareBy({java.util.Locale.forLanguageTag(it).getDisplayLanguage(java.util.Locale.ENGLISH)},{it})).map{tag->
        val locale=java.util.Locale.forLanguageTag(tag);Suggestion(tag,listOf(locale.getDisplayLanguage(java.util.Locale.ENGLISH),locale.getDisplayCountry(java.util.Locale.ENGLISH).takeIf(String::isNotBlank),tag).filterNotNull().joinToString(" · "))
    }
    val libraries=entries.map{it.library}.distinct().sorted().map{Suggestion(it,it)}
    val models=entries.map{it.model}.distinct().sorted().map{Suggestion(it,it)}
    val performances=entries.map{it.voice.performanceClass}.filter{it.isNotBlank()&&it!="unknown"}.distinct().sorted()
    val genders=entries.map{it.voice.gender.lowercase()}.filter(String::isNotBlank).distinct().sorted()
    val capabilities=entries.flatMap{it.voice.capabilities}.distinct().sorted()
    return VoiceCatalogUi(entries,languages,libraries,models,performances,genders,capabilities,app.router.effectiveDefault())
}

@Composable private fun VoicesPage(revision:Int,snapshot:VoiceCatalogUi?,onChanged:()->Unit,onStatus:(String)->Unit){
    val app=UtterMuxApp.instance
    var voiceSearch by rememberSaveable{mutableStateOf("")};var languageSearch by rememberSaveable{mutableStateOf("")};var librarySearch by rememberSaveable{mutableStateOf("")};var modelSearch by rememberSaveable{mutableStateOf("")};var accentSearch by rememberSaveable{mutableStateOf("")}
    var locality by rememberSaveable{mutableStateOf("all")};var readiness by rememberSaveable{mutableStateOf("all")};var performance by rememberSaveable{mutableStateOf("all")};var gender by rememberSaveable{mutableStateOf("all")};var capability by rememberSaveable{mutableStateOf("all")};var cost by rememberSaveable{mutableStateOf("all")};var sort by rememberSaveable{mutableStateOf("name")}
    var defaultVoice by rememberSaveable{mutableStateOf(app.settings.defaultVoice)};var shown by remember{mutableStateOf<List<VoiceSearchEntry>>(emptyList())}
    LaunchedEffect(revision){defaultVoice=app.settings.defaultVoice}
    LaunchedEffect(snapshot,voiceSearch,languageSearch,librarySearch,modelSearch,accentSearch,locality,readiness,performance,gender,capability,cost,sort){
        if(snapshot==null){shown=emptyList();return@LaunchedEffect};delay(60)
        val filters=VoiceFilters(voiceSearch,languageSearch,librarySearch,modelSearch,accentSearch,locality,readiness,performance,gender,capability,cost,sort)
        shown=withContext(Dispatchers.Default){VoiceDiscovery.filter(snapshot.entries,filters)}
    }
    if(snapshot==null){Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){CircularProgressIndicator()};return}
    val selected=snapshot.entries.firstOrNull{it.voice.id==defaultVoice&&it.ready}?.voice;val effectiveDefault=selected?:snapshot.effectiveDefault
    val configuredReady=selected!=null
    val activity by VoiceActivity.state.collectAsState()
    fun clearFilters(){voiceSearch="";languageSearch="";librarySearch="";modelSearch="";accentSearch="";locality="all";readiness="all";performance="all";gender="all";capability="all";cost="all";sort="name"}
    val filtersActive=listOf(voiceSearch,languageSearch,librarySearch,modelSearch,accentSearch).any(String::isNotBlank)||listOf(locality,readiness,performance,gender,capability,cost).any{it!="all"}||sort!="name"
    val modelOptions=remember(snapshot.models,librarySearch){if(librarySearch.isBlank())snapshot.models else snapshot.entries.filter{it.library.contains(librarySearch,true)}.map{Suggestion(it.model,it.model)}.distinctBy{it.value}.sortedBy{it.label}}
    LazyColumn(Modifier.fillMaxSize().padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
        if(snapshot.entries.none{it.ready})item{Card{Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Text("No voice is installed or configured",style=MaterialTheme.typography.titleMedium);Text("Download an offline voice below or configure an online provider in Settings. UtterMux intentionally bundles no voice model.");Button(onClick={app.startActivity(Intent("com.android.settings.TTS_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))}){Text("Android TTS settings")}}}}
        if(!configuredReady&&effectiveDefault!=null)item{Card{Text("Configured default is unavailable. Currently using ${effectiveDefault.name}; the saved preference will be restored automatically if its provider becomes available.",Modifier.padding(12.dp))}}
        item{Card(Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
            Text("Active voice",style=MaterialTheme.typography.titleMedium)
            Text(app.router.voice(activity.activeVoice)?.name ?: effectiveDefault?.name ?: "No ready voice")
            Text(when(activity.status){"speaking"->"Speaking in ${activity.language} for ${activity.client}";"warming"->"Loading voice";else->"Configured default: ${selected?.name?:app.settings.defaultVoice}"},style=MaterialTheme.typography.bodySmall)
            if(activity.fallbackReason.isNotBlank())Text(activity.fallbackReason,style=MaterialTheme.typography.labelSmall)
        }}}
        item{Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text("Find a voice",Modifier.weight(1f),style=MaterialTheme.typography.titleMedium);TextButton(enabled=filtersActive,onClick=::clearFilters){Text("Clear filters")}}}
        item{SearchField("Voice, accent, or variant",voiceSearch){voiceSearch=it}}
        item{SuggestionSearchField("Language",languageSearch,snapshot.languages){languageSearch=it}}
        item{SuggestionSearchField("Voice library",librarySearch,snapshot.libraries){librarySearch=it;modelSearch=""}}
        item{SuggestionSearchField("Model / version",modelSearch,modelOptions){modelSearch=it}}
        item{SearchField("Accent or region",accentSearch){accentSearch=it}}
        item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Box(Modifier.weight(1f)){Selector("Location",locality,listOf("all","on-device","cloud")){locality=it}};Box(Modifier.weight(1f)){Selector("Availability",readiness,listOf("all","ready","downloadable","setup")){readiness=it}}}}
        item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Box(Modifier.weight(1f)){Selector("Performance",performance,listOf("all")+snapshot.performances){performance=it}};Box(Modifier.weight(1f)){Selector("Gender",gender,listOf("all")+snapshot.genders){gender=it}}}}
        item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Box(Modifier.weight(1f)){Selector("Capability",capability,listOf("all")+snapshot.capabilities){capability=it}};Box(Modifier.weight(1f)){Selector("Cost",cost,listOf("all","free","metered","subscription")){cost=it}}}}
        item{Selector("Sort",sort,listOf("name","library","smallest","fastest")){sort=it}}
        item{Text("${shown.size} of ${snapshot.entries.size} voices",style=MaterialTheme.typography.bodySmall)}
        items(shown,key={it.voice.id}){entry->val voice=entry.voice;VoiceCard(voice,"${entry.library} · ${entry.model}",entry.ready,voice.id==(effectiveDefault?.id?:defaultVoice),{app.settings.defaultVoice=voice.id;defaultVoice=voice.id;Thread{app.router.warm(voice.id)}.start();onStatus("Default: ${voice.name}")},{onChanged()},{onStatus(it)})}
    }
}

@Composable private fun SearchField(label:String,value:String,onValue:(String)->Unit)=OutlinedTextField(value,onValue,Modifier.fillMaxWidth().heightIn(min=56.dp),label={Text(label)},singleLine=true,trailingIcon={if(value.isNotBlank())IconButton({onValue("")},Modifier.semantics{contentDescription="Clear $label"}){Text("×")}})

private data class Suggestion(val value:String,val label:String)
@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun SuggestionSearchField(label:String,value:String,options:List<Suggestion>,onValue:(String)->Unit){
    var expanded by remember{mutableStateOf(false)}
    val matches=remember(value,options){options.filter{value.isBlank()||it.value.contains(value,true)||it.label.contains(value,true)}.take(30)}
    ExposedDropdownMenuBox(expanded&&matches.isNotEmpty(),{expanded=it}){
        OutlinedTextField(value,{onValue(it);expanded=true},Modifier.fillMaxWidth().heightIn(min=56.dp).menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),label={Text(label)},singleLine=true,trailingIcon={if(value.isNotBlank())IconButton({onValue("");expanded=false},Modifier.semantics{contentDescription="Clear $label"}){Text("×")}else ExposedDropdownMenuDefaults.TrailingIcon(expanded)})
        ExposedDropdownMenu(expanded&&matches.isNotEmpty(),{expanded=false}){matches.forEach{item->DropdownMenuItem(text={Text(item.label)},onClick={onValue(item.value);expanded=false})}}
    }
}

@Composable private fun VoiceCard(voice:VoiceRecord,service:String,catalogReady:Boolean,selected:Boolean,onDefault:()->Unit,onChanged:()->Unit,onStatus:(String)->Unit){
    val app=UtterMuxApp.instance;val scope=rememberCoroutineScope();val localId=voice.downloadId.ifBlank{voice.takeIf{it.provider==ProviderIds.SHERPA}?.id?.split('/')?.getOrNull(1).orEmpty()}
    var installed by remember(voice.id){mutableStateOf(localId.isBlank()&&app.router.isAvailable(voice)||localId.isNotBlank()&&runCatching{app.models.installed(localId)}.getOrDefault(false))}
    var repairNeeded by remember(voice.id,installed){mutableStateOf(installed&&localId.isNotBlank()&&runCatching{app.models.needsRepair(localId)}.getOrDefault(false))}
    var confirmPaid by remember{mutableStateOf(false)}
    val ready=if(localId.isNotBlank())installed else catalogReady;val canRemotePreview=voice.previewUrl.isNotBlank();val canPreview=ready||canRemotePreview
    val doPreview:()->Unit={scope.launch{onStatus("Previewing ${voice.name}…");runCatching{withContext(Dispatchers.IO){
        val audio=if(!ready&&canRemotePreview)CompressedAudioDecoder.decode(app,HttpAudio.get(voice.previewUrl),voice.previewUrl.substringAfterLast('.',"audio"))else app.router.synthesizeExact(voice.id,previewText(voice.locale.language),voice.locale.toLanguageTag(),1f,AtomicBoolean());Playback.play(audio)
    }}.onSuccess{onStatus("Previewed ${voice.name}")}.onFailure{onStatus("Preview unavailable: ${it.message}")}}}
    Card(Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(7.dp)){
        Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(voice.name,style=MaterialTheme.typography.titleSmall);Text("$service · ${voice.model} · ${voice.languages.joinToString()}",style=MaterialTheme.typography.bodySmall);if(voice.description.isNotBlank())Text(voice.description,style=MaterialTheme.typography.bodySmall)};RadioButton(selected,onClick=onDefault,enabled=ready)}
        val facts=listOf(voice.quantization,voice.approxSizeMb.takeIf{it>0}?.let{"$it MB"}.orEmpty(),voice.estimatedRamMb.takeIf{it>0}?.let{"~$it MB RAM"}.orEmpty(),voice.performanceClass.takeUnless{it=="unknown"}.orEmpty(),voice.license).filter(String::isNotBlank)
        if(facts.isNotEmpty())Text(facts.joinToString(" · "),style=MaterialTheme.typography.labelSmall)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically){
            AssistChip(onClick={},label={Text(if(ready)"Ready" else if(voice.downloadable&&localId.isNotBlank())"Downloadable" else "Setup required")})
            TextButton(enabled=canPreview,onClick={if(voice.networkRequired&&voice.provider!=ProviderIds.EDGE&&!app.settings.paidPreviewConfirmed)confirmPaid=true else doPreview()}){Text(if(voice.networkRequired&&voice.provider!=ProviderIds.EDGE)"Preview · may cost" else if(canPreview)"Preview" else "Install to preview")}
        }
        if(localId.isNotBlank())Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
            if(!installed&&voice.downloadable)Button(onClick={scope.launch{onStatus("Downloading $localId…");runCatching{withContext(Dispatchers.IO){app.models.install(localId)}}.onSuccess{installed=true;app.notifyVoiceDataChanged();onChanged();onStatus("Installed ${voice.name}")}.onFailure{onStatus("Install failed: ${it.message}")}}}){Text("Download")}
            if(installed&&repairNeeded)Button(onClick={scope.launch{onStatus("Updating $localId…");runCatching{withContext(Dispatchers.IO){app.models.repair(localId)}}.onSuccess{repairNeeded=false;app.notifyVoiceDataChanged();onChanged();onStatus("Updated ${voice.model}")}.onFailure{onStatus("Update failed: ${it.message}")}}}){Text("Update model")}
            if(installed)OutlinedButton(onClick={scope.launch{onStatus("Deleting ${voice.model}…");val deleted=withContext(Dispatchers.IO){app.providers.forEach{it.trimMemory()};app.models.delete(localId)};if(deleted){installed=false;if(selected)app.settings.defaultVoice="uttermux:auto@en";app.notifyVoiceDataChanged();onChanged();onStatus("Deleted ${voice.model}")}}}){Text("Delete model")}
        }
        if(voice.attribution.isNotBlank())Text(voice.attribution,style=MaterialTheme.typography.labelSmall)
        if(voice.sourceUrl.isNotBlank())TextButton(onClick={app.startActivity(Intent(Intent.ACTION_VIEW,android.net.Uri.parse(voice.sourceUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))}){Text("Model information")}
    }}
    if(confirmPaid)AlertDialog(onDismissRequest={confirmPaid=false},title={Text("Paid voice preview")},text={Text("This sends the sample text to ${service.substringBefore(" ·")} and may use paid API credits. UtterMux will remember this confirmation.")},confirmButton={Button(onClick={app.settings.paidPreviewConfirmed=true;confirmPaid=false;doPreview()}){Text("Preview")}},dismissButton={TextButton(onClick={confirmPaid=false}){Text("Cancel")}})
}

@Composable private fun SettingsPage(revision:Int,theme:String,onTheme:(String)->Unit,onRefresh:()->Unit,onChanged:()->Unit,onStatus:(String)->Unit){
    val app=UtterMuxApp.instance;val scope=rememberCoroutineScope();val clipboard=LocalClipboardManager.current;val values=remember{mutableStateMapOf<String,String>()};var providersOpen by remember{mutableStateOf(false)};var routesOpen by remember{mutableStateOf(false)};var storageOpen by remember{mutableStateOf(false)};var diagnosticsOpen by remember{mutableStateOf(false)};var profilesOpen by remember{mutableStateOf(false)}
    var selectedProvider by rememberSaveable{mutableStateOf("")};var providerSearch by rememberSaveable{mutableStateOf("")}
    var language by remember{mutableStateOf("en-US")};var selected by remember{mutableStateOf("")};var routeRevision by remember{mutableIntStateOf(0)};var chain by remember(language,routeRevision,revision){mutableStateOf(app.settings.routeChain(language))}
    fun pocketLabel(steps:Int)=when(steps){3->"Fast · 3 steps";5->"Highest quality · 5 steps";else->"Balanced · 4 steps"}
    var koReader by remember{mutableStateOf(app.settings.koReaderEnabled)};var profile by remember{mutableStateOf(app.settings.latencyProfile)};var pocketQuality by remember{mutableStateOf(pocketLabel(app.settings.pocketNumSteps))};var startup by remember{mutableStateOf(app.settings.manualStartupMs.toString())};var cache by remember{mutableStateOf(app.settings.modelCacheSize.toString())};var report by remember{mutableStateOf("")}
    val profileStore=remember{io.uttermux.android.provider.PocketProfileStore(app)};var profileName by rememberSaveable{mutableStateOf("")};var profileRevision by remember{mutableIntStateOf(0)};val pocketProfiles=remember(profileRevision){profileStore.profiles()}
    val importProfile=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()){uri->if(uri!=null)scope.launch{onStatus("Importing voice sample…");runCatching{withContext(Dispatchers.IO){profileStore.import(uri,profileName)}}.onSuccess{profileName="";profileRevision++;app.notifyVoiceDataChanged();onChanged();onStatus("Pocket voice profile added")}.onFailure{onStatus("Import failed: ${it.message}")}}}
    val recordPermission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted->if(granted)scope.launch{onStatus("Recording 8-second sample…");runCatching{withContext(Dispatchers.IO){profileStore.record(profileName)}}.onSuccess{profileName="";profileRevision++;app.notifyVoiceDataChanged();onChanged();onStatus("Pocket voice profile recorded")}.onFailure{onStatus("Recording failed: ${it.message}")}}else onStatus("Microphone permission denied")}
    LaunchedEffect(revision){val loaded=withContext(Dispatchers.IO){app.router.providerDescriptors.flatMap{it.credentialFields}.associate{it.key to app.secure.get(it.key)}};values.putAll(loaded)}
    fun saveRoute(next:List<String>){chain=next;app.settings.setRouteChain(language,next);routeRevision++;onStatus("Saved ${Languages.normalized(language)} fallback chain")}
    val choices=remember(routesOpen,language,revision){if(routesOpen)app.router.voices.filter{it.languages.any{tag->Languages.matches(tag,language)}}else emptyList()}
    val installed=remember(revision){app.models.models.filter{runCatching{app.models.installed(it.id)}.getOrDefault(false)}}
    val onlineProviders=remember(revision){app.router.providerDescriptors.filter{it.network}}
    LazyColumn(Modifier.fillMaxSize().padding(12.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
        item{Text("Appearance and playback",style=MaterialTheme.typography.titleMedium)}
        item{Button(onClick={app.startActivity(Intent("com.android.settings.TTS_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))}){Text("Open Android TTS settings")}}
        item{Selector("Theme",theme,listOf("system","light","dark"),onTheme)}
        item{Selector("Latency profile",profile,listOf("automatic","low","balanced","smooth","manual")){profile=it;app.settings.latencyProfile=it}}
        item{Selector("Pocket quality / latency",pocketQuality,listOf(pocketLabel(3),pocketLabel(4),pocketLabel(5))){pocketQuality=it;app.settings.pocketNumSteps=it.substringAfter("· ").substringBefore(' ').toInt()}}
        item{OutlinedTextField(startup,{startup=it.filter(Char::isDigit)},Modifier.fillMaxWidth(),label={Text("Manual startup buffer (ms)")},singleLine=true)}
        item{OutlinedTextField(cache,{cache=it.filter(Char::isDigit)},Modifier.fillMaxWidth(),label={Text("Loaded model cache (1–3)")},singleLine=true)}
        item{Text("Automatic is recommended. Manual buffering and keeping multiple neural models loaded increase memory use; change them only when diagnosing clipping or repeated cold starts.",style=MaterialTheme.typography.bodySmall)}
        item{Button(onClick={app.settings.manualStartupMs=startup.toIntOrNull()?:300;app.settings.modelCacheSize=cache.toIntOrNull()?:1;app.settings.pocketNumSteps=pocketQuality.substringAfter("· ").substringBefore(' ').toIntOrNull()?:3;onStatus("Playback settings saved")}){Text("Save playback settings")}}
        item{Row(verticalAlignment=Alignment.CenterVertically){Switch(koReader,{enabled->koReader=enabled;app.settings.koReaderEnabled=enabled;val intent=Intent(app,KoReaderServerService::class.java);if(enabled)app.startForegroundService(intent)else app.stopService(intent)});Spacer(Modifier.width(8.dp));Column{Text("KOReader bridge");Text("127.0.0.1:5000",style=MaterialTheme.typography.bodySmall)}}}

        item{SectionButton("Pocket voice profiles (${pocketProfiles.size})",profilesOpen){profilesOpen=!profilesOpen}}
        if(profilesOpen){
            item{Text("Optional voice cloning. Use a clean 3–10 second recording that you own or have permission to use. Profiles stay on this device and require the Pocket model.",style=MaterialTheme.typography.bodySmall)}
            item{SearchField("Profile name",profileName){profileName=it}}
            item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Button(onClick={importProfile.launch("audio/*")}){Text("Import audio")};OutlinedButton(onClick={recordPermission.launch(Manifest.permission.RECORD_AUDIO)}){Text("Record 8 seconds")}}}
            items(pocketProfiles,key={"profile-${it.id}"}){p->Card{Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(p.name);Text("Pocket · private on-device reference",style=MaterialTheme.typography.bodySmall)};TextButton(onClick={if(profileStore.delete(p.id)){profileRevision++;app.notifyVoiceDataChanged();onChanged();onStatus("Deleted ${p.name}")}}){Text("Delete")}}}}
        }

        item{SectionButton("Online providers",providersOpen){providersOpen=!providersOpen}}
        if(providersOpen){
            item{Text("Credentials are encrypted with Android Keystore. Direct cloud synthesis may be billable.",style=MaterialTheme.typography.bodySmall)}
            item{SuggestionSearchField("Online provider",providerSearch,onlineProviders.map{Suggestion(it.id,it.name)}){providerSearch=it;selectedProvider=it}}
            val provider=onlineProviders.firstOrNull{it.id==selectedProvider||it.name.equals(providerSearch,true)}
            if(provider!=null)item{Card{Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(7.dp)){Text(provider.name,style=MaterialTheme.typography.titleSmall);if(provider.note.isNotBlank())Text(provider.note,style=MaterialTheme.typography.bodySmall);if(provider.id==ProviderIds.AWS)TextButton(onClick={clipboard.setText(AnnotatedString(AWS_POLLY_POLICY));onStatus("Polly IAM policy copied")}){Text("Copy least-privilege IAM policy")};provider.credentialFields.forEach{field->
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
            items(installed,key={"installed-${it.id}"}){model->Card{Column(Modifier.fillMaxWidth().padding(12.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(model.title);Text(listOf(model.family,model.quantization,"${model.downloadSizeMb.takeIf{it>0}?:folderMegabytes(java.io.File(app.models.root,model.id))} MB",model.performanceClass).filter{it.isNotBlank()&&it!="unknown"}.joinToString(" · "),style=MaterialTheme.typography.bodySmall);if(model.languages.isNotEmpty())Text(model.languages.joinToString(),style=MaterialTheme.typography.labelSmall)};TextButton(onClick={scope.launch{onStatus("Deleting ${model.title}…");val deleted=withContext(Dispatchers.IO){app.providers.forEach{it.trimMemory()};app.models.delete(model.id)};if(deleted){app.notifyVoiceDataChanged();onChanged();onStatus("Deleted ${model.title}")}}}){Text("Delete")}};app.router.voices.firstOrNull{it.downloadId==model.id}?.let{voice->TextButton(onClick={scope.launch{onStatus("Previewing ${voice.name}…");runCatching{withContext(Dispatchers.IO){val audio=app.router.synthesizeExact(voice.id,previewText(voice.locale.language),voice.locale.toLanguageTag(),1f,AtomicBoolean());Playback.play(audio)}}.onSuccess{onStatus("Previewing ${voice.name}")}.onFailure{onStatus("Preview unavailable: ${it.message}")}}}){Text("Preview installed model")}}}}}
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
private fun folderMegabytes(root:java.io.File):Int=(root.walkTopDown().filter{it.isFile}.sumOf{it.length()}/1024/1024).toInt()
private const val AWS_POLLY_POLICY="""{"Version":"2012-10-17","Statement":[{"Effect":"Allow","Action":["polly:DescribeVoices","polly:SynthesizeSpeech"],"Resource":"*"}]}"""
