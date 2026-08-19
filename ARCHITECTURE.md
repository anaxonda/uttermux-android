# Android architecture

```text
Android apps / PROCESS_TEXT / KOReader
                    |
             VoiceRouter session
                    |
     local Sherpa or selected cloud provider
                    |
     normalized 24 kHz mono PCM16 chunks
                    |
 Android callbacks or adaptive playback queue
```

Provider IDs are stable strings rather than an enum so the catalog can grow
without changing routing persistence. A provider prepares an immutable session,
may warm resources, and streams `AudioChunk` values containing PCM and source
text ranges. Buffered codecs are adapted behind the same interface.

The router resolves one ordered route per utterance. It detects language when
the caller does not provide a useful locale, applies BCP-47 fallback, and only
tries another provider before the first audio chunk. Network providers declare
metered behavior, credentials, capabilities, and streaming strategy.

Local engines are cached in a configurable LRU. Piper/VITS text is segmented
without losing characters or offsets. Sherpa callback-capable families stream
their inference output directly. Model downloads run through WorkManager with
network/storage constraints, HTTP resume, cancellation, SHA-256 checks, safe
extraction, and atomic publication.

KOReader receives a handle immediately after creating a synthesis session.
Generation fills a bounded producer queue while `/play` drains it through an
adaptive buffer. `/remaining` reports generation, buffered duration, playback,
and errors independently.

Diagnostics retain a bounded timing history: request, route, prepare, callback
start, model/network start, first audio, completion, cancellation, and failure.
No API key or request text is recorded.
