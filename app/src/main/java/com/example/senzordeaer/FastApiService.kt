package com.example.senzordeaer

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

class FastApiService {
    private val client = OkHttpClient()
    private val gson = Gson()
    private val baseUrl = "https://ai-senzor-de-calitate-a-aerului-production.up.railway.app"

    private fun createPostRequest(path: String, bodyJson: String = "{}"): Request {
        val body = bodyJson.toRequestBody("application/json".toMediaType())
        return Request.Builder()
            .url("$baseUrl$path")
            .post(body)
            .build()
    }

    suspend fun chatWithAi(message: String): String = withContext(Dispatchers.IO) {
        val json = gson.toJson(mapOf("message" to message))
        val request = createPostRequest("/chat", json)
        try {
            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""
                if (!response.isSuccessful) return@withContext "Serverul AI nu a răspuns corect."
                val result = gson.fromJson(responseBody, Map::class.java)
                result["response"]?.toString() ?: result["reply"]?.toString() ?: "AI-ul nu are un răspuns."
            }
        } catch (e: Exception) {
            "Eroare de conexiune la AI."
        }
    }

    suspend fun getPrediction(algorithm: String = "random_forest"): Map<String, Any>? = withContext(Dispatchers.IO) {
        executeAnalysisRequest(createPostRequest("/predict?model_type=$algorithm"))
    }

    suspend fun getForecast(horizons: List<Int> = listOf(1, 3, 6, 12, 24)): Map<String, Any>? = withContext(Dispatchers.IO) {
        val horizonsStr = horizons.joinToString(",")
        val url = "/predict?include_forecast=true&forecast_horizons=$horizonsStr"
        executeAnalysisRequest(createPostRequest(url))
    }

    suspend fun getAnomaly(): Map<String, Any>? = withContext(Dispatchers.IO) {
        executeAnalysisRequest(createPostRequest("/anomaly"))
    }

    suspend fun predictCustom(temp: Float, hum: Float, pm25: Float, pm10: Float, co2: Int): Map<String, Any>? = withContext(Dispatchers.IO) {
        val json = gson.toJson(mapOf(
            "temperature" to temp,
            "humidity" to hum,
            "pm25" to pm25,
            "pm10" to pm10,
            "co2" to co2
        ))
        executeAnalysisRequest(createPostRequest("/predict-custom", json))
    }

    suspend fun trainModel(
        model: String,
        hours: Int? = null,
        minutes: Int? = null,
        allowDerivedLabelFallback: Boolean = true
    ): Map<String, Any>? = withContext(Dispatchers.IO) {
        val params = mutableMapOf<String, Any>("training_model" to model)
        if (hours != null) params["aggregation_hours"] = hours
        if (minutes != null) params["aggregation_minutes"] = minutes
        params["allow_derived_label_fallback"] = allowDerivedLabelFallback
        val json = gson.toJson(params)
        executeAnalysisRequest(createPostRequest("/train", json))
    }

    suspend fun getHealthStatus(): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("$baseUrl/health").get().build()
        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) "Online" else "Probleme API (${response.code})"
            }
        } catch (e: Exception) { "Inaccesibil" }
    }

    suspend fun getDataStatus(): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("$baseUrl/health/data").get().build()
        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) "Conectat la Supabase" else "Eroare Sursă Date"
            }
        } catch (e: Exception) { "Eroare Conexiune Date" }
    }

    suspend fun getAiAnalysis(deviceId: String): Map<String, Any>? = withContext(Dispatchers.IO) {
        val url = "/predict?device_id=$deviceId&include_forecast=true&include_anomalies=true"
        executeAnalysisRequest(createPostRequest(url))
    }

    private fun executeAnalysisRequest(request: Request): Map<String, Any>? {
        return try {
            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    @Suppress("UNCHECKED_CAST")
                    gson.fromJson(responseBody, Map::class.java) as? Map<String, Any>
                } else {
                    val errorObj = try { gson.fromJson(responseBody, Map::class.java) } catch(e: Exception) { null }
                    val msg = errorObj?.get("detail")?.toString() ?: errorObj?.get("message")?.toString() ?: "Eroare AI (${response.code})"
                    mapOf("error" to msg)
                }
            }
        } catch (e: Exception) {
            mapOf("error" to "Eroare de rețea.")
        }
    }
}
