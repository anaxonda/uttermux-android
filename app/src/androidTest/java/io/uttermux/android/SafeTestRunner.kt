package io.uttermux.android

import androidx.test.runner.AndroidJUnitRunner

/** Prevent Gradle's install/uninstall cycle from ever targeting the live TTS app. */
class SafeTestRunner : AndroidJUnitRunner() {
    override fun onStart() {
        check(targetContext.packageName != "io.uttermux.android") {
            "Refusing to run instrumentation against the live UtterMux package"
        }
        super.onStart()
    }
}

@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class OptInDeviceTest
