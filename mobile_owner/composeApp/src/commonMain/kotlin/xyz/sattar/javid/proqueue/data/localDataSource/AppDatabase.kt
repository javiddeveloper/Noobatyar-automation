package xyz.sattar.javid.proqueue.data.localDataSource

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import xyz.sattar.javid.proqueue.data.localDataSource.appointment.AppointmentDao
import xyz.sattar.javid.proqueue.data.localDataSource.appointment.AppointmentEntity
import xyz.sattar.javid.proqueue.data.localDataSource.business.BusinessDao
import xyz.sattar.javid.proqueue.data.localDataSource.business.BusinessEntity
import xyz.sattar.javid.proqueue.data.localDataSource.message.MessageDao
import xyz.sattar.javid.proqueue.data.localDataSource.message.MessageEntity
import xyz.sattar.javid.proqueue.data.localDataSource.visitor.VisitorDao
import xyz.sattar.javid.proqueue.data.localDataSource.visitor.VisitorEntity
import xyz.sattar.javid.proqueue.data.localDataSource.user.UserDao
import xyz.sattar.javid.proqueue.data.localDataSource.user.UserEntity
import xyz.sattar.javid.proqueue.data.localDataSource.user.SubscriptionEntity

internal const val dbFileName = "proQueue.db"

@Database(
    entities = [
        BusinessEntity::class,
        VisitorEntity::class,
        AppointmentEntity::class,
        MessageEntity::class,
        UserEntity::class,
        SubscriptionEntity::class
    ],
    version = 3
)
@ConstructedBy(DbFactory::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun businessDao(): BusinessDao
    abstract fun visitorDao(): VisitorDao
    abstract fun appointmentDao(): AppointmentDao
    abstract fun messageDao(): MessageDao
    abstract fun userDao(): UserDao
}
