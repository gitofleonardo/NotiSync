package io.github.gitofleonardo.coreservice

import android.app.Service
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.os.Handler
import android.os.IBinder
import android.os.Message
import android.os.RemoteCallbackList
import android.os.RemoteException
import android.service.notification.StatusBarNotification
import android.util.Log
import com.clj.fastble.BleManager
import com.clj.fastble.callback.BleGattCallback
import com.clj.fastble.callback.BleScanCallback
import com.clj.fastble.callback.BleWriteCallback
import com.clj.fastble.data.BleDevice
import com.clj.fastble.exception.BleException
import com.clj.fastble.scan.BleScanRuleConfig
import io.github.gitofleonardo.coreservice.data.BondedBleDevice
import io.github.gitofleonardo.coreservice.util.BleMessageChannel
import io.github.gitofleonardo.coreservice.util.Executors
import io.github.gitofleonardo.coreservice.util.KEY_BLE_WORK_MODE
import io.github.gitofleonardo.coreservice.util.KEY_SYNC_ON_REMOVAL
import io.github.gitofleonardo.coreservice.util.checkBluetoothAdvertisePermission
import io.github.gitofleonardo.coreservice.util.checkBluetoothConnectPermission
import io.github.gitofleonardo.coreservice.util.toProtoMessage
import io.github.gitofleonardo.coreservice.util.toRemovalProtoMessage
import io.github.gitofleonardo.libservice.ICoreBleListener
import io.github.gitofleonardo.libservice.ICoreBleService
import io.github.gitofleonardo.libservice.R
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "CoreBle"

private const val BLE_SCAN_TIMEOUT = 10000L

/**
 * For posting notification
 */
private const val MSG_NOTIFICATION_POSTED = 0

/**
 * For removing notification
 */
private const val MSG_NOTIFICATION_REMOVED = 1

/**
 * For start scanning devices
 */
private const val MSG_SCAN_DEVICES = 2

/**
 * Called when scanning started
 */
private const val MSG_START_SCANNING = 3

/**
 * Called when scanning finished
 */
private const val MSG_SCAN_FINISHED = 4

/**
 * Called while scanning device
 */
private const val MSG_SCANNING = 5

/**
 * For registering core callback
 */
private const val MSG_REGISTER_CORE_CALLBACK = 6

/**
 * For unregistering core callback
 */
private const val MSG_UNREGISTER_CORE_CALLBACK = 7

/**
 * For starting advertise mode
 */
private const val MSG_START_ADVERTISING = 8

/**
 * For stopping advertise mode
 */
private const val MSG_STOP_ADVERTISING = 9

/**
 * For connect to a new device
 */
private const val MSG_CONNECT_DEVICE = 10

/**
 * Called when device connected
 */
private const val MSG_DEVICE_CONNECTED = 11

/**
 * Called when device disconnected
 */
private const val MSG_DEVICE_DISCONNECTED = 12

/**
 * Called when failed to connect to a device
 */
private const val MSG_DEVICE_CONNECT_FAILED = 13

/**
 * Called when bond state changed
 */
private const val MSG_BOND_STATE_CHANGED = 14

/**
 * Service call only, for loading all bonded devices
 */
private const val MSG_INIT_BONDED_DEVICES = 15

/**
 * For setting sync state
 */
private const val MSG_SET_DEVICE_SYNC_STATE = 16

/**
 * For disconnect device
 */
private const val MSG_DISCONNECT_DEVICE = 17

/**
 * For removing device
 */
private const val MSG_REMOVE_SYNC_DEVICE = 18

/**
 * Current ble scan state
 */
private enum class ScanState {

    /**
     * No scanning ever
     */
    UnInitialized,

    /**
     * Currently scanning
     */
    Scanning,

    /**
     * Performed scanning at least once
     */
    Scanned
}

/**
 * Core ble manager
 */
class CoreBleManagerService : Service(), SharedPreferences.OnSharedPreferenceChangeListener {

    /**
     * Worker handler, all jobs should be posted here, and leave the main thread lightweight
     */
    private val workerHandler by lazy {
        Handler(Executors.BLE_CORE_MODEL_EXECUTOR.looper, this::handleMessage)
    }

    /**
     * Current ble scan rule
     */
    private lateinit var scanRule: BleScanRuleConfig

