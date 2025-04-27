// ICoreBleService.aidl
package io.github.gitofleonardo.libservice;

import android.service.notification.StatusBarNotification;
import io.github.gitofleonardo.libservice.ICoreBleListener;
import io.github.gitofleonardo.coreservice.data.BondedBleDevice;
import com.clj.fastble.data.BleDevice;

interface ICoreBleService {

    oneway void onNotificationPosted(in StatusBarNotification notification) = 0;

    oneway void onNotificationRemoved(in StatusBarNotification notification) = 1;

    oneway void scanDevices() = 2;

    oneway void registerCoreListener(in ICoreBleListener callback) = 3;

    oneway void unregisterCoreListener(in ICoreBleListener callback) = 4;

    oneway void startAdvertising() = 5;

    oneway void stopAdvertising() = 6;

    oneway void connectDevice(in BleDevice device) = 7;

    BondedBleDevice[] getBondedDevices() = 8;

    BondedBleDevice[] getConnectedDevices() = 9;

    oneway void setSyncState(in BleDevice device, boolean on) = 10;

    oneway void removeSyncDevice(in BleDevice device) = 11;

    oneway void disconnectDevice(in BleDevice device) = 12;

}
