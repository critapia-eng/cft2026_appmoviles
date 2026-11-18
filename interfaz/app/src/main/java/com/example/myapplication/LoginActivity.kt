package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class LoginActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val rgUserRole = findViewById<RadioGroup>(R.id.rgUserRole)
        val rbPatient = findViewById<RadioButton>(R.id.rbPatient)
        val rbCaregiver = findViewById<RadioButton>(R.id.rbCaregiver)
        val tvLabelRut = findViewById<TextView>(R.id.tvLabelRut)
        val etRut = findViewById<EditText>(R.id.etRut)
        val tvCaregiverHint = findViewById<TextView>(R.id.tvCaregiverHint)
        val llPatientOnlyFields = findViewById<LinearLayout>(R.id.llPatientOnlyFields)

        val etName = findViewById<EditText>(R.id.etName)
        val etPhone = findViewById<EditText>(R.id.etPhone)
        val etMedicalHistory = findViewById<EditText>(R.id.etMedicalHistory)
        val spinnerCondition = findViewById<Spinner>(R.id.spinnerCondition)
        val btnRegister = findViewById<Button>(R.id.btnRegister)
        val tvStatus = findViewById<TextView>(R.id.tvStatus)

        val conditions = arrayOf(
            "Ritmo Normal",
            "Bradicardia (Pulsaciones Lentas)",
            "Taquicardia (Pulsaciones Rápidas)"
        )
        spinnerCondition.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            conditions
        )

        // Cambiar la interfaz dinámicamente según el rol seleccionado
        rgUserRole.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == R.id.rbCaregiver) {
                // Modo Familiar / Médico: Ocultar campos de paciente y solicitar solo el RUT a monitorear
                llPatientOnlyFields.visibility = View.GONE
                tvCaregiverHint.visibility = View.VISIBLE
                tvLabelRut.text = "RUT del Paciente a Monitorear"
                etRut.hint = "Ej: 12.345.678-9 (RUT del paciente)"
                btnRegister.text = "🔍 Verificar y Monitorear Paciente"
                tvStatus.text = ""
            } else {
                // Modo Paciente: Mostrar formulario completo de registro clínico y vinculación
                llPatientOnlyFields.visibility = View.VISIBLE
                tvCaregiverHint.visibility = View.GONE
                tvLabelRut.text = getString(R.string.label_patient_rut)
                etRut.hint = getString(R.string.hint_patient_rut)
                btnRegister.text = getString(R.string.btn_start_monitoring)
                tvStatus.text = ""
            }
        }

        btnRegister.setOnClickListener {
            val rut = etRut.text.toString().trim()
            val isCaregiver = rbCaregiver.isChecked

            if (rut.isEmpty()) {
                Toast.makeText(this, "Por favor ingresa el RUT", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // ══════════════════════════════════════════════════════════════════
            // CASO 1: ACCESO FAMILIAR / MÉDICO (DEBE EXISTIR EL PACIENTE SÍ O SÍ)
            // ══════════════════════════════════════════════════════════════════
            if (isCaregiver) {
                tvStatus.text = "Consultando paciente en el sistema..."
                btnRegister.isEnabled = false

                RetrofitClient.instance.getUserByRut(rut).enqueue(object : Callback<UserRegisterResponse> {
                    override fun onResponse(
                        call: Call<UserRegisterResponse>,
                        response: Response<UserRegisterResponse>
                    ) {
                        btnRegister.isEnabled = true
                        if (response.isSuccessful && response.body() != null) {
                            val patient = response.body()!!
                            Toast.makeText(
                                this@LoginActivity,
                                "¡Paciente verificado: ${patient.name}!",
                                Toast.LENGTH_SHORT
                            ).show()

                            val intent = Intent(this@LoginActivity, MainActivity::class.java).apply {
                                putExtra("USER_ID", patient.user_id)
                                putExtra("USER_NAME", patient.name)
                                putExtra("USER_CONDITION", patient.condition_type ?: "Ritmo Normal")
                                putExtra("EMERGENCY_PHONE", patient.emergency_phone)
                                putExtra("USER_ROLE", "FAMILIAR")
                            }
                            startActivity(intent)
                            finish()
                        } else {
                            tvStatus.text = "Paciente no registrado en la base de datos"
                            AlertDialog.Builder(this@LoginActivity)
                                .setTitle("⚠️ Paciente No Encontrado")
                                .setMessage("No existe ningún paciente registrado con el RUT ingresado ($rut).\n\nPara que un familiar o médico pueda monitorear a un paciente, el paciente debe registrarse primero en la aplicación.")
                                .setIcon(android.R.drawable.ic_dialog_alert)
                                .setPositiveButton("Entendido", null)
                                .show()
                        }
                    }

                    override fun onFailure(call: Call<UserRegisterResponse>, t: Throwable) {
                        btnRegister.isEnabled = true
                        tvStatus.text = "Error de conexión: ${t.message}"
                        Toast.makeText(this@LoginActivity, "Fallo al conectar con el servidor", Toast.LENGTH_SHORT).show()
                    }
                })
                return@setOnClickListener
            }

            // ══════════════════════════════════════════════════════════════════
            // CASO 2: REGISTRO / ACCESO DE PACIENTE
            // ══════════════════════════════════════════════════════════════════
            val name = etName.text.toString().trim()
            val phone = etPhone.text.toString().trim()
            val selectedCondition = spinnerCondition.selectedItem.toString()

            if (name.isEmpty() || phone.isEmpty()) {
                Toast.makeText(this, "Por favor completa el nombre y teléfono", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val medicalHistory = etMedicalHistory.text.toString().trim()

            val request = UserRegisterRequest(
                rut = rut,
                name = name,
                email = "$rut@uniteccftla.space",
                phone = phone,
                emergency_phone = phone,
                medical_history = if (medicalHistory.isNotEmpty()) medicalHistory else "Sin antecedentes reportados"
            )

            tvStatus.text = "Registrando paciente..."
            btnRegister.isEnabled = false

            RetrofitClient.instance.registerUser(request).enqueue(object : Callback<UserRegisterResponse> {
                override fun onResponse(
                    call: Call<UserRegisterResponse>,
                    response: Response<UserRegisterResponse>
                ) {
                    btnRegister.isEnabled = true
                    if (response.isSuccessful && response.body() != null) {
                        val userId = response.body()?.user_id ?: ""
                        val patientName = response.body()?.name ?: name
                        Toast.makeText(this@LoginActivity, "¡Registro Exitoso!", Toast.LENGTH_SHORT).show()

                        val intent = Intent(this@LoginActivity, MainActivity::class.java).apply {
                            putExtra("USER_ID", userId)
                            putExtra("USER_NAME", patientName)
                            putExtra("USER_CONDITION", selectedCondition)
                            putExtra("EMERGENCY_PHONE", phone)
                            putExtra("USER_ROLE", "PACIENTE")
                        }
                        startActivity(intent)
                        finish()
                    } else {
                        tvStatus.text = "Error al registrar: Código ${response.code()}"
                        Toast.makeText(
                            this@LoginActivity,
                            "Error en servidor (${response.code()})",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

                override fun onFailure(call: Call<UserRegisterResponse>, t: Throwable) {
                    btnRegister.isEnabled = true
                    tvStatus.text = "Error de conexión: ${t.message}"
                    Toast.makeText(this@LoginActivity, "Fallo de conexión", Toast.LENGTH_SHORT).show()
                }
            })
        }
    }
}
