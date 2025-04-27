package io.github.gitofleonardo.coreservice

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattCharacteristic.PERMISSION_READ
import android.bluetooth.BluetoothGattCharacteristic.PERMISSION_WRITE
import android.bluetooth.BluetoothGattCharacteristic.PROPERTY_NOTIFY
import android.bluetooth.BluetoothGattCharacteristic.PROPERTY_READ
import android.bluetooth.BluetoothGattCharacteristic.PROPERTY_WRITE
import android.bluetooth.BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import androidx.annotation.RequiresPermission
import io.github.gitofleonardo.coreservice.util.BleMessageChannel
import io.github.gitofleonardo.coreservice.util.MessageCallback
import io.github.gitofleonardo.coreservice.util.buildAndRemoveNotification
import io.github.gitofleonardo.coreservice.util.buildAndSendNotification
import io.github.gitofleonardo.coreservice.util.checkBluetoothConnectPermission
import java.util.UUID

private const val TAG = "BleBroadcastManager"

/**
 * Managing ble advertising, reading [BleMessageProto.BleMessage] from characteristic
 */
class BleBroadcastManager(
    private val context: Context
) : MessageCallback {

    private val bluetoothManager by lazy {
        context.getSystemService(BluetoothManager::class.java)
    }

    private val bluetoothAdapter by lazy {
        bluetoothManager.adapter
    }

    private val bleAdvertiser by lazy { bluetoothAdapter.bluetoothLeAdvertiser }
    private val gattCallback by lazy { GattCallback() }
    private val advertiseCallback by lazy { BleAdvertiseCallback() }

    private val writeCharacteristic by lazy {
        BluetoothGattCharacteristic(
            UUID.fromString(WRITE_UUID),
            PROPERTY_WRITE or PROPERTY_WRITE_NO_RESPONSE,
            PERMISSION_WRITE
        )
    }

    private val notifyCharacteristic by lazy {
        BluetoothGattCharacteristic(
            UUID.fromString(NOTIFY_UUID),
            PROPERTY_READ or PROPERTY_NOTIFY,
            PERMISSION_READ
        )
    }

    private val readChannel by lazy {
        BleMessageChannel().also {
            it.addMessageCallback(this)
        }
    }

    private val gattDescriptor by lazy {
        BluetoothGattDescriptor(
            UUID.fromString(DESCRIPTOR_UUID),
            PERMISSION_WRITE
        )
    }

    private var gattServer: BluetoothGattServer? = null
    private var gattService: BluetoothGattService? = null

    private var broadcastStarted = false

    /**
     * Start advertising this device
     */
    @RequiresPermission(
        allOf = [
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_ADVERTISE
        ]
    )
    fun startBroadcast() {
        if (broadcastStarted) {
            return
        }
        broadcastStarted = true
        val server = bluetoothManager.openGattServer(context, gattCallback)
        val service = BluetoothGattService(
            UUID.fromString(SERVICE_UUID),
            BluetoothGattService.SERVICE_TYPE_PRIMARY
        )
        service.addCharacteristic(writeCharacteristic)
        service.addCharacteristic(notifyCharacteristic)
        server.addService(service)
        gattServer = server
        gattService = service

        val settingsBuilder = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
            .setConnectable(true)
            .setTimeout(0)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            settingsBuilder.setDiscoverable(true)
        }
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addServiceUuid(ParcelUuid(UUID.fromString(SERVICE_UUID)))
            .build()
        val resp = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .build()
        bleAdvertiser.startAdvertising(settingsBuilder.build(), data, resp, advertiseCallback)
    }

    /**
     * Stop advertising this device
     */
    @RequiresPermission(
        allOf = [
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT
        ]
    )
    fun stopBroadcast() {
        if (!broadcastStarted) {
            return
        }
        broadcastStarted = false
        bleAdvertiser.stopAdvertising(advertiseCallback)
        gattService?.let {
            gattServer?.removeService(it)
        }
        gattServer?.close()
        gattServer = null
    }

    override fun onMessageAvailable(
        device: BluetoothDevice,
        msg: BleMessageProto.BleMessage
    ) {
        if (context.checkBluetoothConnectPermission()) {
            Log.d(TAG, "Receive message from ${device.name}, data=$msg")
        }
        when (msg.type) {
            BleMessageProto.MsgType.MSG_TYPE_POST_NOTIFICATION -> {
                context.buildAndSendNotification(device, msg.notification)
            }

            BleMessageProto.MsgType.MSG_TYPE_REMOVE_NOTIFICATION -> {
                context.buildAndRemoveNotification(device, msg.notification)
            }

            BleMessageProto.MsgType.UNRECOGNIZED -> {

            }
        }
    }

    /**
     * Server callback, for handling characteristic bytes read and write request
     * (but actually we don't need read support).
     */
    private inner class GattCallback() : BluetoothGattServerCallback() {

        override fun onConnectionStateChange(device: BluetoothDevice?, status: Int, newState: Int) {
            super.onConnectionStateChange(device, status, newState)
        }

        override fun onCharacteristicReadRequest(
            device: BluetoothDevice?,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic?
        ) {
            super.onCharacteristicReadRequest(device, requestId, offset, characteristic)
            if (!context.checkBluetoothConnectPermission()) {
                return
            }
            gattServer?.sendResponse(
                device,
                requestId,
                BluetoothGatt.GATT_SUCCESS,
                offset,
                characteristic?.value
            )
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic?,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ) {
            super.onCharacteristicWriteRequest(
                device,
                requestId,
                characteristic,
                preparedWrite,
                responseNeeded,
                offset,
                value
            )
            // Post bytes to readChannel
            value?.let { readChannel.read(device, it) }
            if (responseNeeded && context.checkBluetoothConnectPermission()) {
                gattServer?.sendResponse(
                    device,
                    requestId,
                    BluetoothGatt.GATT_SUCCESS,
                    offset,
                    null
                )
            }
        }
    }

    private inner class BleAdvertiseCallback() : AdvertiseCallback() {

        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            super.onStartSuccess(settingsInEffect)
            Log.d(TAG, "Advertise start success")
        }

        override fun onStartFailure(errorCode: Int) {
            super.onStartFailure(errorCode)
            Log.d(TAG, "Advertise start failure, code = $errorCode")
        }
    }

    companion object {

        const val SERVICE_UUID = "2fde19cb-3a12-4297-83ac-b997ce17754b"
        const val WRITE_UUID = "70b19d64-0da0-4dbf-bb04-68b1c5a0f804"
        const val NOTIFY_UUID = "1049bce6-8ee2-44e0-8c28-d97c6a1c461c"
        const val DESCRIPTOR_UUID = "33fa70b4-adb1-423e-a91b-99f03aa9b444"
    }
}