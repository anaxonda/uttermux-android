package io.uttermux.android

import android.Manifest
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.uttermux.android.audio.CompressedAudioDecoder
import io.uttermux.android.audio.PreviewController
import io.uttermux.android.config.*
import io.uttermux.android.diagnostics.Diagnostics
import io.uttermux.android.provider.HttpAudio
import io.uttermux.android.service.KoReaderServerService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity:ComponentActivity(){
    private val notifications=registerForActivityResult(ActivityResultContracts.RequestPermission()){}
    override fun onCreate(savedInstanceState:android.os.Bundle?){super.onCreate(savedInstanceState)
        val app=UtterMuxApp.instance;if(intent.getBooleanExtra("enable_koreader",false))app.settings.koReaderEnabled=true
        if(android.os.Build.VERSION.SDK_INT>=33&&app.settings.koReaderEnabled)notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        if(app.settings.koReaderEnabled)startForegroundService(Intent(this,KoReaderServerService::class.java))
        setContent{var theme by remember{mutableStateOf(app.settings.theme)};val dark=when(theme){"dark"->true;"light"->false;else->androidx.compose.foundation.isSystemInDarkTheme()}
            MaterialTheme(colorScheme=if(dark)darkColorScheme()else lightColorScheme()){UtterMuxManager(theme){app.settings.theme=it;theme=it}}}
    }
}

private enum class Page(val label:String){VOICES("Voices"),FILTER_CHOOSER("Filter"),CREATE("Create"),TUNE("Test"),SETTINGS("Settings")}
private enum class FilterKey(val label:String){VOICE("Voice"),LANGUAGE("Language"),LIBRARY("Voice library"),MODEL("Model / version"),ACCENT("Accent / region"),LOCATION("Location"),AVAILABILITY("Availability"),PERFORMANCE("Performance"),GENDER("Gender"),CAPABILITY("Capability"),COST("Cost"),SORT("Sort")}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun UtterMuxManager(theme:String,onTheme:(String)->Unit){
    val app=UtterMuxApp.instance;val scope=rememberCoroutineScope();var page by rememberSaveable{mutableStateOf(Page.VOICES)};var revision by remember{mutableIntStateOf(0)};var status by remember{mutableStateOf("Ready")};var voiceCatalog by remember{mutableStateOf<VoiceCatalogUi?>(null)};var testArtifact by rememberSaveable{mutableStateOf("")};var filterKey by rememberSaveable{mutableStateOf(FilterKey.LANGUAGE)}
    val previewState by PreviewController.state.collectAsState();val previewBusy=previewState.phase in setOf("loading","playing")
    val catalogRevision by app.catalogRevision.collectAsState()
    val filters=rememberVoiceFilterState();val voiceListState=rememberLazyListState()
    BackHandler(page==Page.FILTER_CHOOSER){page=Page.VOICES}
    LaunchedEffect(revision,catalogRevision){voiceCatalog=withContext(Dispatchers.Default){buildVoiceCatalog(app)}}
    fun refresh(){scope.launch{status="Refreshing catalogs…";val errors=withContext(Dispatchers.IO){app.refreshCatalogs()};revision++;status=if(errors.isEmpty())"Catalogs refreshed" else errors.joinToString()}}
    Scaffold(topBar={if(page!=Page.FILTER_CHOOSER)TopAppBar(title={Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){if(previewState.phase=="loading")CircularProgressIndicator(Modifier.size(20.dp),strokeWidth=2.dp);Column{Text("UtterMux");Text(if(previewBusy)previewState.message else status,style=MaterialTheme.typography.labelSmall)}}})},bottomBar={if(page!=Page.FILTER_CHOOSER)NavigationBar{
        NavigationBarItem(selected=page==Page.VOICES,onClick={page=Page.VOICES},icon={Text("◉")},label={Text("Voices")})
        NavigationBarItem(selected=page==Page.CREATE,onClick={page=Page.CREATE},icon={Text("＋")},label={Text("Create")})
        NavigationBarItem(selected=page==Page.TUNE,onClick={testArtifact="";page=Page.TUNE},icon={Text("⌁")},label={Text("Test")})
        NavigationBarItem(selected=page==Page.SETTINGS,onClick={page=Page.SETTINGS},icon={Text("⚙")},label={Text("Settings")})
    }}){padding->Box(Modifier.padding(padding)){when(page){
        Page.VOICES->VoicesPage(revision,voiceCatalog,filters,voiceListState,{key->filterKey=key;page=Page.FILTER_CHOOSER},{revision++},{artifact->testArtifact=artifact;page=Page.TUNE},{status=it})
        Page.FILTER_CHOOSER->FilterChooserPage(voiceCatalog,filters,filterKey){page=Page.VOICES}
        Page.CREATE->CreateVoicePage(revision,{revision++},{status=it})
        Page.TUNE->BenchmarkPage(testArtifact){status=it}
        Page.SETTINGS->ModernSettingsPage(revision,theme,onTheme,{refresh()},{revision++},{status=it})
    }}}
}

