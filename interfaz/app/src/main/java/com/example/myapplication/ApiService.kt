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
    val id: Int,
    val heart_rate: Int,
    val reason: String,
    val timestamp: String
)

interface ApiService {
    @POST("users/register")
    fun registerUser(@Body request: UserRegisterRequest): Call<UserRegisterResponse>

    @POST("telemetry/send")
    fun sendTelemetry(@Body request: TelemetryRequest): Call<TelemetryResponse>

    @GET("alerts/{user_id}")
    fun getAlertsHistory(@Path("user_id") userId: String): Call<AlertsHistoryResponse>
}
