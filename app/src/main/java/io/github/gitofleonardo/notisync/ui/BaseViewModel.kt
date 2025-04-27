package io.github.gitofleonardo.notisync.ui

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clj.fastble.data.BleDevice
import io.github.gitofleonardo.coreservice.CoreBleManagerService
import io.github.gitofleonardo.libservice.ICoreBleListener
import io.github.gitofleonardo.libservice.ICoreBleService
import io.github.gitofleonardo.notisync.NotiSyncApplication
import io.github.gitofleonardo.notisync.ui.BleCallbackState.Scanned
import io.github.gitofleonardo.notisync.ui.BleCallbackState.Scanning
import io.github.gitofleonardo.notisync.ui.BleCallbackState.StartScan
import kotlinx.coroutines.launch
import java.util.function.Consumer

abstract class BaseViewModel : ViewModel(), Observer<BleServiceState>, IBinder.DeathRecipient {
    private val _coreBleService =
        MutableLiveData<BleServiceState>(BleServiceState.Disconnected)
    private val _bleCallback =
        MutableLiveData<BleCallbackState>(BleCallbackState.None)

    private val coreBleListener by lazy { CoreBleListener() }
    private val conn by lazy { Connection() }

    protected val context: Context
        get() = NotiSyncApplication.appContext

    val coreBleServiceState: LiveData<BleServiceState>
        get() = _coreBleService
    val bleCallback: LiveData<BleCallbackState>
        get() = _bleCallback

    val coreBleService: ICoreBleService?
        get() = with(coreBleServiceState.value) {
            return@with when (this) {
                is BleServiceState.Connected -> this.coreBleService
                is BleServiceState.Disconnected -> null
                null -> null
            }
        }

    private val pendingActions = mutableListOf<Consumer<ICoreBleService>>()

    init {
        coreBleServiceState.observeForever(this)
    }

    protected fun runOnCoreBleService(
        cachePendingAction: Boolean = true,
        consumer: Consumer<ICoreBleService>
        ) {
        coreBleService.let {
            if (it == null) {
                if (cachePendingAction) {
                    pendingActions.add(consumer)
                }
            } else {
                consumer.accept(it)
            }
        }
    }

    override fun onChanged(value: BleServiceState) {
        when (value) {
            is BleServiceState.Connected -> {
                value.coreBleService.registerCoreListener(coreBleListener)
                value.binder.linkToDeath(this, 0)
                pendingActions.forEach { action ->
                    action.accept(value.coreBleService)
                }
                pendingActions.clear()
            }

            BleServiceState.Disconnected -> {
                // Retry connection
                viewModelScope.launch {
                    CoreBleManagerService.bind(context, conn)
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        coreBleServiceState.value?.let {
            when (it) {
                is BleServiceState.Connected -> {
                    it.coreBleService.unregisterCoreListener(coreBleListener)
                    it.binder.unlinkToDeath(this, 0)
                }

                BleServiceState.Disconnected -> {
                    // NO-OP
                }
            }
        }
        coreBleServiceState.removeObserver(this)
    }

    override fun binderDied() {
        // Retry connection
        viewModelScope.launch {
            CoreBleManagerService.bind(context, conn)
        }
    }

    private inner class Connection : ServiceConnection {
        override fun onServiceConnected(
            name: ComponentName?,
            service: IBinder?
        ) {
            service?.let {
                _coreBleService.postValue(
                    BleServiceState.Connected(
                        service,
                        ICoreBleService.Stub.asInterface(service)
                    )
                )
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            _coreBleService.postValue(BleServiceState.Disconnected)
        }
    }

    private inner class CoreBleListener : ICoreBleListener.Stub() {
        override fun onStartScan(success: Boolean) {
            _bleCallback.postValue(StartScan(success))
        }

        override fun onScanning(device: BleDevice) {
            _bleCallback.postValue(Scanning(device))
        }

        override fun onScanned(devices: Array<out BleDevice>) {
            _bleCallback.postValue(Scanned(devices.toList()))
        }

        override fun onDeviceConnected(device: Array<BleDevice>) {
            _bleCallback.postValue(BleCallbackState.DeviceConnected(device))
        }

        override fun onDeviceBonded(device: BleDevice) {
            _bleCallback.postValue(BleCallbackState.DeviceBonded(device))
        }

        override fun onDeviceDisconnected(device: BleDevice) {
            _bleCallback.postValue(BleCallbackState.DeviceDisconnected(device))
        }

        override fun onDeviceUnBond(device: BleDevice) {
            _bleCallback.postValue(BleCallbackState.DeviceUnBond(device))
        }

        override fun onDeviceBonding(device: BleDevice) {
            _bleCallback.postValue(BleCallbackState.DeviceBonding(device))
        }

        override fun onDeviceConnectFailure(device: BleDevice) {
            _bleCallback.postValue(BleCallbackState.DeviceConnectFailure(device))
        }

        override fun onDeviceRemoved(device: BleDevice) {
            _bleCallback.postValue(BleCallbackState.DeviceRemoved(device))
        }
    }
}
