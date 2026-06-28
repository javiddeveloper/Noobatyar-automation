# ProQueue

## Introduction
- ProQueue is a smart queue and appointment management app for small businesses, built once and delivered on Android and iOS.
- It streamlines visitor registration, scheduling, daily queue tracking, and lightweight messaging to improve service efficiency.

## Architecture
- Kotlin Multiplatform shared core with platform-specific integrations where needed.
- MVI pattern (Model–View–Intent): intents are processed in the ViewModel, emitting flows of PartialState that are reduced into an immutable UI State; one-off actions are delivered via event channels.
- Unidirectional data flow with ViewModel intents, partial states, reducer, and event channels.
- Repository and use-case layers isolate data and business logic from UI.
- Navigation uses a typed route system for safe screen transitions.
- State holders manage global app state (theme and selected business) outside screen lifecycles.

## Technologies
- UI: JetBrains Compose Multiplatform, Material 3, Compose Resources.
- DI: Koin (core, compose, android).
- Data: Room (multiplatform driver), SQLite bundled driver.
- Persistence: SharedPreferences (Android), NSUserDefaults (iOS) for lightweight settings.
- Networking: Ktor client with Kotlinx Serialization (extensible for future remote sync).
- Concurrency: Kotlin coroutines and flows.
- Navigation: Compose Navigation with typed routes.

## About Me
- Hi, I’m Javid — a Kotlin Multiplatform engineer focused on clean architectures and pragmatic cross‑platform delivery.
- I enjoy building robust, testable app cores with polished Compose UIs across Android and iOS.

## Project Structure
- `composeApp/src/commonMain/kotlin`: shared UI, state, domain, and data layers.
- `composeApp/src/androidMain/kotlin`: Android actual implementations and platform services.
- `composeApp/src/iosMain/kotlin`: iOS actual implementations and platform services.
- `iosApp/`: iOS application entry point and Xcode integration.

## Build & Run
- Android (macOS/Linux): `./gradlew :composeApp:assembleDebug`
- Android (Windows): `.\gradlew.bat :composeApp:assembleDebug`
- iOS: open `iosApp` in Xcode and run.
