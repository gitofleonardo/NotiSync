package io.github.gitofleonardo.coreservice.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bonded_devices")
internal data class BondedDeviceItem(
    @PrimaryKey(autoGenerate = true) var uid: Long,
    @ColumnInfo(name = "device_name") var deviceName: String,
    @ColumnInfo(name = "device_address") val deviceAddress: String,
    @ColumnInfo(name = "sync_on") var syncOn: Boolean
)
