package io.github.gitofleonardo.notisync.ui.home

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.viewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.github.gitofleonardo.coreservice.data.BondedBleDevice
import io.github.gitofleonardo.libservice.ICoreBleService
import io.github.gitofleonardo.notisync.R
import io.github.gitofleonardo.notisync.databinding.FragmentHomeBinding
import io.github.gitofleonardo.notisync.ui.BaseServiceFragment
import io.github.gitofleonardo.notisync.util.navigate

class HomeFragment : BaseServiceFragment<HomeViewModel>() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    override val viewModel by viewModels<HomeViewModel>()
    private val devices = ArrayList<BondedBleDevice>()
    private val adapter = DeviceSyncAdapter(devices)
    private var deleteDialog: AlertDialog? = null

    @SuppressLint("NotifyDataSetChanged")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.devicesResult.observe(this) { state ->
            when (state) {
                is SyncItemResult.AddSyncDevice -> {
                    val newConnectedMac = state.devices.map { it.device.mac }
                    devices.removeIf { newConnectedMac.contains(it.device.mac) }
                    val newDevices = (devices)
                        .also { list ->
                            list.addAll(state.devices)
                        }
                        .distinctBy {
                            it.device.mac
                        }
                    devices.clear()
                    devices.addAll(newDevices)
                    adapter.notifyDataSetChanged()
                    binding.deviceSrl.isRefreshing = false
                    updateEmptyTipText()
                }

                is SyncItemResult.DisconnectSyncDevice -> {
                    val indexToUpdate = devices.indexOfFirst { device ->
                        device.device.mac == state.device.device.mac
                    }
                    if (indexToUpdate >= 0) {
                        devices[indexToUpdate].connected = false
                        adapter.notifyItemChanged(indexToUpdate)
                    }
                    updateEmptyTipText()
                }

                SyncItemResult.LoadingDevices -> {
                    binding.deviceSrl.isRefreshing = true
                }

                is SyncItemResult.RefreshSyncDevice -> {
                    devices.clear()
                    devices.addAll(state.devices)
                    adapter.notifyDataSetChanged()
                    binding.deviceSrl.isRefreshing = false
                    updateEmptyTipText()
                }

                is SyncItemResult.RemoveSyncDevice -> {
                    val index = devices.indexOfFirst {
                        it.device.mac == state.device.device.mac
                    }
                    if (index >= 0) {
                        devices.removeAt(index)
                        adapter.notifyItemRemoved(index)
                    }
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val root: View = binding.root
        binding.addDeviceButton.setOnClickListener {
            val action = HomeFragmentDirections.actionNavigationHomeToNavigationNewDevice()
            navigate(action)
        }
        binding.deviceSrl.setOnRefreshListener {
            viewModel.refresh()
        }
        adapter.setOnSyncChangedListener { device, on ->
            viewModel.setDeviceSyncState(device.device, on)
        }
        adapter.setOnConnectChangedListener { device, on ->
            viewModel.setDeviceConnectState(device.device, on)
        }
        adapter.setOnDeleteListener { device ->
            deleteDialog = MaterialAlertDialogBuilder(requireContext())
                .setTitle(device.device.name)
                .setMessage(R.string.message_remove_device)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    viewModel.removeDevice(device.device)
                }
                .setNegativeButton(android.R.string.cancel) { dialog, _ ->
                    dialog.dismiss()
                }
                .setOnDismissListener {
                    deleteDialog = null
                }
                .create().also {
                    it.show()
                }
        }
        binding.deviceRv.adapter = adapter
        viewModel.refresh()
        return root
    }

    override fun onServiceAvailable(service: ICoreBleService) {
        super.onServiceAvailable(service)
        binding.deviceSrl.isEnabled = true
    }

    override fun onServiceUnavailable() {
        super.onServiceUnavailable()
        binding.deviceSrl.isEnabled = false
    }

    private fun updateEmptyTipText() {
        showEmptyTipText(devices.isEmpty())
    }

    private fun showEmptyTipText(shown: Boolean) {
        if (shown) {
            binding.emptyView.visibility = View.VISIBLE
            binding.deviceRv.visibility = View.GONE
        } else {
            binding.emptyView.visibility = View.GONE
            binding.deviceRv.visibility = View.VISIBLE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
