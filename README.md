# UtterMux for Android

[![Android CI](https://github.com/anaxonda/uttermux-android/actions/workflows/android.yml/badge.svg)](https://github.com/anaxonda/uttermux-android/actions/workflows/android.yml)

UtterMux is an Android system text-to-speech engine and voice manager. It gives
Android readers one interface for local ONNX models and online providers, and
also implements the loopback protocol used by KOReader's `TTS.koplugin`.

> **Status:** beta. No model weights are bundled. Local artifacts are downloaded
> only after the user selects Install.

| Voices and filters | Voice creation | Settings and providers |
| --- | --- | --- |
| <img src="docs/screenshots/android-voices.png" width="280" alt="UtterMux voice filters and active voice"> | <img src="docs/screenshots/android-create.png" width="280" alt="UtterMux Pocket voice creation"> | <img src="docs/screenshots/android-settings.png" width="280" alt="UtterMux settings and provider list"> |

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
- **Create** records or imports a permitted reference sample for Pocket or the
  Qwen device-preview runtime, previews source and generated audio, and manages
  engine-specific private profiles.
- **Settings** contains general integration, individually expandable online
  service cards, language routing, downloaded-model storage, explained advanced
  playback controls, diagnostics, and privacy/version information.

Filters and list position survive tab changes and rotation but intentionally
start clean after a complete process relaunch.

## Models available in the Android app

Every row below appears in the Android voice catalog and has an implemented
download and synthesis path. No model is bundled. Sizes and RAM are catalog
metadata and can change when upstream artifacts change; they are not universal
hardware requirements.

### Cross-platform local support

“Yes” means the released app exposes an install and synthesis path. “Profile”
means a reference recording must be configured before a system voice exists.

| Family | Android | Linux | Current boundary |
| --- | --- | --- | --- |
| Piper/VITS | Yes; dynamic upstream catalog | Yes; Lessac medium in the built-in catalog | Fixed voices |
| Inflect Nano/Micro | Nano and Micro | Nano | Fixed English voices |
| Kitten | FP16 v0.1 and INT8 v0.8 | FP16 v0.1 and INT8 v0.8 | Fixed English voices |
| Matcha | Yes | Yes | LJSpeech + Vocos artifact |
| Supertonic 3 | INT8 | INT8 | Multilingual styles |
| Pocket | Yes; presets and profiles | Yes; presets and profiles | Reference-conditioned cloning |
| Kokoro | v1.0 and v1.1 FP32 | v1.0 FP32 | INT8 and FP8 are not included |
| ZipVoice Distill | No | Profile; INT8 | Linux requires reference audio and transcript |
| MOSS-TTS-Nano | No | Companion adapter; FP32 | Android evaluation failed sustained-reader acceptance |
| Qwen3-TTS 0.6B | Base Q4_K_M device preview; profiles | Companion adapter; CustomVoice | Separate runtimes sharing catalog metadata |

| Concrete artifact | Languages / voices | Clone | Download | Precision | Est. RAM | 2019 SM-G970F result | Upstream |
| --- | --- | ---: | ---: | --- | ---: | --- | --- |
| Piper/VITS dynamic index | 174 packages; 2,707 choices; 50+ languages | No | package-specific | FP32/INT8 by package | package-specific | Tested; Alan Low is the continuity baseline | [Piper](https://github.com/OHF-Voice/piper1-gpl) |
| `vits-inflect-en-nano-v2` | English; 1 | No | 17 MiB | FP32 | 80 MiB | Runnable; no isolated timing recorded | [Inflect Nano](https://huggingface.co/owensong/Inflect-Nano-v2) |
| `vits-inflect-en-micro-v2` | English; 1 | No | 43 MiB | FP32 | 120 MiB | Runnable; no isolated timing recorded | [Inflect Micro](https://huggingface.co/owensong/Inflect-Micro-v2) |
| `kitten-nano-en-v0_1-fp16` | English; 1 | No | 26 MiB | FP16 | 120 MiB | Runnable; no isolated timing recorded | [KittenTTS](https://github.com/KittenML/KittenTTS) |
| `kitten-nano-en-v0_8-int8` | English; 8 | No | 31 MiB | INT8 | 120 MiB | RTF ~0.80 | [KittenTTS](https://github.com/KittenML/KittenTTS) |
| `matcha-icefall-en_US-ljspeech` | English; 1 | No | 77 MiB | FP32 | 260 MiB | Runnable; sustained reader test pending | [Matcha-TTS](https://github.com/shivammehta25/Matcha-TTS) |
| `sherpa-onnx-supertonic-3-tts-int8-2026-05-11` | 31 languages; 10 styles | No | 129 MiB | INT8 | 350 MiB | Runnable; sustained reader test pending | [Supertonic](https://github.com/supertone-inc/supertonic) |
| `sherpa-onnx-pocket-tts-int8-2026-01-26` | English; 10 references + profiles | Yes | 176 MiB | INT8 | 420 MiB | Warm first PCM ~243–262 ms; client boundaries audible | [Pocket TTS](https://github.com/kyutai-labs/pocket-tts) |
| `kokoro-multi-lang-v1_0` | English/Chinese runtime; 53 speakers | No | 350 MiB | FP32 | 650 MiB | RTF ~1.91; not continuous-reader speed | [Kokoro](https://k2-fsa.github.io/sherpa/onnx/tts/pretrained_models/kokoro.html) |
| `kokoro-multi-lang-v1_1` | English/Chinese; 103 speakers | No | 348 MiB | FP32 | 700 MiB | Runnable; same heavy tier, not separately timed | [Kokoro v1.1](https://k2-fsa.github.io/sherpa/onnx/tts/all/Chinese-English/kokoro-multi-lang-v1_1.html) |
| `qwen3-tts-0.6b-base-q4km` | 10 languages; user profiles | Yes | 843 MiB | Q4_K_M GGUF | 3 GiB | Device preview; sustained-reader benchmark required | [Qwen3-TTS](https://github.com/QwenLM/Qwen3-TTS) / [qwen3-tts.cpp](https://github.com/Danmoreng/qwen3-tts.cpp) |

Kokoro v1.1 INT8 exists upstream but is not included because the tested
Android/ARM path produced intermittent rail-pinned audio and regressions.
UtterMux has no tested Kokoro FP8 artifact or FP8 Android runtime configuration.
This does not imply that FP8 is impossible on other devices or execution
providers. A model variant enters the app only after the artifact and runtime
pass synthesis and reader tests together.

## Evaluated models not included in the Android app

The models in this section are **not supported by this release**: they do not
appear in search, cannot be downloaded by the app, and cannot be selected as an
Android system voice. The table records why they were evaluated and what would
be required before adding them. Passing initialization alone is insufficient;
an arm64 implementation must pass exact system-TTS ranges, cancellation, peak
memory, sustained RTF, and multi-client reader tests.

| Model | Main value | Available deployment path | Evaluation hardware | UtterMux status |
| --- | --- | --- | --- | --- |
| [MOSS-TTS-Nano](https://github.com/OpenMOSS/MOSS-TTS-Nano) | 20-language cloning/streaming | Official ONNX Runtime Android example | SM-G970F measured | Rejected: sustained RTF ~1.41–1.47 and audible reader pauses |
| [ZipVoice Distill](https://github.com/k2-fsa/ZipVoice) | English/Chinese zero-shot cloning | sherpa-onnx Android | recent midrange or faster | Not integrated; system voice requires reference audio and exact transcript |
| [Chatterbox Nano](https://huggingface.co/ResembleAI/chatterbox-nano) | English cloning | PyTorch upstream; community ONNX work | current flagship | No accepted maintained arm64 runtime |
| [NeuTTS Nano](https://github.com/neuphonic/neutts) | multilingual per-model cloning | GGUF backbone + ONNX codec | current flagship | Custom runtime and reader acceptance tests required |
| [Audio8 0.6B INT4](https://github.com/Audio8-AI/Audio8_TTS) | multilingual cloning/streaming | official CPU ONNX package | 6–8 GiB current flagship | Android ARM benchmark and service integration required |
| [LEMAS-TTS](https://github.com/LEMAS-Project/LEMAS-TTS) | multilingual cloning | ONNX assets; limited mobile integration | current flagship | No accepted Android runtime |
| [X-Voice](https://github.com/sunnyxrxrx/X-Voice) | cross-lingual cloning | PyTorch-oriented | current flagship | Checkpoint is CC-BY-NC-4.0; not a normal distributable app model |
| [OmniVoice](https://github.com/k2-fsa/OmniVoice) | 600+ language zero-shot synthesis | no accepted Android deployment | unassigned | No accepted Android runtime |

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

## Shared catalog

The reviewed catalog source and deterministic generator live in
[`uttermux-linux`](https://github.com/anaxonda/uttermux-linux/tree/main/catalog).
This repository commits the generated schema-2 JSON used by Android builds.
Families, runnable variants, voices, and artifacts are separate records, so
Linux and Android can use different runtimes for one model family without
presenting unsupported rows on either platform. The build validates the schema
and requires the pinned Android Qwen device-preview variant.

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

Only models in **Models available in the Android app** appear in the voice
catalog. Entries under **Evaluated models not included** are documentation only.
Local model downloads require an explicit install action.
Pocket reuses one runtime/model with cached reference WAV files. Its 3/4/5-step
quality selector trades generation latency for refinement; three steps is the
measured low-latency default. Kokoro uses the supported FP32 graph: the available
INT8 export is intentionally hidden because current ARM reports include rail-pinned
audio, tones, and performance regressions. MOSS and ZipVoice are intentionally
excluded from this release. MOSS did not meet sustained document-reading latency;
ZipVoice requires a reference recording plus transcript and has no preset-voice
catalog suitable for the current system-TTS UX.

Qwen Base Q4_K_M is visible as a **device preview**: its pinned arm64 runtime,
download, cancellation, streaming callback, and profile path are implemented,
but it is excluded from automatic fallback until sustained-reader benchmarks
establish suitable hardware. Audio8, Chatterbox, NeuTTS, LEMAS, X-Voice, and
OmniVoice remain documentation-only candidates.

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

The reference phone is a 2019 Samsung Galaxy S10 SM-G970F (Exynos 9820,
Android 12, about 5.5 GiB usable RAM). It is a low-spec acceptance target by
2026 standards, not a representative current flagship. RTF is generation time
divided by generated audio duration; below 1.0 is faster than realtime. These
are local engineering measurements, not upstream claims.

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

Run the argument-driven benchmark against any installed voice:

```sh
adb shell am instrument -w \
  -e class io.uttermux.android.ModelBenchmarkTest#benchmarkInstalledVoice \
  -e voice sherpa/sherpa-onnx-pocket-tts-int8-2026-01-26/alba-casual@en-US \
  -e runs 3 \
  io.uttermux.android.test/androidx.test.runner.AndroidJUnitRunner
adb logcat -d -s UtterMuxBenchmark:I
```

The JSON log reports device/SoC, voice ID, model, wall time, generated duration,
RTF, and process PSS for every run. It excludes playback. Run 1 includes model
loading only if the test process did not already cache that model. It does not
control thermal state, CPU governor, charging state, or background load.

### Newer-phone model scope

Kokoro FP32 is already functionally compatible with the reference phone; its
measured RTF is the blocker. A recent high-performance ARM phone may make it a
continuous-reading choice, but UtterMux does not infer that from RAM or core
count alone. Qwen3-TTS 0.6B, Audio8 0.6B INT4, ZipVoice, and other heavyweight
families require a maintained arm64 runtime plus measured cold/warm latency,
sustained RTF, peak PSS, cancellation, and multi-client tests before they can
enter the runnable catalog. A hardware label will remain advisory and will not
download or hide models.

## Related work

- [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) provides the common JNI
  runtime and local model exports.
- [HayaiTTS](https://github.com/HayaiApp/HayaiTTS) is a similar offline Android
  system engine with a broad sherpa-onnx catalog and per-speaker samples.
- [NekoSpeak](https://github.com/siva-sub/NekoSpeak) informed the adaptive
  producer/consumer PCM pipeline and underrun handling.
- [Read Aloud](https://github.com/ken107/read-aloud) informed cloud-provider
  discovery, authentication, and proxy tradeoffs.
- [KOReader](https://github.com/koreader/koreader) is supported both through
  Android system TTS and the loopback compatibility protocol.
- [qwen3-tts-android](https://github.com/Danmoreng/qwen3-tts-android) is a
  reference for local quantized Qwen deployment; UtterMux does not embed it.

The project is GPL-3.0-or-later. The pinned sherpa-onnx JNI wrapper and native
libraries are Apache-2.0 components from k2-fsa; individual voice/model licenses
are displayed in the catalog.
