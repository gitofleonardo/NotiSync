package io.github.gitofleonardo.notisync.ui.home

import io.github.gitofleonardo.coreservice.data.BondedBleDevice

sealed class SyncItemResult {

    object LoadingDevices : SyncItemResult()

    class RefreshSyncDevice(val devices: List<BondedBleDevice>) : SyncItemResult()

    class AddSyncDevice(val devices: List<BondedBleDevice>) : SyncItemResult()

    class DisconnectSyncDevice(val device: BondedBleDevice) : SyncItemResult()

    class RemoveSyncDevice(val device: BondedBleDevice) : SyncItemResult()
}