    /**
     * Current scanning state
     */
    private var scanState = ScanState.UnInitialized


    /**
     * Client callback, for example, used by UI
     */
    private val coreCallbacks by lazy { RemoteCallbackList<ICoreBleListener>() }
    private val bleManager by lazy { BleManager.getInstance() }
    private val bleScanCallback by lazy { ScanCallback() }
    private val bleConnectCallback by lazy { GattDeviceConnectCallback() }
    private val notificationWriteCallback by lazy { NotificationWriteCallback() }

    /**
     * Remote server stub
     */
    private val serviceImpl by lazy { CoreBleServiceImpl() }
    private val advertiseManager by lazy { BleBroadcastManager(this) }

    /**
     * Channel for writing characteristic
     */
    private val writeChannel by lazy { BleMessageChannel() }
    private val bondStateReceiver by lazy { BondStateChangeReceiver() }

    private val model by lazy { ServiceModel(this, bleManager) }
    private val syncRemoval by lazy { AtomicBoolean(true) }

    override fun onCreate() {
        super.onCreate()
        registerReceiver(bondStateReceiver, IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        })
        model.registerOnSharedPreferenceChangeListener(this)
        initBondedDevices()
        if (model.receiveModeEnabled()) {
            serviceImpl.startAdvertising()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(bondStateReceiver)
        serviceImpl.stopAdvertising()
        bleManager.disconnectAllDevice()
        bleManager.destroy()
        model.unregisterOnSharedPreferenceChangeListener(this)
        model.destroy()
    }

    override fun onBind(intent: Intent): IBinder {
        return serviceImpl
    }

