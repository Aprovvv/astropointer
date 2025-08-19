package com.example.astropointerAPP

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.MotionEvent
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.IOException
import java.util.UUID

class MainActivity : AppCompatActivity() {

    private val REQUEST_PERMISSIONS = 100
    private val REQUEST_ENABLE_BT = 101

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothSocket: BluetoothSocket? = null
    private val uuidSPP: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth не поддерживается", Toast.LENGTH_LONG).show()
            return
        }

        // Запросим пермишны на Android 12+
        requestMissingPermissionsIfNeeded()

        val btnConnect = findViewById<Button>(R.id.btnConnect)
        val btnSend = findViewById<Button>(R.id.btnSend)
        val btnUp = findViewById<Button>(R.id.btnUp)
        val btnDown = findViewById<Button>(R.id.btnDown)
        val btnRight = findViewById<Button>(R.id.btnRight)
        val btnLeft = findViewById<Button>(R.id.btnLeft)

        btnConnect.setOnClickListener {
            if (!ensureBluetoothEnabled()) return@setOnClickListener
            showPairedDevicesDialog()
        }

        /*btnUp.setOnClickListener {
            send_message("up")
        }*/

        btnUp.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    send_message("up")
                }
                MotionEvent.ACTION_UP -> {
                    send_message("stop")
                }
            }
            false // true = событие обработано
        }

        btnDown.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    send_message("down")
                }
                MotionEvent.ACTION_UP -> {
                    send_message("stop")
                }
            }
            false // true = событие обработано
        }

        btnRight.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    send_message("right")
                }
                MotionEvent.ACTION_UP -> {
                    send_message("stop")
                }
            }
            false // true = событие обработано
        }

        btnLeft.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    send_message("left")
                }
                MotionEvent.ACTION_UP -> {
                    send_message("stop")
                }
            }
            false // true = событие обработано
        }

        btnSend.setOnClickListener {
            send_message("1")
        }
    }

    private fun send_message(msg: String) {
        if (bluetoothSocket?.isConnected == true) {
            try {
                bluetoothSocket?.outputStream?.write(msg.toByteArray())
                Toast.makeText(this, "Sent: $msg", Toast.LENGTH_SHORT).show()
            } catch (e: IOException) {
                Toast.makeText(this, "Ошибка отправки: ${e.message}", Toast.LENGTH_SHORT).show()
            } catch (se: SecurityException) {
                Toast.makeText(this, "Нет разрешения для отправки", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Нет соединения", Toast.LENGTH_SHORT).show()
        }
    }

    /** ========== Пермишны ========== */

    private fun isSPlus() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    private fun hasConnectPermission(): Boolean =
        !isSPlus() || ContextCompat.checkSelfPermission(
            this, Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED

    private fun hasScanPermission(): Boolean =
        !isSPlus() || ContextCompat.checkSelfPermission(
            this, Manifest.permission.BLUETOOTH_SCAN
        ) == PackageManager.PERMISSION_GRANTED

    private fun requestMissingPermissionsIfNeeded() {
        if (!isSPlus()) return
        val missing = mutableListOf<String>()
        if (!hasConnectPermission()) missing += Manifest.permission.BLUETOOTH_CONNECT
        if (!hasScanPermission()) missing += Manifest.permission.BLUETOOTH_SCAN
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), REQUEST_PERMISSIONS)
        }
    }

    private fun ensureBluetoothEnabled(): Boolean {
        val adapter = bluetoothAdapter ?: return false
        if (adapter.isEnabled) return true

        // На Android 12+ сначала просим пермишны, если их ещё нет
        if (isSPlus() && !hasConnectPermission()) {
            requestMissingPermissionsIfNeeded()
            Toast.makeText(this, "Разрешите доступ к Bluetooth", Toast.LENGTH_SHORT).show()
            return false
        }

        // Открываем системные настройки Bluetooth — пользователь включает BT и возвращается
        startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
        Toast.makeText(this, "Включите Bluetooth и вернитесь в приложение", Toast.LENGTH_SHORT).show()
        return false
    }


    /** ========== UI: список устройств ========== */

    @SuppressLint("MissingPermission") // мы вручную проверяем hasConnectPermission перед доступом
    private fun showPairedDevicesDialog() {
        if (isSPlus() && !hasConnectPermission()) {
            requestMissingPermissionsIfNeeded()
            Toast.makeText(this, "Нет разрешения BLUETOOTH_CONNECT", Toast.LENGTH_SHORT).show()
            return
        }

        val paired: Set<BluetoothDevice>? = bluetoothAdapter?.bondedDevices
        if (paired.isNullOrEmpty()) {
            Toast.makeText(this, "Нет спаренных устройств", Toast.LENGTH_SHORT).show()
            return
        }

        val devices = paired.toList()
        val names = mutableListOf("Назад")
        for (d in devices) {
            // Доступ к имени тоже требует CONNECT на S+
            val name = if (isSPlus()) {
                if (hasConnectPermission()) d.name else "(без имени)"
            } else d.name
            names += (name ?: "(без имени)")
        }

        AlertDialog.Builder(this)
            .setTitle("Выберите устройство")
            .setItems(names.toTypedArray()) { dialog, which ->
                if (which == 0) {
                    dialog.dismiss()
                } else {
                    val device = devices[which - 1]
                    connectToDevice(device)
                }
            }
            .show()
    }

    /** ========== Подключение ========== */

    @SuppressLint("MissingPermission") // все вызовы ограждены проверками
    private fun connectToDevice(device: BluetoothDevice) {
        if (isSPlus() && !hasConnectPermission()) {
            requestMissingPermissionsIfNeeded()
            Toast.makeText(this, "Нет разрешения BLUETOOTH_CONNECT", Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(this, "Подключение к ${device.name ?: device.address}…", Toast.LENGTH_SHORT).show()

        Thread {
            try {
                // cancelDiscovery требует BLUETOOTH_SCAN на S+
                if (!isSPlus() || hasScanPermission()) {
                    try {
                        bluetoothAdapter?.cancelDiscovery()
                    } catch (_: SecurityException) { /* пропустим, если нет разрешения */ }
                }

                bluetoothSocket = device.createRfcommSocketToServiceRecord(uuidSPP)
                bluetoothSocket?.connect()

                runOnUiThread {
                    Toast.makeText(
                        this,
                        "Успешно подключено к ${device.name ?: device.address}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: IOException) {
                runOnUiThread {
                    Toast.makeText(this, "Ошибка подключения: ${e.message}", Toast.LENGTH_SHORT).show()
                }
                try { bluetoothSocket?.close() } catch (_: IOException) {}
            } catch (se: SecurityException) {
                runOnUiThread {
                    Toast.makeText(this, "Нет нужных Bluetooth-разрешений", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    /** ========== Результаты запросов ========== */

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSIONS) {
            // Можно перезапустить действие, если все выданы
        }
    }
}
