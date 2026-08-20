#!/usr/bin/env bash
set -euo pipefail
project_dir=$(cd "$(dirname "$0")/.." && pwd)
cd "$project_dir"
printf '%s  %s\n' \
  994848008526a934dfb579ac773b00e5867929234852b061005d45aacaee9533 app/src/main/jniLibs/arm64-v8a/libonnxruntime.so \
  bbb1dee1ccb1e9ccdff57384654788f228b709e1dcd3ffd09afdab2df9182f77 app/src/main/jniLibs/arm64-v8a/libsherpa-onnx-jni.so |
  sha256sum --check --strict

