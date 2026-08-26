package xyz.sattar.javid.proqueue

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document
import org.koin.core.context.startKoin
import xyz.sattar.javid.proqueue.di.appModule
import xyz.sattar.javid.proqueue.di.webPlatformModule

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    startKoin {
        // No dbModule/roomModule here — wasmJs has no Room (see
        // docs/OWNER_WEB_PLAN.md section 5); webPlatformModule provides the
        // same *LocalSource bindings appModule expects, backed by in-memory
        // stores instead.
        modules(webPlatformModule, appModule)
    }
    ComposeViewport(document.body!!) {
        App()
    }
}
