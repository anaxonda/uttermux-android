# F-Droid release preparation

UtterMux uses the permanent application ID `io.uttermux.android`, is
GPL-3.0-or-later, contains no advertising or tracking SDK, and works entirely
offline after an optional local model is downloaded. Online providers are
optional and should be declared with the applicable `NonFreeNet` anti-feature.

## Native source provenance

The JNI runtime is sherpa-onnx at the revision pinned in
`native/build-sherpa-android.sh`, plus the reviewed Pocket progressive-decoding
patch in `native/patches`. The build script rejects any other upstream revision.
ONNX Runtime is Apache-2.0 and pinned to the same version in Gradle and the
native script.

Current development APKs retain the reproducibly generated arm64 libraries for
fast device iteration. An F-Droid recipe must run the native script against a
declared sherpa-onnx source library, compare the resulting SHA-256 values with
the release provenance record, and package those outputs instead of trusting
the repository binaries. This is a release blocker, not a scanner exception.

## Release checklist

- Build and tag from a clean public source tree.
- Run unit tests, lint, connected system-TTS tests, and the F-Droid scanner.
- Build sherpa-onnx JNI from its pinned source revision and reviewed patch.
- Confirm that the APK contains no model, voice recording, API key, or test key.
- Update model URLs, hashes, sizes, licenses, and the dependency inventory.
- Reproduce the signed release from the tag and archive native-library hashes.
- Update fastlane descriptions, screenshots, changelog, and privacy policy.

## Current readiness

The project is **not ready for fdroiddata submission yet**. GitHub beta builds
are suitable for testing, but the following work remains:

1. Add a reproducible fdroiddata recipe that fetches the pinned sherpa-onnx and
   ONNX Runtime sources and runs `native/build-sherpa-android.sh` (or an
   equivalent recipe-local build) instead of packaging checked-in `.so` files.
2. Rebuild twice in clean environments and compare the unsigned APK and native
   library hashes; record unavoidable signing/ZIP differences separately.
3. Run `fdroid scanner` and `fdroid build` against the public tag.
4. Complete Fastlane store metadata: feature graphic, icon, phone screenshots,
   per-version changelog, privacy-policy URL, and localized descriptions.
5. Declare `NonFreeNet` because the app can connect to proprietary online TTS
   services, even though offline local models remain available.
6. Tag a non-beta version with a monotonically increased `versionCode` after
   connected system-TTS, pause/resume, cancellation, and reader regression
   tests pass on the release APK.

GitHub release APKs are signed by the tag workflow using repository secrets.
That signing key is for GitHub distribution only; F-Droid independently builds
and signs its APK from source.

The normal CI workflow verifies the checked-in development JNI hashes, builds
the application, runs unit tests and lint, and rejects APKs containing model
weights, voice recordings, credential directories, or obvious key patterns.
That protects ordinary development builds but does not replace the F-Droid
source build of the two JNI libraries.

Model downloads are user-initiated data downloads. Every entry must show its
size and license before download and must be verified by SHA-256.
