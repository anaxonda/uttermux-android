package io.uttermux.android

import android.Manifest
import android.app.*
import android.content.*
import android.os.Bundle
import android.provider.Settings
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
import androidx.compose.ui.unit.dp
import io.uttermux.android.audio.Playback
import io.uttermux.android.config.*
import io.uttermux.android.service.KoReaderServerService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : ComponentActivity() {
    private val notifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (android.os.Build.VERSION.SDK_INT >= 33) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        setContent { MaterialTheme { ManagerScreen() } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun ManagerScreen() {
    val app = UtterMuxApp.instance
    var defaultVoice by remember { mutableStateOf(app.settings.defaultVoice) }
    var grokKey by remember { mutableStateOf(app.secure.get("grok")) }
    var elevenKey by remember { mutableStateOf(app.secure.get("elevenlabs")) }
    var query by remember { mutableStateOf("") }
    var provider by remember { mutableStateOf("all") }
    var status by remember { mutableStateOf("Ready") }
    var koReader by remember { mutableStateOf(app.settings.koReaderEnabled) }
    val scope = rememberCoroutineScope()
    val voices = app.router.voices.filter { v ->
        (provider == "all" || v.provider.name.equals(provider, true)) &&
            (query.isBlank() || listOf(v.name, v.id, v.model, v.languages.joinToString()).any { it.contains(query, true) })
    }
    Scaffold(topBar = { TopAppBar(title = { Text("UtterMux") }) }) { padding ->
        LazyColumn(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Text("System TTS provider", style = MaterialTheme.typography.titleMedium) }
            item { Button(onClick = { UtterMuxApp.instance.startActivity(Intent("com.android.settings.TTS_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }) { Text("Open Android TTS settings") } }
            item { OutlinedTextField(grokKey, { grokKey = it }, Modifier.fillMaxWidth(), label = { Text("Grok API key") }, singleLine = true) }
            item { OutlinedTextField(elevenKey, { elevenKey = it }, Modifier.fillMaxWidth(), label = { Text("ElevenLabs API key") }, singleLine = true) }
            item { Button(onClick = { app.secure.put("grok", grokKey.trim()); app.secure.put("elevenlabs", elevenKey.trim()); status = "Keys encrypted and saved" }) { Text("Save provider keys") } }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(koReader, { enabled ->
                        koReader = enabled; app.settings.koReaderEnabled = enabled
                        val intent = Intent(app, KoReaderServerService::class.java)
                        if (enabled) app.startForegroundService(intent) else app.stopService(intent)
                    }); Spacer(Modifier.width(8.dp)); Text("KOReader server · localhost:5000")
                }
            }
            item { OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text("Search voices or languages") }, singleLine = true) }
            item {
                SingleChoiceSegmentedButtonRow {
                    listOf("all", "grok", "elevenlabs", "edge", "sherpa").forEachIndexed { index, value ->
                        SegmentedButton(selected = provider == value, onClick = { provider = value }, shape = SegmentedButtonDefaults.itemShape(index, 5)) { Text(value) }
                    }
                }
            }
            item { Text("$status · ${voices.size} voices", style = MaterialTheme.typography.bodySmall) }
            items(voices, key = { it.id }) { voice ->
                val localModel = voice.takeIf { it.provider == ProviderKind.SHERPA }?.id?.split('/')?.getOrNull(1)
                var installed by remember(voice.id) { mutableStateOf(localModel?.let(app.models::installed) ?: true) }
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(voice.name, style = MaterialTheme.typography.titleSmall)
                            Text("${voice.provider.name.lowercase()} · ${voice.model} · ${voice.languages.joinToString()}", style = MaterialTheme.typography.bodySmall)
                        }
                        if (!installed && localModel != null) TextButton(onClick = {
                            status = "Downloading $localModel…"
                            scope.launch { runCatching { withContext(Dispatchers.IO) { app.models.install(localModel) { message -> status = message } } }
                                .onSuccess { installed = true; status = "Installed $localModel" }.onFailure { status = it.message ?: "Install failed" } }
                        }) { Text("Download") }
                        TextButton(enabled = installed, onClick = {
                            status = "Previewing ${voice.name}…"
                            scope.launch {
                                runCatching { withContext(Dispatchers.IO) { app.router.synthesize(voice.id, sample(voice.locale.language), voice.locale.toLanguageTag(), 1f, AtomicBoolean()) } }
                                    .onSuccess { audio -> withContext(Dispatchers.IO) { Playback.play(audio) }; status = "Previewed ${voice.name}" }
                                    .onFailure { status = it.message ?: "Preview failed" }
                            }
                        }) { Text("▶") }
                        RadioButton(selected = voice.id == defaultVoice, onClick = { app.settings.defaultVoice = voice.id; defaultVoice = voice.id; status = "Default: ${voice.name}" })
                    }
                }
            }
        }
    }
}

private fun sample(language: String) = when (language) {
    "fr" -> "Bonjour. Voici un aperçu de cette voix avec UtterMux."
    "de" -> "Hallo. Dies ist eine Vorschau dieser Stimme mit UtterMux."
    "es" -> "Hola. Esta es una muestra de esta voz con UtterMux."
    else -> "This is an UtterMux voice preview."
}
