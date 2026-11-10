package org.wcn.bluetoothreset

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

object BluetoothController {
    private const val TAG = "BluetoothController"
    private const val PREFS_NAME = "bluetooth_reset_prefs"
    private const val KEY_WARM_START_RESET_ENABLED = "warm_start_reset_enabled"
    private const val TIMEOUT_MS = 10000L

    interface StatusListener {
        fun onStatusChanged(status: String)
        fun onLogAdded(log: String)
    }

    private val listeners = CopyOnWriteArrayList<StatusListener>()
    private val logList = CopyOnWriteArrayList<String>()

    private fun postOnMainThread(runnable: Runnable) {
        try {
            val looper = Looper.getMainLooper()
            if (looper != null) {
                Handler(looper).post(runnable)
            } else {
                runnable.run()
            }
        } catch (e: Throwable) {
            runnable.run()
        }
    }

    private fun postDelayedOnMainThread(runnable: Runnable, delayMs: Long) {
        try {
            val looper = Looper.getMainLooper()
            if (looper != null) {
                Handler(looper).postDelayed(runnable, delayMs)
            } else {
                runnable.run()
            }
        } catch (e: Throwable) {
            runnable.run()
        }
    }

    private fun removeCallbacksFromMainThread(runnable: Runnable) {
        try {
            val looper = Looper.getMainLooper()
            if (looper != null) {
                Handler(looper).removeCallbacks(runnable)
            }
        } catch (e: Throwable) {
            // Ignored
        }
    }

    @Volatile
    var isResetting = false
        private set

    fun addListener(listener: StatusListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: StatusListener) {
        listeners.remove(listener)
    }

    fun getLogs(): List<String> = logList.toList()

    fun clearLogs() {
        logList.clear()
        notifyLog("Logs cleared.")
    }

    fun isWarmStartResetEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_WARM_START_RESET_ENABLED, true)
    }

    fun setWarmStartResetEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_WARM_START_RESET_ENABLED, enabled).apply()
        notifyLog("Warm start Bluetooth reset setting updated: $enabled")
    }

    fun hasBluetoothPermissions(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun getBluetoothAdapter(context: Context): BluetoothAdapter? {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        return bluetoothManager?.adapter ?: BluetoothAdapter.getDefaultAdapter()
    }

    fun getBluetoothStatusDescription(context: Context): String {
        if (!hasBluetoothPermissions(context)) {
            return "Permission required (BLUETOOTH_CONNECT)"
        }
        val adapter = getBluetoothAdapter(context) ?: return "Bluetooth not supported"
        return when (adapter.state) {
            BluetoothAdapter.STATE_OFF -> "Disabled (OFF)"
            BluetoothAdapter.STATE_TURNING_OFF -> "Turning OFF..."
            BluetoothAdapter.STATE_ON -> "Enabled (ON)"
            BluetoothAdapter.STATE_TURNING_ON -> "Turning ON..."
            else -> "Unknown State"
        }
    }

    @SuppressLint("MissingPermission")
    fun resetBluetoothAdapter(context: Context, reason: String = "Manual") {
        if (isResetting) {
            notifyLog("Reset already in progress ($reason ignored).")
            return
        }

        if (!hasBluetoothPermissions(context)) {
            notifyStatus("Reset Failed: Missing Permissions")
            notifyLog("[$reason] Cannot reset Bluetooth: Missing required permissions.")
            return
        }

        val adapter = getBluetoothAdapter(context)
        if (adapter == null) {
            notifyStatus("Reset Failed: No Bluetooth Hardware")
            notifyLog("[$reason] Bluetooth adapter is not available on this device.")
            return
        }

        isResetting = true
        notifyLog("[$reason] Starting Bluetooth adapter reset sequence...")
        notifyStatus("Resetting Bluetooth...")

        val applicationContext = context.applicationContext

        if (!adapter.isEnabled) {
            notifyLog("Bluetooth is currently OFF. Enabling adapter...")
            enableBluetooth(applicationContext, adapter, reason)
            return
        }

        notifyLog("Bluetooth is currently ON. Disabling adapter...")
        disableAndReEnable(applicationContext, adapter, reason)
    }

    @SuppressLint("MissingPermission")
    private fun disableAndReEnable(context: Context, adapter: BluetoothAdapter, reason: String) {
        var receiverRegistered = false
        var timeoutRunnable: Runnable? = null

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                if (intent?.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    Log.d(TAG, "State changed: $state")
                    if (state == BluetoothAdapter.STATE_OFF) {
                        notifyLog("Bluetooth disabled (STATE_OFF). Re-enabling adapter...")
                        cleanup(context, this, timeoutRunnable)
                        enableBluetooth(context, adapter, reason)
                    }
                }
            }
        }

        timeoutRunnable = Runnable {
            notifyLog("Timeout waiting for Bluetooth to turn OFF. Attempting force enable...")
            cleanup(context, receiver, null)
            enableBluetooth(context, adapter, reason)
        }

        try {
            val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
            context.registerReceiver(receiver, filter)
            receiverRegistered = true
            timeoutRunnable?.let { postDelayedOnMainThread(it, TIMEOUT_MS) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register state receiver", e)
        }

        val disabled = try {
            adapter.disable()
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException disabling bluetooth", e)
            false
        }

        if (!disabled) {
            notifyLog("Direct adapter.disable() call returned false or missing permission.")
            if (receiverRegistered) {
                cleanup(context, receiver, timeoutRunnable)
            }
            enableBluetooth(context, adapter, reason)
        }
    }

    @SuppressLint("MissingPermission")
    private fun enableBluetooth(context: Context, adapter: BluetoothAdapter, reason: String) {
        var timeoutRunnable: Runnable? = null

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                if (intent?.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    if (state == BluetoothAdapter.STATE_ON) {
                        notifyLog("Bluetooth successfully re-enabled (STATE_ON). Reset complete.")
                        cleanup(context, this, timeoutRunnable)
                        isResetting = false
                        notifyStatus(getBluetoothStatusDescription(context))
                    }
                }
            }
        }

        timeoutRunnable = Runnable {
            notifyLog("Timeout waiting for Bluetooth to turn ON.")
            cleanup(context, receiver, null)
            isResetting = false
            notifyStatus(getBluetoothStatusDescription(context))
        }

        try {
            val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
            context.registerReceiver(receiver, filter)
            timeoutRunnable?.let { postDelayedOnMainThread(it, TIMEOUT_MS) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register state receiver", e)
        }

        val enabled = try {
            adapter.enable()
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException enabling bluetooth", e)
            false
        }

        if (!enabled) {
            notifyLog("Direct adapter.enable() returned false. Please verify permissions.")
            postDelayedOnMainThread({
                isResetting = false
                notifyStatus(getBluetoothStatusDescription(context))
            }, 1000)
        }
    }

    private fun cleanup(context: Context, receiver: BroadcastReceiver, timeoutRunnable: Runnable?) {
        if (timeoutRunnable != null) {
            removeCallbacksFromMainThread(timeoutRunnable)
        }
        try {
            context.unregisterReceiver(receiver)
        } catch (e: Exception) {
            // Receiver might not be registered or already unregistered
        }
    }

    fun notifyLog(message: String) {
        val timeStr = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        val formattedLog = "[$timeStr] $message"
        logList.add(formattedLog)
        Log.d(TAG, formattedLog)
        postOnMainThread {
            listeners.forEach { it.onLogAdded(formattedLog) }
        }
    }

    fun notifyStatus(status: String) {
        postOnMainThread {
            listeners.forEach { it.onStatusChanged(status) }
        }
    }
}