    private fun handleMessage(msg: Message): Boolean {
        return when (msg.what) {
            MSG_NOTIFICATION_POSTED -> {
                val sbn = msg.obj as StatusBarNotification
                if (model.getFilteredApps().contains(sbn.packageName)) {
                    // Notification filtered
                    return true
                }

                /**
                 * Fast forward to other devices
                 */
                val bytes = sbn.toProtoMessage(this).toByteArray()
                model.getConnectedDevices().forEach {
                    writeChannel.write(
                        bleManager,
                        it.first,
                        BleBroadcastManager.SERVICE_UUID,
                        BleBroadcastManager.WRITE_UUID,
                        bytes,
                        notificationWriteCallback
                    )
                }
                true
            }

            MSG_NOTIFICATION_REMOVED -> {
                if (!syncRemoval.get()) {
                    return true
                }
                val sbn = msg.obj as StatusBarNotification
                val bytes = sbn.toRemovalProtoMessage().toByteArray()
                model.getConnectedDevices().forEach {
                    writeChannel.write(
                        bleManager,
                        it.first,
                        BleBroadcastManager.SERVICE_UUID,
                        BleBroadcastManager.WRITE_UUID,
                        bytes,
                        notificationWriteCallback
                    )
                }
                true
            }

            MSG_START_SCANNING -> {
                val success = msg.obj as Boolean
                // Notify all listeners
                broadcastAllListeners { callback ->
                    callback.safeInvoke {
                        it.onStartScan(success)
                    }
                }
                if (!success) {
                    // Failed to scan
                    scanState = ScanState.Scanned
                    return true
                }
                model.clearScanningDevices()
                scanState = ScanState.Scanning
                true
            }

            MSG_SCANNING -> {
                val device = msg.obj as BleDevice
                if (model.addScanningDevice(device)) {
                    // Notify all listeners
                    broadcastAllListeners { callback ->
                        callback.safeInvoke {
                            it.onScanning(device)
                        }
                    }
                }
                true
            }

            MSG_SCAN_FINISHED -> {
                val result = msg.obj as List<*>
                result.forEach {
                    if (it is BleDevice) {
                        model.addScanningDevice(it)
                    }
                }
                scanState = ScanState.Scanned
                // Notify all listeners
                broadcastAllListeners { callback ->
                    callback.safeInvoke {
                        it.onScanned(
                            model.getScanningDevices().toTypedArray()
                        )
                    }
                }
                true
            }

            /**
             * Sync current state once a callback is registered
             */
            MSG_REGISTER_CORE_CALLBACK -> {
                val callback = msg.obj as ICoreBleListener
                coreCallbacks.register(callback)
                when (scanState) {
                    ScanState.UnInitialized -> {
                        // NO-OP
                    }

                    ScanState.Scanning -> {
                        callback.safeInvoke {
                            it.onStartScan(true)
                        }
                        model.getScanningDevices().forEach { device ->
                            callback.safeInvoke {
                                it.onScanning(device)
                            }
                        }
                    }

                    ScanState.Scanned -> {
                        callback.safeInvoke {
                            it.onScanned(model.getScanningDevices().toTypedArray())
                        }
                    }
                }
                callback.safeInvoke {
                    val connectedDevices = model.getConnectedDevices()
                    it.onDeviceConnected(connectedDevices.map { it.first }.toTypedArray())
                }
                true
            }

            MSG_SCAN_DEVICES -> {
                if (scanState != ScanState.Scanning) {
                    scanDevices()
                }
                true
            }

            MSG_UNREGISTER_CORE_CALLBACK -> {
                val callback = msg.obj as ICoreBleListener
                coreCallbacks.unregister(callback)
                true
            }

            MSG_CONNECT_DEVICE -> {
                val device = msg.obj as BleDevice
                if (!checkBluetoothConnectPermission()) {
                    return true
                }
                val bonded = device.device.bondState == BluetoothDevice.BOND_BONDED
                if (!bonded) {
                    Log.w(TAG, "You should bond the device first then connect to it.")
                    return true
                }
                bleManager.connect(device, bleConnectCallback)
                true
            }

            MSG_DISCONNECT_DEVICE -> {
                val device = msg.obj as BleDevice
                if (!checkBluetoothConnectPermission()) {
                    return true
                }
                bleManager.disconnect(device)
                true
            }

            MSG_DEVICE_CONNECTED -> {
                val device = msg.obj as BleDevice
                model.addConnectedDevice(device)
                broadcastAllListeners { listener ->
                    listener.safeInvoke {
                        it.onDeviceConnected(arrayOf(device))
                    }
                }
                true
            }

            MSG_DEVICE_DISCONNECTED -> {
                val device = msg.obj as BleDevice
                model.removeConnectedDevice(device)
                broadcastAllListeners { listener ->
                    listener.safeInvoke {
                        it.onDeviceDisconnected(device)
                    }
                }
                true
            }

            MSG_DEVICE_CONNECT_FAILED -> {
                val device = msg.obj as BleDevice
                broadcastAllListeners { listener ->
                    listener.safeInvoke {
                        it.onDeviceConnectFailure(device)
                    }
                }
                true
            }

            MSG_START_ADVERTISING -> {
                if (!this@CoreBleManagerService.checkBluetoothConnectPermission() ||
                    !this@CoreBleManagerService.checkBluetoothAdvertisePermission()
                ) {
                    return true
                }
                advertiseManager.startBroadcast()
                true
            }

            MSG_STOP_ADVERTISING -> {
                if (!this@CoreBleManagerService.checkBluetoothConnectPermission() ||
                    !this@CoreBleManagerService.checkBluetoothAdvertisePermission()
                ) {
                    return true
                }
                advertiseManager.stopBroadcast()
                true
            }

            MSG_BOND_STATE_CHANGED -> {
                val p = msg.obj as Pair<BluetoothDevice, Int>
                broadcastAllListeners { listener ->
                    listener.safeInvoke {
                        when (p.second) {
                            BluetoothDevice.BOND_BONDED -> {
                                listener.onDeviceBonded(BleDevice(p.first))
                            }

                            BluetoothDevice.BOND_BONDING -> {
                                listener.onDeviceBonding(BleDevice(p.first))
                            }

                            BluetoothDevice.BOND_NONE -> {
                                listener.onDeviceUnBond(BleDevice(p.first))
                            }
                        }
                    }
                }
                true
            }

            MSG_INIT_BONDED_DEVICES -> {
                model.getBondedDevices().forEach {
                    if (!it.second.syncOn) {
                        return@forEach
                    }
                    workerHandler.obtainMessage(
                        MSG_CONNECT_DEVICE,
                        it.first
                    ).sendToTarget()
                }
                true
            }

            MSG_SET_DEVICE_SYNC_STATE -> {
                val device = msg.obj as BleDevice
                val on = msg.arg1 == 1
                model.setSyncState(device, on)
                if (on) {
                    workerHandler.obtainMessage(MSG_CONNECT_DEVICE, device)
                        .sendToTarget()
                } else {
                    workerHandler.obtainMessage(MSG_DISCONNECT_DEVICE, device)
                        .sendToTarget()
                }
                true
            }

            MSG_REMOVE_SYNC_DEVICE -> {
                val device = msg.obj as BleDevice
                model.removeSyncDevice(device)
                workerHandler.obtainMessage(MSG_DISCONNECT_DEVICE, device)
                    .sendToTarget()
                broadcastAllListeners { listener ->
                    listener.safeInvoke {
                        it.onDeviceRemoved(device)
                    }
                }
                true
            }

            else -> false
        }
    }


