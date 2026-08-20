# Native sherpa-onnx runtime

UtterMux carries a small downstream sherpa-onnx patch so Pocket TTS decodes
bounded latent chunks while generation is still running. Upstream v1.13.6
collects the complete sentence's latents before its first PCM callback, which
makes Android document readers pause between requests even though they use the
streaming API.

Build from the pinned upstream revision with:

```bash
native/build-sherpa-android.sh /path/to/sherpa-onnx /path/to/android-ndk
```

The script verifies the upstream revision, applies
`patches/sherpa-pocket-progressive-decode.patch`, builds arm64-v8a against ONNX
Runtime 1.27.0, and copies the matched JNI and ONNX Runtime libraries into the
app. Re-run the Android integration tests after every native update.

## Design references

- [Pocket TTS](https://github.com/kyutai-labs/pocket-tts) recommends caching
  exported voice state because processing reference WAV audio is comparatively
  slow. UtterMux primes sherpa's in-memory reference-embedding cache when the
  selected voice is loaded.
- [NekoSpeak](https://github.com/siva-sub/NekoSpeak) demonstrates bounded,
  adaptive latent generation and decode. UtterMux uses the same progressive
  principle but keeps generation and Mimi decode serialized inside sherpa for
  deterministic ordering and avoids the missing-sentence failure mode reported
  against NekoSpeak's Pocket path.
- sherpa-onnx's Pocket callback was still invoked only after complete latent
  generation at the pinned revision. This patch moves stateful Mimi decoding
  into the generation loop and honors callback cancellation immediately.
