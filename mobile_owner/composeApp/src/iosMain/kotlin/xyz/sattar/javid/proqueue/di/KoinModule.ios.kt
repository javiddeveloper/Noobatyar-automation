package xyz.sattar.javid.proqueue.di

import org.koin.dsl.module
import xyz.sattar.javid.proqueue.core.notifications.IosNotificationScheduler
import xyz.sattar.javid.proqueue.core.notifications.NotificationScheduler
import xyz.sattar.javid.proqueue.data.localDataSource.AppDatabase
import xyz.sattar.javid.proqueue.db.DbFactoryIos

val dbModuleiOS = module {

    single<AppDatabase> {
        DbFactoryIos.initialize()
    }

}

val platformModule = module {
    single<NotificationScheduler> { IosNotificationScheduler() }
}
