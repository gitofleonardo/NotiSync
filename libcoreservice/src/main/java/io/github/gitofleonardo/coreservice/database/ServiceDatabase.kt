package io.github.gitofleonardo.coreservice.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [BondedDeviceItem::class, FilteredAppItem::class], version = 1)
abstract class ServiceDatabase : RoomDatabase() {
    internal abstract fun bondedDevicesDao(): BondedDevicesDao

    abstract fun filteredAppsDao(): FilteredAppsDao
}
