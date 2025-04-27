package io.github.gitofleonardo.notisync.ui.device

import androidx.lifecycle.viewModelScope
import com.clj.fastble.data.BleDevice
import io.github.gitofleonardo.notisync.ui.BaseViewModel
import kotlinx.coroutines.launch

class NewDeviceViewModel : BaseViewModel() {

    fun scanDevices() {
        runOnCoreBleService {
            viewModelScope.launch {
                it.scanDevices()
            }
        }
    }

    fun connectDevice(device: BleDevice) {
        runOnCoreBleService {
            viewModelScope.launch {
                it.connectDevice(device)
            }
        }
    }
}
