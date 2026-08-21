#!/usr/bin/env bash
set -euo pipefail

# The isolated host has a distinct application ID. It can be installed and
# removed without touching the live system-TTS engine or its private data.
test_class=${1:-io.uttermux.android.SystemTtsCompatibilityTest,io.uttermux.android.BenchmarkWizardTest}
project_root=$(cd "$(dirname "$0")/.." && pwd)
cd "$project_root"

: "${JAVA_HOME:=/usr/lib/jvm/java-17-openjdk}"
export JAVA_HOME
./gradlew :app:assembleIsolatedHost :app:assembleIsolatedHostAndroidTest
adb install -r app/build/outputs/apk/isolatedHost/app-isolatedHost.apk
adb install -r app/build/outputs/apk/androidTest/isolatedHost/app-isolatedHost-androidTest.apk
adb shell am instrument -w \
  -e class "$test_class" \
  io.uttermux.android.testhost.test/io.uttermux.android.SafeTestRunner
