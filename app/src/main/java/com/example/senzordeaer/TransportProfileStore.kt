package com.example.senzordeaer

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class TransportProfile(
    val id: String,
    val cargoName: String,
    val limits: Map<String, ParameterLimit>
)

data class ParameterLimit(val minimum: Float?, val maximum: Float?)

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

    fun get(deviceId: String): TransportProfile = getProfiles().firstOrNull {
        it.id == preferences.getString("$deviceId.selected_profile_id", STANDARD_PROFILE_ID)
    } ?: standardProfile

    fun getProfiles(): List<TransportProfile> {
        val customProfilesJson = preferences.getString("custom_profiles", "[]") ?: "[]"
        val customProfiles = JSONArray(customProfilesJson).let { profiles ->
            (0 until profiles.length()).map { index -> profileFromJson(profiles.getJSONObject(index)) }
        }
        return listOf(standardProfile) + customProfiles
    }

    fun save(profile: TransportProfile): TransportProfile {
        val profileWithId = if (profile.id.isBlank()) profile.copy(id = UUID.randomUUID().toString()) else profile
        val profiles = getProfiles().filterNot { it.id == STANDARD_PROFILE_ID }.toMutableList()
        val existingIndex = profiles.indexOfFirst { it.id == profileWithId.id }
        if (existingIndex >= 0) profiles[existingIndex] = profileWithId else profiles.add(profileWithId)
        preferences.edit()
            .putString("custom_profiles", JSONArray().apply { profiles.forEach { put(profileToJson(it)) } }.toString())
            .apply()
        return profileWithId
    }

    fun select(deviceId: String, profileId: String) {
        preferences.edit().putString("$deviceId.selected_profile_id", profileId).apply()
    }

    fun delete(profileId: String): Boolean {
        if (profileId == STANDARD_PROFILE_ID) return false
        val profiles = getProfiles().filter { it.id != STANDARD_PROFILE_ID && it.id != profileId }
        val editor = preferences.edit()
            .putString("custom_profiles", JSONArray().apply { profiles.forEach { put(profileToJson(it)) } }.toString())
        preferences.all
            .filter { (key, value) -> key.endsWith(".selected_profile_id") && value == profileId }
            .forEach { (key, _) -> editor.putString(key, STANDARD_PROFILE_ID) }
        editor.apply()
        return true
    }

    fun exportForRemote(): String {
        return JSONArray().apply {
            put(profileToJson(standardProfile).put("is_standard", true))
            getProfiles().filter { it.id != STANDARD_PROFILE_ID }.forEach { put(profileToJson(it).put("is_standard", false)) }
        }.toString()
    }

    fun exportProfile(profile: TransportProfile): String = JSONArray().apply {
        put(profileToJson(profile))
    }.toString()

    fun importFromRemote(json: String) {
        val remoteProfiles = JSONArray(json)
        val customProfiles = JSONArray().apply {
            (0 until remoteProfiles.length()).forEach { index ->
                val profile = remoteProfiles.getJSONObject(index)
                if (!profile.optBoolean("is_standard", false)) put(profile)
            }
        }
        preferences.edit().putString("custom_profiles", customProfiles.toString()).apply()
    }

    private fun profileToJson(profile: TransportProfile): JSONObject = JSONObject().apply {
        put("id", profile.id)
        put("name", profile.cargoName)
        put("limits", JSONObject().apply {
            profile.limits.forEach { (parameterId, limit) ->
                put(parameterId, JSONObject().apply {
                    if (limit.minimum != null) put("minimum", limit.minimum) else put("minimum", JSONObject.NULL)
                    if (limit.maximum != null) put("maximum", limit.maximum) else put("maximum", JSONObject.NULL)
                })
            }
        })
    }

    private fun profileFromJson(json: JSONObject): TransportProfile {
        val limitsJson = json.optJSONObject("limits")
        return TransportProfile(
            id = json.getString("id"),
            cargoName = displayName(json.getString("name")),
            limits = if (limitsJson != null) parseJsonLimits(limitsJson) else parseTableLimits(json)
        )
    }

    private fun displayName(name: String): String {
        val legacyPrefix = "transport-profile:"
        if (!name.startsWith(legacyPrefix)) return name
        return try {
            JSONObject(name.removePrefix(legacyPrefix)).optString("profile_name", name)
        } catch (_: Exception) {
            name
        }
    }

    private fun parseJsonLimits(limitsJson: JSONObject): Map<String, ParameterLimit> =
        transportParameters.mapNotNull { parameter ->
            limitsJson.optJSONObject(parameter.id)?.let { limit ->
                parameter.id to ParameterLimit(
                    limit.optDouble("minimum").takeIf { !limit.isNull("minimum") }?.toFloat(),
                    limit.optDouble("maximum").takeIf { !limit.isNull("maximum") }?.toFloat()
                )
            }
        }.toMap()

    private fun parseTableLimits(json: JSONObject): Map<String, ParameterLimit> {
        val columnPrefixes = mapOf(
            "temperature" to "temperature",
            "humidity" to "humidity",
            "pressure" to "pressure",
            "co2" to "co2",
            "pm25" to "pm25",
            "pm10" to "pm10",
            "light" to "light"
        )
        return columnPrefixes.mapNotNull { (parameterId, prefix) ->
            val minimum = json.optDouble("${prefix}_min").takeIf { !json.isNull("${prefix}_min") }?.toFloat()
            val maximum = json.optDouble("${prefix}_max").takeIf { !json.isNull("${prefix}_max") }?.toFloat()
            if (minimum == null && maximum == null) null else parameterId to ParameterLimit(minimum, maximum)
        }.toMap()
    }

    companion object {
        const val STANDARD_PROFILE_ID = "c9a903cb-a418-3bf4-8d1e-562b5f3213a2"
        private val standardProfile = TransportProfile(
            id = STANDARD_PROFILE_ID,
            cargoName = "Standard",
            limits = mapOf(
                "temperature" to ParameterLimit(18f, 25f),
                "humidity" to ParameterLimit(40f, 60f)
            )
        )
    }
}