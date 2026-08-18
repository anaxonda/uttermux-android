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

For KOReader, enable the compatibility server in UtterMux and install
`TTS.koplugin` under `/sdcard/koreader/plugins/`. It listens only on
`127.0.0.1:5000` and implements `/voices`, `/`, `/play`, `/stop`, and
`/remaining`.

## Providers

- Grok/xAI: multilingual PCM with automatic language selection.
- ElevenLabs: Flash v2.5 PCM.
- Sherpa-ONNX: Kokoro, Kitten, Piper, and Inflect models.
- Edge: experimental adapter (currently shown as unavailable until its native
  transport is enabled).

The project is GPL-3.0-or-later. The pinned sherpa-onnx JNI wrapper and native
libraries are Apache-2.0 components from k2-fsa.
