# KOReader bridge compatibility

UtterMux protocol 2 distinguishes resumable pause from terminal stop. Apply
`uttermux-v2.patch` to KOReader's `TTS.koplugin/main.lua`. The patch also removes
legacy single-byte Windows-1252 substitutions that corrupt UTF-8 punctuation
(notably U+2019 being spoken as “TM”).

The patched plugin is installed on the development phone at
`/sdcard/koreader/plugins/TTS.koplugin/main.lua`.
