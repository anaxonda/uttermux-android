# UtterMux for Android

UtterMux is an Android system text-to-speech engine and voice manager. It gives
Android readers one interface for local ONNX models and online providers, and
also implements the loopback protocol used by KOReader's `TTS.koplugin`.

## What works

- Android `TextToSpeechService` integration for Feeder, Librera, KOReader, and
  other clients using the standard system API.
- Early `onStart` and incremental PCM delivery, exact text ranges, cancellation,
  engine warming, segmented Piper synthesis, bounded silence trimming, and an
  adaptive startup buffer for direct playback.
- Searchable voice catalog, exact-voice previews, separate searchable
  language/provider/model/voice filters,
  ordered BCP-47 fallback routes, model downloads, settings, and diagnostics.
- Full Piper catalog (174 models and 2,707 speaker choices), all 53 Kokoro 1.0
  speakers, all eight Kitten speakers, Inflect, Matcha, and Supertonic.
  Pocket includes four explicitly licensed Kyutai reference voices. MOSS Nano
  includes ten built-in presets and its complete optional ONNX runtime path.
- Edge, ElevenLabs, Grok/xAI, OpenAI-compatible, Azure, Qwen/DashScope,
  Deepgram, Cartesia, PlayHT, Resemble, Google Cloud, AWS Polly, and a constrained
  custom PCM provider. Google accepts a restricted API key or proxy. AWS accepts
  direct SigV4 credentials, Cognito temporary credentials, or a proxy.

Paid cloud providers are never inserted as implicit fallbacks. Add them to a
language route explicitly. API keys are encrypted with Android Keystore and are
excluded from backup and device transfer.

## Build and install

```sh
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk
export ANDROID_HOME=$HOME/Android/Sdk
unset ANDROID_SDK_ROOT
./gradlew testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Select UtterMux under Android's text-to-speech settings, then open UtterMux to
install a local voice or configure a cloud provider. Do not use Gradle's generic
`connectedDebugAndroidTest` task against a phone whose configured app data must
be preserved; install the test APK and invoke selected instrumentation tests
directly instead.

UtterMux deliberately ships with **no voice or model in the APK**. The first-run
catalog therefore has no ready offline voice until one is downloaded. This keeps
the engine small and makes every model/license choice explicit.

## Adaptive streaming

UtterMux prepares a route before starting, tells Android the fixed output format
(24 kHz, mono, signed PCM16) immediately, and emits audio as soon as its provider
can produce it. Local VITS/Piper text is split on semantic boundaries and the
engine is retained in a small LRU cache. Generation, decoding, and playback are
independent. Direct playback, including KOReader, uses a PCM-duration-bounded
producer/consumer queue whose startup reserve adapts to measured real-time
factor and underruns, including changes caused by thermal throttling.

A route may fall back only before it has emitted audio. This avoids switching
voices in the middle of an utterance. Language routes use exact BCP-47 matches,
then base-language matches, then the global default and local-only safe
fallbacks.

## KOReader

Enable the compatibility server in UtterMux and install `TTS.koplugin` under
`/sdcard/koreader/plugins/`. The server binds only IPv4 loopback at
`127.0.0.1:5000` and implements `/voices`, `/`, `/play`, `/stop`, `/remaining`,
and `/health`. Synthesis and playback are concurrent, so KOReader no longer
waits for a complete passage before hearing audio.

## Model policy

Only runnable voices appear in the voice catalog; research and compatibility
notes are documentation, not dead UI rows. Local models are optional downloads.
Pocket reuses one runtime/model with per-voice licensed reference WAV files.
MOSS-TTS-Nano downloads approximately 760 MB and uses its official ONNX graphs,
a native-compatible SentencePiece implementation, incremental token generation,
and overlapping codec decoding. MOSS remains a heavy option and needs acceptance
testing on each device class before sustained document reading.

Qwen, Audio8, Chatterbox, NeuTTS, LEMAS, X-Voice, and OmniVoice are intentionally
not advertised in the app until an arm64 runtime passes system-TTS, cancellation,
memory, and sustained-speed acceptance tests. Voice-cloning import/recording UX
is likewise deferred.

## Cloud credentials and proxy contract

Google Cloud can use a Google API key restricted to the Text-to-Speech API and
the Android app. AWS can sign Polly requests locally with a least-privilege IAM
key, obtain temporary credentials from a Cognito identity pool, or use a proxy.
Direct secrets are encrypted with Android Keystore and excluded from backup.
The settings screen can copy the minimal Polly IAM policy. Cognito avoids a
permanent AWS secret on the phone and is preferred for a client-only deployment.

A compatible proxy exposes:

- `GET /v1/voices`: JSON array containing at least `id` and `language`.
- `POST /v1/synthesize`: JSON request containing voice, language, and text;
  response body is 24 kHz mono PCM16.

The custom endpoint is deliberately constrained to this PCM contract. The
OpenAI-compatible provider separately supports a configurable base URL and
model for APIs implementing OpenAI's speech endpoint.

## Verification

The release test suite covers exact segmentation/ranges, PCM conversion and
silence trimming, adaptive buffering policy, routing, model management, MOSS
tokenization, and system-TTS compatibility. On the development
Samsung SM-G970F, Alan Low produced first audio in about 2.1 seconds cold and
0.33 seconds warm while completing through Android's system TTS callback.

Opt-in large-model tests also download, checksum, initialize, synthesize, and
complete through Android's real `TextToSpeech` API. On that SM-G970F, Pocket
occupies 198 MB and directly generated 3.76 seconds of audio in 4.51 seconds;
its cold system request reached first audio in 3.14 seconds. MOSS occupies
684 MB and generated 3.2 seconds in 9.81 seconds; its cold system request took
7.35 seconds to first audio and 11.94 seconds overall. MOSS is therefore
functional on this phone but not suitable for uninterrupted live reading;
Pocket is much closer to usable real time. These tests are excluded from the
ordinary suite because they consume substantial bandwidth, storage, and time.

The project is GPL-3.0-or-later. The pinned sherpa-onnx JNI wrapper and native
libraries are Apache-2.0 components from k2-fsa; individual voice/model licenses
are displayed in the catalog.
