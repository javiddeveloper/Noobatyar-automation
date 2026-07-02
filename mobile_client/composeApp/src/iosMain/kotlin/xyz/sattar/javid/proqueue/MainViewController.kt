package xyz.sattar.javid.proqueue

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.ComposeUIViewController
import org.koin.core.context.startKoin
import org.koin.mp.KoinPlatformTools
import xyz.sattar.javid.proqueue.di.appModule
import xyz.sattar.javid.proqueue.di.dbModuleiOS
import xyz.sattar.javid.proqueue.di.platformModule

fun MainViewController() = ComposeUIViewController {
    var isKoinInitialized by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val alreadyExists = KoinPlatformTools.defaultContext().getOrNull() != null
        if (!alreadyExists) {
            startKoin {
                modules(dbModuleiOS, platformModule, appModule)
            }
        }
        isKoinInitialized = true
    }

    if(isKoinInitialized) {
        App()
    }
}