package io.github.gitofleonardo.coreservice

import android.app.Notification
import android.content.ComponentName
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Message
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.text.TextUtils
import android.util.Log
import androidx.annotation.AnyThread
import androidx.annotation.WorkerThread
import io.github.gitofleonardo.coreservice.util.Executors
import io.github.gitofleonardo.libservice.ICoreBleService
import java.util.Objects

private const val TAG = "NotificationListener"

/**
 * Delay for reconnecting to core service
 */
private const val BLE_SERVICE_RECONNECT_DELAY = 5000L

/**
 * Connect to ble core
 */
private const val MSG_CONNECT_BLE_CORE = 0

/**
 * Fetch all current notifications and send to ble core
 */
private const val MSG_NOTIFICATION_FULL_REFRESH = 1

/**
 * When a notification is posted
 */
private const val MSG_NOTIFICATION_POSTED = 2

/**
 * When a notification is removed
 */
private const val MSG_NOTIFICATION_REMOVED = 3

/**
 * Flush all pending notifications to ble core
 */
private const val MSG_FLUSH_PENDING_NOTIFICATIONS = 4

/**
 * Inner enum for notification state type
 */
private enum class Action {

    /**
     * Indicating a notification is posted
     */
    Posted,

    /**
     * Indicating a notification is removed
     */
    Removed
}

/**
 * Notification listener for listening all posted notifications, and transfer to core ble service.
 */
class NotificationListener : NotificationListenerService(), ServiceConnection {

    /**
     * Handler for processing notifications
     */
    private val workerHandler by lazy {
        Handler(Executors.NOTIFICATION_MODEL_EXECUTOR.looper, this::handleWorkerMessage)
    }

    /**
     * Pending notifications, when lost connection when ble core, all notification states will be
     * stored here, and will be flushed once ble core is connected.
     */
    private val pendingNotifications =
        mutableMapOf<Action, Map<Int, StatusBarNotification>>()

    /**
     * Ble core service
     */
    private var coreBleService: ICoreBleService? = null

    @Volatile
    private var connected = false

    override fun onCreate() {
        super.onCreate()
        scheduleLinkToBleCore(0)
    }

    private fun handleWorkerMessage(msg: Message): Boolean {
        return when (msg.what) {
            MSG_CONNECT_BLE_CORE -> {
                CoreBleManagerService.bind(this, this)
                true
            }

            MSG_NOTIFICATION_FULL_REFRESH -> {
                val activeNotifications = if (connected) {
                    getActiveNotificationsSafely(emptyArray())
                        .filter { notificationIsValidForPost(it) }
                        .toList()
                } else {
                    emptyList()
                }
                pushToCoreService(activeNotifications, Action.Posted)
                true
            }

            MSG_NOTIFICATION_POSTED -> {
                val sbn = msg.obj as StatusBarNotification
                if (!notificationIsValidForPost(sbn)) {
                    return true
                }
                pushToCoreService(listOf(sbn), Action.Posted)
                true
            }

            MSG_NOTIFICATION_REMOVED -> {
                val sbn = msg.obj as StatusBarNotification
                if (!notificationIsValidForPost(sbn)) {
                    return true
                }
                pushToCoreService(listOf(sbn), Action.Removed)
                true
            }

            MSG_FLUSH_PENDING_NOTIFICATIONS -> {
                val pending = pendingNotifications.toList()
                pendingNotifications.clear()

                pending.forEach { p ->
                    pushToCoreService(
                        p.second.map { it.value }.toList(),
                        p.first
                    )
                }
                true
            }

            else -> false
        }
    }

    override fun onServiceConnected(
        name: ComponentName?,
        service: IBinder?
    ) {
        coreBleService = ICoreBleService.Stub.asInterface(service)
        /**
         * Flush all pending notifications right after the service is connected
         */
        flushPendingNotifications()
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        coreBleService = null
        /**
         * Lost connect for some reasons, schedule a reconnect
         */
        scheduleLinkToBleCore(BLE_SERVICE_RECONNECT_DELAY)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        connected = true
        notificationFullRefresh()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        connected = false
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        sbn?.let {
            workerHandler.obtainMessage(MSG_NOTIFICATION_POSTED, it).sendToTarget()
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        sbn?.let {
            workerHandler.obtainMessage(MSG_NOTIFICATION_REMOVED, it).sendToTarget()
        }
    }

    @AnyThread
    private fun notificationFullRefresh() {
        workerHandler.obtainMessage(MSG_NOTIFICATION_FULL_REFRESH).sendToTarget()
    }

    @AnyThread
    private fun flushPendingNotifications() {
        workerHandler.obtainMessage(MSG_FLUSH_PENDING_NOTIFICATIONS).sendToTarget()
    }

    @AnyThread
    private fun scheduleLinkToBleCore(delayInMillis: Long) {
        if (workerHandler.hasMessages(MSG_CONNECT_BLE_CORE)) {
            return
        }
        val msg = workerHandler.obtainMessage(MSG_CONNECT_BLE_CORE)
        workerHandler.sendMessageDelayed(msg, delayInMillis)
    }

    @WorkerThread
    private fun getActiveNotificationsSafely(keys: Array<String>): Array<StatusBarNotification> {
        var result: Array<StatusBarNotification>? = null
        try {
            result = getActiveNotifications(keys)
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException: failed to fetch notifications", e)
        }
        return result ?: emptyArray()
    }

    /**
     * Returns true for notifications that have an intent and are not headers for grouped
     * notifications and should be shown in the notification popup.
     */
    @WorkerThread
    private fun notificationIsValidForPost(sbn: StatusBarNotification): Boolean {
        if (Objects.equals(sbn.packageName, packageName)) {
            // Filter out self notifications
            return false
        }
        val notification = sbn.notification
        val title = notification.extras.getCharSequence(Notification.EXTRA_TITLE)
        val text = notification.extras.getCharSequence(Notification.EXTRA_TEXT)
        val missingTitleAndText = TextUtils.isEmpty(title) && TextUtils.isEmpty(text)
        val isGroupHeader = (notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0
        return !isGroupHeader && !missingTitleAndText
    }

    /**
     * Push notifications states to ble core by their actions
     */
    @WorkerThread
    private fun pushToCoreService(
        notifications: List<StatusBarNotification>,
        action: Action
    ) {
        val managerService = coreBleService ?: return Unit.also {
            scheduleLinkToBleCore(BLE_SERVICE_RECONNECT_DELAY)
            pendingNotifications.computeIfAbsent(action) {
                notifications.associate { it.id to it }
            }
        }
        notifications.forEach {
            when (action) {
                Action.Posted ->
                    managerService.onNotificationPosted(it)

                Action.Removed ->
                    managerService.onNotificationRemoved(it)
            }
        }
    }
}