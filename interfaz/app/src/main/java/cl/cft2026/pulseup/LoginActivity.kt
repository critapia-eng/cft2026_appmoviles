package cl.cft2026.pulseup

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
        val spinnerCondition = findViewById<Spinner>(R.id.spinnerCondition)
        val btnRegister = findViewById<Button>(R.id.btnRegister)
        val tvStatus = findViewById<TextView>(R.id.tvStatus)

        // Opciones del desplegable claras y sin tecnicismos para el adulto mayor
        val conditions = arrayOf(
            "Ritmo Normal",
            "Bradicardia (Pulsaciones Lentas)",
            "Taquicardia (Pulsaciones Rápidas)"
        )

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, conditions)
        spinnerCondition.adapter = adapter

        btnRegister.setOnClickListener {
            val name = etName.text.toString().trim()
            val rut = etRut.text.toString().trim()
            val phone = etPhone.text.toString().trim()
            val selectedCondition = spinnerCondition.selectedItem.toString()

            if (name.isEmpty() || rut.isEmpty() || phone.isEmpty()) {
                Toast.makeText(this, "Por favor completa todos los campos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val request = UserRegisterRequest(
                name = name,
                rut = rut,
                emergency_phone = phone,
                condition_type = selectedCondition
            )

            tvStatus.text = "Registrando paciente..."

            RetrofitClient.instance.registerUser(request).enqueue(object : Callback<UserRegisterResponse> {
                override fun onResponse(call: Call<UserRegisterResponse>, response: Response<UserRegisterResponse>) {
                    if (response.isSuccessful && response.body() != null) {
                        val userId = response.body()?.user_id ?: ""
                        Toast.makeText(this@LoginActivity, "¡Registro Exitoso!", Toast.LENGTH_SHORT).show()

                        val intent = Intent(this@LoginActivity, MainActivity::class.java).apply {
                            putExtra("USER_ID", userId)
                            putExtra("USER_NAME", name)
                            putExtra("USER_CONDITION", selectedCondition)
                        }
                        startActivity(intent)
                        finish()
                    } else {
                        // Manejo seguro del error leyendo únicamente el código de estado
                        val code = response.code()
                        tvStatus.text = "Error al registrar: Código $code"
                        Toast.makeText(this@LoginActivity, "Error en servidor ($code)", Toast.LENGTH_LONG).show()
                    }
                }

                override fun onFailure(call: Call<UserRegisterResponse>, t: Throwable) {
                    tvStatus.text = "Error de conexión: ${t.message}"
                    Toast.makeText(this@LoginActivity, "Fallo de conexión", Toast.LENGTH_SHORT).show()
                }
            })
        }
    }
}