private class VoiceFilterState(private val initial:List<String> = emptyList()){
    private fun value(index:Int,default:String="")=initial.getOrNull(index)?:default
    var voiceSearch by mutableStateOf(value(0));var languageSearch by mutableStateOf(value(1));var librarySearch by mutableStateOf(value(2));var modelSearch by mutableStateOf(value(3));var accentSearch by mutableStateOf(value(4))
    var locality by mutableStateOf(value(5,"all"));var readiness by mutableStateOf(value(6,"all"));var performance by mutableStateOf(value(7,"all"));var gender by mutableStateOf(value(8,"all"));var capability by mutableStateOf(value(9,"all"));var cost by mutableStateOf(value(10,"all"));var sort by mutableStateOf(value(11,"name"))
    var globalSearch by mutableStateOf(value(12))
    fun values()=listOf(voiceSearch,languageSearch,librarySearch,modelSearch,accentSearch,locality,readiness,performance,gender,capability,cost,sort,globalSearch)
    fun clear(){globalSearch="";voiceSearch="";languageSearch="";librarySearch="";modelSearch="";accentSearch="";locality="all";readiness="all";performance="all";gender="all";capability="all";cost="all";sort="name"}
    fun get(key:FilterKey)=when(key){FilterKey.VOICE->voiceSearch;FilterKey.LANGUAGE->languageSearch;FilterKey.LIBRARY->librarySearch;FilterKey.MODEL->modelSearch;FilterKey.ACCENT->accentSearch;FilterKey.LOCATION->locality;FilterKey.AVAILABILITY->readiness;FilterKey.PERFORMANCE->performance;FilterKey.GENDER->gender;FilterKey.CAPABILITY->capability;FilterKey.COST->cost;FilterKey.SORT->sort}
    fun set(key:FilterKey,value:String){when(key){FilterKey.VOICE->voiceSearch=value;FilterKey.LANGUAGE->languageSearch=value;FilterKey.LIBRARY->{librarySearch=value;modelSearch=""};FilterKey.MODEL->modelSearch=value;FilterKey.ACCENT->accentSearch=value;FilterKey.LOCATION->locality=value;FilterKey.AVAILABILITY->readiness=value;FilterKey.PERFORMANCE->performance=value;FilterKey.GENDER->gender=value;FilterKey.CAPABILITY->capability=value;FilterKey.COST->cost=value;FilterKey.SORT->sort=value}}
    fun clear(key:FilterKey)=set(key,if(key==FilterKey.SORT)"name" else if(key in setOf(FilterKey.LOCATION,FilterKey.AVAILABILITY,FilterKey.PERFORMANCE,FilterKey.GENDER,FilterKey.CAPABILITY,FilterKey.COST))"all" else "")
    fun active()=FilterKey.entries.filter{key->val value=get(key);if(key==FilterKey.SORT)value!="name" else value.isNotBlank()&&value!="all"}
    fun query(omit:FilterKey?=null)=VoiceFilters(query=globalSearch,voice=if(omit==FilterKey.VOICE)"" else voiceSearch,language=if(omit==FilterKey.LANGUAGE)"" else languageSearch,library=if(omit==FilterKey.LIBRARY)"" else librarySearch,model=if(omit==FilterKey.MODEL)"" else modelSearch,accent=if(omit==FilterKey.ACCENT)"" else accentSearch,locality=if(omit==FilterKey.LOCATION)"all" else locality,readiness=if(omit==FilterKey.AVAILABILITY)"all" else readiness,performance=if(omit==FilterKey.PERFORMANCE)"all" else performance,gender=if(omit==FilterKey.GENDER)"all" else gender,capability=if(omit==FilterKey.CAPABILITY)"all" else capability,cost=if(omit==FilterKey.COST)"all" else cost,sort=if(omit==FilterKey.SORT)"name" else sort)
}
private val VoiceFilterSaver=androidx.compose.runtime.saveable.Saver<VoiceFilterState,ArrayList<String>>(save={ArrayList(it.values())},restore={VoiceFilterState(it)})
@Composable private fun rememberVoiceFilterState()=rememberSaveable(saver=VoiceFilterSaver){VoiceFilterState()}

