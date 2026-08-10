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
    // Both platforms build with fallbackToDestructiveMigration, so this drops
    // the local cache and refetches — no real migration path needed, just a
    // version number higher than anything already shipped.
    // 10: BusinessEntity moderation columns (moderationStatus/Note/etc, this
    //     branch) AND, independently on develop, BusinessEntity.noticeEnabled/
    //     noticeMessage/reminderDelivery — both landed as "version 10" on
    //     their own branch and collided on merge.
    // 11: BusinessEntity.isLocked (billing lock, separate from moderation).
    // 12: AppointmentEntity.selectedServices (service-catalog chips picked
    //     when recording an appointment).
    // 13: BusinessEntity.services / allowClientAddService (the business's own
    //     service menu, defined once and reused by the client booking page).
    // 14: merge of the two branches above — no schema change of its own,
    //     just the next version number since 10/11 and 13 were assigned
    //     independently on two branches and can't both be "the" version 10/11.
    version = 14
)
@ConstructedBy(DbFactory::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun businessDao(): BusinessDao
    abstract fun visitorDao(): VisitorDao
    abstract fun appointmentDao(): AppointmentDao
    abstract fun messageDao(): MessageDao
    abstract fun userDao(): UserDao
}
