package io.github.gitofleonardo.coreservice

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import com.clj.fastble.BleManager
import com.clj.fastble.data.BleDevice
import io.github.gitofleonardo.coreservice.database.BondedDeviceItem
import io.github.gitofleonardo.coreservice.database.FilteredAppItem
import io.github.gitofleonardo.coreservice.util.KEY_BLE_WORK_MODE
import io.github.gitofleonardo.coreservice.util.SERVICE_PREFERENCE_NAME
import io.github.gitofleonardo.coreservice.util.checkBluetoothConnectPermission
import io.github.gitofleonardo.coreservice.util.getServiceDatabase
import io.github.gitofleonardo.libservice.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

internal class ServiceModel(
    private val context: Context,
    private val bleManager: BleManager
) {

    /**
     * Devices already connected to
     */
    private val bleConnectedDevices = HashMap<String, Pair<BleDevice, BondedDeviceItem>>()

    /**
     * Map mac address to device
     */
    private val bleScanningDevices = HashMap<String, BleDevice>()

    /**
     * Bonded devices from local database
     */
    private val allBondedDevices by lazy {
        val map = HashMap<String, Pair<BleDevice, BondedDeviceItem>>()
        initBondedDevices(map)
        map
    }

    private val filteredApps = ArrayList<FilteredAppItem>()

    private val serviceDb by lazy { context.getServiceDatabase() }
    private val preference by lazy {
        context.getSharedPreferences(SERVICE_PREFERENCE_NAME, MODE_PRIVATE)
    }

    private val mainScope by lazy {
        CoroutineScope(Dispatchers.Main)
    }

    private val appsFlow by lazy {
        serviceDb.filteredAppsDao()
            .getFilteredAppsFlow()
    }

    init {
        mainScope.launch {
            appsFlow.flowOn(Dispatchers.IO)
                .collect {
                    onFilteredAppsChanged(it)
                }
        }
    }

    @Synchronized
    fun removeConnectedDevice(device: BleDevice) {
        bleConnectedDevices.remove(device.mac)
    }

    @Synchronized
    fun addConnectedDevice(device: BleDevice) {
        val bondedDevice = insertBondedDevice(device)
        bleConnectedDevices[device.mac] = Pair(device, bondedDevice)
    }

    @Synchronized
    fun getBondedDevices(): List<Pair<BleDevice, BondedDeviceItem>> {
        return allBondedDevices.map { it.value }
    }

    @Synchronized
    fun getConnectedDevices(): List<Pair<BleDevice, BondedDeviceItem>> {
        return bleConnectedDevices.map { it.value }
    }

    @Synchronized
    fun setSyncState(device: BleDevice, on: Boolean) {
        val p = allBondedDevices[device.mac]
        p?.let {
            p.second.syncOn = on
            serviceDb
                .bondedDevicesDao()
                .update(p.second)
        }
    }

    @Synchronized
    fun removeSyncDevice(device: BleDevice) {
        allBondedDevices.remove(device.mac)?.also {
            serviceDb
                .bondedDevicesDao()
                .delete(it.second)
        }
    }

    @Synchronized
    fun addScanningDevice(device: BleDevice): Boolean {
        if (checkDeviceBonded(device)) {
            return false
        }
        bleScanningDevices[device.mac] = device
        return true
    }

    @Synchronized
    fun clearScanningDevices() {
        bleScanningDevices.clear()
    }

    @Synchronized
    fun getScanningDevices(): List<BleDevice> {
        return bleScanningDevices.map { it.value }
    }

    @Synchronized
    private fun checkDeviceBonded(device: BleDevice): Boolean {
        return allBondedDevices.containsKey(device.mac)
    }

    fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener
    ) = preference.registerOnSharedPreferenceChangeListener(listener)

    fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener
    ) = preference.unregisterOnSharedPreferenceChangeListener(listener)

    fun receiveModeEnabled(): Boolean =
        preference.getString(
            KEY_BLE_WORK_MODE,
            context.getString(R.string.mode_ble_sending_only)
        ) == context.getString(R.string.mode_ble_sending_n_receiving)

    @Synchronized
    private fun insertBondedDevice(device: BleDevice): BondedDeviceItem {
        if (allBondedDevices.containsKey(device.mac)) {
            return allBondedDevices[device.mac]!!.second
        }
        val bondedDevice = BondedDeviceItem(0, device.name, device.mac, true)
        bondedDevice.uid = serviceDb.bondedDevicesDao().insert(bondedDevice)
        allBondedDevices.put(bondedDevice.deviceAddress, Pair(device, bondedDevice))
        return bondedDevice
    }

    @Synchronized
    private fun initBondedDevices(
        map: HashMap<String, Pair<BleDevice, BondedDeviceItem>>
    ) {
        val hasPermission = context.checkBluetoothConnectPermission()
        val remoteBondedDevices = if (hasPermission) {
            bleManager
                .bluetoothManager
                .adapter
                .bondedDevices
                .associate { it.address to it }
        } else {
            emptyMap()
        }
        val unBondedDevices = mutableListOf<BondedDeviceItem>()
        val localBondedDevices = serviceDb
            .bondedDevicesDao()
            .getBondedDevices()
            .map {
                val bonded = remoteBondedDevices[it.deviceAddress]
                return@map if (bonded == null) {
                    if (hasPermission) {
                        unBondedDevices.add(it)
                    }
                    null
                } else {
                    Pair(BleDevice(bonded), it)
                }
            }
            .filterNotNull()
        val updatedItems = mutableListOf<BondedDeviceItem>()
        localBondedDevices.forEach {
            if (it.first.name != it.second.deviceName) {
                it.second.deviceName = it.first.name
                updatedItems.add(it.second)
            }
        }
        serviceDb
            .bondedDevicesDao()
            .update(*updatedItems.toTypedArray())

        serviceDb
            .bondedDevicesDao()
            .delete(*unBondedDevices.toTypedArray())

        map.clear()
        map.putAll(
            localBondedDevices.map { it.second.deviceAddress to it }
        )
    }

    @Synchronized
    private fun onFilteredAppsChanged(apps: List<FilteredAppItem>) {
        filteredApps.clear()
        filteredApps.addAll(apps)
    }

    @Synchronized
    fun getFilteredApps(): Set<String> {
        return filteredApps.map { it.packageName }.toSet()
    }

    fun destroy() {

    }
}