    /**
     * Broadcast action to all connected remotes
     */
    private fun broadcastAllListeners(consumer: (ICoreBleListener) -> Unit) {
        val count = coreCallbacks.beginBroadcast()
        for (i in 0 until count) {
            consumer(coreCallbacks.getBroadcastItem(i))
        }
        coreCallbacks.finishBroadcast()
    }

    private fun scanDevices() {
        if (!::scanRule.isInitialized) {
            scanRule = BleScanRuleConfig.Builder()
                .setAutoConnect(true)
                .setScanTimeOut(BLE_SCAN_TIMEOUT)
                .setServiceUuids(
                    arrayOf(
                        UUID.fromString(BleBroadcastManager.SERVICE_UUID)
                    )
                )
                .build()
            bleManager.initScanRule(scanRule)
        }
        bleManager.scan(bleScanCallback)
    }

    private fun initBondedDevices() {
        workerHandler.obtainMessage(MSG_INIT_BONDED_DEVICES).sendToTarget()
    }

    override fun onSharedPreferenceChanged(
        sharedPreferences: SharedPreferences,
        key: String?
    ) = when (key) {
        KEY_BLE_WORK_MODE -> {
            when (sharedPreferences.getString(
                key,
                getString(R.string.mode_ble_sending_only)
            )) {
                getString(R.string.mode_ble_sending_only) -> {
                    serviceImpl.stopAdvertising()
                }

                getString(R.string.mode_ble_sending_n_receiving) -> {
                    serviceImpl.startAdvertising()
                }

                else -> {}
            }
        }

        KEY_SYNC_ON_REMOVAL -> {
            syncRemoval.set(sharedPreferences.getBoolean(key, true))
        }

        else -> {}
    }

    /**
     * Device scan callback, post all to [workerHandler] to run
     */
    private inner class ScanCallback : BleScanCallback() {

        override fun onScanFinished(scanResultList: List<BleDevice?>?) {
            val result = scanResultList
                ?.filter { it != null }
                ?.map { it!! } ?: emptyList()
            workerHandler.obtainMessage(MSG_SCAN_FINISHED, result).sendToTarget()
        }

        override fun onScanStarted(success: Boolean) {
            workerHandler.obtainMessage(MSG_START_SCANNING, success).sendToTarget()
        }

        override fun onScanning(bleDevice: BleDevice?) {
            bleDevice?.let {
                workerHandler.obtainMessage(MSG_SCANNING, it).sendToTarget()
            }
        }
    }

    /**
     * Device connect callback, post all to [workerHandler] to run
     */
    private inner class GattDeviceConnectCallback : BleGattCallback() {
        override fun onStartConnect() {
        }

        override fun onConnectFail(
            bleDevice: BleDevice?,
            exception: BleException?
        ) {
            Log.d(TAG, "Failed to connect to device ${bleDevice?.name}-${bleDevice?.mac}")
            bleDevice?.let {
                workerHandler.obtainMessage(MSG_DEVICE_CONNECT_FAILED, it).sendToTarget()
            }
        }

        override fun onConnectSuccess(
            bleDevice: BleDevice?,
            gatt: BluetoothGatt?,
            status: Int
        ) {
            Log.d(TAG, "Connected to device ${bleDevice?.name}-${bleDevice?.mac}")
            bleDevice?.let {
                workerHandler.obtainMessage(MSG_DEVICE_CONNECTED, it).sendToTarget()
            }
        }

        override fun onDisConnected(
            isActiveDisConnected: Boolean,
            device: BleDevice?,
            gatt: BluetoothGatt?,
            status: Int
        ) {
            Log.d(TAG, "Disconnected from device ${device?.name}-${device?.mac}")
            device?.let {
                workerHandler.obtainMessage(MSG_DEVICE_DISCONNECTED, it).sendToTarget()
            }
        }
    }

