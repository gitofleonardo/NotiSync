package io.github.gitofleonardo.notisync.ui

import com.clj.fastble.data.BleDevice

sealed class BleCallbackState {

    object None : BleCallbackState()

    class StartScan(val success: Boolean) : BleCallbackState()

    class Scanning(val device: BleDevice) : BleCallbackState()

    class Scanned(val devices: List<BleDevice>) : BleCallbackState()

    class DeviceConnected(val devices: Array<BleDevice>) : BleCallbackState()

    class DeviceDisconnected(val device: BleDevice) : BleCallbackState()

    class DeviceBonded(val device: BleDevice) : BleCallbackState()

    class DeviceBonding(val device: BleDevice) : BleCallbackState()

    class DeviceUnBond(val device: BleDevice) : BleCallbackState()

    class DeviceConnectFailure(val device: BleDevice) : BleCallbackState()

    class DeviceRemoved(val device: BleDevice) : BleCallbackState()
}
