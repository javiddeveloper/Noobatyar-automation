import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import com.codingfeline.buildkonfig.compiler.FieldSpec.Type.STRING
import org.gradle.kotlin.dsl.buildkonfig
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
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

// Push notifications (FCM). The google-services plugin fails the build outright
// when google-services.json is missing, and that file carries project-specific
// keys that are not in this repository — so it is applied only once somebody has
// dropped theirs into composeApp/. Without it the app still builds and runs;
// PushTokenProvider simply returns no token and the owner falls back to the
// local alarm reminders. See docs/FCM_SETUP.md.
val googleServicesJson = file("google-services.json")
if (googleServicesJson.exists()) {
    apply(plugin = libs.plugins.googleServices.get().pluginId)
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
    // Room (and sqlite-bundled under it) has no web target — see
    // docs/OWNER_WEB_PLAN.md section 5. "roomMain" is an intermediate source
    // set that only androidMain and iosMain fall back to, so every Room type
    // (AppDatabase, the DAOs, the @Entity classes) can live somewhere a
    // future wasmJs target never compiles against, while Android/iOS keep
    // seeing exactly what they saw before this source set existed.
    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    applyDefaultHierarchyTemplate {
        common {
            group("room") {
                withAndroidTarget()
                withIos()
            }
        }
    }

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

    // Owner web panel — see docs/OWNER_WEB_PLAN.md. Resolves cleanly now that
    // Room (section 5) is confined to roomMain, which this target does not
    // fall back to. `binaries.executable()` is what actually produces a
    // runnable browser bundle rather than just a library.
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        binaries.executable()
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
           // FCM. The dependency is unconditional (the Kotlin in
           // core/push/ references it) while the google-services *plugin*
           // above is not — a build with no google-services.json compiles
           // fine and just never obtains a token at runtime.
           implementation(project.dependencies.platform(libs.firebase.bom))
           implementation(libs.firebase.messaging)
           // Image picker. Only androidMain/iosMain use it now — feature/
           // code goes through our own core/utils/ImagePicker.kt expect/actual
           // instead, since peekaboo has no web target.
           implementation(libs.peekaboo.image.picker)
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
            api(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(compose.materialIconsExtended)
            implementation(libs.kotlinx.datetime)

            // navigation
            implementation(libs.navigation.compose)

            // Image loading. The picker itself (peekaboo) lives in
            // androidMain/iosMain only — see core/utils/ImagePicker.kt.
            // easycrop was removed: ImageCropperDialog.kt is hand-written and
            // never used it.
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor3)

            // Glassmorphism blur for bars/toolbars
            implementation(libs.haze)
            implementation(libs.haze.materials)
        }
        val roomMain by getting {
            dependencies {
                implementation(libs.androidx.room.runtime)
                implementation(libs.sqlite.bundled)
            }
        }
        iosMain.dependencies {
            implementation(libs.sqldelight.native.driver)
            implementation(libs.ktor.client.darwin)
            implementation(libs.ktor.client.logginig)
            // Image picker. See the androidMain block above for why this
            // moved out of commonMain.
            implementation(libs.peekaboo.image.picker)
        }
        val wasmJsMain by getting {
            dependencies {
                implementation(libs.ktor.client.js)
                implementation(libs.ktor.client.logginig)
                // window/document/localStorage — see core/prefs/PreferencesManager.wasmJs.kt,
                // core/network/TokenManager.wasmJs.kt, core/utils/ContactActions.wasmJs.kt.
                implementation(libs.kotlinx.browser)
            }
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
