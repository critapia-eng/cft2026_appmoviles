package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class LoginActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val etName = findViewById<EditText>(R.id.etName)
        val etRut = findViewById<EditText>(R.id.etRut)
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

        val rgUserRole = findViewById<android.widget.RadioGroup>(R.id.rgUserRole)
        val rbCaregiver = findViewById<android.widget.RadioButton>(R.id.rbCaregiver)

        rgUserRole.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == R.id.rbCaregiver) {
                btnRegister.text = "Acceder como Cuidador / Médico"
            } else {
                btnRegister.text = getString(R.string.btn_start_monitoring)
            }
        }

        btnRegister.setOnClickListener {
            val name = etName.text.toString().trim()
            val rut = etRut.text.toString().trim()
            val phone = etPhone.text.toString().trim()
            val selectedCondition = spinnerCondition.selectedItem.toString()
            val isCaregiver = rbCaregiver.isChecked
            val userRole = if (isCaregiver) "FAMILIAR" else "PACIENTE"

            if (name.isEmpty() || rut.isEmpty() || phone.isEmpty()) {
                Toast.makeText(this, "Por favor completa todos los campos", Toast.LENGTH_SHORT).show()
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

            tvStatus.text = if (isCaregiver) "Verificando acceso de supervisor..." else "Registrando paciente..."
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
                        val welcomeMsg = if (isCaregiver) "¡Acceso concedido a telemonitoreo!" else "¡Registro Exitoso!"
                        Toast.makeText(this@LoginActivity, welcomeMsg, Toast.LENGTH_SHORT).show()

                        val intent = Intent(this@LoginActivity, MainActivity::class.java).apply {
                            putExtra("USER_ID", userId)
                            putExtra("USER_NAME", patientName)
                            putExtra("USER_CONDITION", selectedCondition)
                            putExtra("EMERGENCY_PHONE", phone)
                            putExtra("USER_ROLE", userRole)
                        }
                        startActivity(intent)
                        finish()
                    } else {
                        tvStatus.text = "Error al autenticar: Código ${response.code()}"
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