private data class VoiceCatalogUi(val entries:List<VoiceSearchEntry>,val voices:List<Suggestion>,val languages:List<Suggestion>,val libraries:List<Suggestion>,val models:List<Suggestion>,val accents:List<Suggestion>,val performances:List<String>,val genders:List<String>,val capabilities:List<String>,val effectiveDefault:VoiceRecord?)
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
    val voices=entries.asSequence().map{it.voice.name}.filter(VoiceDiscovery::usefulVoiceSuggestion)
        .distinct().sortedWith(compareBy<String>({if("Piper" in it)1 else 0},{it.lowercase()})).map{Suggestion(it,it)}.toList()
    val accents=entries.map{it.voice.accent}.filter(String::isNotBlank).distinct().sorted().map{Suggestion(it,it)}
    return VoiceCatalogUi(entries,voices,languages,libraries,models,accents,performances,genders,capabilities,app.router.effectiveDefault())
}

@Composable private fun VoicesPage(revision:Int,snapshot:VoiceCatalogUi?,filters:VoiceFilterState,listState:LazyListState,onFilter:(FilterKey)->Unit,onChanged:()->Unit,onTest:(String)->Unit,onStatus:(String)->Unit){
    val app=UtterMuxApp.instance
    var defaultVoice by rememberSaveable{mutableStateOf(app.settings.defaultVoice)};var shown by remember{mutableStateOf<List<VoiceSearchEntry>>(emptyList())}
    LaunchedEffect(revision){defaultVoice=app.settings.defaultVoice}
    LaunchedEffect(snapshot,filters.globalSearch,filters.voiceSearch,filters.languageSearch,filters.librarySearch,filters.modelSearch,filters.accentSearch,filters.locality,filters.readiness,filters.performance,filters.gender,filters.capability,filters.cost,filters.sort){
        if(snapshot==null){shown=emptyList();return@LaunchedEffect};delay(60)
        shown=withContext(Dispatchers.Default){VoiceDiscovery.filter(snapshot.entries,filters.query())}
    }
    if(snapshot==null){Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){CircularProgressIndicator()};return}
    val selected=snapshot.entries.firstOrNull{it.voice.id==defaultVoice&&it.ready}?.voice;val effectiveDefault=selected?:snapshot.effectiveDefault
    val configuredReady=selected!=null
    val activity by VoiceActivity.state.collectAsState()
    val activeFilters=filters.active();var filterMenuOpen by remember{mutableStateOf(false)}
    LazyColumn(Modifier.fillMaxSize().padding(12.dp),state=listState,verticalArrangement=Arrangement.spacedBy(8.dp)){
        if(snapshot.entries.none{it.ready})item{Card{Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Text("No voice is installed or configured",style=MaterialTheme.typography.titleMedium);Text("Download an offline voice below or configure an online provider in Settings. UtterMux intentionally bundles no voice model.");Button(onClick={app.startActivity(Intent("com.android.settings.TTS_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))}){Text("Android TTS settings")}}}}
        if(!configuredReady&&effectiveDefault!=null)item{Card{Text("Configured default is unavailable. Currently using ${effectiveDefault.name}; the saved preference will be restored automatically if its provider becomes available.",Modifier.padding(12.dp))}}
        item{Card(Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
            Text("Active voice",style=MaterialTheme.typography.titleMedium)
            Text(app.router.voice(activity.activeVoice)?.name ?: effectiveDefault?.name ?: "No ready voice")
            Text(when(activity.status){"speaking"->"Speaking in ${activity.language} for ${activity.client}";"warming"->"Loading voice";else->"Configured default: ${selected?.name?:app.settings.defaultVoice}"},style=MaterialTheme.typography.bodySmall)
            if(activity.fallbackReason.isNotBlank())Text(activity.fallbackReason,style=MaterialTheme.typography.labelSmall)
        }}}
        item{
            Card(Modifier.fillMaxWidth()){
                Column(Modifier.fillMaxWidth().padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
                        Column(Modifier.weight(1f)){Text("Voice catalog",style=MaterialTheme.typography.titleMedium);Text(if(activeFilters.isNotEmpty())"${shown.size} results" else "${shown.size} voices · all locations",style=MaterialTheme.typography.bodySmall)}
                        Box{
                            OutlinedButton(onClick={filterMenuOpen=true}){Text("Filter")}
                            DropdownMenu(filterMenuOpen,{filterMenuOpen=false}){FilterKey.entries.forEach{key->DropdownMenuItem(text={Text(key.label)},onClick={filterMenuOpen=false;onFilter(key)})}}
                        }
                    }
                    OutlinedTextField(filters.globalSearch,{filters.globalSearch=it},Modifier.fillMaxWidth(),label={Text("Search all voices")},placeholder={Text("Voice, service, model, language, accent…")},singleLine=true,trailingIcon={if(filters.globalSearch.isNotBlank())IconButton({filters.globalSearch=""}){Text("×")}})
                    if(activeFilters.isNotEmpty())LazyRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){
                        items(activeFilters,key={it.name}){key->InputChip(selected=true,onClick={onFilter(key)},label={Text("${key.label}: ${filterDisplay(snapshot,key,filters.get(key))}")},trailingIcon={IconButton(onClick={filters.clear(key)},Modifier.size(24.dp)){Text("×")}})}
                    }
                }
            }
        }
        items(shown,key={it.voice.id}){entry->val voice=entry.voice;VoiceCard(voice,"${entry.library} · ${entry.model}",entry.ready,voice.id==(effectiveDefault?.id?:defaultVoice),{app.settings.defaultVoice=voice.id;defaultVoice=voice.id;Thread{app.router.warm(voice.id)}.start();onStatus("Default: ${voice.name}")},{onChanged()},onTest,{onStatus(it)})}
    }
}

private data class FilterOption(val value:String,val label:String,val count:Int=0)
@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun FilterChooserPage(snapshot:VoiceCatalogUi?,filters:VoiceFilterState,key:FilterKey,onDone:()->Unit){
    if(snapshot==null){Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){CircularProgressIndicator()};return}
    var search by rememberSaveable(key){mutableStateOf("")};var countSort by rememberSaveable(key){mutableStateOf(false)}
    val baseOptions=remember(snapshot,key,filters.librarySearch){filterOptions(snapshot,key,filters.librarySearch)}
    val options=remember(baseOptions,search,countSort,filters.values()){
        val candidates=VoiceDiscovery.filter(snapshot.entries,filters.query(key))
        baseOptions.map{option->option.copy(count=if(key==FilterKey.SORT)candidates.size else candidates.count{entry->optionMatches(entry,key,option.value)})}.filter{it.label.contains(search,true)||it.value.contains(search,true)}.sortedWith(if(countSort)compareByDescending<FilterOption>{it.count}.thenBy{it.label.lowercase()}else compareBy{it.label.lowercase()})
    }
    LazyColumn(Modifier.fillMaxSize().padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
        item{Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){TextButton(onClick=onDone){Text("← Back")};Text(key.label,Modifier.weight(1f),style=MaterialTheme.typography.headlineSmall);if(filters.get(key).let{it.isNotBlank()&&it!="all"&&!(key==FilterKey.SORT&&it=="name")})TextButton(onClick={filters.clear(key);onDone()}){Text("Clear")}}}
        if(baseOptions.size>8)item{OutlinedTextField(search,{search=it},Modifier.fillMaxWidth(),label={Text("Search ${key.label.lowercase()}")},singleLine=true)}
        if(baseOptions.size>8)item{SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()){SegmentedButton(!countSort,{countSort=false},SegmentedButtonDefaults.itemShape(0,2)){Text("A–Z")};SegmentedButton(countSort,{countSort=true},SegmentedButtonDefaults.itemShape(1,2)){Text("Most matches")}}}
        item{Text("${options.size} choices · counts respect your other filters",style=MaterialTheme.typography.bodySmall)}
        items(options,key={it.value}){option->ListItem(headlineContent={Text(option.label)},supportingContent={if(key!=FilterKey.SORT)Text("${option.count} matching voice${if(option.count==1)"" else "s"}")},leadingContent={RadioButton(filters.get(key)==option.value,onClick=null)},modifier=Modifier.fillMaxWidth().clickable(enabled=option.count>0||key==FilterKey.SORT){filters.set(key,option.value);onDone()});HorizontalDivider()}
    }
}

