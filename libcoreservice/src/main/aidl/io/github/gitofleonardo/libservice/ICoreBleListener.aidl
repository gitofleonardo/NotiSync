// ICoreBleListener.aidl
package io.github.gitofleonardo.libservice;

import com.clj.fastble.data.BleDevice;

interface ICoreBleListener {

    oneway void onStartScan(boolean success) = 0;

    oneway void onScanning(in BleDevice device) = 1;

    oneway void onScanned(in BleDevice[] devices) = 2;

    oneway void onDeviceConnected(in BleDevice[] device) = 3;

    oneway void onDeviceBonded(in BleDevice device) = 4;

    oneway void onDeviceDisconnected(in BleDevice device) = 5;

    oneway void onDeviceUnBond(in BleDevice device) = 6;

    oneway void onDeviceBonding(in BleDevice device) = 7;

    oneway void onDeviceConnectFailure(in BleDevice device) = 8;

    oneway void onDeviceRemoved(in BleDevice device) = 9;
}
