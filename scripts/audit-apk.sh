#!/usr/bin/env bash
set -euo pipefail
if [[ $# -ne 1 ]]; then
  echo "usage: $0 APK" >&2
  exit 2
fi
apk=$1
entries=$(unzip -Z1 "$apk")
payload_pattern='(^|/)(models?|voices?|credentials?)/|\.(onnx|gguf|wav|mp3)$|(^|/)(model[^/]*|voices|tokens)\.bin$'
if grep -Eiq "$payload_pattern" <<<"$entries"; then
  echo "APK unexpectedly contains a model, voice recording, credential directory, or audio sample" >&2
  grep -Ei "$payload_pattern" <<<"$entries" >&2
  exit 1
fi
if unzip -p "$apk" resources.arsc 2>/dev/null | strings | grep -Eiq '(sk-[A-Za-z0-9_-]{20,}|xi-api-key[=:]|api[_-]?key[=:][A-Za-z0-9_-]{16,})'; then
  echo "APK resources contain a possible API credential" >&2
  exit 1
fi
echo "APK payload audit passed"