private fun filterDisplay(snapshot:VoiceCatalogUi?,key:FilterKey,value:String)=filterOptions(snapshot,key,"").firstOrNull{it.value==value}?.label?:value
private fun filterOptions(snapshot:VoiceCatalogUi?,key:FilterKey,library:String):List<FilterOption>{
    fun suggestions(values:List<Suggestion>)=values.map{FilterOption(it.value,it.label)}
    return when(key){
        FilterKey.VOICE->suggestions(snapshot?.voices.orEmpty());FilterKey.LANGUAGE->suggestions(snapshot?.languages.orEmpty());FilterKey.LIBRARY->suggestions(snapshot?.libraries.orEmpty())
        FilterKey.MODEL->suggestions(if(snapshot==null)emptyList() else if(library.isBlank())snapshot.models else snapshot.entries.filter{it.library.contains(library,true)}.map{Suggestion(it.model,it.model)}.distinctBy{it.value})
        FilterKey.ACCENT->suggestions(snapshot?.accents.orEmpty());FilterKey.LOCATION->listOf(FilterOption("offline","Offline"),FilterOption("online","Online"));FilterKey.AVAILABILITY->listOf(FilterOption("ready","Ready"),FilterOption("downloadable","Downloadable"),FilterOption("setup","Setup required"));FilterKey.PERFORMANCE->snapshot?.performances.orEmpty().map{FilterOption(it,it.replaceFirstChar(Char::uppercase))};FilterKey.GENDER->snapshot?.genders.orEmpty().map{FilterOption(it,it.replaceFirstChar(Char::uppercase))};FilterKey.CAPABILITY->snapshot?.capabilities.orEmpty().map{FilterOption(it,it.replace('-', ' ').replaceFirstChar(Char::uppercase))};FilterKey.COST->listOf("free","metered","subscription").map{FilterOption(it,it.replaceFirstChar(Char::uppercase))};FilterKey.SORT->listOf(FilterOption("name","Name"),FilterOption("library","Voice library"),FilterOption("smallest","Smallest download"),FilterOption("fastest","Fastest first"))
    }
}
private fun optionMatches(entry:VoiceSearchEntry,key:FilterKey,value:String)=when(key){FilterKey.VOICE->entry.voice.name.equals(value,true);FilterKey.LANGUAGE->entry.voice.languages.any{it.equals(value,true)};FilterKey.LIBRARY->entry.library.equals(value,true);FilterKey.MODEL->entry.model.equals(value,true);FilterKey.ACCENT->entry.voice.accent.equals(value,true);FilterKey.LOCATION->if(value=="offline")!entry.voice.networkRequired else entry.voice.networkRequired;FilterKey.AVAILABILITY->when(value){"ready"->entry.ready;"downloadable"->!entry.ready&&entry.voice.downloadable;else->!entry.ready&&!entry.voice.downloadable};FilterKey.PERFORMANCE->entry.voice.performanceClass.equals(value,true);FilterKey.GENDER->entry.voice.gender.equals(value,true);FilterKey.CAPABILITY->value in entry.voice.capabilities;FilterKey.COST->VoiceDiscovery.cost(entry.voice).equals(value,true);FilterKey.SORT->true}

