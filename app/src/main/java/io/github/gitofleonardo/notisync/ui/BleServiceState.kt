package io.github.gitofleonardo.notisync.ui

import android.os.IBinder
import io.github.gitofleonardo.libservice.ICoreBleService

sealed class BleServiceState {

    class Connected(
        val binder: IBinder,
        val coreBleService: ICoreBleService
    ) : BleServiceState()

    object Disconnected : BleServiceState()
}
