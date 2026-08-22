# Native release provenance

Development libraries currently checked into the arm64 source set were built
from sherpa-onnx revision `1cb484af5e69d3c7803c1eb0b3b5ab8041e0e911`
with ONNX Runtime 1.27.0 and the Pocket patch in this directory.

| File | SHA-256 |
| --- | --- |
| `libonnxruntime.so` | `994848008526a934dfb579ac773b00e5867929234852b061005d45aacaee9533` |
| `libsherpa-onnx-jni.so` | `bbb1dee1ccb1e9ccdff57384654788f228b709e1dcd3ffd09afdab2df9182f77` |

These hashes identify development artifacts; they are not a substitute for an
F-Droid source build. Release automation must reproduce and compare them.

The embedded eSpeak NG runtime and generated language data are built from the
git submodule pinned at `7d426728fe146f4168fa716e29d8e276c7da33f2`. The app
compiles upstream's Android JNI source directly; no prebuilt eSpeak library is
checked in.
