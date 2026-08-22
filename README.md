# UtterMux for Android

<img src="docs/assets/uttermux.svg" width="112" alt="UtterMux jellyfish waveform logo">

[![Android CI](https://github.com/anaxonda/uttermux-android/actions/workflows/android.yml/badge.svg)](https://github.com/anaxonda/uttermux-android/actions/workflows/android.yml)

UtterMux is an Android system text-to-speech engine and voice manager. Local
models and online providers share one `TextToSpeechService`, voice catalog,
default voice, and language-routing policy.

> **Status:** beta. The APK includes eSpeak NG but no neural model weights.
> Neural models are downloaded only on request.

| Voices | Filters | Create | Settings |
| --- | --- | --- | --- |
| <img src="docs/screenshots/android-voices.png" width="240" alt="Voice catalog"> | <img src="docs/screenshots/android-filters.png" width="240" alt="Voice filters"> | <img src="docs/screenshots/android-create.png" width="240" alt="Voice creation"> | <img src="docs/screenshots/android-settings.png" width="240" alt="Settings"> |

The companion [Linux Speech Dispatcher broker](https://github.com/anaxonda/uttermux-linux)
uses the same catalog schema and routing model.

## Capabilities

- Android system TTS integration with exact text ranges, cancellation, and
  incremental PCM delivery.
- Searchable voice catalog with dedicated filter screens, favorites, previews,
  downloads, installed sizes, and model removal.
- BCP-47 language metadata, automatic detection, ordered language routes, and
  configurable fallbacks.
- Adaptive buffering, bounded local-model caching, and per-artifact benchmarks
  and settings.
- Pocket reference profiles and a gated Qwen cloning preview.
- Embedded multilingual eSpeak NG fallback.
- Optional localhost or trusted-LAN PCM adapter for legacy clients.

API keys are encrypted with Android Keystore and excluded from backup. Paid
providers are never added to fallback routes automatically.

## App layout

- **Voices** searches, filters, previews, favorites, downloads, and selects the
  active voice. Filters persist while navigating within the running app.
- **Create** records or imports permitted reference audio and manages private
  voice profiles.
- **Test** previews and benchmarks exact installed artifacts. Results include
  first-audio latency, real-time factor, memory, and thermal state; changes are
  applied only after confirmation.
- **Settings** contains system integration, online providers, language routes,
  downloaded models, advanced playback controls, and diagnostics.

## Local engines

Every Android entry below has a synthesis path in the released catalog. A
hardware label is advisory; use **Test** on the target device before continuous
reading.

| Family | Android support | Linux support |
| --- | --- | --- |
| eSpeak NG | Embedded; 100+ languages/accents | System engine |
| Piper/VITS | 174 pinned packages; 2,707 choices | Generated pinned catalog |
| Inflect | Nano and Micro | Nano |
| Kitten | FP16 v0.1; INT8 v0.8 | FP16 v0.1; INT8 v0.8 |
| Matcha | LJSpeech + Vocos | LJSpeech + Vocos |
| Supertonic 3 | INT8 | INT8 |
| Pocket | Presets and profiles | Presets and profiles |
| Kokoro | v1.0 and v1.1 FP32 | v1.0 FP32 |
| MOSS-TTS-Nano | Explicit heavy FP32 download | FP32 companion adapter |
| Qwen3-TTS 0.6B | Q4_K_M device preview | CustomVoice companion adapter |
| ZipVoice Distill | Not released | INT8 profile |

The Android catalog also exposes all 53 Kokoro v1.0 speakers, 103 Kokoro v1.1
speakers, eight Kitten speakers, ten Pocket references, and the full verified
Piper speaker index. Kokoro/Kitten sample previews are available before download.

Kokoro INT8 is excluded because the tested ARM path produced audio corruption
and performance regressions. Other unlisted variants and candidate families do
not appear as dead rows: they require a maintained arm64 runtime plus model,
memory, cancellation, and reader tests. Exact artifacts and candidate status are
documented by the shared
[generated model index](https://github.com/anaxonda/uttermux-linux/blob/main/docs/MODELS.generated.md)
and [runtime candidate notes](https://github.com/anaxonda/uttermux-linux/blob/main/docs/runtime-candidates.md).

## Online services

| Provider | Authentication |
| --- | --- |
| Microsoft Edge Read Aloud | none; unofficial endpoint |
| ElevenLabs, xAI/Grok, Deepgram, Cartesia | API key |
| OpenAI-compatible | API key, endpoint, model |
| Azure Speech | resource key and region/endpoint |
| Qwen/DashScope | API key, region, optional workspace |
| Google Cloud TTS | restricted API key or proxy |
| Amazon Polly | SigV4, Cognito temporary credentials, or proxy |
| PlayHT, Resemble | provider credentials |
| Custom PCM | HTTPS endpoint and bearer token |

Where supported, voices are discovered from the provider at runtime. The
cross-platform request and authentication contracts are in
[cloud-providers.md](https://github.com/anaxonda/uttermux-linux/blob/main/docs/cloud-providers.md).

## Build and install

Requirements: JDK 17, Android SDK, NDK, and an arm64 Android device or emulator.

```sh
git clone --recurse-submodules https://github.com/anaxonda/uttermux-android
cd uttermux-android
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk
export ANDROID_HOME=$HOME/Android/Sdk
unset ANDROID_SDK_ROOT
./gradlew testIsolatedHostUnitTest assembleDebug lintDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Select UtterMux in Android's text-to-speech settings, then open the app to
choose eSpeak, download a neural model, or configure an online provider.

Device tests run against the separate `io.uttermux.android.testhost` package;
the runner refuses to target the live app:

```sh
scripts/device-test.sh
scripts/device-test.sh io.uttermux.android.PiperPreviewTest
```

## Runtime behavior

UtterMux prepares a route before starting a request, announces a fixed 24 kHz
mono PCM16 format, and emits audio as soon as the provider produces it. Local
generation, decoding, and delivery use bounded queues. Startup reserve adapts to
measured throughput and underruns. A route can fall back only before audio has
started, preventing mixed voices within one highlighted utterance.

Benchmark profiles are bound to both the catalog artifact and app runtime.
Manual per-model values override a valid profile, which overrides global and
automatic defaults. Model precision and quality variants retain independent
results. Benchmarks measure performance, not pronunciation or voice quality.

The 2019 Galaxy S10 development device is a low-spec acceptance target, not a
universal requirement. Piper, Inflect Nano, and Kitten meet real-time generation
there; Pocket is usable with conservative settings; Kokoro is marginal for
continuous reading; MOSS and Qwen are exposed for evaluation on faster hardware.
Detailed measurements remain in test logs and the Linux project's model-specific
benchmark documents.

## Compatibility adapter

Standard Android applications use `TextToSpeechService` and need no adapter.
The optional PCM server supports integrations using the legacy HTTP protocol:

- Loopback mode binds `127.0.0.1:5000`.
- **Allow hotspot/LAN clients** binds the phone's network interfaces so another
  device can submit text over Wi-Fi or a personal hotspot.
- Audio uses the phone's selected output, including a connected Bluetooth
  headset or speaker. Bluetooth is not the client transport.

LAN mode has no application-layer authentication. Enable it only on a trusted
network and disable it afterward. The maintained compatibility patch and
protocol details are in [`koreader/README.md`](koreader/README.md).

## Catalog and release policy

The reviewed catalog source and deterministic generator live in
[`uttermux-linux`](https://github.com/anaxonda/uttermux-linux/tree/main/catalog).
Android commits a generated catalog plus `catalog.lock.json`, which records the
source commit and checksum. CI validates it offline; a scheduled workflow opens
a reviewable synchronization pull request when the source changes. Builds never
download a mutable catalog.

GitHub tags build an arm64 APK after unit, lint, provenance, and payload audits.
F-Droid submission remains blocked until its recipe rebuilds the pinned
sherpa-onnx JNI and ONNX Runtime libraries from source. See
[`docs/F-DROID.md`](docs/F-DROID.md).

## Related work

UtterMux uses [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) for its common
local runtime. Design and deployment references include
[HayaiTTS](https://github.com/HayaiApp/HayaiTTS),
[NekoSpeak](https://github.com/siva-sub/NekoSpeak),
[Read Aloud](https://github.com/ken107/read-aloud), and
[qwen3-tts.cpp](https://github.com/Danmoreng/qwen3-tts.cpp).

## License

UtterMux is GPL-3.0-or-later. Models, services, and packaged dependencies retain
their own licenses and terms.
