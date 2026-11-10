package org.wcn.bluetoothreset

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d(TAG, "BootReceiver triggered with action: $action")
        BluetoothController.notifyLog("System Warm Start / Boot broadcast received: $action")

        if (BluetoothController.isWarmStartResetEnabled(context)) {
            BluetoothController.resetBluetoothAdapter(context, "System Warm Boot ($action)")
        } else {
            BluetoothController.notifyLog("System warm boot detected, but Warm Start reset setting is DISABLED.")
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}