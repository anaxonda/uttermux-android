# UtterMux for Android

[![Android CI](https://github.com/anaxonda/uttermux-android/actions/workflows/android.yml/badge.svg)](https://github.com/anaxonda/uttermux-android/actions/workflows/android.yml)

UtterMux is an Android system text-to-speech engine and voice manager. It gives
Android readers one interface for local ONNX models and online providers, and
also implements the loopback protocol used by KOReader's `TTS.koplugin`.

## What works

- Android `TextToSpeechService` integration for Feeder, Librera, KOReader, and
  other clients using the standard system API.
- Early `onStart`, adaptively reserved incremental PCM delivery, exact text
  ranges, cancellation, engine warming, segmented Piper synthesis, and bounded
  silence trimming.
- A pre-indexed, debounced voice catalog with dependent voice-library and
  model/version searches plus independent voice, language, and accent searches;
  availability, location, capability, cost, performance, gender, size, and
  speed controls; one-tap clearing; exact-voice previews;
  ordered BCP-47 fallback routes, model downloads, settings, and diagnostics.
- Full Piper catalog (174 models and 2,707 speaker choices), all 53 Kokoro 1.0
  speakers, the 103-speaker Kokoro 1.1 FP32 option, all eight Kitten speakers,
  Inflect, Matcha, and Supertonic.
  Kokoro and Kitten can be auditioned before downloading via Hayai's per-speaker
  sample catalog. Pocket includes ten reference voices and can create private
  profiles from imported audio or an eight-second microphone recording.
- Edge, ElevenLabs, Grok/xAI, OpenAI-compatible, Azure, Qwen/DashScope,
  Deepgram, Cartesia, PlayHT, Resemble, Google Cloud, AWS Polly, and a constrained
  custom PCM provider. Google accepts a restricted API key or proxy. AWS accepts
  direct SigV4 credentials, Cognito temporary credentials, or a proxy.

Paid cloud providers are never inserted as implicit fallbacks. Add them to a
language route explicitly. API keys are encrypted with Android Keystore and are
excluded from backup and device transfer.

## App navigation

- **Voices** searches voice name, language, library, model/version, and accent;
  filters local/cloud readiness and capability; installs, previews, and selects
  the default voice. Searchable pickers open their list on the first tap and the
  keyboard only on a second tap or the search icon.
- **Create** records or imports a permitted reference sample for Pocket, lets
  you preview both the source and generated voice, and manages private profiles.
- **Settings** contains general integration, individually expandable online
  service cards, language routing, downloaded-model storage, explained advanced
  playback controls, diagnostics, and privacy/version information.

Filters and list position survive tab changes and rotation but intentionally
start clean after a complete process relaunch.

## Supported local models

No model is bundled. Sizes and RAM are approximate and can change when upstream
artifacts change; the catalog is the download source of truth.

| Library / variant | Languages / voices | Clone | Download | Quantization | Approx. RAM | SM-G970F assessment | Upstream |
| --- | --- | ---: | ---: | --- | ---: | --- | --- |
| Piper / VITS | 174 packages, 2,707 speaker choices, 50+ languages | No | varies | ONNX | varies | **Recommended**; best continuity baseline | [Piper](https://github.com/rhasspy/piper) |
| Inflect Nano v2 | English, fixed voice | No | ~17 MB | FP32 | ~80 MB | Likely excellent; not separately benchmarked | [Inflect Nano](https://huggingface.co/owensong/Inflect-Nano-v2) |
| Inflect Micro v2 | English, fixed voice | No | ~43 MB | FP32 | ~120 MB | Likely excellent; not separately benchmarked | [Inflect Micro](https://huggingface.co/owensong/Inflect-Micro-v2) |
| Matcha LJSpeech | English, one voice | No | ~77 MB | FP32 | ~260 MB | Expected usable; sustained test pending | [sherpa-onnx TTS](https://github.com/k2-fsa/sherpa-onnx) |
| Kitten Nano 0.8 | English, eight voices | No | ~31 MB | INT8 | ~120 MB | **Recommended**; measured faster than realtime | [KittenTTS](https://github.com/KittenML/KittenTTS) |
| Kokoro 1.0 / 1.1 | multilingual, 53 / 103 speakers | No | ~348–350 MB | FP32 | ~650–700 MB | Works, but too slow for seamless reading | [sherpa-onnx Kokoro](https://k2-fsa.github.io/sherpa/onnx/tts/) |
| Pocket TTS | English, ten references plus private profiles | **Yes** | ~176 MB | INT8 | ~420 MB | Usable with reader-dependent section gaps | [Pocket TTS](https://github.com/kyutai-labs/pocket-tts) |
| Supertonic 3 | 31 languages, ten styles | No | ~129 MB | INT8 | ~350 MB | Expected usable; sustained test pending | [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) |

Kokoro INT8 is not exposed because the current Android/ARM export has produced
intermittent rail-pinned audio and regressions; the FP32 graph is the supported
variant. A model is not promoted merely because its runtime can initialize.

## Candidate and rejected local models

These entries are documentation, not dead rows in the app. “Candidate” means an
arm64 implementation still has to pass exact system-TTS ranges, cancellation,
memory, sustained RTF, and multi-client reader tests.

| Model | Main value | Runtime position | Likely phone tier | Current decision |
| --- | --- | --- | --- | --- |
| MOSS-TTS-Nano | multilingual cloning/streaming | Official ONNX path exists | mid/high | **Rejected for now:** measured sustained latency and pauses were not usable |
| ZipVoice Distill | English/Chinese zero-shot cloning | Supported by sherpa-onnx | mid/high | Candidate; requires reference audio and transcript, with no suitable preset catalog yet |
| Chatterbox Nano | English cloning | Mobile ONNX path is not yet accepted here | high | Candidate after a maintained arm64 runtime |
| NeuTTS Nano | multilingual per-model cloning | GGUF backbone plus ONNX codec | high | Candidate; custom runtime and sustained tests required |
| Qwen3-TTS 0.6B | built-in voices or cloning | Local Android ports exist outside UtterMux | 6–8 GB preferred | Deferred; large runtime and memory acceptance work |
| Audio8 0.6B INT4 | multilingual cloning and streaming | Official ONNX package, Android unproven here | high | Candidate after ARM benchmarks |
| LEMAS-TTS | multilingual cloning | weak mobile ecosystem | high | Research only |
| X-Voice | cross-lingual cloning | PyTorch-oriented and noncommercial checkpoint | high | Not distributable as a normal app model |
| OmniVoice | very broad language ambition | no accepted Android deployment | high | Research only |

## Online services

| Service | Authentication | Voice discovery / preview |
| --- | --- | --- |
| Edge Read Aloud | none | live locale catalog; unofficial endpoint |
| ElevenLabs | API key | account voice catalog; metered preview |
| xAI / Grok | API key | provider voices; metered preview |
| OpenAI-compatible | API key, endpoint, model | configured endpoint |
| Azure Speech | resource key and region/endpoint | Azure voice catalog |
| Qwen / DashScope | API key, region, optional workspace | DashScope voices |
| Google Cloud TTS | restricted API key or proxy | Google voice catalog |
| Amazon Polly | SigV4, Cognito temporary credentials, or proxy | Polly voice catalog |
| Deepgram, Cartesia, PlayHT, Resemble | provider credentials | provider-specific catalog |
| Custom PCM | HTTPS endpoint and bearer token | configured voice ID |

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
`/pause`, `/resume`, and `/health`. Synthesis and playback are concurrent, so
KOReader no longer
waits for a complete passage before hearing audio.

## Model policy

Only runnable voices appear in the voice catalog; research and compatibility
notes are documentation, not dead UI rows. Local models are optional downloads.
Pocket reuses one runtime/model with cached reference WAV files. Its 3/4/5-step
quality selector trades generation latency for refinement; three steps is the
measured low-latency default. Kokoro uses the supported FP32 graph: the available
INT8 export is intentionally hidden because current ARM reports include rail-pinned
audio, tones, and performance regressions. MOSS and ZipVoice are intentionally
excluded from this release. MOSS did not meet sustained document-reading latency;
ZipVoice requires a reference recording plus transcript and has no preset-voice
catalog suitable for the current system-TTS UX.

Qwen, Audio8, Chatterbox, NeuTTS, LEMAS, X-Voice, and OmniVoice are intentionally
not advertised in the app until an arm64 runtime passes system-TTS, cancellation,
memory, and sustained-speed acceptance tests.

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
silence trimming, adaptive buffering policy, routing, model management, text
normalization, unsafe-output rejection, and system-TTS compatibility. On the development
Samsung SM-G970F, Alan Low produced first audio in about 2.1 seconds cold and
0.33 seconds warm while completing through Android's system TTS callback.

### Galaxy S10 development benchmark

The development phone is an SM-G970F (Exynos 9820, Android 12, about 5.5 GB
usable RAM). RTF is generation time divided by generated audio duration; below
1.0 is faster than realtime. These are engineering measurements, not upstream
claims.

| Model | Measurement | Storage / process observation | Reader conclusion |
| --- | --- | --- | --- |
| Piper Alan Low | ~2.1 s first audio cold; ~0.33 s warm | model-dependent | Best tested continuity |
| Kitten Nano INT8 | ~2.95 s generation for ~3.70 s audio, RTF ~0.80 | ~45 MB installed in the tested package | Realtime-capable |
| Pocket INT8, 3 steps | cold RTF ~1.35; warm first PCM ~243–262 ms | ~201 MB installed; Pocket-loaded process ~598 MB PSS | Works, but client request boundaries can remain audible |
| Kokoro FP32 | ~6.06 s generation for ~3.16 s audio, RTF ~1.91 | ~408 MB installed | Too slow for seamless document reading on this phone |
| MOSS INT8 | sustained RTF ~1.41–1.47 | test artifact removed | Rejected for this release |

Measurements use short fixed passages after a clean install and again with a
warm engine. Sustained reader acceptance also requires repeated section
requests, cancellation, pause/resume, and thermal observation; a good first-PCM
number alone is not sufficient.

Opt-in large-model tests also download, checksum, initialize, synthesize, and
exercise repeated provider requests. On that SM-G970F, progressive warm Pocket
at three steps produced first PCM in about 243–262 ms with no callback deficit
in repeated short sections. Because readers
such as Librera submit the next section only after the previous Android TTS
request completes, that per-request generation time still becomes an audible
section gap; UtterMux cannot pre-generate text the client has not supplied.
A KOReader regression test now plays the same Pocket section twice
and waits for AudioTrack's actual playback head, covering stale-stream reuse and
clipped section tails. These tests are excluded from the ordinary suite because
they consume substantial bandwidth, storage, and time.

The project is GPL-3.0-or-later. The pinned sherpa-onnx JNI wrapper and native
libraries are Apache-2.0 components from k2-fsa; individual voice/model licenses
are displayed in the catalog.
