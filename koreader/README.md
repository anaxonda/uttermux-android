# KOReader bridge compatibility

UtterMux protocol 2 distinguishes resumable pause from terminal stop. Apply
`uttermux-v2.patch` to KOReader's `TTS.koplugin/main.lua`. The patch also removes
legacy single-byte Windows-1252 substitutions that corrupt UTF-8 punctuation
(notably U+2019 being spoken as “TM”).

The patched plugin is installed on the development phone at
`/sdcard/koreader/plugins/TTS.koplugin/main.lua`.

For a separate KOReader device, enable both **KOReader compatibility bridge**
and **Allow hotspot/LAN clients** on the phone. In KOReader, set **TTS server
URL** to the phone's hotspot/LAN address with port 5000, for example
`192.168.43.1:5000`. Synthesis and playback occur on the phone. LAN mode has no
authentication and should be enabled only on a trusted personal network.
