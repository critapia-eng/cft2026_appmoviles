package cl.cft2026.pulseup

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import kotlin.random.Random

class MainActivity : AppCompatActivity() {

    private var userId: String = ""
    private var userName: String = ""
    private var userCondition: String = ""

    private val handler = Handler(Looper.getMainLooper())
    private var isAutoMonitoring = false
    private val intervalMillis: Long = 5000 // Envío automático cada 5 segundos

    private val autoTelemetryRunnable = object : Runnable {
        override fun run() {
            if (isAutoMonitoring) {
                // Simulación con rangos médicos realistas según la condición del adulto mayor
                val simulatedBpm = when {
                    userCondition.contains("Bradicardia") -> {
                        // Bradicardia típica: 48-58 BPM. 1 de cada 5 veces simula evento crítico (<40)
                        if (Random.nextInt(1, 6) == 1) Random.nextInt(34, 39) else Random.nextInt(48, 58)
                    }
                    userCondition.contains("Taquicardia") -> {
                        // Taquicardia típica: 98-118 BPM. 1 de cada 5 veces simula evento crítico (>145)
                        if (Random.nextInt(1, 6) == 1) Random.nextInt(150, 175) else Random.nextInt(98, 118)
                    }
                    else -> {
                        // Ritmo normal adulto mayor: 68-82 BPM. 1 de cada 5 veces simula evento crítico (>125)
                        if (Random.nextInt(1, 6) == 1) Random.nextInt(130, 150) else Random.nextInt(68, 82)
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

        tvWelcome?.text = "Paciente: $userName ($userCondition)"

        // Configuración de métricas y referencias clínicas según la condición del paciente
        when {
            userCondition.contains("Bradicardia") -> {
                tvMinBpm?.text = "45"
                tvAvgBpm?.text = "54"
                tvMaxBpm?.text = "68"
                etHeartRate?.hint = "Ej: 52 habitual o 35 para alerta SOS"
            }
            userCondition.contains("Taquicardia") -> {
                tvMinBpm?.text = "95"
                tvAvgBpm?.text = "108"
                tvMaxBpm?.text = "135"
                etHeartRate?.hint = "Ej: 105 habitual o 160 para alerta SOS"
            }
            else -> {
                tvMinBpm?.text = "60"
                tvAvgBpm?.text = "74"
                tvMaxBpm?.text = "98"
                etHeartRate?.hint = "Ej: 75 habitual o 140 para alerta SOS"
            }
        }

        // Envío manual
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

        // Encender simulación automática (Smartwatch/Banda)
        btnStartAuto?.setOnClickListener {
            if (!isAutoMonitoring) {
                isAutoMonitoring = true
                handler.post(autoTelemetryRunnable)
                Toast.makeText(this, "Monitoreo automático iniciado (cada 5s)", Toast.LENGTH_SHORT).show()
            }
        }

        // Detener simulación automática
        btnStopAuto?.setOnClickListener {
            if (isAutoMonitoring) {
                isAutoMonitoring = false
                handler.removeCallbacks(autoTelemetryRunnable)
                tvStatus?.text = "Monitoreo automático detenido"
                Toast.makeText(this, "Monitoreo automático detenido", Toast.LENGTH_SHORT).show()
            }
        }

        // Consultar Historial de Alertas SOS desde el VPS
        val btnViewAlerts = findViewById<Button?>(R.id.btnViewAlerts)
        btnViewAlerts?.setOnClickListener {
            consultarHistorialAlertas()
        }
    }

    private fun actualizarColorVisual(bpm: Int, tvHeartRateDisplay: TextView?, tvZoneTag: TextView?) {
        val esCritico: Boolean
        val tagTexto: String

        when {
            userCondition.contains("Bradicardia") -> {
                if (bpm < 40) {
                    esCritico = true
                    tagTexto = "⚠️ ZONA CRÍTICA (Bradicardia Severa <40)"
                } else if (bpm > 120) {
                    esCritico = true
                    tagTexto = "⚠️ ZONA CRÍTICA (Ritmo Elevado >120)"
                } else {
                    esCritico = false
                    tagTexto = "✅ RITMO ESPERADO (Bradicardia Controlada)"
                }
            }
            userCondition.contains("Taquicardia") -> {
                if (bpm > 145) {
                    esCritico = true
                    tagTexto = "⚠️ ZONA CRÍTICA (Taquicardia Severa >145)"
                } else if (bpm < 50) {
                    esCritico = true
                    tagTexto = "⚠️ ZONA CRÍTICA (Caída Inesperada <50)"
                } else {
                    esCritico = false
                    tagTexto = "✅ RITMO ESPERADO (Taquicardia Controlada)"
                }
            }
            else -> {
                if (bpm > 125) {
                    esCritico = true
                    tagTexto = "⚠️ ZONA CRÍTICA (Taquicardia >125)"
                } else if (bpm < 50) {
                    esCritico = true
                    tagTexto = "⚠️ ZONA CRÍTICA (Bradicardia <50)"
                } else {
                    esCritico = false
                    tagTexto = "✅ RITMO CARDÍACO NORMAL"
                }
            }
        }

        if (esCritico) {
            tvZoneTag?.text = tagTexto
            tvZoneTag?.setTextColor(Color.parseColor("#D32F2F"))
            tvHeartRateDisplay?.setTextColor(Color.parseColor("#D32F2F"))
        } else {
            tvZoneTag?.text = tagTexto
            tvZoneTag?.setTextColor(Color.parseColor("#2E7D32"))
            tvHeartRateDisplay?.setTextColor(Color.parseColor("#1976D2"))
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

        tvStatus?.text = "Enviando datos al servidor..."

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
                    val isAlert = body.alert_triggered == true

                    if (isAlert) {
                        tvStatus?.text = "🚨 ALERTA SOS GENERADA: ${body.message}"
                        if (!esAutomatico) {
                            mostrarDialogoEmergencia(bpm, body.message)
                        }
                    } else {
                        tvStatus?.text = "✅ Medición registrada ($bpm BPM)"
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
            .setMessage("Frecuencia cardíaca crítica detectada: $bpm BPM.\n\n$detalle\n\nSe ha emitido el reporte de alerta al servidor.")
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setPositiveButton("Entendido") { dialog, _ ->
                dialog.dismiss()
            }
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

    override fun onDestroy() {
        super.onDestroy()
        isAutoMonitoring = false
        handler.removeCallbacks(autoTelemetryRunnable)
    }
}