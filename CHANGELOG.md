# Changelog

## Unreleased

- Moved voice filtering to a dedicated screen and refreshed app screenshots.
- Added direct active-voice selection to installed-model test cards.
- Documented shared catalog ownership, Linux interoperability, and remaining
  F-Droid release blockers.

## 0.4.0-beta.2 - 2026-08-21

- Fixed paused local streams starving subsequent voices and previews.
- Fixed strict Android TTS terminal-callback handling and KOReader voice availability.
- Added per-model tuning, benchmark feedback, adaptive icons, and preview progress.
- Added model-input contraction normalization for VITS and Pocket artifacts.
- Clarified incomplete voice downloads and provider credential saving.
- Documented the required KOReader pause/resume and UTF-8 compatibility patch.

## 0.4.0-beta.1 - 2026-08-21

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
