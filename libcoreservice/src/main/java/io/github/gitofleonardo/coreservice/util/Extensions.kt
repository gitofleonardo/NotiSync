package io.github.gitofleonardo.coreservice.util

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Notification.EXTRA_SUB_TEXT
import android.app.Notification.EXTRA_TEXT
import android.app.Notification.EXTRA_TITLE
import android.app.Notification.EXTRA_TITLE_BIG
import android.app.NotificationChannel
import android.app.NotificationManager
import android.bluetooth.BluetoothDevice
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.service.notification.StatusBarNotification
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.room.Room
import io.github.gitofleonardo.coreservice.BleMessageProto
import io.github.gitofleonardo.coreservice.NotificationListener
import io.github.gitofleonardo.coreservice.database.ServiceDatabase
import io.github.gitofleonardo.libservice.R
import java.util.Objects

/**
 * Permissions required for bluetooth
 */
private val BLE_PERMISSIONS = arrayOf<String>(
    Manifest.permission.BLUETOOTH,
    Manifest.permission.BLUETOOTH_ADMIN,
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
)

@RequiresApi(Build.VERSION_CODES.S)
private val BLE_PERMISSIONS_API_31 = arrayOf<String>(
    Manifest.permission.BLUETOOTH_SCAN,
    Manifest.permission.BLUETOOTH_CONNECT,
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.BLUETOOTH_ADVERTISE
)

/**
 * Permissions for notification posting
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private val NOTIFICATION_PERMISSIONS = arrayOf<String>(
    Manifest.permission.POST_NOTIFICATIONS
)

private const val NOTIFICATION_ENABLED_LISTENERS = "enabled_notification_listeners"
private const val EXTRA_FRAGMENT_ARG_KEY = ":settings:fragment_args_key"
private const val EXTRA_SHOW_FRAGMENT_ARGS = ":settings:show_fragment_args"

const val SERVICE_PREFERENCE_NAME = "service_preference"

const val KEY_BLE_WORK_MODE = "ble_work_mode"
const val KEY_SYNC_ON_REMOVAL = "sync_on_notification_removal"

/**
 * Check has bluetooth connect permission
 */
fun Context.checkBluetoothConnectPermission(): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
    } else {
        true
    }

/**
 * Check has bluetooth advertise permission
 */
fun Context.checkBluetoothAdvertisePermission(): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) ==
                PackageManager.PERMISSION_GRANTED
    } else {
        true
    }

/**
 * Check has notification post permission
 */
fun Context.checkPostNotificationPermission(): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }

/**
 * Check and request all necessary permissions needed for this app to run
 */
fun Activity.checkAndRequestPermissions(requestCode: Int): Boolean {
    val permissions = collectBlePermissions()
    if (!allPermissionsGranted(permissions)) {
        ActivityCompat.requestPermissions(this, permissions, requestCode)
        return false
    }
    return true
}

/**
 * Check whether all necessary permissions granted for this app to run
 */
fun Context.allBlePermissionsGranted(): Boolean {
    return allPermissionsGranted(collectBlePermissions())
}

/**
 * Collect all ble required permissions
 */
private fun collectBlePermissions(): Array<String> {
    val permissions = mutableListOf(*BLE_PERMISSIONS)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        permissions.addAll(BLE_PERMISSIONS_API_31)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        permissions.addAll(NOTIFICATION_PERMISSIONS)
    }
    return permissions.toTypedArray()
}

private fun Context.allPermissionsGranted(permissions: Array<String>): Boolean {
    for (permission in permissions) {
        if (ContextCompat.checkSelfPermission(this, permission)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
    }
    return true
}

/**
 * Build notification from proto message, then send it
 */
@SuppressLint("WrongConstant")
fun Context.buildAndSendNotification(
    device: BluetoothDevice,
    notification: BleMessageProto.StatusBarNotification
) {
    val deviceName = device.name
    // Channel id by device-package-channelId
    val channelId = device.address + notification.pkg + notification.notification.channelId
    val builder = NotificationCompat.Builder(this, channelId)
        .setSmallIcon(R.drawable.notification)
        .setContentText(notification.notification.contentText)
        .setContentTitle(
            "${notification.notification.title} (${notification.notification.appName})"
        )
        .setPriority(notification.notification.priority)
        .setVisibility(notification.notification.visibility)

    val notificationManager = getSystemService(NotificationManager::class.java)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
        notificationManager.getNotificationChannel(channelId) == null
    ) {
        // Create new channel
        val name = getString(
            R.string.channel_name,
            "${deviceName}-${notification.notification.appName}"
        )
        val descriptionText = getString(R.string.channel_description, deviceName)
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val channel = NotificationChannel(channelId, name, importance).apply {
            description = descriptionText
        }
        notificationManager.createNotificationChannel(channel)
    }

    if (checkPostNotificationPermission()) {
        val notificationId = Objects.hash(notification.id, channelId)
        NotificationManagerCompat.from(this).apply {
            notify(notificationId, builder.build())
        }
    }
}

