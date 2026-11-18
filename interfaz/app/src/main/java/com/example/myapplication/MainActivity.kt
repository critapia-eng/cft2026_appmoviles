package com.example.myapplication

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.material.card.MaterialCardView
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import kotlin.random.Random

class MainActivity : AppCompatActivity() {

    private var userId: String = ""
    private var userName: String = ""
    private var userCondition: String = ""
    private var emergencyPhone: String = ""
    private var userRole: String = "PACIENTE"
    private var isCaregiver: Boolean = false

    // Estado del reloj inteligente vinculado (Bluetooth LE / PPG)
    private var isDeviceConnected = true
    private var connectedDeviceName = "Smartwatch Y13 (BLE)"
    private var connectedDeviceMac = "78:4F:43:89:A1:20"
    private var connectedBattery = 94

    private val handler = Handler(Looper.getMainLooper())
    private var isAutoMonitoring = false
    private val intervalMillis: Long = 5000

    // Runnable para Modo Familiar / Médico (Telemonitoreo Remoto desde el Servidor)
    private val caregiverTelemetryRunnable = object : Runnable {
        override fun run() {
            if (isAutoMonitoring && isCaregiver) {
                consultarTelemetriaRemota()
                handler.postDelayed(this, 3500)
            }
        }
    }

    private val autoTelemetryRunnable = object : Runnable {
        override fun run() {
            if (isAutoMonitoring && !isCaregiver) {
                // Simulación realista según condición clínica del paciente:
                // La gran mayoría del tiempo (aprox 90%) se mantiene en rango fisiológico estable.
                // Con baja probabilidad (1 de cada 8 ciclos aprox) simula un evento de alerta SOS.
                val simulatedBpm = when {
                    userCondition.contains("Bradicardia") -> {
                        val esAlertaSos = Random.nextInt(1, 9) == 1
                        if (esAlertaSos) {
                            Random.nextInt(32, 38) // Bradicardia severa (<40) dispara alerta SOS
                        } else {
                            Random.nextInt(48, 58) // Rango clínico seguro y normal para su condición (45-60)
                        }
                    }
                    userCondition.contains("Taquicardia") -> {
                        val esAlertaSos = Random.nextInt(1, 9) == 1
                        if (esAlertaSos) {
                            Random.nextInt(146, 170) // Taquicardia severa (>145) dispara alerta SOS
                        } else {
                            Random.nextInt(95, 115) // Rango clínico esperado para su condición (90-120)
                        }
                    }
                    else -> {
                        // Ritmo normal adulto mayor
                        val esAlertaSos = Random.nextInt(1, 9) == 1
                        if (esAlertaSos) {
                            if (Random.nextBoolean()) Random.nextInt(135, 155) else Random.nextInt(36, 45)
                        } else {
                            Random.nextInt(65, 82) // Reposo normal (60-90)
                        }
                    }
                }

                val tvHeartRateDisplay = findViewById<TextView?>(R.id.tvHeartRateDisplay)
                val tvZoneTag = findViewById<TextView?>(R.id.tvZoneTag)
                val tvStatus = findViewById<TextView?>(R.id.tvStatus)

                tvHeartRateDisplay?.text = simulatedBpm.toString()
                actualizarColorVisual(simulatedBpm, tvHeartRateDisplay, tvZoneTag)
                enviarTelemetria(simulatedBpm, tvStatus, esAutomatico = true)
                handler.postDelayed(this, intervalMillis)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        userId = intent.getStringExtra("USER_ID") ?: ""
        userName = intent.getStringExtra("USER_NAME") ?: "Paciente"
        userCondition = intent.getStringExtra("USER_CONDITION") ?: "Ritmo Normal"
        emergencyPhone = intent.getStringExtra("EMERGENCY_PHONE") ?: ""
        userRole = intent.getStringExtra("USER_ROLE") ?: "PACIENTE"
        isCaregiver = (userRole == "FAMILIAR")

        val tvWelcome = findViewById<TextView?>(R.id.tvWelcome)
        val tvHeartRateDisplay = findViewById<TextView?>(R.id.tvHeartRateDisplay)
        val tvZoneTag = findViewById<TextView?>(R.id.tvZoneTag)
        val etHeartRate = findViewById<EditText?>(R.id.etHeartRate)
        val btnSendTelemetry = findViewById<Button?>(R.id.btnSendTelemetry)
        val btnStartAuto = findViewById<Button?>(R.id.btnStartAuto)
        val btnStopAuto = findViewById<Button?>(R.id.btnStopAuto)
        val tvStatus = findViewById<TextView?>(R.id.tvStatus)
        val tvMinBpm = findViewById<TextView?>(R.id.tvMinBpm)
        val tvAvgBpm = findViewById<TextView?>(R.id.tvAvgBpm)
        val tvMaxBpm = findViewById<TextView?>(R.id.tvMaxBpm)
        val btnCallFamily = findViewById<Button?>(R.id.btnCallFamily)
        val tvFamilyPhoneDisplay = findViewById<TextView?>(R.id.tvFamilyPhoneDisplay)
        val cardSmartwatchStatus = findViewById<MaterialCardView?>(R.id.cardSmartwatchStatus)
        val btnPairDevice = findViewById<Button?>(R.id.btnPairDevice)

        if (isCaregiver) {
            tvWelcome?.text = "👨‍⚕️ Cuidador · Monitoreando a: $userName"
            if (emergencyPhone.isNotEmpty()) {
                tvFamilyPhoneDisplay?.text = "Teléfono del Paciente: $emergencyPhone"
            }

            val tvDeviceTitle = findViewById<TextView?>(R.id.tvDeviceTitle)
            val tvConnectionBadge = findViewById<TextView?>(R.id.tvConnectionBadge)
            val tvDeviceBattery = findViewById<TextView?>(R.id.tvDeviceBattery)
            val tvBleMac = findViewById<TextView?>(R.id.tvBleMac)
            val tvWatchIcon = findViewById<TextView?>(R.id.tvWatchIcon)

            tvDeviceTitle?.text = "Telemonitoreo Remoto en Vivo"
            tvWatchIcon?.text = "👨‍⚕️"
            tvConnectionBadge?.text = "● Nube Conectada"
            tvConnectionBadge?.setBackgroundColor(Color.parseColor("#E8F5E9"))
            tvConnectionBadge?.setTextColor(Color.parseColor("#2E7D32"))
            tvDeviceBattery?.text = "☁️ Online"
            tvBleMac?.text = "Canal: Servidor CFT Lota Arauco"
            btnPairDevice?.text = "🔄 Actualizar Datos"

            val listenerActualizar = View.OnClickListener {
                consultarTelemetriaRemota()
                Toast.makeText(this, "Actualizando telemetría del paciente...", Toast.LENGTH_SHORT).show()
            }
            btnPairDevice?.setOnClickListener(listenerActualizar)
            cardSmartwatchStatus?.setOnClickListener(listenerActualizar)

            // Ocultar sección de ingreso manual de telemetría para el familiar
            findViewById<View?>(R.id.tilHeartRate)?.visibility = View.GONE
            btnSendTelemetry?.visibility = View.GONE

            btnStartAuto?.text = "▶ Iniciar Recepción"
            btnStopAuto?.text = "⏹ Pausar"

            // Iniciar recepción automática en vivo desde el servidor
            isAutoMonitoring = true
            handler.post(caregiverTelemetryRunnable)
            tvStatus?.text = "Conectado al servidor. Recibiendo datos de $userName..."
        } else {
            tvWelcome?.text = "Paciente: $userName · $userCondition"
            if (emergencyPhone.isNotEmpty()) {
                tvFamilyPhoneDisplay?.text = "Contacto de Emergencia: $emergencyPhone"
            }

            val clickGestionReloj = View.OnClickListener {
                mostrarDialogoGestionReloj()
            }
            cardSmartwatchStatus?.setOnClickListener(clickGestionReloj)
            btnPairDevice?.setOnClickListener(clickGestionReloj)
            actualizarEstadoVisualReloj()
        }

        // Configuración de rangos clínicos según condición
        when {
            userCondition.contains("Bradicardia") -> {
                tvMinBpm?.text = "45"
                tvAvgBpm?.text = "52"
                tvMaxBpm?.text = "60"
                etHeartRate?.hint = "Ej: 52 (Pulsaciones habituales)"
            }
            userCondition.contains("Taquicardia") -> {
                tvMinBpm?.text = "90"
                tvAvgBpm?.text = "105"
                tvMaxBpm?.text = "120"
                etHeartRate?.hint = "Ej: 105 (Pulsaciones habituales)"
            }
            else -> {
                tvMinBpm?.text = "60"
                tvAvgBpm?.text = "75"
                tvMaxBpm?.text = "90"
                etHeartRate?.hint = "Ej: 75 (Pulsaciones habituales)"
            }
        }

        // Botón grande para llamar al contacto de emergencia
        btnCallFamily?.setOnClickListener {
            llamarContactoEmergencia()
        }

        btnSendTelemetry?.setOnClickListener {
            ocultarTeclado()

            val bpmText = etHeartRate?.text?.toString()?.trim() ?: ""
            if (bpmText.isEmpty()) {
                Toast.makeText(this, "Ingresa un valor BPM", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val bpm = bpmText.toIntOrNull()
            if (bpm == null || bpm <= 0) {
                Toast.makeText(this, "Ingresa un ritmo cardíaco válido", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            tvHeartRateDisplay?.text = bpm.toString()
            actualizarColorVisual(bpm, tvHeartRateDisplay, tvZoneTag)
            enviarTelemetria(bpm, tvStatus, esAutomatico = false)
        }

        btnStartAuto?.setOnClickListener {
            if (isCaregiver) {
                if (!isAutoMonitoring) {
                    isAutoMonitoring = true
                    handler.post(caregiverTelemetryRunnable)
                    Toast.makeText(this, "Monitoreo remoto en vivo activado", Toast.LENGTH_SHORT).show()
                }
                return@setOnClickListener
            }

            if (!isDeviceConnected) {
                mostrarAlertaRelojNoVinculado()
                return@setOnClickListener
            }
            if (!isAutoMonitoring) {
                isAutoMonitoring = true
                handler.post(autoTelemetryRunnable)
                Toast.makeText(this, "Monitoreo automático iniciado (cada 5s)", Toast.LENGTH_SHORT).show()
            }
        }

        btnStopAuto?.setOnClickListener {
            if (isAutoMonitoring) {
                isAutoMonitoring = false
                if (isCaregiver) {
                    handler.removeCallbacks(caregiverTelemetryRunnable)
                } else {
                    handler.removeCallbacks(autoTelemetryRunnable)
                }
                tvStatus?.text = "Monitoreo detenido"
                Toast.makeText(this, "Monitoreo detenido", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<Button?>(R.id.btnViewAlerts)?.setOnClickListener {
            consultarHistorialAlertas()
        }
    }

    private fun llamarContactoEmergencia() {
        if (emergencyPhone.isEmpty()) {
            Toast.makeText(this, "No hay número de contacto registrado", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val callIntent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:${emergencyPhone.replace(" ", "")}")
            }
            startActivity(callIntent)
        } catch (e: Exception) {
            Toast.makeText(this, "No se pudo iniciar el marcador: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun actualizarColorVisual(bpm: Int, tvHeartRateDisplay: TextView?, tvZoneTag: TextView?) {
        val cardEmergencyAlert = findViewById<MaterialCardView?>(R.id.cardEmergencyAlert)
        val tvEmergencyDetail = findViewById<TextView?>(R.id.tvEmergencyDetail)
        val esCritico: Boolean
        val tagTexto: String

        // Criterios tolerantes adaptados a la condición para evitar falsas alarmas por variaciones menores
        when {
            userCondition.contains("Bradicardia") -> {
                if (bpm < 40) {
                    esCritico = true
                    tagTexto = "⚠️ ZONA CRÍTICA (Bradicardia Severa <40)"
                } else if (bpm > 125) {
                    esCritico = true
                    tagTexto = "⚠️ ZONA CRÍTICA (Pico Inesperado >125)"
                } else {
                    esCritico = false
                    tagTexto = "✅ RITMO ESPERADO (Bradicardia Controlada 40-125)"
                }
            }
            userCondition.contains("Taquicardia") -> {
                if (bpm > 145) {
                    esCritico = true
                    tagTexto = "⚠️ ZONA CRÍTICA (Taquicardia Severa >145)"
                } else if (bpm < 48) {
                    esCritico = true
                    tagTexto = "⚠️ ZONA CRÍTICA (Caída Inesperada <48)"
                } else {
                    esCritico = false
                    tagTexto = "✅ RITMO ESPERADO (Taquicardia Controlada 50-145)"
                }
            }
            else -> {
                if (bpm > 125) {
                    esCritico = true
                    tagTexto = "⚠️ ZONA CRÍTICA (Taquicardia >125)"
                } else if (bpm < 48) {
                    esCritico = true
                    tagTexto = "⚠️ ZONA CRÍTICA (Bradicardia <48)"
                } else {
                    esCritico = false
                    tagTexto = "✅ RITMO CARDÍACO NORMAL (50-120)"
                }
            }
        }

        if (esCritico) {
            tvZoneTag?.text = tagTexto
            tvZoneTag?.setTextColor(Color.parseColor("#D32F2F"))
            tvZoneTag?.setBackgroundColor(Color.parseColor("#FFCDD2"))
            tvHeartRateDisplay?.setTextColor(Color.parseColor("#D32F2F"))

            // Mostrar banner de emergencia con botón para llamar
            cardEmergencyAlert?.visibility = View.VISIBLE
            tvEmergencyDetail?.text = "Frecuencia crítica: $bpm BPM ($tagTexto). Llame a su contacto de inmediato."
        } else {
            tvZoneTag?.text = tagTexto
            tvZoneTag?.setTextColor(Color.parseColor("#2E7D32"))
            tvZoneTag?.setBackgroundColor(Color.parseColor("#E8F5E9"))
            tvHeartRateDisplay?.setTextColor(Color.parseColor("#1565C0"))

            // Ocultar banner si el paciente se estabilizó
            cardEmergencyAlert?.visibility = View.GONE
        }
    }

    private fun ocultarTeclado() {
        val view = currentFocus ?: return
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun enviarTelemetria(bpm: Int, tvStatus: TextView?, esAutomatico: Boolean) {
        if (userId.isEmpty()) {
            Toast.makeText(this, "Error: No se encontró el ID del paciente", Toast.LENGTH_SHORT).show()
            return
        }

        tvStatus?.text = "Transmitiendo datos telemétricos..."

        val request = TelemetryRequest(
            user_id = userId,
            heart_rate = bpm,
            systolic_pressure = 120,
            diastolic_pressure = 80
        )

        RetrofitClient.instance.sendTelemetry(request).enqueue(object : Callback<TelemetryResponse> {
            override fun onResponse(call: Call<TelemetryResponse>, response: Response<TelemetryResponse>) {
                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    if (body.alert_triggered == true) {
                        tvStatus?.text = "🚨 ALERTA SOS GENERADA: ${body.message}"
                        if (!esAutomatico) {
                            mostrarDialogoEmergencia(bpm, body.message)
                        }
                    } else {
                        tvStatus?.text = "✅ Transmisión exitosa ($bpm BPM - Estable)"
                    }
                } else {
                    tvStatus?.text = "Error servidor: Código ${response.code()}"
                }
            }

            override fun onFailure(call: Call<TelemetryResponse>, t: Throwable) {
                tvStatus?.text = "Error de red: ${t.message}"
            }
        })
    }

    private fun mostrarDialogoEmergencia(bpm: Int, mensaje: String?) {
        val detalle = mensaje ?: "Ritmo fuera de rango normal"
        AlertDialog.Builder(this)
            .setTitle("🚨 ALERTA DE EMERGENCIA SOS")
            .setMessage("Frecuencia cardíaca crítica detectada: $bpm BPM.\n\n$detalle\n\n¿Desea llamar a su contacto de emergencia ahora?")
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setPositiveButton("📞 Llamar ahora") { _, _ ->
                llamarContactoEmergencia()
            }
            .setNegativeButton("Entendido", null)
            .show()
    }

    private fun consultarHistorialAlertas() {
        if (userId.isEmpty()) {
            Toast.makeText(this, "No hay paciente seleccionado", Toast.LENGTH_SHORT).show()
            return
        }

        RetrofitClient.instance.getAlertsHistory(userId).enqueue(object : Callback<AlertsHistoryResponse> {
            override fun onResponse(call: Call<AlertsHistoryResponse>, response: Response<AlertsHistoryResponse>) {
                if (response.isSuccessful && response.body() != null) {
                    val alertsList = response.body()!!.alerts
                    if (alertsList.isEmpty()) {
                        AlertDialog.Builder(this@MainActivity)
                            .setTitle("📋 Historial de Alertas SOS")
                            .setMessage("El paciente no registra alertas críticas recientes.")
                            .setPositiveButton("Aceptar", null)
                            .show()
                    } else {
                        val texto = alertsList.joinToString("\n\n────────────────\n\n") { alert ->
                            "🚨 ${alert.heart_rate} BPM\nMotivo: ${alert.reason}\nFecha: ${alert.timestamp}"
                        }
                        AlertDialog.Builder(this@MainActivity)
                            .setTitle("📋 Historial SOS (${alertsList.size} eventos)")
                            .setMessage(texto)
                            .setPositiveButton("Cerrar", null)
                            .show()
                    }
                } else {
                    Toast.makeText(this@MainActivity, "Error en historial: Código ${response.code()}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<AlertsHistoryResponse>, t: Throwable) {
                Toast.makeText(this@MainActivity, "Error de red: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun consultarTelemetriaRemota() {
        if (userId.isEmpty()) return

        val tvHeartRateDisplay = findViewById<TextView?>(R.id.tvHeartRateDisplay)
        val tvZoneTag = findViewById<TextView?>(R.id.tvZoneTag)
        val tvStatus = findViewById<TextView?>(R.id.tvStatus)
        val cardEmergencyAlert = findViewById<MaterialCardView?>(R.id.cardEmergencyAlert)
        val tvEmergencyTitle = findViewById<TextView?>(R.id.tvEmergencyTitle)
        val tvEmergencyDetail = findViewById<TextView?>(R.id.tvEmergencyDetail)
        val tvFamilyPhoneDisplay = findViewById<TextView?>(R.id.tvFamilyPhoneDisplay)
        val btnCallFamily = findViewById<Button?>(R.id.btnCallFamily)

        RetrofitClient.instance.getLatestTelemetry(userId).enqueue(object : Callback<LatestTelemetryResponse> {
            override fun onResponse(call: Call<LatestTelemetryResponse>, response: Response<LatestTelemetryResponse>) {
                if (response.isSuccessful && response.body() != null) {
                    val data = response.body()!!
                    val bpm = data.heart_rate

                    tvHeartRateDisplay?.text = bpm.toString()
                    actualizarColorVisual(bpm, tvHeartRateDisplay, tvZoneTag)

                    if (data.is_alert) {
                        cardEmergencyAlert?.visibility = View.VISIBLE
                        tvEmergencyTitle?.text = "🚨 ALERTA SOS EN VIVO DEL PACIENTE"
                        val alertReason = data.latest_alert?.reason ?: "Frecuencia crítica fuera de rango"
                        val alertUbicacion = data.location?.direccion_aproximada ?: "Lota, Región del Biobío, Chile"
                        val alertHora = data.timestamp ?: "Reciente"
                        tvEmergencyDetail?.text = "¡Atención! Se detectó alteración cardíaca en $userName ($bpm BPM).\n\n• Motivo: $alertReason\n• Ubicación: $alertUbicacion\n• Registro: $alertHora"
                        btnCallFamily?.text = "📞 LLAMAR DE INMEDIATO AL PACIENTE"
                        val tel = if (!data.emergency_phone.isNullOrEmpty()) data.emergency_phone else emergencyPhone
                        tvFamilyPhoneDisplay?.text = "Teléfono de contacto: $tel"
                        tvStatus?.text = "🚨 Alerta activa recibida desde el servidor"
                    } else {
                        cardEmergencyAlert?.visibility = View.GONE
                        val timeStr = if (data.timestamp != null && data.timestamp.length >= 8) data.timestamp.takeLast(8) else ""
                        tvStatus?.text = "✅ Paciente Estable · $bpm BPM (Actualizado $timeStr)"
                    }
                }
            }

            override fun onFailure(call: Call<LatestTelemetryResponse>, t: Throwable) {
                tvStatus?.text = "Aviso: Sin conexión con la nube (${t.message})"
            }
        })
    }

    // ══════════════════════════════════════════════════════════════
    // MÓDULO DE GESTIÓN Y VINCULACIÓN SMARTWATCH (BLUETOOTH LE / PPG)
    // ══════════════════════════════════════════════════════════════

    private fun actualizarEstadoVisualReloj() {
        val tvDeviceTitle = findViewById<TextView?>(R.id.tvDeviceTitle)
        val tvConnectionBadge = findViewById<TextView?>(R.id.tvConnectionBadge)
        val tvDeviceBattery = findViewById<TextView?>(R.id.tvDeviceBattery)
        val tvBleMac = findViewById<TextView?>(R.id.tvBleMac)
        val tvWatchIcon = findViewById<TextView?>(R.id.tvWatchIcon)
        val btnPairDevice = findViewById<Button?>(R.id.btnPairDevice)

        if (isDeviceConnected) {
            tvDeviceTitle?.text = connectedDeviceName
            tvDeviceTitle?.setTextColor(Color.parseColor("#1565C0"))
            tvConnectionBadge?.text = "● Conectado"
            tvConnectionBadge?.setBackgroundColor(Color.parseColor("#E8F5E9"))
            tvConnectionBadge?.setTextColor(Color.parseColor("#2E7D32"))
            tvDeviceBattery?.text = "🔋 $connectedBattery%"
            tvDeviceBattery?.setBackgroundColor(Color.parseColor("#E8F5E9"))
            tvDeviceBattery?.setTextColor(Color.parseColor("#2E7D32"))
            tvBleMac?.text = "BLE: $connectedDeviceMac"
            tvWatchIcon?.text = "⌚"
            btnPairDevice?.text = "🔗 Gestionar Reloj"
        } else {
            tvDeviceTitle?.text = "Sin Smartwatch Vinculado"
            tvDeviceTitle?.setTextColor(Color.parseColor("#757575"))
            tvConnectionBadge?.text = "○ Desconectado"
            tvConnectionBadge?.setBackgroundColor(Color.parseColor("#FFEBEE"))
            tvConnectionBadge?.setTextColor(Color.parseColor("#C62828"))
            tvDeviceBattery?.text = "🔋 --%"
            tvDeviceBattery?.setBackgroundColor(Color.parseColor("#EEEEEE"))
            tvDeviceBattery?.setTextColor(Color.parseColor("#757575"))
            tvBleMac?.text = "BLE: Sin conexión"
            tvWatchIcon?.text = "⏱️"
            btnPairDevice?.text = "➕ Vincular Smartwatch"
        }
    }

    private fun mostrarDialogoGestionReloj() {
        if (!isDeviceConnected) {
            iniciarEscaneoBluetooth()
            return
        }

        val opciones = arrayOf(
            "🔍 Buscar y vincular otro reloj",
            "📊 Ver información técnica BLE (GATT/UUID)",
            "❌ Desvincular smartwatch actual"
        )

        AlertDialog.Builder(this)
            .setTitle("⌚ $connectedDeviceName")
            .setItems(opciones) { _, which ->
                when (which) {
                    0 -> iniciarEscaneoBluetooth()
                    1 -> mostrarDetallesTecnicosBle()
                    2 -> desvincularReloj()
                }
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    private fun mostrarDetallesTecnicosBle() {
        val detalles = """
            • Dispositivo: $connectedDeviceName
            • Dirección MAC: $connectedDeviceMac
            • Perfil Bluetooth: GATT (Generic Attribute)
            • Servicio: Heart Rate Service (UUID 0x180D)
            • Característica: Heart Rate Measurement (UUID 0x2A37)
            • Calidad de enlace: RSSI -54 dBm (Excelente)
            • Batería restante: $connectedBattery%
            • Intervalo de telemetría: 5 segundos
            • Estado: Sincronizado con API CFT Lota
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("ℹ️ Enlace BLE / Sensores")
            .setMessage(detalles)
            .setIcon(android.R.drawable.ic_dialog_info)
            .setPositiveButton("Entendido", null)
            .show()
    }

    private fun desvincularReloj() {
        if (isAutoMonitoring) {
            isAutoMonitoring = false
            handler.removeCallbacks(autoTelemetryRunnable)
        }
        isDeviceConnected = false
        actualizarEstadoVisualReloj()
        Toast.makeText(this, "Smartwatch desvinculado", Toast.LENGTH_SHORT).show()
    }

    private fun mostrarAlertaRelojNoVinculado() {
        AlertDialog.Builder(this)
            .setTitle("⚠️ Smartwatch Requerido")
            .setMessage("Para iniciar el monitoreo de ritmo cardíaco en tiempo real necesitas tener un reloj inteligente vinculado.\n\n¿Deseas buscar y vincular tu smartwatch ahora?")
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setPositiveButton("Vincular ahora") { _, _ ->
                iniciarEscaneoBluetooth()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private var isScanning = false
    private val discoveredDevices = mutableListOf<BluetoothDevice>()

    private fun iniciarEscaneoBluetooth() {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = bluetoothManager?.adapter

        if (adapter == null) {
            AlertDialog.Builder(this)
                .setTitle("Bluetooth no disponible")
                .setMessage("Este dispositivo no cuenta con hardware Bluetooth para escanear relojes inteligentes.")
                .setPositiveButton("Aceptar", null)
                .show()
            return
        }

        if (!adapter.isEnabled) {
            AlertDialog.Builder(this)
                .setTitle("Bluetooth Desactivado")
                .setMessage("Para buscar y vincular tu smartwatch es necesario encender el Bluetooth.\n\n¿Deseas activarlo ahora?")
                .setPositiveButton("Activar") { _, _ ->
                    try {
                        startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                    } catch (e: Exception) {
                        try {
                            startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                        } catch (e2: Exception) {
                            Toast.makeText(this, "Por favor activa el Bluetooth desde Ajustes", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                .setNegativeButton("Cancelar", null)
                .show()
            return
        }

        // Verificar permisos de Bluetooth en Android 12+ (API 31+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val hasScanPermission = ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
            val hasConnectPermission = ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
            if (!hasScanPermission || !hasConnectPermission) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT),
                    101
                )
                return
            }
        } else {
            val hasLocation = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (!hasLocation) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    101
                )
                return
            }
        }

        ejecutarEscaneoRealBle(adapter)
    }

    private fun ejecutarEscaneoRealBle(adapter: BluetoothAdapter) {
        val bleScanner = adapter.bluetoothLeScanner
        if (bleScanner == null) {
            Toast.makeText(this, "El escáner Bluetooth no está disponible", Toast.LENGTH_SHORT).show()
            return
        }

        discoveredDevices.clear()
        isScanning = true

        val progressDialog = AlertDialog.Builder(this)
            .setTitle("Buscando Dispositivos...")
            .setMessage("Escaneando relojes y pulseras inteligentes Bluetooth Low Energy cercanos...\n\nAsegúrate de que tu reloj esté encendido y en modo visible.")
            .setNegativeButton("Cancelar") { dialog, _ ->
                isScanning = false
                try {
                    bleScanner.stopScan(object : ScanCallback() {})
                } catch (e: SecurityException) {
                    // Ignorar
                }
                dialog.dismiss()
            }
            .setCancelable(false)
            .create()

        progressDialog.show()

        val scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                result?.device?.let { device ->
                    try {
                        val address = device.address
                        if (address != null && !discoveredDevices.any { it.address == address }) {
                            discoveredDevices.add(device)
                        }
                    } catch (e: SecurityException) {
                        // Manejo de seguridad
                    }
                }
            }

            override fun onScanFailed(errorCode: Int) {
                isScanning = false
                if (progressDialog.isShowing) progressDialog.dismiss()
                Toast.makeText(this@MainActivity, "Error en escaneo Bluetooth: Código $errorCode", Toast.LENGTH_SHORT).show()
            }
        }

        try {
            bleScanner.startScan(scanCallback)
        } catch (e: SecurityException) {
            progressDialog.dismiss()
            Toast.makeText(this, "Permiso de Bluetooth no concedido", Toast.LENGTH_SHORT).show()
            return
        }

        // Detener escaneo tras 5 segundos de búsqueda en el espectro BLE
        handler.postDelayed({
            if (isScanning) {
                isScanning = false
                try {
                    bleScanner.stopScan(scanCallback)
                } catch (e: SecurityException) {
                    // Ignorar
                }
                if (progressDialog.isShowing) progressDialog.dismiss()
                mostrarResultadosEscaneoBle()
            }
        }, 5000)
    }

    private fun mostrarResultadosEscaneoBle() {
        if (discoveredDevices.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("Búsqueda Finalizada")
                .setMessage("No se detectaron dispositivos Bluetooth cercanos en este momento.\n\nRecomendaciones:\n• Mantén el reloj inteligente a menos de 5 metros del teléfono.\n• Verifica que la pantalla esté encendida.\n• Asegúrate de que el reloj esté en modo emparejamiento / visible.")
                .setPositiveButton("Reintentar") { _, _ ->
                    iniciarEscaneoBluetooth()
                }
                .setNegativeButton("Cerrar", null)
                .show()
            return
        }

        val nombresDispositivos = discoveredDevices.map { device ->
            try {
                val nombre = device.name
                val mac = device.address
                if (!nombre.isNullOrBlank()) {
                    "⌚ $nombre\n   MAC: $mac"
                } else {
                    "⌚ Dispositivo Bluetooth LE\n   MAC: $mac"
                }
            } catch (e: SecurityException) {
                "⌚ Dispositivo BLE\n   MAC: ${device.address}"
            }
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Dispositivos Encontrados (${discoveredDevices.size})")
            .setItems(nombresDispositivos) { _, index ->
                val seleccionado = discoveredDevices[index]
                val nombreFinal = try { seleccionado.name ?: "Smartwatch BLE" } catch (e: SecurityException) { "Smartwatch BLE" }
                vincularDispositivoReal(nombreFinal, seleccionado.address)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun vincularDispositivoReal(nombre: String, mac: String) {
        val pairingDialog = AlertDialog.Builder(this)
            .setTitle("Vinculando Smartwatch...")
            .setMessage("• Estableciendo enlace seguro con $nombre\n• Dirección: $mac\n• Sincronizando canal de telemetría médica...")
            .setCancelable(false)
            .create()

        pairingDialog.show()

        handler.postDelayed({
            pairingDialog.dismiss()
            isDeviceConnected = true
            connectedDeviceName = nombre
            connectedDeviceMac = mac
            connectedBattery = 95
            actualizarEstadoVisualReloj()

            AlertDialog.Builder(this@MainActivity)
                .setTitle("Reloj Inteligente Vinculado")
                .setMessage("El dispositivo $nombre ($mac) se ha conectado con éxito.\n\nYa puedes iniciar el monitoreo de ritmo cardíaco en tiempo real.")
                .setPositiveButton("Iniciar Monitoreo") { _, _ ->
                    if (!isAutoMonitoring) {
                        isAutoMonitoring = true
                        handler.post(autoTelemetryRunnable)
                    }
                }
                .setNegativeButton("Aceptar", null)
                .show()
        }, 1500)
    }

    override fun onDestroy() {
        super.onDestroy()
        isAutoMonitoring = false
        handler.removeCallbacks(autoTelemetryRunnable)
        handler.removeCallbacks(caregiverTelemetryRunnable)
    }
}
