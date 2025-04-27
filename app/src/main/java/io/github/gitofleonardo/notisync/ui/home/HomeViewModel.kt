package io.github.gitofleonardo.notisync.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.viewModelScope
import com.clj.fastble.data.BleDevice
import io.github.gitofleonardo.coreservice.data.BondedBleDevice
import io.github.gitofleonardo.notisync.ui.BaseViewModel
import io.github.gitofleonardo.notisync.ui.BleCallbackState
import io.github.gitofleonardo.notisync.ui.home.SyncItemResult.AddSyncDevice
import io.github.gitofleonardo.notisync.ui.home.SyncItemResult.DisconnectSyncDevice
import io.github.gitofleonardo.notisync.ui.home.SyncItemResult.LoadingDevices
import io.github.gitofleonardo.notisync.ui.home.SyncItemResult.RefreshSyncDevice
import io.github.gitofleonardo.notisync.ui.home.SyncItemResult.RemoveSyncDevice
import kotlinx.coroutines.launch

class HomeViewModel : BaseViewModel() {

    private val callback = BleCallback()
    private val _devices =
        MutableLiveData<SyncItemResult>((AddSyncDevice(emptyList())))

    val devicesResult: LiveData<SyncItemResult>
        get() = _devices

    init {
        bleCallback.observeForever(callback)
    }

    fun refresh() {
        _devices.value = LoadingDevices
        runOnCoreBleService {
            viewModelScope.launch {
                it.bondedDevices.let {
                    _devices.postValue(
                        RefreshSyncDevice(it.toList().sortedBy { it.device.name })
                    )
                }
            }
        }
    }

    fun setDeviceSyncState(device: BleDevice, on: Boolean) {
        runOnCoreBleService {
            viewModelScope.launch {
                it.setSyncState(device, on)
            }
        }
    }

    fun removeDevice(device: BleDevice) {
        runOnCoreBleService {
            viewModelScope.launch {
                it.removeSyncDevice(device)
            }
        }
    }

    fun setDeviceConnectState(device: BleDevice, on: Boolean) {
        runOnCoreBleService {
            viewModelScope.launch {
                if (on) {
                    it.connectDevice(device)
                } else {
                    it.disconnectDevice(device)
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        bleCallback.removeObserver(callback)
    }

    private inner class BleCallback : Observer<BleCallbackState> {
        override fun onChanged(value: BleCallbackState) {
            when (value) {
                is BleCallbackState.DeviceConnected -> {
                    _devices.postValue(
                        AddSyncDevice(
                            value.devices
                                .map { BondedBleDevice(it, true, true) }
                                .sortedBy { it.device.name }
                        )
                    )
                }

                is BleCallbackState.DeviceDisconnected -> {
                    _devices.postValue(
                        DisconnectSyncDevice(BondedBleDevice(value.device, true, false))
                    )
                }

                is BleCallbackState.DeviceRemoved -> {
                    _devices.postValue(
                        RemoveSyncDevice(BondedBleDevice(value.device, false, false))
                    )
                }

                is BleCallbackState.DeviceBonded -> {}
                is BleCallbackState.DeviceBonding -> {}
                is BleCallbackState.DeviceUnBond -> {}
                is BleCallbackState.Scanned -> {}
                is BleCallbackState.Scanning -> {}
                is BleCallbackState.StartScan -> {}
                BleCallbackState.None -> {}
                is BleCallbackState.DeviceConnectFailure -> {}
            }
        }

    }
}