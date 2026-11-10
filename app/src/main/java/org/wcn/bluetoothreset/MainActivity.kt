package org.wcn.bluetoothreset

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.materialswitch.MaterialSwitch

class MainActivity : AppCompatActivity(), BluetoothController.StatusListener {

    private lateinit var tvBluetoothStatus: TextView
    private lateinit var switchWarmStartReset: MaterialSwitch
    private lateinit var btnResetNow: Button
    private lateinit var btnClearLogs: Button
    private lateinit var tvLogOutput: TextView

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.values.all { it }
        if (granted) {
            BluetoothController.notifyLog("Required Bluetooth permissions granted by user.")
            updateStatusText()
        } else {
            BluetoothController.notifyLog("Bluetooth permissions denied by user.")
            Toast.makeText(this, "Bluetooth permissions required for reset functionality.", Toast.LENGTH_LONG).show()
            updateStatusText()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvBluetoothStatus = findViewById(R.id.tvBluetoothStatus)
        switchWarmStartReset = findViewById(R.id.switchWarmStartReset)
        btnResetNow = findViewById(R.id.btnResetNow)
        btnClearLogs = findViewById(R.id.btnClearLogs)
        tvLogOutput = findViewById(R.id.tvLogOutput)

        switchWarmStartReset.isChecked = BluetoothController.isWarmStartResetEnabled(this)
        switchWarmStartReset.setOnCheckedChangeListener { _, isChecked ->
            BluetoothController.setWarmStartResetEnabled(this, isChecked)
        }

        btnResetNow.setOnClickListener {
            if (checkAndRequestPermissions()) {
                BluetoothController.resetBluetoothAdapter(this, "Manual UI Request")
            }
        }

        btnClearLogs.setOnClickListener {
            BluetoothController.clearLogs()
        }

        checkAndRequestPermissions()
        refreshLogs()
        updateStatusText()
    }

    override fun onStart() {
        super.onStart()
        BluetoothController.addListener(this)
        updateStatusText()
    }

    override fun onStop() {
        super.onStop()
        BluetoothController.removeListener(this)
    }

    private fun checkAndRequestPermissions(): Boolean {
        val permissionsToRequest = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_SCAN)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_ADMIN)
            }
        }

        return if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
            false
        } else {
            true
        }
    }

    private fun updateStatusText() {
        val status = BluetoothController.getBluetoothStatusDescription(this)
        tvBluetoothStatus.text = "Status: $status"
    }

    private fun refreshLogs() {
        val logs = BluetoothController.getLogs()
        tvLogOutput.text = if (logs.isEmpty()) "No logs yet.\n" else logs.joinToString("\n") + "\n"
    }

    override fun onStatusChanged(status: String) {
        tvBluetoothStatus.text = "Status: $status"
    }

    override fun onLogAdded(log: String) {
        refreshLogs()
    }
}