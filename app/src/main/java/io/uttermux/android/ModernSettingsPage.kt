package io.uttermux.android

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.uttermux.android.audio.PreviewController
import io.uttermux.android.config.*
import io.uttermux.android.diagnostics.Diagnostics
import io.uttermux.android.service.KoReaderServerService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable internal fun ModernSettingsPage(revision:Int,theme:String,onTheme:(String)->Unit,onRefresh:()->Unit,onChanged:()->Unit,onStatus:(String)->Unit){
    val app=UtterMuxApp.instance;val scope=rememberCoroutineScope();val clipboard=LocalClipboardManager.current
    var providersOpen by remember{mutableStateOf(true)};var expandedProvider by remember{mutableStateOf("")};var routesOpen by remember{mutableStateOf(false)};var storageOpen by remember{mutableStateOf(false)};var advancedOpen by remember{mutableStateOf(false)};var diagnosticsOpen by remember{mutableStateOf(false)};var aboutOpen by remember{mutableStateOf(false)}
    var koReader by remember{mutableStateOf(app.settings.koReaderEnabled)};val values=remember{mutableStateMapOf<String,String>()}
    val onlineProviders=remember(revision){app.router.providerDescriptors.filter{it.network}}
    LaunchedEffect(revision){val loaded=withContext(Dispatchers.IO){onlineProviders.flatMap{it.credentialFields}.associate{it.key to app.secure.get(it.key)}};values.putAll(loaded)}

    var language by remember{mutableStateOf("en-US")};var routeVoice by remember{mutableStateOf("")};var routeRevision by remember{mutableIntStateOf(0)};var chain by remember(language,routeRevision,revision){mutableStateOf(app.settings.routeChain(language))}
    fun saveRoute(next:List<String>){chain=next;app.settings.setRouteChain(language,next);routeRevision++;onStatus("Saved ${Languages.normalized(language)} fallback chain")}
    val routeChoices=remember(routesOpen,language,revision){if(routesOpen)app.router.voices.filter{it.languages.any{tag->Languages.matches(tag,language)}}else emptyList()}
    val installed=remember(revision){app.models.models.filter{runCatching{app.models.installed(it.id)}.getOrDefault(false)}}
    val hardware=remember{HardwareAdvisor.detect(app)}

    var latency by remember{mutableStateOf(app.settings.latencyProfile)};var startup by remember{mutableStateOf(app.settings.manualStartupMs.toString())};var pocketSteps by remember{mutableStateOf(app.settings.pocketNumSteps.toString())};var cache by remember{mutableStateOf(app.settings.modelCacheSize.toString())};var threads by remember{mutableStateOf(app.settings.engineThreads.toString())};var report by remember{mutableStateOf("")}
    fun saveAdvanced(){app.settings.latencyProfile=latency;app.settings.manualStartupMs=startup.toIntOrNull()?:300;app.settings.pocketNumSteps=pocketSteps.toIntOrNull()?:3;app.settings.modelCacheSize=cache.toIntOrNull()?:1;app.settings.engineThreads=threads.toIntOrNull()?:0;app.providers.forEach{it.trimMemory()};onStatus("Advanced playback settings saved")}

    LazyColumn(Modifier.fillMaxSize().padding(12.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
        item{Text("General",style=MaterialTheme.typography.titleMedium)}
        item{SettingsSelector("Theme",theme,listOf("system","light","dark"),onTheme)}
        item{Button(onClick={app.startActivity(Intent("com.android.settings.TTS_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))}){Text("Open Android TTS settings")}}
        item{Card{Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=Alignment.CenterVertically){Switch(koReader,{enabled->koReader=enabled;app.settings.koReaderEnabled=enabled;val intent=Intent(app,KoReaderServerService::class.java);if(enabled)app.startForegroundService(intent)else app.stopService(intent)});Spacer(Modifier.width(10.dp));Column{Text("KOReader compatibility bridge");Text("Local-only service at 127.0.0.1:5000",style=MaterialTheme.typography.bodySmall)}}}}

        item{SettingsSectionButton("Online services (${onlineProviders.size})",providersOpen){providersOpen=!providersOpen}}
        if(providersOpen){
            item{Text("Credentials are encrypted with Android Keystore. Cloud synthesis and previews may use paid credits.",style=MaterialTheme.typography.bodySmall)}
            items(onlineProviders,key={"provider-${it.id}"}){provider->
                val expanded=expandedProvider==provider.id;val configured=provider.credentialFields.isEmpty()||provider.credentialFields.any{values[it.key].orEmpty().isNotBlank()}
                Card{Column(Modifier.fillMaxWidth().padding(12.dp),verticalArrangement=Arrangement.spacedBy(7.dp)){
                    Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(provider.name,style=MaterialTheme.typography.titleSmall);Text(if(provider.credentialFields.isEmpty())"No account setup required" else if(configured)"Configured · online${if(provider.experimental)" · experimental" else ""}" else "Not configured · online${if(provider.experimental)" · experimental" else ""}",style=MaterialTheme.typography.bodySmall)};TextButton(onClick={expandedProvider=if(expanded)"" else provider.id}){Text(if(expanded)"Close" else if(configured)"Edit" else "Set up")}}
                    if(provider.note.isNotBlank())Text(provider.note,style=MaterialTheme.typography.bodySmall)
                    if(expanded){
                        if(provider.id==ProviderIds.AWS)TextButton(onClick={clipboard.setText(AnnotatedString(SETTINGS_AWS_POLICY));onStatus("Polly IAM policy copied")}){Text("Copy least-privilege IAM policy")}
                        provider.credentialFields.forEach{field->if(field.choices.isNotEmpty())SettingsSelector(field.label,values[field.key].orEmpty().ifBlank{field.placeholder},field.choices){values[field.key]=it}else OutlinedTextField(values[field.key].orEmpty(),{values[field.key]=it},Modifier.fillMaxWidth(),label={Text(field.label)},placeholder={Text(field.placeholder)},singleLine=true,visualTransformation=if(field.secret)PasswordVisualTransformation()else VisualTransformation.None)}
                        Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){Button(onClick={provider.credentialFields.forEach{field->val saved=values[field.key].orEmpty().trim();values[field.key]=saved;app.secure.put(field.key,saved)};expandedProvider="";onChanged();onStatus("${provider.name} settings saved");onRefresh()}){Text("Save and close")};OutlinedButton(onClick={provider.credentialFields.forEach{values[it.key]="";app.secure.put(it.key,"")};expandedProvider="";onChanged();onStatus("${provider.name} credentials removed");onRefresh()}){Text("Remove")}}
                    }
                }}
            }
            item{OutlinedButton(onClick=onRefresh){Text("Refresh online voice catalogs")}}
        }

        item{SettingsSectionButton("Automatic language routing",routesOpen){routesOpen=!routesOpen}}
        if(routesOpen){
            item{Text("The selected global voice is tried first when it supports the requested language. Add explicit fallbacks here; paid cloud voices are never inserted automatically.",style=MaterialTheme.typography.bodySmall)}
            item{OutlinedTextField(language,{language=Languages.normalized(it)},Modifier.fillMaxWidth(),label={Text("Language (BCP-47)")},singleLine=true)}
            item{SettingsSelector("Add compatible voice",routeVoice,listOf("")+routeChoices.map{it.id}){routeVoice=it}}
            item{Button(enabled=routeVoice.isNotBlank()&&routeVoice !in chain,onClick={saveRoute(chain+routeVoice)}){Text("Add to fallback chain")}}
            items(chain,key={"route-$it"}){id->val voice=app.router.voice(id);Card{Column(Modifier.padding(10.dp)){Text("${chain.indexOf(id)+1}. ${voice?.name?:id}");Row{TextButton(onClick={val i=chain.indexOf(id);if(i>0){val n=chain.toMutableList();n[i]=n[i-1].also{n[i-1]=n[i]};saveRoute(n)}}){Text("Up")};TextButton(onClick={val i=chain.indexOf(id);if(i in 0 until chain.lastIndex){val n=chain.toMutableList();n[i]=n[i+1].also{n[i+1]=n[i]};saveRoute(n)}}){Text("Down")};TextButton(onClick={saveRoute(chain-id)}){Text("Remove")}}}}}
        }

        item{SettingsSectionButton("Downloads and storage (${installed.size})",storageOpen){storageOpen=!storageOpen}}
        if(storageOpen){
            if(installed.isEmpty())item{Text("No local models are installed. UtterMux deliberately bundles no voice model.")}
            items(installed,key={"installed-${it.id}"}){model->val voice=app.router.voices.firstOrNull{it.downloadId==model.id};Card{Column(Modifier.fillMaxWidth().padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
                Text(model.title,style=MaterialTheme.typography.titleSmall);Text(listOf(model.family,model.quantization,"${model.downloadSizeMb.takeIf{it>0}?:settingsFolderMegabytes(java.io.File(app.models.root,model.id))} MB",model.estimatedRamMb.takeIf{it>0}?.let{"~$it MB RAM"}.orEmpty(),model.performanceClass).filter{it.isNotBlank()&&it!="unknown"}.joinToString(" · "),style=MaterialTheme.typography.bodySmall);if(model.languages.isNotEmpty())Text(model.languages.joinToString(),style=MaterialTheme.typography.labelSmall);if(model.license.isNotBlank())Text("License: ${model.license}",style=MaterialTheme.typography.labelSmall)
                Row(horizontalArrangement=Arrangement.spacedBy(4.dp)){if(voice!=null)TextButton(onClick={scope.launch{runCatching{PreviewController.play(voice.id){cancelled->app.router.synthesizeExact(voice.id,settingsPreviewText(voice.locale.language),voice.locale.toLanguageTag(),1f,cancelled)}}.onSuccess{onStatus(PreviewController.state.value.message)}.onFailure{onStatus("Preview failed: ${it.message}")}}}){Text("Preview")};if(model.sourceUrl.isNotBlank())TextButton(onClick={app.startActivity(Intent(Intent.ACTION_VIEW,android.net.Uri.parse(model.sourceUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))}){Text("Upstream")};TextButton(onClick={scope.launch{onStatus("Deleting ${model.title}…");val deleted=withContext(Dispatchers.IO){app.providers.forEach{it.trimMemory()};app.models.delete(model.id)};if(deleted){app.notifyVoiceDataChanged();onChanged();onStatus("Deleted ${model.title}")}}}){Text("Delete")}}
            }}}
        }

        item{SettingsSectionButton("Global defaults and advanced playback",advancedOpen){advancedOpen=!advancedOpen}}
        if(advancedOpen){
            item{Text("These are global defaults. Settings for one downloaded artifact on Test & tune take precedence. Playback mode and model cache remain global.",style=MaterialTheme.typography.bodySmall)}
            item{Column(verticalArrangement=Arrangement.spacedBy(4.dp)){SettingsSelector("Playback mode",latency,listOf("automatic","low","smooth","manual")){latency=it};SettingExplanation(when(latency){"low"->"Starts with less audio buffered. Faster response, but slower models may briefly underrun.";"smooth"->"Waits for more audio before playback. More reliable, with a longer initial delay.";"manual"->"Uses the exact startup reserve below.";else->"Learns each voice's real-time factor and underruns, then adjusts its reserve automatically."})}}
            if(latency=="manual")item{OutlinedTextField(startup,{startup=it.filter(Char::isDigit)},Modifier.fillMaxWidth(),label={Text("Startup reserve (80–5000 ms)")},supportingText={Text("More reserve reduces clipping but increases the delay before speech.")},singleLine=true)}
            item{Column(verticalArrangement=Arrangement.spacedBy(4.dp)){SettingsSelector("Default Pocket refinement",pocketSteps,listOf("1","2","3","4","5")){pocketSteps=it};SettingExplanation(when(pocketSteps){"1"->"Least refinement. Similar warm speed to two steps on the reference phone; use only if its voice quality is acceptable.";"2"->"Recommended reader setting: sustained RTF 0.47–0.48 with two threads on the reference phone.";"3"->"More refinement, but previously reached realtime limits on the reference phone.";"4"->"Higher refinement and slower generation.";else->"Highest refinement and slowest generation."})}}
            item{Column(verticalArrangement=Arrangement.spacedBy(4.dp)){SettingsSelector("Default local engine threads",threads,listOf("0","1","2","4")){threads=it};SettingExplanation(if(threads=="0")"Automatic: up to two threads for Pocket and four for other engines, limited by available CPU cores." else "$threads inference thread(s) for artifacts without an override or tuned profile. Use Test & tune to adjust only the current model.")}}
            item{Column(verticalArrangement=Arrangement.spacedBy(4.dp)){SettingsSelector("Loaded-model cache",cache,listOf("1","2","3")){cache=it};SettingExplanation("Keeping more models avoids cold starts but can consume hundreds of megabytes of RAM per model.")}}
            item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Button(onClick=::saveAdvanced){Text("Save advanced settings")};OutlinedButton(onClick={app.settings.resetAdvanced();latency="automatic";startup="300";pocketSteps="2";threads="0";cache="1";app.providers.forEach{it.trimMemory()};onStatus("Advanced settings reset")}){Text("Reset")}}}
        }

        item{SettingsSectionButton("Diagnostics",diagnosticsOpen){diagnosticsOpen=!diagnosticsOpen}}
        if(diagnosticsOpen){
            item{Text("${hardware.architecture} · ${hardware.logicalCores} logical CPU cores · ${hardware.totalRamMb} MB RAM (${hardware.availableRamMb} MB currently available) · inference: ${hardware.inferenceProviders.joinToString()}",style=MaterialTheme.typography.bodySmall)}
            item{Text(app.adaptiveBuffers.snapshot().ifBlank{"No timing samples yet. Effective reserves appear after playback."},style=MaterialTheme.typography.bodySmall)}
            item{Text("Reports contain timing, voice IDs, character counts, and redacted errors. They do not include spoken document text or credentials.",style=MaterialTheme.typography.bodySmall)}
            item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Button(onClick={report=Diagnostics.report()}){Text("Refresh report")};OutlinedButton(onClick={Diagnostics.clear();report=""}){Text("Clear")}}}
            item{Text(if(report.isBlank())"No requests recorded" else report,style=MaterialTheme.typography.bodySmall)}
        }

        item{SettingsSectionButton("About and privacy",aboutOpen){aboutOpen=!aboutOpen}}
        if(aboutOpen)item{Card{Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){Text("UtterMux ${BuildConfig.VERSION_NAME}");Text("GPL-3.0-or-later · application ID io.uttermux.android",style=MaterialTheme.typography.bodySmall);Text("Local voice profiles and API credentials stay on this device. Credentials are encrypted and excluded from backup. Model downloads are explicit and checksum verified.",style=MaterialTheme.typography.bodySmall)}}}
    }
}

