package io.github.gitofleonardo.notisync.ui.device

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.clj.fastble.data.BleDevice
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.github.gitofleonardo.coreservice.util.checkAndRequestPermissions
import io.github.gitofleonardo.coreservice.util.checkBluetoothConnectPermission
import io.github.gitofleonardo.libservice.ICoreBleService
import io.github.gitofleonardo.notisync.R
import io.github.gitofleonardo.notisync.databinding.FragmentNewDeviceBinding
import io.github.gitofleonardo.notisync.ui.BaseServiceFragment
import io.github.gitofleonardo.notisync.ui.BleCallbackState

class NewDeviceFragment : BaseServiceFragment<NewDeviceViewModel>() {

    private var _binding: FragmentNewDeviceBinding? = null

    private val binding: FragmentNewDeviceBinding
        get() = _binding!!
    override val viewModel: NewDeviceViewModel by viewModels()
    private val devices = ArrayList<BleDevice>()
    private val deviceAdapter by lazy { BleDeviceAdapter(devices) }
    private var connectDialog: AlertDialog? = null
    private var connectingDialog: AlertDialog? = null
    private var pendingDevice: BleDevice? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupStates()
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun setupStates() {
        viewModel.bleCallback.observe(this) {
            when (it) {
                is BleCallbackState.None -> {}

                is BleCallbackState.Scanned -> {
                    binding.mainSrl.isRefreshing = false
                    devices.clear()
                    devices.addAll(it.devices)
                    deviceAdapter.notifyDataSetChanged()
                    updateEmptyTipText()
                }

                is BleCallbackState.Scanning -> {
                    if (!binding.mainSrl.isRefreshing) {
                        binding.mainSrl.isRefreshing = true
                    }
                    devices.add(it.device)
                    deviceAdapter.notifyItemInserted(devices.size - 1)
                    updateEmptyTipText()
                }

                is BleCallbackState.StartScan -> {
                    if (!it.success) {
                        binding.mainSrl.isRefreshing = false
                    } else {
                        if (!binding.mainSrl.isRefreshing) {
                            binding.mainSrl.isRefreshing = true
                        }
                        devices.clear()
                        deviceAdapter.notifyDataSetChanged()
                    }
                }

                is BleCallbackState.DeviceBonded -> {
                    if (it.device.mac == pendingDevice?.mac) {
                        viewModel.connectDevice(it.device)
                    }
                }

                is BleCallbackState.DeviceConnected -> {
                    if (it.devices.any { it.mac == pendingDevice?.mac }) {
                        Toast.makeText(
                            requireContext(),
                            R.string.message_connect_succeed,
                            Toast.LENGTH_SHORT
                        ).show()
                        connectDialog?.dismiss()
                        connectingDialog?.dismiss()
                        findNavController().popBackStack()
                    }
                }

                is BleCallbackState.DeviceDisconnected -> {}
                is BleCallbackState.DeviceBonding -> {
                    if (it.device.mac == pendingDevice?.mac) {
                        // NO-OP
                        // Maybe we can do something later
                    }
                }

                is BleCallbackState.DeviceUnBond -> {
                    if (it.device.mac == pendingDevice?.mac) {
                        connectDialog?.dismiss()
                        connectingDialog?.dismiss()
                        Toast.makeText(
                            requireContext(),
                            R.string.message_failed_connect_device,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                is BleCallbackState.DeviceConnectFailure -> {
                    if (it.device.mac == pendingDevice?.mac) {
                        connectDialog?.dismiss()
                        connectingDialog?.dismiss()
                        Toast.makeText(
                            requireContext(),
                            R.string.message_failed_connect_device,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                is BleCallbackState.DeviceRemoved -> {}
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNewDeviceBinding.inflate(inflater, container, false)

        binding.mainSrl.setOnRefreshListener {
            viewModel.scanDevices()
        }
        binding.mainRv.adapter = deviceAdapter

        deviceAdapter.setOnConnectDeviceCallback { device ->
            showConnectDialog(device)
        }
        viewModel.scanDevices()
        return binding.root
    }

    private fun showConnectDialog(device: BleDevice) {
        if (!requireActivity().checkAndRequestPermissions(0)) {
            return
        }
        val name = if (device.name.isNullOrEmpty()) {
            resources.getString(R.string.device_default_name)
        } else {
            device.name
        }
        pendingDevice = device
        connectDialog?.dismiss()
        connectDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(name)
            .setMessage(R.string.message_connect_to_device)
            .setNegativeButton(android.R.string.cancel) { dialog, which ->
                dialog.dismiss()
            }
            .setPositiveButton(android.R.string.ok) { dialog, which ->
                if (requireContext().checkBluetoothConnectPermission()) {
                    handleConnectDevice(device)
                    showConnectingDialog()
                }
            }
            .setCancelable(true)
            .setOnDismissListener {
                connectDialog = null
            }
            .show()
    }

    private fun showConnectingDialog() {
        connectingDialog?.dismiss()
        connectingDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.title_connecting_device)
            .setView(R.layout.layout_infinite_progress_bar)
            .setCancelable(false)
            .setOnDismissListener {
                connectDialog = null
            }
            .show()
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun handleConnectDevice(device: BleDevice) {
        if (device.device.bondState == BluetoothDevice.BOND_BONDED) {
            viewModel.connectDevice(device)
        } else {
            device.device.createBond()
        }
    }

    override fun onServiceAvailable(service: ICoreBleService) {
        super.onServiceAvailable(service)
        binding.mainSrl.isEnabled = true
    }

    override fun onServiceUnavailable() {
        super.onServiceUnavailable()
        binding.mainSrl.isEnabled = false
    }

    private fun updateEmptyTipText() {
        showEmptyTipText(devices.isEmpty())
    }

    private fun showEmptyTipText(shown: Boolean) {
        if (shown) {
            binding.emptyView.visibility = View.VISIBLE
            binding.mainRv.visibility = View.GONE
        } else {
            binding.emptyView.visibility = View.GONE
            binding.mainRv.visibility = View.VISIBLE
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}