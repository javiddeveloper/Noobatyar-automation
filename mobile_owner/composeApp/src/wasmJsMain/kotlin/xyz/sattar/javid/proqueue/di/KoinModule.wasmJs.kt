package xyz.sattar.javid.proqueue.di

import org.koin.dsl.module
import xyz.sattar.javid.proqueue.core.notifications.NotificationScheduler
import xyz.sattar.javid.proqueue.core.notifications.WebNotificationScheduler
import xyz.sattar.javid.proqueue.data.localDataSource.appointment.AppointmentLocalSource
import xyz.sattar.javid.proqueue.data.localDataSource.appointment.InMemoryAppointmentLocalSource
import xyz.sattar.javid.proqueue.data.localDataSource.business.BusinessLocalSource
import xyz.sattar.javid.proqueue.data.localDataSource.business.InMemoryBusinessLocalSource
import xyz.sattar.javid.proqueue.data.localDataSource.message.InMemoryMessageLocalSource
import xyz.sattar.javid.proqueue.data.localDataSource.message.MessageLocalSource
import xyz.sattar.javid.proqueue.data.localDataSource.user.InMemoryUserLocalSource
import xyz.sattar.javid.proqueue.data.localDataSource.user.UserLocalSource
import xyz.sattar.javid.proqueue.data.localDataSource.visitor.InMemoryVisitorLocalSource
import xyz.sattar.javid.proqueue.data.localDataSource.visitor.VisitorLocalSource

/**
 * The wasmJs equivalent of `roomModule` (roomMain, di/RoomKoinModule.kt): the
 * same four `*LocalSource` bindings `appModule` expects, backed by in-memory
 * stores instead of Room (which has no web target — see
 * docs/OWNER_WEB_PLAN.md section 5). `single` (not `factory`) matters here
 * the same way it does for Room — one shared instance per app run, so an
 * upsert from one repository call is visible to the next.
 */
val webPlatformModule = module {
    single<BusinessLocalSource> { InMemoryBusinessLocalSource() }
    single<VisitorLocalSource> { InMemoryVisitorLocalSource() }
    single<MessageLocalSource> { InMemoryMessageLocalSource() }
    single<UserLocalSource> { InMemoryUserLocalSource() }
    single<AppointmentLocalSource> { InMemoryAppointmentLocalSource(get(), get()) }
    single<NotificationScheduler> { WebNotificationScheduler() }
}
