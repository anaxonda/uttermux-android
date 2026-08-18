# UtterMux for Android

UtterMux is an Android system text-to-speech engine and voice manager for local
Sherpa-ONNX models and online providers. It also implements the loopback API
used by KOReader's `TTS.koplugin`.

## Build

```sh
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk
export ANDROID_HOME=$HOME/Android/Sdk
unset ANDROID_SDK_ROOT
./gradlew testDebugUnitTest assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Select UtterMux in Android's text-to-speech settings. Provider credentials are
encrypted with Android Keystore and are never included in backups. Local models
are downloaded on demand and verified against the pinned SHA-256 catalog.

The manager searches voice names, accents, descriptions, locales, models, and
providers. Provider, language, and model filters can be combined. Its theme can
follow Android or be forced light/dark. Voice previews use provider preview
audio when available; installed local models are previewed through Sherpa.

For KOReader, enable the compatibility server in UtterMux and install
`TTS.koplugin` under `/sdcard/koreader/plugins/`. It listens only on
`127.0.0.1:5000` and implements `/voices`, `/`, `/play`, `/stop`, and
`/remaining`.

## Providers

- Grok/xAI: multilingual PCM with automatic language selection.
- ElevenLabs: authenticated, paginated voice discovery and Flash v2.5 PCM.
- Sherpa-ONNX: Kokoro, Kitten, Inflect, and the complete upstream Piper catalog.
- Edge: live voice discovery plus WebSocket synthesis and on-device decoding.

The bundled Piper index is generated from `rhasspy/piper-voices/voices.json`
and the Sherpa `tts-models` release. It currently contains 174 models and 2,707
speaker choices. Official Piper sample MP3s permit previews before download;
141 models have matching checksum-verified Sherpa archives. Entries without a
published matching archive remain explicitly preview-only.

The project is GPL-3.0-or-later. The pinned sherpa-onnx JNI wrapper and native
libraries are Apache-2.0 components from k2-fsa.
