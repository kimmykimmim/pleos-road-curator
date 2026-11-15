import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
}

// Submission signing. The keystore password never enters version control: keystore.properties is
// ignored by git, and the file itself is optional so a fresh clone still builds the debug variant.
val keystoreFile = rootProject.file("keystore.properties")
val keystore = Properties().apply {
    if (keystoreFile.exists()) keystoreFile.inputStream().use { load(it) }
}
val keystoreStore = keystore.getProperty("storeFile")?.let { rootProject.file(it) }
val keystoreUsable = keystoreStore?.exists() == true

android {
    namespace = "dev.changbak.explorer"
    compileSdk = 34
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "dev.changbak.explorer"
        minSdk = 28
        targetSdk = 34
        versionCode = 41
        versionName = "0.39.0"
        testInstrumentationRunner = "dev.changbak.explorer.SceneRenderCheck"
    }

    // Compile against the SDK's car API. Runtime/device requirements live in the manifest.
    useLibrary("android.car")

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Built-in Kotlin requires Kotlin sources to be registered with the Kotlin set.
    sourceSets.named("test") {
        kotlin.directories += rootProject.file("tests/audio").path
    }

    signingConfigs {
        if (keystoreUsable) {
            create("contest") {
                storeFile = keystoreStore
                storePassword = keystore.getProperty("storePassword")
                keyAlias = keystore.getProperty("keyAlias")
                keyPassword = keystore.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Unsigned release APKs cannot be installed, and the submission requires an
            // installable release build. Without the keystore the task still runs unsigned.
            if (keystoreUsable) signingConfig = signingConfigs.getByName("contest")
        }
    }
}

// AGP 9 provides Kotlin support. Do not add org.jetbrains.kotlin.android.
kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("ai.pleos.playground:NaviHelper:3.0.5")
    implementation("ai.pleos.playground:Vehicle:2.3.2")
    implementation("ai.pleos.playground:TextToSpeech:3.3.1")
    implementation("ai.pleos.playground:SpeechToText:3.3.1")
    testImplementation("junit:junit:4.13.2")
    // Android supplies org.json at runtime, but the unit-test stub throws on every call, which
    // would let a stored-record bug hide behind the decoder's own corrupt-record handling.
    // Test scope only, so the real classes never reach the APK.
    testImplementation("org.json:json:20240303")
}
