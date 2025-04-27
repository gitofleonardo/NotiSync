package io.github.gitofleonardo.notisync.ui

import android.os.Bundle
import androidx.fragment.app.Fragment
import io.github.gitofleonardo.libservice.ICoreBleService

abstract class BaseServiceFragment<T> : Fragment() where T : BaseViewModel {

    protected abstract val viewModel: T

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.coreBleServiceState.observe(this) {
            when (it) {
                is BleServiceState.Connected -> {
                    onServiceAvailable(it.coreBleService)
                }

                BleServiceState.Disconnected -> {
                    onServiceUnavailable()
                }
            }
        }
    }

    protected open fun onServiceAvailable(service: ICoreBleService) {}

    protected open fun onServiceUnavailable() {}
}