private data class Suggestion(val value:String,val label:String)
@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun SuggestionSearchField(label:String,value:String,options:List<Suggestion>,onValue:(String)->Unit){
    var expanded by remember{mutableStateOf(false)};var editing by remember{mutableStateOf(false)}
    val focusRequester=remember{FocusRequester()};val keyboard=LocalSoftwareKeyboardController.current;val focusManager=LocalFocusManager.current
    val matches=remember(value,options){options.filter{value.isBlank()||it.value.contains(value,true)||it.label.contains(value,true)}.take(30)}
    LaunchedEffect(editing){if(editing){focusRequester.requestFocus();keyboard?.show()}}
    fun close(){editing=false;expanded=false;keyboard?.hide();focusManager.clearFocus()}
    ExposedDropdownMenuBox(expanded,{requested->
        if(requested){expanded=true}else if(expanded&&!editing){editing=true;expanded=true}else close()
    }){
        OutlinedTextField(value,{if(editing){onValue(it);expanded=true}},Modifier.fillMaxWidth().heightIn(min=56.dp).focusRequester(focusRequester).menuAnchor(if(editing)ExposedDropdownMenuAnchorType.PrimaryEditable else ExposedDropdownMenuAnchorType.PrimaryNotEditable),readOnly=!editing,label={Text(label)},singleLine=true,trailingIcon={
            Row{if(value.isNotBlank())IconButton({onValue("");editing=false;expanded=false;keyboard?.hide();focusManager.clearFocus()},Modifier.semantics{contentDescription="Clear $label"}){Text("×")};if(!editing)IconButton({editing=true;expanded=true},Modifier.semantics{contentDescription="Search $label"}){Text("⌕")}else ExposedDropdownMenuDefaults.TrailingIcon(expanded)}
        })
        ExposedDropdownMenu(expanded,{close()}){
            if(matches.isEmpty())DropdownMenuItem(text={Text("No matches")},enabled=false,onClick={})
            matches.forEach{item->DropdownMenuItem(text={Text(item.label)},onClick={onValue(item.value);close()})}
        }
    }
}

