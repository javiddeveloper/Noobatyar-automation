package xyz.sattar.javid.proqueue.di

import org.koin.dsl.module
import xyz.sattar.javid.proqueue.data.localDataSource.AppDatabase
import xyz.sattar.javid.proqueue.data.localDataSource.appointment.AppointmentLocalSource
import xyz.sattar.javid.proqueue.data.localDataSource.appointment.RoomAppointmentLocalSource
import xyz.sattar.javid.proqueue.data.localDataSource.business.BusinessLocalSource
import xyz.sattar.javid.proqueue.data.localDataSource.business.RoomBusinessLocalSource
import xyz.sattar.javid.proqueue.data.localDataSource.message.MessageLocalSource
import xyz.sattar.javid.proqueue.data.localDataSource.message.RoomMessageLocalSource
import xyz.sattar.javid.proqueue.data.localDataSource.user.RoomUserLocalSource
import xyz.sattar.javid.proqueue.data.localDataSource.user.UserLocalSource
import xyz.sattar.javid.proqueue.data.localDataSource.visitor.RoomVisitorLocalSource
import xyz.sattar.javid.proqueue.data.localDataSource.visitor.VisitorLocalSource

/**
 * The half of `appModule` (di/KoinModule.kt) that needs Room. Split out into
 * `roomMain` because [AppDatabase] lives there and `commonMain` cannot see it
 * — see docs/OWNER_WEB_PLAN.md section 5. androidMain/iosMain each load this
 * alongside `appModule`; a future web target would load a different module
 * providing the same *LocalSource bindings instead (e.g. backed by an
 * in-memory store) without this module or anything in `roomMain` involved.
 */
val roomModule = module {
    single<BusinessLocalSource> { RoomBusinessLocalSource(get<AppDatabase>().businessDao()) }
    single<VisitorLocalSource> { RoomVisitorLocalSource(get<AppDatabase>().visitorDao()) }
    single<AppointmentLocalSource> { RoomAppointmentLocalSource(get<AppDatabase>().appointmentDao()) }
    single<MessageLocalSource> { RoomMessageLocalSource(get<AppDatabase>().messageDao()) }
    single<UserLocalSource> { RoomUserLocalSource(get<AppDatabase>().userDao()) }
}
