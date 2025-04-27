package io.github.gitofleonardo.coreservice.data

import android.os.Parcel
import android.os.Parcelable
import com.clj.fastble.data.BleDevice

data class BondedBleDevice(
    val device: BleDevice,
    var synced: Boolean,
    var connected: Boolean
) : Parcelable {

    constructor(parcel: Parcel) : this(
        parcel.readParcelable<BleDevice>(BleDevice::class.java.classLoader)!!,
        parcel.readInt() == 1,
        parcel.readInt() == 1
    )

    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeParcelable(device, 0)
        dest.writeInt(if (synced) 1 else 0)
        dest.writeInt(if (connected) 1 else 0)
    }

    companion object {

        @JvmField
        val CREATOR = object : Parcelable.Creator<BondedBleDevice> {
            override fun createFromParcel(source: Parcel): BondedBleDevice {
                return BondedBleDevice(source)
            }

            override fun newArray(size: Int): Array<out BondedBleDevice?> {
                return Array<BondedBleDevice?>(size) { null }
            }
        }
    }
}