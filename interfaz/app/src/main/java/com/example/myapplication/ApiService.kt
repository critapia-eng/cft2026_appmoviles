package com.example.myapplication

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

data class UserRegisterRequest(
    val rut: String,
    val name: String,
    val email: String,
    val phone: String,
    val emergency_phone: String,
    val medical_history: String? = null
)

data class UserRegisterResponse(
    val user_id: String,
    val name: String,
    val rut_hashed: String,
    val emergency_phone: String,
    val medical_history: String? = null,
    val condition_type: String
)

data class TelemetryRequest(
    val user_id: String,
    val heart_rate: Int,
    val systolic_pressure: Int,
    val diastolic_pressure: Int
)

data class TelemetryResponse(
    val alert_triggered: Boolean? = false,
    val message: String? = null
)

data class AlertsHistoryResponse(
    val status: String,
    val count: Int,
    val alerts: List<AlertItem>
)

data class AlertItem(
    val id: Int? = null,
    val alert_id: String? = null,
    val heart_rate: Int? = null,
    val reason: String? = null,
    val contact_phone: String? = null,
    val status: String? = null,
    val timestamp: String? = null
)

data class LocationData(
    val latitud: Double? = null,
    val longitud: Double? = null,
    val direccion_aproximada: String? = null
)

data class LatestTelemetryResponse(
    val status: String,
    val user_id: String,
    val patient_name: String? = null,
    val emergency_phone: String? = null,
    val heart_rate: Int = 75,
    val systolic_pressure: Int = 120,
    val diastolic_pressure: Int = 80,
    val timestamp: String? = null,
    val is_alert: Boolean = false,
    val latest_alert: AlertItem? = null,
    val location: LocationData? = null
)

interface ApiService {
    @POST("users/register")
    fun registerUser(@Body request: UserRegisterRequest): Call<UserRegisterResponse>

    @POST("telemetry/send")
    fun sendTelemetry(@Body request: TelemetryRequest): Call<TelemetryResponse>

    @GET("alerts/{user_id}")
    fun getAlertsHistory(@Path("user_id") userId: String): Call<AlertsHistoryResponse>

    @GET("telemetry/latest/{user_id}")
    fun getLatestTelemetry(@Path("user_id") userId: String): Call<LatestTelemetryResponse>

    @GET("users/by-rut/{rut}")
    fun getUserByRut(@Path("rut") rut: String): Call<UserRegisterResponse>
}
