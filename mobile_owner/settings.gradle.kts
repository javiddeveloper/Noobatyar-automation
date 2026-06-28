rootProject.name = "ProQueue"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        maven { url = uri("https://maven.myket.ir") }
        maven { url = uri("https://mirror-maven.runflare.com/maven2") }
        maven { url = uri("https://mirror-maven.runflare.com/gradle-plugins/") }
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        maven { url = uri("https://maven.myket.ir") }
        maven { url = uri("https://mirror-maven.runflare.com/maven2") }
        maven { url = uri("https://mirror-maven.runflare.com/gradle-plugins/") }
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

include(":composeApp")
