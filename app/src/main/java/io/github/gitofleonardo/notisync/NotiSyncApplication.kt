package io.github.gitofleonardo.notisync

import android.app.Application
import com.clj.fastble.BleManager

class NotiSyncApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        appContext = this
        BleManager.getInstance().init(this)
        BleManager.getInstance()
            .enableLog(true)
            .setReConnectCount(1, 5000)
            .setOperateTimeout(5000)
    }

    companion object {

        lateinit var appContext: Application
    }
}
