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
- Searchable voice catalog, voice previews, language/provider/model filters,
  ordered BCP-47 fallback routes, model downloads, settings, and diagnostics.
- Full Piper catalog (174 models and 2,707 speaker choices), plus runnable
  Kokoro, Kitten, Inflect, Matcha, and Supertonic definitions. Pocket and
  ZipVoice installers are present for the future voice-profile workflow.
- Edge, ElevenLabs, Grok/xAI, OpenAI-compatible, Azure, Qwen/DashScope,
  Deepgram, Cartesia, PlayHT, Resemble, Google proxy, AWS proxy, and a constrained
  custom PCM provider.

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

## Adaptive streaming

UtterMux prepares a route before starting, tells Android the fixed output format
(24 kHz, mono, signed PCM16) immediately, and emits audio as soon as its provider
can produce it. Local VITS/Piper text is split on semantic boundaries and the
engine is retained in a small LRU cache. Direct playback, including KOReader,
uses a producer/consumer queue whose startup reserve adapts to measured
real-time factor and underruns. Low-latency, balanced, smooth, and manual
profiles are available.

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

## Model status and honesty

The Models page distinguishes runnable, downloadable, experimental, preview-
only, benchmark, blocked, and incompatible entries. Catalog visibility does not
imply that a runtime exists.

In particular, MOSS-TTS-Nano is cataloged but blocked: its official Android ONNX
example accepts pre-tokenized prompts and omits the production SentencePiece
path required for arbitrary Android text. Qwen, Audio8, Chatterbox, NeuTTS,
LEMAS, X-Voice, and OmniVoice are retained as research/benchmark entries until
their Android inference paths meet the same system-TTS requirements. Voice
cloning data structures are planned, but recording/import UX is intentionally
not exposed yet.

## Proxy contract

Google and AWS use a user-operated proxy rather than storing long-lived cloud
service credentials on the phone. A compatible proxy exposes:

- `GET /v1/voices`: JSON array containing at least `id` and `language`.
- `POST /v1/synthesize`: JSON request containing voice, language, and text;
  response body is 24 kHz mono PCM16.

The custom endpoint is deliberately constrained to this PCM contract. The
OpenAI-compatible provider separately supports a configurable base URL and
model for APIs implementing OpenAI's speech endpoint.

## Verification

The release test suite covers exact segmentation/ranges, PCM conversion and
silence trimming, adaptive buffering policy, routing, catalog statuses, system
TTS compatibility, and live Edge discovery/synthesis. On the development
Samsung SM-G970F, Alan Low produced first audio in about 2.1 seconds cold and
0.33 seconds warm while completing through Android's system TTS callback.

The project is GPL-3.0-or-later. The pinned sherpa-onnx JNI wrapper and native
libraries are Apache-2.0 components from k2-fsa; individual voice/model licenses
are displayed in the catalog.
