package xyz.sattar.javid.proqueue.di

import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import xyz.sattar.javid.proqueue.core.notifications.AndroidNotificationScheduler
import xyz.sattar.javid.proqueue.core.notifications.NotificationScheduler
import xyz.sattar.javid.proqueue.data.localDataSource.AppDatabase
import xyz.sattar.javid.proqueue.db.DbFactoryAndroid

val dbModuleAndroid = module {

    // Ensure that the database is initialized before using it
    single<AppDatabase> {
        DbFactoryAndroid.initDatabase(context = androidContext())
        DbFactoryAndroid.initialize()
    }
}

val platformModule = module {
    single<NotificationScheduler> { AndroidNotificationScheduler(androidContext()) }
}