    private inner class NotificationWriteCallback : BleWriteCallback() {
        override fun onWriteSuccess(
            current: Int,
            total: Int,
            justWrite: ByteArray?
        ) {
            Log.d(TAG, "Write $current of total $total, ${justWrite?.size} bytes wrote to device")
        }

        override fun onWriteFailure(exception: BleException) {
            Log.e(TAG, "Write notification failed: $exception")
        }
    }

    /**
     * Core ble service server side implementation
     */
    private inner class CoreBleServiceImpl : ICoreBleService.Stub() {

        override fun onNotificationPosted(notification: StatusBarNotification) {
            workerHandler.obtainMessage(
                MSG_NOTIFICATION_POSTED,
                notification
            ).sendToTarget()
        }

        override fun onNotificationRemoved(notification: StatusBarNotification) {
            workerHandler.obtainMessage(
                MSG_NOTIFICATION_REMOVED,
                notification
            ).sendToTarget()
        }

        override fun scanDevices() {
            workerHandler.obtainMessage(MSG_SCAN_DEVICES).sendToTarget()
        }

        override fun registerCoreListener(callback: ICoreBleListener) {
            workerHandler.obtainMessage(MSG_REGISTER_CORE_CALLBACK, callback).sendToTarget()
        }

        override fun unregisterCoreListener(callback: ICoreBleListener) {
            workerHandler.obtainMessage(MSG_UNREGISTER_CORE_CALLBACK, callback).sendToTarget()
        }

        override fun startAdvertising() {
            workerHandler.obtainMessage(MSG_START_ADVERTISING).sendToTarget()
        }

        override fun stopAdvertising() {
            workerHandler.obtainMessage(MSG_STOP_ADVERTISING).sendToTarget()
        }

        override fun connectDevice(device: BleDevice) {
            workerHandler.obtainMessage(MSG_CONNECT_DEVICE, device).sendToTarget()
        }

        override fun getBondedDevices(): Array<out BondedBleDevice> {
            synchronized(model) {
                val connectedAddresses = model.getConnectedDevices()
                    .map { it.first.mac }
                return model.getBondedDevices()
                    .map {
                        BondedBleDevice(
                            BleDevice(it.first.device),
                            it.second.syncOn,
                            connectedAddresses.contains(it.first.mac)
                        )
                    }
                    .toTypedArray()
            }
        }

        override fun getConnectedDevices(): Array<out BondedBleDevice> {
            return model.getConnectedDevices()
                .map { BondedBleDevice(it.first, it.second.syncOn, true) }
                .toTypedArray()
        }

        override fun setSyncState(device: BleDevice, on: Boolean) {
            workerHandler.obtainMessage(MSG_SET_DEVICE_SYNC_STATE, device).also {
                it.arg1 = if (on) 1 else 0
                it.sendToTarget()
            }
        }

        override fun removeSyncDevice(device: BleDevice?) {
            workerHandler.obtainMessage(MSG_REMOVE_SYNC_DEVICE, device).sendToTarget()
        }

        override fun disconnectDevice(device: BleDevice) {
            workerHandler.obtainMessage(MSG_DISCONNECT_DEVICE, device).sendToTarget()
        }
    }

    private inner class BondStateChangeReceiver : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent?) {
            when (intent?.action) {
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    val device =
                        intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    val bondState =
                        intent.getIntExtra(
                            BluetoothDevice.EXTRA_BOND_STATE,
                            BluetoothDevice.BOND_NONE
                        )
                    workerHandler.obtainMessage(
                        MSG_BOND_STATE_CHANGED,
                        Pair(device, bondState)
                    ).sendToTarget()
                }
            }
        }
    }

    companion object {

        fun bind(context: Context, connection: ServiceConnection) {
            val intent = Intent(context, CoreBleManagerService::class.java)
            context.bindService(intent, connection, BIND_AUTO_CREATE)
        }

        private fun ICoreBleListener.safeInvoke(consumer: (ICoreBleListener) -> Unit) {
            try {
                consumer(this)
            } catch (e: RemoteException) {
                Log.e(TAG, "Failed to notify callback $this", e)
            }
        }
    }
}
