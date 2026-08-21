package com.example.senzordeaer

import android.content.Context
import org.json.JSONObject

data class TransportProfile(
    val cargoName: String,
    val limits: Map<String, ParameterLimit>
)

data class ParameterLimit(val minimum: Float, val maximum: Float)

data class TransportParameter(val id: String, val label: String, val unit: String)

val transportParameters = listOf(
    TransportParameter("temperature", "Temperatură", "°C"),
    TransportParameter("humidity", "Umiditate", "%"),
    TransportParameter("pressure", "Presiune", "hPa"),
    TransportParameter("voc", "VOC", "KOhm"),
    TransportParameter("light", "Lumină", "lux"),
    TransportParameter("co2", "CO₂", "ppm"),
    TransportParameter("pm1", "PM1", "µg/m³"),
    TransportParameter("pm25", "PM2.5", "µg/m³"),
    TransportParameter("pm10", "PM10", "µg/m³")
)

class TransportProfileStore(context: Context) {
    private val preferences = context.getSharedPreferences("TransportProfiles", Context.MODE_PRIVATE)

    fun get(deviceId: String): TransportProfile? {
        val prefix = "$deviceId."
        val cargoName = preferences.getString("${prefix}cargo_name", null) ?: return null
        val limitsJson = preferences.getString("${prefix}limits", null)
        val limits = if (limitsJson != null) {
            val json = JSONObject(limitsJson)
            transportParameters.mapNotNull { parameter ->
                json.optJSONObject(parameter.id)?.let { limit ->
                    parameter.id to ParameterLimit(
                        minimum = limit.getDouble("minimum").toFloat(),
                        maximum = limit.getDouble("maximum").toFloat()
                    )
                }
            }.toMap()
        } else {
            mapOf(
                "temperature" to ParameterLimit(
                    preferences.getFloat("${prefix}minimum_temperature", 0f),
                    preferences.getFloat("${prefix}maximum_temperature", 0f)
                ),
                "humidity" to ParameterLimit(
                    preferences.getFloat("${prefix}minimum_humidity", 0f),
                    preferences.getFloat("${prefix}maximum_humidity", 0f)
                )
            )
        }
        return TransportProfile(
            cargoName = cargoName,
            limits = limits
        )
    }

    fun save(deviceId: String, profile: TransportProfile) {
        val prefix = "$deviceId."
        val limitsJson = JSONObject().apply {
            profile.limits.forEach { (parameterId, limit) ->
                put(parameterId, JSONObject().apply {
                    put("minimum", limit.minimum)
                    put("maximum", limit.maximum)
                })
            }
        }
        preferences.edit()
            .putString("${prefix}cargo_name", profile.cargoName)
            .putString("${prefix}limits", limitsJson.toString())
            .apply()
    }
}