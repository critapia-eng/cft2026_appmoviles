package cl.cft2026.pulseup

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {

    private const val BASE_URL = "https://api2.uniteccftla.space/"
    
    // Llave de seguridad que autentica la app frente al backend
    private const val API_KEY = "PulseUP_CFT2026_SecKey"

    // Interceptor para inyectar automáticamente la cabecera X-API-KEY en cada petición
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("X-API-KEY", API_KEY)
                .build()
            chain.proceed(request)
        }
        .build()

    val instance: ApiService by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        retrofit.create(ApiService::class.java)
    }
}