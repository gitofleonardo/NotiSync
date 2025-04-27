package io.github.gitofleonardo.coreservice.util

import android.bluetooth.BluetoothDevice
import android.util.Log
import com.clj.fastble.BleManager
import com.clj.fastble.callback.BleWriteCallback
import com.clj.fastble.data.BleDevice
import io.github.gitofleonardo.coreservice.BleMessageProto

/**
 * Callback for [BleMessageProto.BleMessage] construction
 */
interface MessageCallback {

    /**
     * Called when an entire [BleMessageProto.BleMessage] is constructed
     */
    fun onMessageAvailable(device: BluetoothDevice, msg: BleMessageProto.BleMessage)
}

private const val TAG = "BleMessageChannel"

/**
 * Ble message channel for reading and writing message bytes
 */
class BleMessageChannel {

    private val messageCallbacks by lazy { ArrayList<MessageCallback>() }
    private var currentBytes: MutableList<Byte>? = null

    /**
     * Read bytes to construct a [BleMessageProto.BleMessage]
     */
    fun read(device: BluetoothDevice, bytes: ByteArray) {
        try {
            val frame = bytes.frame
            val frameCount = bytes.frameCount
            if (frame == 0) {
                // New message
                currentBytes.let { currBytes ->
                    if (currBytes != null) {
                        Log.w(TAG, "Discard message of ${currBytes.size} bytes")
                    }
                }
                // Just drop it!
                currentBytes = mutableListOf()
            } else if (currentBytes == null) {
                Log.d(TAG, "Unexpected ${bytes.size} bytes")
                return
            }
            val currBytes = currentBytes!!
            currBytes.addAll(bytes.slice(4 until bytes.size))
            Log.d(TAG, "Read ${bytes.size} bytes of message, $frame of $frameCount")
            if (frame == frameCount - 1) {
                currentBytes = null
                val message = BleMessageProto.BleMessage.parseFrom(currBytes.toByteArray())
                messageCallbacks.forEach {
                    it.onMessageAvailable(device, message)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error read bytes $bytes.", e)
        }
    }

    /**
     * Write bytes to given service and characteristic
     */
    fun write(
        bleManager: BleManager,
        device: BleDevice,
        serviceUUID: String,
        writeUUID: String,
        content: ByteArray,
        callback: BleWriteCallback
    ) {
        val byteLists = content.toList().chunked(bleManager.splitWriteNum - 4)
        val frameCount = byteLists.size
        val bytesToSend = mutableListOf<Byte>()
        byteLists.forEachIndexed { index, bytes ->
            val frameBytes = intToBytes(index)
            val frameCountBytes = intToBytes(frameCount)
            bytes.toMutableList().apply {
                addAll(0, frameCountBytes.toList())
                addAll(0, frameBytes.toList())
            }.also {
                bytesToSend.addAll(it)
            }
        }
        bleManager.write(
            device,
            serviceUUID,
            writeUUID,
            bytesToSend.toByteArray(),
            true,
            true,
            10,
            callback
        )
    }

    /**
     * Add a callback for listening [BleMessageProto.BleMessage] construction
     */
    fun addMessageCallback(callback: MessageCallback) {
        messageCallbacks.add(callback)
    }

    companion object {

        private val ByteArray.frameCount: Int
            get() = bytesToInt(this[2], this[3])

        private val ByteArray.frame: Int
            get() = bytesToInt(this[0], this[1])

        private fun bytesToInt(h: Byte, l: Byte): Int {
            val high = h.toInt() shl 8
            val low = l.toInt()
            val res = low + high
            return if (res < 0)
                res + 65536
            else
                res
        }

        private fun intToBytes(num: Int): ByteArray {
            val high = ((num shr 8) and 0xff).toByte()
            val low = (num and 0xff).toByte()
            return byteArrayOf(high, low)
        }
    }
}