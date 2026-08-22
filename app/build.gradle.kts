import java.security.MessageDigest

plugins { id("com.android.application"); id("org.jetbrains.kotlin.plugin.compose") }

val releaseKeystorePath=System.getenv("ANDROID_KEYSTORE_PATH")

android {
    namespace = "io.uttermux.android"
    compileSdk = 36
    defaultConfig {
        applicationId = "io.uttermux.android"
        minSdk = 26
        targetSdk = 36
        versionCode = 5
        versionName = "0.4.0-beta.2"
        testInstrumentationRunner = "io.uttermux.android.SafeTestRunner"
        testInstrumentationRunnerArguments["notAnnotation"] = "io.uttermux.android.OptInDeviceTest"
        ndk { abiFilters += "arm64-v8a" }
        externalNativeBuild {
            cmake {
                targets += listOf("qwen3_tts_jni","ttsespeak")
                arguments += listOf(
                    "-DANDROID_STL=c++_shared",
                    "-DQWEN3_ANDROID_OPENMP=OFF",
                    "-DQWEN3_ANDROID_VULKAN=OFF",
                    "-DQWEN3_ANDROID_OPENCL=OFF",
                )
            }
        }
    }
    buildFeatures { compose = true; buildConfig = true }
    externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt") } }
    packaging { jniLibs.useLegacyPackaging = true; jniLibs.pickFirsts += "**/libonnxruntime.so" }
    testBuildType = "isolatedHost"
    buildTypes {
        create("isolatedHost") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".testhost"
            matchingFallbacks += listOf("debug")
            isDebuggable = true
        }
    }
    if(!releaseKeystorePath.isNullOrBlank()){
        signingConfigs {
            create("release"){
                storeFile=file(releaseKeystorePath)
                storePassword=System.getenv("ANDROID_KEYSTORE_PASSWORD")
                keyAlias=System.getenv("ANDROID_KEY_ALIAS")
                keyPassword=System.getenv("ANDROID_KEY_PASSWORD")
            }
        }
        buildTypes.getByName("release").signingConfig=signingConfigs.getByName("release")
    }
}

val espeakRoot=rootProject.layout.projectDirectory.dir("external/espeak-ng")
val espeakHostBuild=layout.buildDirectory.dir("espeak-host")
val espeakGeneratedRes=layout.buildDirectory.dir("generated/espeak-res")
val configureEspeakData by tasks.registering(Exec::class){
    inputs.file(espeakRoot.file("CMakeLists.txt"));outputs.file(espeakHostBuild.map{it.file("build.ninja")})
    commandLine("cmake","-S",espeakRoot.asFile,"-B",espeakHostBuild.get().asFile,"-G","Ninja","-DUSE_LIBSONIC=OFF","-DUSE_ASYNC=OFF","-DUSE_MBROLA=OFF","-DCMAKE_BUILD_TYPE=Release")
}
val seedEspeakData by tasks.registering(Copy::class){
    dependsOn(configureEspeakData);from(espeakRoot.dir("espeak-ng-data"));into(espeakHostBuild.map{it.dir("espeak-ng-data")})
}
val buildEspeakData by tasks.registering(Exec::class){
    dependsOn(seedEspeakData);inputs.dir(espeakRoot.dir("dictsource"));outputs.dir(espeakHostBuild.map{it.dir("espeak-ng-data")})
    commandLine("cmake","--build",espeakHostBuild.get().asFile,"--target","data","--parallel","2")
}
val packageEspeakData by tasks.registering(Zip::class){
    dependsOn(buildEspeakData);from(espeakHostBuild.map{it.dir("espeak-ng-data")}){into("espeak-ng-data")}
    archiveFileName.set("espeakdata.zip");destinationDirectory.set(espeakGeneratedRes.map{it.dir("raw")})
    isPreserveFileTimestamps=false;isReproducibleFileOrder=true
}
android.sourceSets.getByName("main").res.srcDir(espeakGeneratedRes.get().asFile)
tasks.named("preBuild").configure{dependsOn(packageEspeakData)}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.06.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.activity:activity-compose:1.12.4")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-service:2.10.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("com.squareup.okhttp3:okhttp:5.3.2")
    implementation("org.apache.commons:commons-compress:1.28.0")
    implementation("androidx.work:work-runtime:2.11.2")
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.27.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}

val validateGeneratedCatalog by tasks.registering {
    val catalog=layout.projectDirectory.file("src/main/assets/catalog/v2/catalog.json")
    val lock=layout.projectDirectory.file("src/main/assets/catalog/v2/catalog.lock.json")
    inputs.files(catalog,lock)
    doLast {
        val document=groovy.json.JsonSlurper().parse(catalog.asFile) as Map<*,*>
        val pinned=groovy.json.JsonSlurper().parse(lock.asFile) as Map<*,*>
        check(document["schemaVersion"]==2){"Unsupported generated catalog schema"}
        val digest=MessageDigest.getInstance("SHA-256").digest(catalog.asFile.readBytes()).joinToString(""){"%02x".format(it.toInt() and 0xff)}
        check(pinned["sha256"]==digest){"Generated catalog does not match catalog.lock.json"}
        check(pinned["provenance"]==document["provenance"]){"Generated catalog provenance does not match catalog.lock.json"}
        val variants=document["variants"] as List<*>
        check(variants.filterIsInstance<Map<*,*>>().any{it["id"]=="qwen3-tts-0.6b-base-q4km"&&it["status"]=="device-preview"}){
            "Generated catalog is missing the Android Qwen device-preview variant"
        }
    }
}
tasks.named("preBuild").configure{dependsOn(validateGeneratedCatalog)}
