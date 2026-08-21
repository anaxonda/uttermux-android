#!/usr/bin/env bash
set -euo pipefail

# Gradle's connected-test lifecycle may uninstall the target package. Install
# both APKs explicitly and invoke instrumentation so configured app data stays.
test_class=${1:-io.uttermux.android.SystemTtsCompatibilityTest}
project_root=$(cd "$(dirname "$0")/.." && pwd)
cd "$project_root"

: "${JAVA_HOME:=/usr/lib/jvm/java-17-openjdk}"
export JAVA_HOME
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w \
  -e class "$test_class" \
  io.uttermux.android.test/androidx.test.runner.AndroidJUnitRunner
