package com.example.senzordeaer

import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class FastApiService {
    private val client = NetworkSecurity.hardenedClientBuilder()
        .readTimeout(75, TimeUnit.SECONDS)
        .callTimeout(90, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
    private val gson = Gson()
    private val baseUrl = "https://ai-senzor-de-calitate-a-aerului-production.up.railway.app"

    private fun createPostRequest(path: String, bodyJson: String = "{}"): Request {
        val body = bodyJson.toRequestBody("application/json".toMediaType())
        return Request.Builder()
            .url("$baseUrl$path")
            .post(body)
            .build()
    }

    private fun pathWithQuery(path: String, params: Map<String, String?>): String {
        val query = params
            .filterValues { !it.isNullOrBlank() }
            .map { (key, value) -> "${encodeQueryParam(key)}=${encodeQueryParam(value.orEmpty())}" }
            .joinToString("&")
        if (query.isBlank()) return path
        val separator = if (path.contains("?")) "&" else "?"
        return "$path$separator$query"
    }

    private fun encodeQueryParam(value: String): String = URLEncoder.encode(value, "UTF-8")

    suspend fun chatWithAi(message: String, deviceId: String? = null): String = withContext(Dispatchers.IO) {
        val payload = mutableMapOf<String, Any>("message" to message)
        if (!deviceId.isNullOrBlank()) {
            payload["device_id"] = deviceId
        }
        val json = gson.toJson(payload)
        val request = createPostRequest("/chat", json)
        try {
            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""
                if (!response.isSuccessful) return@withContext "Serverul AI nu a răspuns corect."
                Log.d("FastApiService", "Chat API response: $responseBody")

                val result = try {
                    gson.fromJson(responseBody, Map::class.java)
                } catch (_: Exception) {
                    null
                }

                if (result == null) {
                    return@withContext responseBody.ifBlank { "AI-ul nu are un răspuns." }
                }

                val directMessage = result["response"]?.toString()
                    ?: result["reply"]?.toString()
                    ?: result["message"]?.toString()
                    ?: result["text"]?.toString()

                if (!directMessage.isNullOrBlank()) return@withContext directMessage

                val dataObj = result["data"] as? Map<*, *>
                val nestedMessage = dataObj?.get("response")?.toString()
                    ?: dataObj?.get("reply")?.toString()
                    ?: dataObj?.get("message")?.toString()
                    ?: dataObj?.get("text")?.toString()

                nestedMessage?.takeIf { it.isNotBlank() }
                    ?: responseBody.ifBlank { "AI-ul nu are un răspuns." }
            }
        } catch (e: SocketTimeoutException) {
            Log.w("FastApiService", "Chat AI timed out", e)
            "AI-ul răspunde prea greu momentan. Încearcă din nou peste câteva secunde."
        } catch (e: IOException) {
            Log.w("FastApiService", "Chat AI connection failed", e)
            "Nu mă pot conecta la serverul AI. Verifică internetul sau statusul serviciului AI."
        } catch (e: Exception) {
            Log.w("FastApiService", "Chat AI request failed", e)
            "A apărut o eroare la comunicarea cu AI-ul."
        }
    }

    suspend fun getPrediction(algorithm: String = "random_forest", deviceId: String? = null): Map<String, Any>? = withContext(Dispatchers.IO) {
        val url = pathWithQuery("/predict", mapOf("model_type" to algorithm, "device_id" to deviceId))
        executeAnalysisRequest(createPostRequest(url))
    }

    suspend fun getForecast(horizons: List<Int> = listOf(1, 3, 6, 12, 24), deviceId: String? = null): Map<String, Any>? = withContext(Dispatchers.IO) {
        val horizonsStr = horizons.joinToString(",")
        val url = pathWithQuery(
            "/predict",
            mapOf(
                "include_forecast" to "true",
                "forecast_horizons" to horizonsStr,
                "device_id" to deviceId
            )
        )
        executeAnalysisRequest(createPostRequest(url))
    }

    suspend fun getAnomaly(deviceId: String? = null): Map<String, Any>? = withContext(Dispatchers.IO) {
        executeAnalysisRequest(createPostRequest(pathWithQuery("/anomaly", mapOf("device_id" to deviceId))))
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
        deviceId: String? = null,
        allowDerivedLabelFallback: Boolean = true
    ): Map<String, Any>? = withContext(Dispatchers.IO) {
        val params = mutableMapOf<String, Any>("training_model" to model)
        if (hours != null) params["aggregation_hours"] = hours
        if (minutes != null) params["aggregation_minutes"] = minutes
        if (!deviceId.isNullOrBlank()) params["device_id"] = deviceId
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
