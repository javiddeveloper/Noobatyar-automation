package xyz.sattar.javid.proqueue

import android.app.Application
import android.content.Context
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import xyz.sattar.javid.proqueue.core.notifications.NotificationChannels
import xyz.sattar.javid.proqueue.di.appModule
import xyz.sattar.javid.proqueue.di.dbModuleAndroid
import xyz.sattar.javid.proqueue.di.platformModule

class ProQueueApp : Application() {
    companion object {
        lateinit var appContext: Context
    }

    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext
        startKoin {
            androidContext(applicationContext)
            modules(dbModuleAndroid, platformModule, appModule)
        }
        // Must happen here rather than at the point of showing a notification:
        // background pushes are posted by the FCM SDK itself, and Android drops
        // them silently if the channel does not exist yet. See NotificationChannels.
        NotificationChannels.ensureCreated(applicationContext)
    }
}
