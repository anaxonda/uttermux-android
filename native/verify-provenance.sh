#!/usr/bin/env bash
set -euo pipefail
project_dir=$(cd "$(dirname "$0")/.." && pwd)
cd "$project_dir"
printf '%s  %s\n' \
  994848008526a934dfb579ac773b00e5867929234852b061005d45aacaee9533 app/src/main/jniLibs/arm64-v8a/libonnxruntime.so \
  bbb1dee1ccb1e9ccdff57384654788f228b709e1dcd3ffd09afdab2df9182f77 app/src/main/jniLibs/arm64-v8a/libsherpa-onnx-jni.so |
  sha256sum --check --strict

qwen_commit=$(git -C external/qwen3-tts.cpp rev-parse HEAD)
ggml_commit=$(git -C external/qwen3-tts.cpp/ggml rev-parse HEAD)
test "$qwen_commit" = 16bb5afcd06311031c72a8488f8d59660dc2fb46
test "$ggml_commit" = af97976c7810cdabb1863172f31c432dab767de7
test -z "$(git -C external/qwen3-tts.cpp status --short)"
printf 'Qwen native provenance verified\n'
