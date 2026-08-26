package xyz.sattar.javid.proqueue.data.localDataSource

import androidx.room.RoomDatabaseConstructor

expect object DbFactory: RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}