@Composable private fun SettingExplanation(text:String)=Text(text,style=MaterialTheme.typography.bodySmall)
@Composable private fun SettingsSectionButton(label:String,expanded:Boolean,onClick:()->Unit)=OutlinedButton(onClick,Modifier.fillMaxWidth().heightIn(min=48.dp)){Text(if(expanded)"▾ $label" else "▸ $label")}
@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun SettingsSelector(label:String,value:String,options:List<String>,onSelect:(String)->Unit){var expanded by remember{mutableStateOf(false)};ExposedDropdownMenuBox(expanded,{expanded=it}){OutlinedTextField(if(value=="0"&&label=="Local engine threads")"Automatic" else value,{},Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),readOnly=true,label={Text(label)},trailingIcon={ExposedDropdownMenuDefaults.TrailingIcon(expanded)});ExposedDropdownMenu(expanded,{expanded=false}){options.forEach{option->DropdownMenuItem(text={Text(if(option=="0"&&label=="Local engine threads")"Automatic" else option.ifBlank{"Choose…"})},onClick={onSelect(option);expanded=false})}}}}
private fun settingsPreviewText(language:String)=when(Languages.normalized(language).substringBefore('-')){"fr"->"Bonjour. Voici un aperçu de cette voix avec UtterMux.";"de"->"Hallo. Dies ist eine Vorschau dieser Stimme mit UtterMux.";"es"->"Hola. Esta es una muestra de esta voz con UtterMux.";else->"This is an UtterMux voice preview."}
private fun settingsFolderMegabytes(root:java.io.File):Int=(root.walkTopDown().filter{it.isFile}.sumOf{it.length()}/1024/1024).toInt()
private const val SETTINGS_AWS_POLICY="""{"Version":"2012-10-17","Statement":[{"Effect":"Allow","Action":["polly:DescribeVoices","polly:SynthesizeSpeech"],"Resource":"*"}]}"""
