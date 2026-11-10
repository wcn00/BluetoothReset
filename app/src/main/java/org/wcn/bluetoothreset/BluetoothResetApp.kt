package org.wcn.bluetoothreset

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log

class BluetoothResetApp : Application(), Application.ActivityLifecycleCallbacks {
    private var isColdStart = true
    private var startedActivityCount = 0
    private var isChangingConfigurations = false

    override fun onCreate() {
        super.onCreate()
        instance = this
        registerActivityLifecycleCallbacks(this)
        BluetoothController.notifyLog("Application onCreate (Process initialized). Cold start flag = true.")
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        Log.d(TAG, "onActivityCreated: ${activity.javaClass.simpleName}, savedInstanceState=$savedInstanceState")
    }

    override fun onActivityStarted(activity: Activity) {
        startedActivityCount++
        if (startedActivityCount == 1 && !isChangingConfigurations) {
            if (isColdStart) {
                isColdStart = false
                BluetoothController.notifyLog("Cold Start detected: First activity (${activity.javaClass.simpleName}) brought to foreground.")
            } else {
                BluetoothController.notifyLog("Warm Start detected: App brought to foreground from background.")
                triggerWarmStartReset("Foreground Warm Start")
            }
        }
    }

    override fun onActivityResumed(activity: Activity) {
        // Can also monitor resume state if needed
    }

    override fun onActivityPaused(activity: Activity) {
        // Activity paused
    }

    override fun onActivityStopped(activity: Activity) {
        isChangingConfigurations = activity.isChangingConfigurations
        startedActivityCount--
        if (startedActivityCount == 0) {
            BluetoothController.notifyLog("App backgrounded.")
        }
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
        // Save state
    }

    override fun onActivityDestroyed(activity: Activity) {
        // Activity destroyed
    }

    fun triggerWarmStartReset(reason: String) {
        if (BluetoothController.isWarmStartResetEnabled(this)) {
            BluetoothController.notifyLog("Triggering Bluetooth adapter reset on Warm Start ($reason)...")
            BluetoothController.resetBluetoothAdapter(this, reason)
        } else {
            BluetoothController.notifyLog("Warm Start detected ($reason), but Warm Start reset setting is DISABLED.")
        }
    }

    companion object {
        private const val TAG = "BluetoothResetApp"
        lateinit var instance: BluetoothResetApp
            private set
    }
}