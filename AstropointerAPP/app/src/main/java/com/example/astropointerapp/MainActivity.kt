package com.example.astropointerAPP

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.MotionEvent
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.slider.Slider
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
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
        val btnGoto = findViewById<Button>(R.id.btnGoto)
        val btnUp = findViewById<Button>(R.id.btnUp)
        val btnDown = findViewById<Button>(R.id.btnDown)
        val btnRight = findViewById<Button>(R.id.btnRight)
        val btnLeft = findViewById<Button>(R.id.btnLeft)
        val btnCalibrate = findViewById<Button>(R.id.btnCalibrate)
        val btnLocation = findViewById<Button>(R.id.btnLocation)

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
                    send_message("up ")
                }

                MotionEvent.ACTION_UP -> {
                    send_message("stop ")
                }
            }
            false // true = событие обработано
        }

        btnDown.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    send_message("down ")
                }

                MotionEvent.ACTION_UP -> {
                    send_message("stop ")
                }
            }
            false // true = событие обработано
        }

        btnRight.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    send_message("right ")
                }

                MotionEvent.ACTION_UP -> {
                    send_message("stop ")
                }
            }
            false // true = событие обработано
        }

        btnLeft.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    send_message("left ")
                }

                MotionEvent.ACTION_UP -> {
                    send_message("stop ")
                }
            }
            false // true = событие обработано
        }

        btnGoto.setOnClickListener {
            input_ra_dec { result ->
                if (result != null) {
                    val (ra, dec) = result
                    send_message("goto " + ra + " " + dec)
                }
            }
        }

        val slider = findViewById<Slider>(R.id.slider)

        // Настройка подписей (показываем реальные значения)
        slider.setLabelFormatter { logicalValue ->
            val realValue = Math.pow(2.0, logicalValue.toDouble()).toFloat()
            "%.2f".format(realValue)  // Форматируем до 2 знаков после запятой
        }

        // Слушатель изменений
        slider.addOnChangeListener { _, logicalValue, fromUser ->
            if (fromUser) {
                // Преобразуем логическое значение в реальное
                val realValue = Math.pow(2.0, logicalValue.toDouble()).toFloat()

                // Используем реальное значение
                send_message("setspeed " + realValue)
            }
        }

        btnCalibrate.setOnClickListener {
            input_ra_dec { result ->
                if (result != null) {
                    val (ra, dec) = result
                    var str = "cal " + ra + " " + dec
                    //отправляем данные о месте и времени
                    val date = Date(System.currentTimeMillis()) // текущее время
                    val format = SimpleDateFormat("yyyy MM dd HH mm ss", Locale.getDefault())
                    str = str + " " + format.format(date)

                    val timeZone: TimeZone = TimeZone.getDefault()
                    val offset = timeZone.rawOffset / 3600000  // сдвиг в часах (UTC+X)
                    str = str + " " + offset + " "

                    val coords = loadCoordinates(this)
                    if (coords != null) {
                        str = str + String.format(Locale.US, "%.3f %.3f", coords.first, coords.second)
                        send_message(str)
                        println(str)
                    } else {
                        Toast.makeText(this, "Error: coords not found", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        
        btnLocation.setOnClickListener {
            val dialogView = layoutInflater.inflate(R.layout.coord_input, null)

            val input1 = dialogView.findViewById<EditText>(R.id.inputNumber1)
            val input2 = dialogView.findViewById<EditText>(R.id.inputNumber2)
            AlertDialog.Builder(this)
                    val coords = loadCoordinates(this)
                    if (coords != null) {
                        AlertDialog.Builder(this).setMessage(
                            "Current Location: " + String.format(Locale.US, "%.3f %.3f", coords.first, coords.second))
                    } else {
                        AlertDialog.Builder(this).setMessage("Current Location: Undefined")
                    }
                .setTitle("Enter coordinates")
                .setView(dialogView)
                .setPositiveButton("OK") { _, _ ->
                    val phi = input1.text.toString().toDoubleOrNull()
                    val lambda = input2.text.toString().toDoubleOrNull()

                    if (phi != null && lambda != null) {
                        Toast.makeText(this, "Location saved", Toast.LENGTH_SHORT).show()
                        saveCoordinates(this, phi, lambda)
                    } else {
                        Toast.makeText(this, "The input was incorrect", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun saveCoordinates(context: Context, latitude: Double, longitude: Double) {
        val sharedPref = context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        val editor = sharedPref.edit()
        editor.putFloat("latitude", latitude.toFloat())
        editor.putFloat("longitude", longitude.toFloat())
        editor.apply() // сохраняем асинхронно
    }

    private fun loadCoordinates(context: Context): Pair<Double, Double>? {
        val sharedPref = context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        if (!sharedPref.contains("latitude") || !sharedPref.contains("longitude")) {
            return null // координаты ещё не сохранены
        }
        val latitude = sharedPref.getFloat("latitude", 0f).toDouble()
        val longitude = sharedPref.getFloat("longitude", 0f).toDouble()
        return Pair(latitude, longitude)
    }


    private fun input_ra_dec(onResult: (Pair<Double, Double>?) -> Unit) {
        val dialogView = layoutInflater.inflate(R.layout.ra_dec_input, null)

        val input1 = dialogView.findViewById<EditText>(R.id.inputNumber1)
        val input2 = dialogView.findViewById<EditText>(R.id.inputNumber2)

        AlertDialog.Builder(this)
            .setTitle("Enter coordinates")
            .setView(dialogView)
            .setPositiveButton("OK") { _, _ ->
                val num1 = input1.text.toString().toDoubleOrNull()
                val num2 = input2.text.toString().toDoubleOrNull()

                if (num1 != null && num2 != null) {
                    Toast.makeText(this, "Moving to: $num1 и $num2", Toast.LENGTH_SHORT).show()
                    onResult(Pair(num1, num2))
                } else {
                    Toast.makeText(this, "The input was incorrect", Toast.LENGTH_SHORT).show()
                    onResult(null) // сигнализируем об ошибке
                }
            }
            .setNegativeButton("Cancel") { _, _ ->
                onResult(null) // явно возвращаем null при отмене
            }
            .show()
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