/**
 * Remove notification
 */
fun Context.buildAndRemoveNotification(
    device: BluetoothDevice,
    notification: BleMessageProto.StatusBarNotification
) {
    val notificationManager = getSystemService(NotificationManager::class.java)
    // Channel id by device-package-channelId
    val channelId = device.address + notification.pkg + notification.notification.channelId
    val notificationId = Objects.hash(notification.id, channelId)
    notificationManager.cancel(notificationId)
}

/**
 * Check has notification listener permission
 */
fun Context.checkNotificationListenerPermission(): Boolean {
    val listeners = Settings.Secure.getString(contentResolver, NOTIFICATION_ENABLED_LISTENERS)
    val myComponent = ComponentName(this, NotificationListener::class.java)
    return listeners.contains(myComponent.flattenToString())
}

/**
 * Start activity to acquire notification listener permission
 */
fun Activity.requestNotificationListenerPermission() {
    val cn = ComponentName(this, NotificationListener::class.java)
    val showFragmentArgs = Bundle()
    showFragmentArgs.putString(EXTRA_FRAGMENT_ARG_KEY, cn.flattenToString())

    val intent: Intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        .putExtra(EXTRA_FRAGMENT_ARG_KEY, cn.flattenToString())
        .putExtra(EXTRA_SHOW_FRAGMENT_ARGS, showFragmentArgs)
    startActivity(intent)
}

/**
 * Convert a [StatusBarNotification] to proto message [BleMessageProto.BleMessage]
 */
fun StatusBarNotification.toProtoMessage(context: Context): BleMessageProto.BleMessage {
    val notification = BleMessageProto.Notification.newBuilder()
        .setTitle(notification.extras.getCharSequence(EXTRA_TITLE).toString())
        .setBigTitle(
            notification.extras.getCharSequence(EXTRA_TITLE_BIG).toString()
        )
        .setContentText(notification.extras.getCharSequence(EXTRA_TEXT).toString())
        .setSubText(notification.extras.getCharSequence(EXTRA_SUB_TEXT).toString())
        .setPriority(notification.priority)
        .setFlags(notification.flags)
        .setVisibility(notification.visibility)
        .setAppName(context.getAppName(packageName))
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        notification.setChannelId(notification.channelId)
    }
    val sbnNotification = BleMessageProto.StatusBarNotification.newBuilder()
        .setPkg(packageName)
        .setId(id)
        .setNotification(notification)
    val bleMessage = BleMessageProto.BleMessage.newBuilder()
        .setType(BleMessageProto.MsgType.MSG_TYPE_POST_NOTIFICATION)
        .setNotification(sbnNotification)
        .build()
    return bleMessage
}

/**
 * Convert a [StatusBarNotification] to proto message for removing remote notification
 */
fun StatusBarNotification.toRemovalProtoMessage(): BleMessageProto.BleMessage {
    val sbnNotification = BleMessageProto.StatusBarNotification.newBuilder()
        .setPkg(packageName)
        .setId(id)
    val bleMessage = BleMessageProto.BleMessage.newBuilder()
        .setType(BleMessageProto.MsgType.MSG_TYPE_REMOVE_NOTIFICATION)
        .setNotification(sbnNotification)
        .build()
    return bleMessage
}

private fun Context.getAppName(pkg: String): String {
    return try {
        packageManager.getApplicationInfo(pkg, 0).loadLabel(packageManager).toString()
    } catch (_: Exception) {
        ""
    }
}

private lateinit var appServiceDb: ServiceDatabase

fun Context.getServiceDatabase(): ServiceDatabase {
    synchronized(applicationContext) {
        if (!::appServiceDb.isInitialized) {
            appServiceDb = Room.databaseBuilder(
                this,
                ServiceDatabase::class.java,
                "service_database"
            ).build()
        }
        return appServiceDb
    }
}
