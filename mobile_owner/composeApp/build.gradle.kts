import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import com.codingfeline.buildkonfig.compiler.FieldSpec.Type.STRING
import org.gradle.kotlin.dsl.buildkonfig
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.room)
    alias(libs.plugins.ksp)
    alias(libs.plugins.buildkonfig)
}


// Two Android product flavors pick which server this build talks to (see
// docs/ENVIRONMENTS.md). "prod" is the safe default returned by defaultConfigs,
// so anything that isn't explicitly built as the "local" flavor (iOS included,
// which has no flavor concept) still points at the real server.
buildkonfig {
    packageName = "xyz.sattar.javid.proqueue"
    defaultConfigs {
        buildConfigField(STRING, "BASE_URL", "https://api.noobatyar.ir")
        buildConfigField(STRING, "BOOKING_BASE_URL", "https://app.noobatyar.ir")
    }
    defaultConfigs("local") {
        // 10.0.2.2 is the Android emulator's alias for the host machine's
        // loopback interface — this is where `python manage.py runserver`
        // listens per docs/ENVIRONMENTS.md. On a real device on the same LAN,
        // override with your machine's IP instead (adb won't route 10.0.2.2).
        buildConfigField(STRING, "BASE_URL", "http://10.0.2.2:8000")
        buildConfigField(STRING, "BOOKING_BASE_URL", "http://10.0.2.2:3000")
    }
}


kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
        freeCompilerArgs.add("-opt-in=androidx.compose.material3.ExperimentalMaterial3Api")
        freeCompilerArgs.add("-opt-in=androidx.compose.foundation.layout.ExperimentalLayoutApi")
    }

    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    sourceSets {
       androidMain.dependencies {
           implementation(compose.preview)
           implementation(libs.androidx.activity.compose)
           // Koin Android integration
           implementation(libs.koin.android)
           implementation(libs.koin.compose)
           implementation(libs.ktor.client.okhttp)
           implementation(libs.ktor.client.logginig)
       }
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            // Networking
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.ktor.client.logginig)
            // Serialization
            implementation(libs.kotlinx.serialization.json)
            // koin
            implementation(libs.androidx.room.runtime)
            implementation(libs.sqlite.bundled)
            api(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(compose.materialIconsExtended)
            implementation(libs.kotlinx.datetime)

            // navigation
            implementation(libs.navigation.compose)

            // Image Picker & Cropper
            implementation(libs.peekaboo.image.picker)
            implementation(libs.easycrop)
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor3)

            // Glassmorphism blur for bars/toolbars
            implementation(libs.haze)
            implementation(libs.haze.materials)
        }
        iosMain.dependencies {
            implementation(libs.sqldelight.native.driver)
            implementation(libs.ktor.client.darwin)
            implementation(libs.ktor.client.logginig)
        }
    }
}

android {
    namespace = "xyz.sattar.javid.proqueue"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "xyz.sattar.javid.proqueue"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = (System.getenv("ANDROID_VERSION_CODE") ?: "100").toInt()
        versionName = System.getenv("ANDROID_VERSION_NAME") ?: "1.0.0"
    }

    // "prod" talks to the real server and is what release builds/CI use by
    // default. "local" talks to a Django dev server on this machine (see
    // docs/ENVIRONMENTS.md) and gets its own applicationId suffix so both
    // variants can be installed on the same emulator/device side by side.
    flavorDimensions += "env"
    productFlavors {
        create("prod") {
            dimension = "env"
        }
        create("local") {
            dimension = "env"
            applicationIdSuffix = ".local"
            versionNameSuffix = "-local"
        }
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    signingConfigs {
        create("release") {
            val keystorePath = System.getenv("ANDROID_KEYSTORE_PATH")
            if (keystorePath != null) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ANDROID_KEY_ALIAS")
                keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            if (System.getenv("ANDROID_KEYSTORE_PATH") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = true
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    debugImplementation(compose.uiTooling)
    
    // Chucker
    debugImplementation(libs.chucker.library)
    releaseImplementation(libs.chucker.library.no.op)

    // After running find ksp generated directories on: composeApp/build/generated/...
    add("kspAndroid", libs.androidx.room.compiler) // Android
    add("kspIosSimulatorArm64", libs.androidx.room.compiler) // Apple Silicon iOS Simulators
    add("kspIosArm64", libs.androidx.room.compiler) // Real iOS Devices
}

room {
    schemaDirectory("$projectDir/schemas")
}
