package com.example.astropointerapp   // <- ЗАМЕНИ на пакет твоего проекта

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import java.io.IOException
import java.util.*

class MainActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "BT_TEST"
        private const val REQUEST_ENABLE_BT = 1
        private const val REQUEST_PERMISSIONS = 2
        // SPP UUID (стандарт для HC-05 / классического Bluetooth SPP)
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    // ЗАМЕНИТЕ на MAC адрес вашего HC-05/HC-06 (сначала спарьте модуль в настройках Android)
    private val deviceAddress = "98:DA:60:0F:62:B2"

    private val btAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var btSocket: BluetoothSocket? = null

    private lateinit var btnConnect: Button
    private lateinit var btnOn: Button
    private lateinit var btnOff: Button
    private lateinit var tvStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main) // подключаем layout выше

        btnConnect = findViewById(R.id.btnConnect)
        btnOn = findViewById(R.id.btnOn)
        btnOff = findViewById(R.id.btnOff)
        tvStatus = findViewById(R.id.tvStatus)

        btnConnect.setOnClickListener { startConnect() }
        btnOn.setOnClickListener { sendData("1") }
        btnOff.setOnClickListener { sendData("0") }

        if (btAdapter == null) {
            tvStatus.text = "Bluetooth not supported on this device"
            btnConnect.isEnabled = false
            btnOn.isEnabled = false
            btnOff.isEnabled = false
            return
        }

        // Запросить разрешения, если нужно
        ensurePermissions()
    }

    private fun ensurePermissions() {
        val perms = ArrayList<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+
            perms.add(Manifest.permission.BLUETOOTH_CONNECT)
            perms.add(Manifest.permission.BLUETOOTH_SCAN)
        } else {
            // Для старых версий иногда нужен доступ к локации, чтобы сканировать (если потребуется)
            perms.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        val missing = perms.filter { ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), REQUEST_PERMISSIONS)
        }
    }

    private fun startConnect() {
        // Включить BT, если выключен
        if (btAdapter?.isEnabled == false) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
            return
        }

        // Подключаемся в отдельном потоке (не в UI)
        tvStatus.text = "Connecting..."
        Thread {
            try {
                // Перед использованием некоторых методов на Android 12+ требуется BLUETOOTH_CONNECT
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                        runOnUiThread { tvStatus.text = "No BLUETOOTH_CONNECT permission" }
                        return@Thread
                    }
                }

                val device: BluetoothDevice = btAdapter!!.getRemoteDevice(deviceAddress)
                btAdapter.cancelDiscovery()
                btSocket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                btSocket?.connect()
                runOnUiThread { tvStatus.text = "Connected to ${device.name ?: device.address}" }

                // Запускаем чтение входящего потока
                startReaderThread()
            } catch (e: IOException) {
                Log.e(TAG, "Connect error", e)
                runOnUiThread { tvStatus.text = "Connect failed: ${e.message}" }
                try { btSocket?.close() } catch (_: Exception) {}
                btSocket = null
            }
        }.start()
    }

    private fun startReaderThread() {
        val socket = btSocket ?: return
        Thread {
            val buffer = ByteArray(1024)
            try {
                val input = socket.inputStream
                while (true) {
                    val read = input.read(buffer)
                    if (read > 0) {
                        val s = String(buffer, 0, read)
                        runOnUiThread { tvStatus.text = "Received: $s" }
                    }
                }
            } catch (e: IOException) {
                Log.e(TAG, "Read error", e)
                runOnUiThread { tvStatus.text = "Connection lost" }
            }
        }.start()
    }

    private fun sendData(text: String) {
        Thread {
            try {
                val socket = btSocket
                if (socket == null || !socket.isConnected) {
                    runOnUiThread { tvStatus.text = "Not connected" }
                    return@Thread
                }

                // Проверка разрешения для Android 12+
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                        runOnUiThread { tvStatus.text = "No BLUETOOTH_CONNECT permission" }
                        return@Thread
                    }
                }

                socket.outputStream.write(text.toByteArray())
                runOnUiThread { tvStatus.text = "Sent: $text" }
            } catch (e: IOException) {
                Log.e(TAG, "Send error", e)
                runOnUiThread { tvStatus.text = "Send failed: ${e.message}" }
            }
        }.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        try { btSocket?.close() } catch (_: Exception) {}
    }
}
