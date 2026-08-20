package io.uttermux.android

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.uttermux.android.audio.PreviewController
import io.uttermux.android.config.Languages
import io.uttermux.android.config.VoiceProfile
import io.uttermux.android.provider.PocketProfileStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable internal fun CreateVoicePage(revision:Int,onChanged:()->Unit,onStatus:(String)->Unit){
    val app=UtterMuxApp.instance;val scope=rememberCoroutineScope();val store=remember{PocketProfileStore(app)}
    var name by rememberSaveable{mutableStateOf("")};var language by rememberSaveable{mutableStateOf("en-US")};var consent by rememberSaveable{mutableStateOf(false)};var profileRevision by remember{mutableIntStateOf(0)};var editing by remember{mutableStateOf<VoiceProfile?>(null)};var rename by remember{mutableStateOf("")}
    val profiles=remember(profileRevision,revision){store.profiles()};val modelId=PocketProfileStore.MODEL_VERSION
    var modelReady by remember(revision){mutableStateOf(runCatching{app.models.installed(modelId)}.getOrDefault(false))}
    fun created(profile:VoiceProfile){name="";consent=false;profileRevision++;app.notifyVoiceDataChanged();onChanged();onStatus("Created ${profile.name} with Pocket")}
    val importer=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()){uri->if(uri!=null)scope.launch{onStatus("Importing reference recording…");runCatching{withContext(Dispatchers.IO){store.import(uri,name,language)}}.onSuccess(::created).onFailure{onStatus("Import failed: ${it.message}")}}}
    val recorder=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted->if(granted)scope.launch{onStatus("Recording eight seconds…");runCatching{withContext(Dispatchers.IO){store.record(name,language)}}.onSuccess(::created).onFailure{onStatus("Recording failed: ${it.message}")}}else onStatus("Microphone permission denied")}
    fun previewReference(profile:VoiceProfile){scope.launch{runCatching{PreviewController.play("reference/${profile.id}"){store.reference(profile)}}.onSuccess{onStatus(PreviewController.state.value.message)}.onFailure{onStatus("Reference preview failed: ${it.message}")}}}
    fun previewVoice(profile:VoiceProfile){scope.launch{val id="sherpa/$modelId/custom-${profile.id}@${profile.language}";runCatching{PreviewController.play(id){cancelled->app.router.synthesizeExact(id,createPreviewText(profile.language),profile.language,1f,cancelled)}}.onSuccess{onStatus(PreviewController.state.value.message)}.onFailure{onStatus("Clone preview failed: ${it.message}")}}}
    LazyColumn(Modifier.fillMaxSize().padding(12.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
        item{Text("Create a voice",style=MaterialTheme.typography.headlineSmall)}
        item{Card{Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){Text("Pocket voice cloning",style=MaterialTheme.typography.titleMedium);Text("Create a private on-device voice from a clean 3–10 second sample. Future clone-capable engines will use this same workspace, while every profile remains tied to the engine that created it.",style=MaterialTheme.typography.bodySmall);Text("Engine: Pocket · Model: 2026-01 INT8 · multilingual conditioning",style=MaterialTheme.typography.labelSmall)}}}
        if(!modelReady)item{Card{Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Text("Pocket is not downloaded");Text("The model is about 176 MB. Reference recordings are small and stay on this device.",style=MaterialTheme.typography.bodySmall);Button(onClick={scope.launch{onStatus("Downloading Pocket…");runCatching{withContext(Dispatchers.IO){app.models.install(modelId)}}.onSuccess{modelReady=true;app.notifyVoiceDataChanged();onChanged();onStatus("Pocket installed")}.onFailure{onStatus("Pocket download failed: ${it.message}")}}}){Text("Download Pocket")}}}}
        item{OutlinedTextField(name,{name=it},Modifier.fillMaxWidth(),label={Text("Voice name")},placeholder={Text("My voice")},singleLine=true)}
        item{OutlinedTextField(language,{language=Languages.normalized(it)},Modifier.fillMaxWidth(),label={Text("Reference language (BCP-47)")},supportingText={Text("Used for Pocket conditioning and automatic routing.")},singleLine=true)}
        item{Row(verticalAlignment=Alignment.CenterVertically){Checkbox(consent,{consent=it});Text("I own this recording or have permission to create this voice.",Modifier.weight(1f),style=MaterialTheme.typography.bodySmall)}}
        item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Button(enabled=consent&&modelReady,onClick={importer.launch("audio/*")}){Text("Import audio")};OutlinedButton(enabled=consent&&modelReady,onClick={recorder.launch(Manifest.permission.RECORD_AUDIO)}){Text("Record 8 seconds")}}}
        item{HorizontalDivider();Text("Created voices (${profiles.size})",style=MaterialTheme.typography.titleMedium)}
        if(profiles.isEmpty())item{Text("No custom voices yet.",style=MaterialTheme.typography.bodySmall)}
        items(profiles,key={it.id}){profile->Card{Column(Modifier.fillMaxWidth().padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
            Text(profile.name,style=MaterialTheme.typography.titleSmall);Text("Pocket · ${profile.modelVersion} · ${profile.language} · private on-device",style=MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement=Arrangement.spacedBy(4.dp)){TextButton(onClick={previewReference(profile)}){Text("Reference")};TextButton(enabled=modelReady,onClick={previewVoice(profile)}){Text("Generated preview")};TextButton(enabled=modelReady,onClick={app.settings.defaultVoice="sherpa/$modelId/custom-${profile.id}@${profile.language}";app.notifyVoiceDataChanged();onChanged();onStatus("Default: ${profile.name}")}){Text("Set default")}}
            Row(horizontalArrangement=Arrangement.spacedBy(4.dp)){TextButton(onClick={editing=profile;rename=profile.name}){Text("Rename")};TextButton(onClick={if(store.delete(profile.id)){profileRevision++;app.notifyVoiceDataChanged();onChanged();onStatus("Deleted ${profile.name}")}}){Text("Delete")}}
        }}}
    }
    editing?.let{profile->AlertDialog(onDismissRequest={editing=null},title={Text("Rename voice")},text={OutlinedTextField(rename,{rename=it},singleLine=true)},confirmButton={Button(onClick={if(store.rename(profile.id,rename)){profileRevision++;app.notifyVoiceDataChanged();onChanged();onStatus("Voice renamed")};editing=null}){Text("Save")}},dismissButton={TextButton(onClick={editing=null}){Text("Cancel")}})}
}

private fun createPreviewText(language:String)=when(Languages.normalized(language).substringBefore('-')){"fr"->"Bonjour. Voici un aperçu de cette voix avec UtterMux.";"de"->"Hallo. Dies ist eine Vorschau dieser Stimme mit UtterMux.";"es"->"Hola. Esta es una muestra de esta voz con UtterMux.";else->"This is an UtterMux voice preview."}
