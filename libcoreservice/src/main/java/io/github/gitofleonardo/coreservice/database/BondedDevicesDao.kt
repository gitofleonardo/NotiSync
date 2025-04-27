package io.github.gitofleonardo.coreservice.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
internal interface BondedDevicesDao {
    @Query("SELECT * FROM bonded_devices")
    fun getBondedDevices(): List<BondedDeviceItem>

    @Insert
    fun insert(device: BondedDeviceItem): Long

    @Delete
    fun delete(vararg devices: BondedDeviceItem)

    @Query("DELETE FROM bonded_devices WHERE device_address = :deviceAddress")
    fun deleteByAddress(deviceAddress: String)

    @Update
    fun update(vararg devices: BondedDeviceItem)
}
