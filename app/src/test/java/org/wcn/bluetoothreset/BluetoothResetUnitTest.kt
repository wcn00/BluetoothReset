package org.wcn.bluetoothreset

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BluetoothResetUnitTest {

    @Before
    fun setUp() {
        BluetoothController.clearLogs()
    }

    @Test
    fun testLogAdditionAndClearing() {
        BluetoothController.clearLogs() // Clears logs and adds "Logs cleared."
        val initialLogs = BluetoothController.getLogs()
        assertEquals(1, initialLogs.size)
        assertTrue(initialLogs[0].contains("Logs cleared."))

        BluetoothController.notifyLog("Test Log Entry 1")
        BluetoothController.notifyLog("Test Log Entry 2")

        val logs = BluetoothController.getLogs()
        assertEquals(3, logs.size)
        assertTrue(logs[1].contains("Test Log Entry 1"))
        assertTrue(logs[2].contains("Test Log Entry 2"))

        BluetoothController.clearLogs()
        val clearedLogs = BluetoothController.getLogs()
        assertEquals(1, clearedLogs.size)
        assertTrue(clearedLogs[0].contains("Logs cleared."))
    }

    @Test
    fun testStatusListener() {
        var lastStatus = ""
        var lastLog = ""

        val listener = object : BluetoothController.StatusListener {
            override fun onStatusChanged(status: String) {
                lastStatus = status
            }

            override fun onLogAdded(log: String) {
                lastLog = log
            }
        }

        BluetoothController.addListener(listener)
        BluetoothController.notifyStatus("Resetting...")
        BluetoothController.notifyLog("EventListenerTest")

        assertEquals("Resetting...", lastStatus)
        assertTrue(lastLog.contains("EventListenerTest"))

        BluetoothController.removeListener(listener)
    }
}