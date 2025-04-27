package io.github.gitofleonardo.notisync.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import io.github.gitofleonardo.coreservice.data.BondedBleDevice
import io.github.gitofleonardo.notisync.R
import io.github.gitofleonardo.notisync.databinding.LayoutSyncDeviceItemBinding

class DeviceSyncHolder(val binding: LayoutSyncDeviceItemBinding) :
    RecyclerView.ViewHolder(binding.root) {

    fun bind(
        item: BondedBleDevice,
        connectSwitchListener: ((BondedBleDevice, Boolean) -> Unit)?,
        syncSwitchListener: ((BondedBleDevice, Boolean) -> Unit)?,
        deleteListener: ((BondedBleDevice) -> Unit)?
    ) {
        item.device.name?.let {
            if (it.isNotEmpty()) {
                binding.deviceName.text = it
            }
        }
        binding.deviceAddress.text = item.device.mac

        /**
         * Connection not available when sync mode off
         */
        binding.connectStateChip.isEnabled = item.synced
        binding.connectStateChip.isChecked = item.connected
        binding.connectStateChip.setOnCheckedChangeListener { _, checked ->
            item.connected = checked
            updateConnectState(checked)
            /**
             * Disable connect chip when changing connection state, instead we wait for the
             * callback from core ble service
             */
            binding.connectStateChip.isEnabled = false
            connectSwitchListener?.invoke(item, checked)
        }

        binding.syncStateChip.isChecked = item.synced
        binding.syncStateChip.jumpDrawablesToCurrentState()

        updateConnectState(item.connected)
        updateSyncState(item.synced)
        binding.syncStateChip.setOnCheckedChangeListener { _, checked ->
            item.synced = checked
            updateSyncState(checked)
            binding.connectStateChip.isEnabled = checked
            syncSwitchListener?.invoke(item, checked)
        }

        binding.removeChip.setOnClickListener {
            deleteListener?.invoke(item)
        }
    }

    private fun updateConnectState(connected: Boolean) {
        binding.connectStateChip.setText(
            if (connected) {
                R.string.title_chip_connected
            } else {
                R.string.title_chip_disconnected
            }
        )
    }

    private fun updateSyncState(isOn: Boolean) {
        binding.syncStateChip.setText(
            if (isOn) {
                R.string.title_chip_state_synced
            } else {
                R.string.title_chip_state_not_synced
            }
        )
    }

    fun unbind() {
        binding.connectStateChip.setOnCheckedChangeListener(null)
        binding.syncStateChip.setOnCheckedChangeListener(null)
    }
}

class DeviceSyncAdapter(private val devices: List<BondedBleDevice>) :
    RecyclerView.Adapter<DeviceSyncHolder>() {

    private var onSyncChangedListener: ((BondedBleDevice, Boolean) -> Unit)? = null
    private var onConnectChangedListener: ((BondedBleDevice, Boolean) -> Unit)? = null
    private var onDeleteListener: ((BondedBleDevice) -> Unit)? = null

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): DeviceSyncHolder {
        val binding =
            LayoutSyncDeviceItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return DeviceSyncHolder(binding)
    }

    override fun onBindViewHolder(
        holder: DeviceSyncHolder,
        position: Int
    ) {
        holder.bind(
            devices[position],
            onConnectChangedListener,
            onSyncChangedListener,
            onDeleteListener
        )
    }

    override fun getItemCount(): Int = devices.size

    override fun onViewRecycled(holder: DeviceSyncHolder) {
        super.onViewRecycled(holder)
        holder.unbind()
    }

    fun setOnSyncChangedListener(listener: (BondedBleDevice, Boolean) -> Unit) {
        onSyncChangedListener = listener
    }

    fun setOnDeleteListener(listener: (BondedBleDevice) -> Unit) {
        onDeleteListener = listener
    }

    fun setOnConnectChangedListener(listener: (BondedBleDevice, Boolean) -> Unit) {
        onConnectChangedListener = listener
    }
}
