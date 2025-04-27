package io.github.gitofleonardo.notisync.ui.device

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.clj.fastble.data.BleDevice
import io.github.gitofleonardo.notisync.databinding.LayoutBleDeviceItemBinding

class DeviceHolder(val binding: LayoutBleDeviceItemBinding) :
    RecyclerView.ViewHolder(binding.root) {

    fun bind(device: BleDevice) {
        device.name?.let {
            if (it.isNotEmpty()) {
                binding.deviceName.text = it
            }
        }
        binding.deviceAddress.text = device.mac
    }
}

class BleDeviceAdapter(private val devices: List<BleDevice>) :
    RecyclerView.Adapter<DeviceHolder>() {

    private var onConnectDeviceCallback: ((BleDevice) -> Unit)? = null

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): DeviceHolder {
        val binding =
            LayoutBleDeviceItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return DeviceHolder(binding)
    }

    override fun onBindViewHolder(
        holder: DeviceHolder,
        position: Int
    ) {
        holder.bind(devices[position])
        holder.binding.root.setOnClickListener {
            onConnectDeviceCallback?.invoke(devices[position])
        }
    }

    override fun getItemCount(): Int {
        return devices.size
    }

    fun setOnConnectDeviceCallback(callback: (BleDevice) -> Unit) {
        onConnectDeviceCallback = callback
    }
}
