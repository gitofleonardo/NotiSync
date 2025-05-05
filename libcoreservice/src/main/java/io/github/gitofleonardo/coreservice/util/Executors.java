package io.github.gitofleonardo.coreservice.util;

import android.os.HandlerThread;
import android.os.Looper;
import android.os.Process;

public class Executors {

    public static final LooperExecutor MAIN_EXECUTOR =
            new LooperExecutor(Looper.getMainLooper());

    public static Looper createAndStartNewLooper(String name) {
        return createAndStartNewLooper(name, Process.THREAD_PRIORITY_DEFAULT);
    }

    public static Looper createAndStartNewLooper(String name, int priority) {
        HandlerThread thread = new HandlerThread(name, priority);
        thread.start();
        return thread.getLooper();
    }

    /**
     * Executor for notification listener
     */
    public static final LooperExecutor NOTIFICATION_MODEL_EXECUTOR =
            new LooperExecutor(createAndStartNewLooper("notification-model"));

    /**
     * Executor for ble core
     */
    public static final LooperExecutor BLE_CORE_MODEL_EXECUTOR =
            new LooperExecutor(createAndStartNewLooper("ble-core-model"));

}