@Composable private fun VoiceCard(voice:VoiceRecord,service:String,catalogReady:Boolean,selected:Boolean,onDefault:()->Unit,onChanged:()->Unit,onTest:(String)->Unit,onStatus:(String)->Unit){
    val app=UtterMuxApp.instance;val context=LocalContext.current;val scope=rememberCoroutineScope();val localId=voice.downloadId.ifBlank{voice.takeIf{it.provider==ProviderIds.SHERPA}?.id?.split('/')?.getOrNull(1).orEmpty()}
    val advice=remember(voice.id,voice.estimatedRamMb,voice.performanceClass,voice.networkRequired){HardwareAdvisor.recommend(context,voice)}
    var installed by remember(voice.id){mutableStateOf(localId.isBlank()&&app.router.isAvailable(voice)||localId.isNotBlank()&&runCatching{app.models.installed(localId)}.getOrDefault(false))}
    var repairNeeded by remember(voice.id,installed){mutableStateOf(installed&&localId.isNotBlank()&&runCatching{app.models.needsRepair(localId)}.getOrDefault(false))}
    var missingAssets by remember(voice.id,installed){mutableStateOf(if(installed&&localId.isNotBlank())runCatching{app.models.missingAssets(localId).size}.getOrDefault(0)else 0)}
    var confirmPaid by remember{mutableStateOf(false)}
    val ready=if(localId.isNotBlank())installed&&app.router.isAvailable(voice) else catalogReady;val canRemotePreview=voice.previewUrl.isNotBlank();val canPreview=ready||canRemotePreview
    val preview by PreviewController.state.collectAsState();val previewActive=preview.voiceId==voice.id&&preview.phase in setOf("loading","playing")
    val doPreview:()->Unit={scope.launch{onStatus("Previewing ${voice.name}…");runCatching{
        PreviewController.play(voice.id){cancelled->if(!ready&&canRemotePreview)CompressedAudioDecoder.decode(app,HttpAudio.get(voice.previewUrl),voice.previewUrl.substringAfterLast('.',"audio"))else app.router.synthesizeExact(voice.id,previewText(voice.locale.language),voice.locale.toLanguageTag(),1f,cancelled)}
    }.onSuccess{onStatus(PreviewController.state.value.message)}.onFailure{onStatus("Preview unavailable: ${it.message}")}}}
    Card(Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(7.dp)){
        Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(voice.name,style=MaterialTheme.typography.titleSmall);Text("$service · ${voice.model} · ${voice.languages.joinToString()}",style=MaterialTheme.typography.bodySmall);if(voice.description.isNotBlank())Text(voice.description,style=MaterialTheme.typography.bodySmall)};RadioButton(selected,onClick=onDefault,enabled=ready)}
        val facts=listOf(voice.quantization,voice.approxSizeMb.takeIf{it>0}?.let{"$it MB"}.orEmpty(),voice.estimatedRamMb.takeIf{it>0}?.let{"~$it MB RAM"}.orEmpty(),voice.performanceClass.takeUnless{it=="unknown"}.orEmpty(),advice.label,voice.license).filter(String::isNotBlank)
        if(facts.isNotEmpty())Text(facts.joinToString(" · "),style=MaterialTheme.typography.labelSmall)
        if(!voice.networkRequired)Text(advice.reason,style=MaterialTheme.typography.bodySmall,modifier=Modifier.semantics{contentDescription="Hardware recommendation: ${advice.label}. ${advice.reason}"})
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically){
            AssistChip(onClick={},label={Text(if(ready)"Ready" else if(voice.downloadable&&localId.isNotBlank())"Downloadable" else "Setup required")})
            TextButton(enabled=canPreview||previewActive,onClick={if(previewActive){PreviewController.stop();onStatus("Preview stopped")}else if(voice.networkRequired&&voice.provider!=ProviderIds.EDGE&&!app.settings.paidPreviewConfirmed)confirmPaid=true else doPreview()}){Text(if(previewActive)"Stop" else if(voice.networkRequired&&voice.provider!=ProviderIds.EDGE)"Preview · may cost" else if(canPreview)"Preview" else "Install to preview")}
            if(ready&&!voice.networkRequired&&localId.isNotBlank())TextButton(onClick={onTest(localId)}){Text("Test model")}
        }
        if(preview.voiceId==voice.id&&preview.phase!="idle")Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
            if(preview.phase=="loading")CircularProgressIndicator(Modifier.size(18.dp),strokeWidth=2.dp)
            Text(preview.message,style=MaterialTheme.typography.labelSmall)
        }
        if(localId.isNotBlank())Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
            if(!installed&&voice.downloadable)Button(onClick={scope.launch{onStatus("Downloading $localId…");runCatching{withContext(Dispatchers.IO){app.models.install(localId)}}.onSuccess{installed=true;app.notifyVoiceDataChanged();onChanged();onStatus("Installed ${voice.name}")}.onFailure{onStatus("Install failed: ${it.message}")}}}){Text("Download")}
            if(installed&&repairNeeded)Button(onClick={scope.launch{onStatus("Downloading $missingAssets missing voice file${if(missingAssets==1)"" else "s"}…");runCatching{withContext(Dispatchers.IO){app.models.repair(localId)}}.onSuccess{repairNeeded=false;missingAssets=0;app.notifyVoiceDataChanged();onChanged();onStatus("Completed ${voice.model}")}.onFailure{onStatus("Voice-file download failed: ${it.message}")}}}){Text("Download")}
            if(installed)OutlinedButton(onClick={scope.launch{onStatus("Deleting ${voice.model}…");val deleted=withContext(Dispatchers.IO){app.providers.forEach{it.trimMemory()};app.models.delete(localId)};if(deleted){installed=false;if(selected)app.settings.defaultVoice="uttermux:auto@en";app.notifyVoiceDataChanged();onChanged();onStatus("Deleted ${voice.model}")}}}){Text("Delete model")}
        }
        if(voice.attribution.isNotBlank())Text(voice.attribution,style=MaterialTheme.typography.labelSmall)
        if(voice.sourceUrl.isNotBlank())TextButton(onClick={app.startActivity(Intent(Intent.ACTION_VIEW,android.net.Uri.parse(voice.sourceUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))}){Text("Model information")}
    }}
    if(confirmPaid)AlertDialog(onDismissRequest={confirmPaid=false},title={Text("Paid voice preview")},text={Text("This sends the sample text to ${service.substringBefore(" ·")} and may use paid API credits. UtterMux will remember this confirmation.")},confirmButton={Button(onClick={app.settings.paidPreviewConfirmed=true;confirmPaid=false;doPreview()}){Text("Preview")}},dismissButton={TextButton(onClick={confirmPaid=false}){Text("Cancel")}})
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun Selector(label:String,value:String,options:List<String>,onSelect:(String)->Unit){var expanded by remember{mutableStateOf(false)};ExposedDropdownMenuBox(expanded,{expanded=it}){OutlinedTextField(value,{},Modifier.fillMaxWidth().heightIn(min=56.dp).menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),readOnly=true,label={Text(label)},trailingIcon={ExposedDropdownMenuDefaults.TrailingIcon(expanded)});ExposedDropdownMenu(expanded,{expanded=false}){options.forEach{option->DropdownMenuItem(text={Text(option.ifBlank{"Choose…"})},onClick={onSelect(option);expanded=false})}}}}
private fun previewText(language:String)=when(Languages.normalized(language).substringBefore('-')){"fr"->"Bonjour. Voici un aperçu de cette voix avec UtterMux.";"de"->"Hallo. Dies ist eine Vorschau dieser Stimme mit UtterMux.";"es"->"Hola. Esta es una muestra de esta voz con UtterMux.";"it"->"Ciao. Questa è un'anteprima della voce.";"pt"->"Olá. Esta é uma prévia desta voz.";"zh"->"你好，这是 UtterMux 语音预览。";else->"This is an UtterMux voice preview."}
