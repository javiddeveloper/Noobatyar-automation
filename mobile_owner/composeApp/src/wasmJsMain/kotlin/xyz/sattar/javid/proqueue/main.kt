package xyz.sattar.javid.proqueue

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlin.js.ExperimentalWasmJsInterop
import kotlinx.browser.document
import org.koin.core.context.startKoin
import org.w3c.dom.HTMLElement
import xyz.sattar.javid.proqueue.di.appModule
import xyz.sattar.javid.proqueue.di.webPlatformModule

// index.html's boot-timer: enforces the loading screen staying up for at
// least its own minimum duration regardless of how fast the wasm module
// actually loaded — see that file's inline <script> for why the timer lives
// there and not here (it has to start counting the instant the HTML parses,
// before this Kotlin has even downloaded).
@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("() => { if (window.__nbHideLoading) window.__nbHideLoading(); }")
private external fun hideBootLoadingScreen()

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    startKoin {
        // No dbModule/roomModule here — wasmJs has no Room (see
        // docs/OWNER_WEB_PLAN.md section 5); webPlatformModule provides the
        // same *LocalSource bindings appModule expects, backed by in-memory
        // stores instead.
        modules(webPlatformModule, appModule)
    }
    // Mounted into its own #compose-root, not document.body: ComposeViewport
    // takes ownership of whatever element it's given and clears its content,
    // which would delete index.html's #compose-loading overlay the instant
    // this line runs — before the minimum-display timer got any say at all.
    ComposeViewport(document.getElementById("compose-root") as HTMLElement) {
        App()
    }
    hideBootLoadingScreen()
}
