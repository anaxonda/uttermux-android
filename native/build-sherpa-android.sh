#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 2 ]]; then
  echo "usage: $0 SHERPA_ONNX_SOURCE ANDROID_NDK" >&2
  exit 2
fi

source_dir=$(realpath "$1")
android_ndk=$(realpath "$2")
project_dir=$(cd "$(dirname "$0")/.." && pwd)
pinned_revision=1cb484af5e69d3c7803c1eb0b3b5ab8041e0e911

actual_revision=$(git -C "$source_dir" rev-parse HEAD)
if [[ "$actual_revision" != "$pinned_revision" ]]; then
  echo "expected sherpa-onnx $pinned_revision, found $actual_revision" >&2
  exit 1
fi

patch_file="$project_dir/native/patches/sherpa-pocket-progressive-decode.patch"
if git -C "$source_dir" apply --reverse --check "$patch_file" 2>/dev/null; then
  echo "Pocket progressive-decode patch is already applied"
else
  git -C "$source_dir" apply --check "$patch_file"
  git -C "$source_dir" apply "$patch_file"
fi

(
  cd "$source_dir"
  ANDROID_NDK="$android_ndk" \
  SHERPA_ONNX_ENABLE_SPEAKER_DIARIZATION=OFF \
  SHERPA_ONNX_ENABLE_BINARY=OFF \
  SHERPA_ONNX_ENABLE_C_API=OFF \
  SHERPA_ONNX_ENABLE_TTS=ON \
  SHERPA_ONNX_ONNXRUNTIME_VERSION=1.27.0 \
    ./build-android-arm64-v8a.sh
)

install_dir="$source_dir/build-android-arm64-v8a/install/lib"
cp "$install_dir/libsherpa-onnx-jni.so" "$project_dir/app/src/main/jniLibs/arm64-v8a/"
cp "$install_dir/libonnxruntime.so" "$project_dir/app/src/main/jniLibs/arm64-v8a/"

sha256sum \
  "$project_dir/app/src/main/jniLibs/arm64-v8a/libsherpa-onnx-jni.so" \
  "$project_dir/app/src/main/jniLibs/arm64-v8a/libonnxruntime.so"
