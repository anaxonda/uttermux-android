# Changelog

## 0.4.0-beta.1 - Unreleased

First public Android beta.

- Android system TTS integration tested with Feeder, Librera, and KOReader.
- Optional Piper/VITS, Inflect, Kitten, Kokoro, Matcha, Supertonic, and Pocket
  voices; no model is bundled.
- Searchable voice catalog, exact previews, automatic language routing,
  hardware guidance, and adaptive PCM streaming.
- Pocket local voice profiles and optional online voice providers.
- Encrypted credentials, redacted diagnostics, checksum-verified downloads,
  dark theme, and Android selection-menu playback.

Known limitations:

- The APK supports arm64-v8a devices only.
- Kokoro FP32 is slower than real time on the Galaxy S10 reference device.
- Pocket section continuity depends partly on how the reader submits requests.
- Some online provider integrations remain experimental.
