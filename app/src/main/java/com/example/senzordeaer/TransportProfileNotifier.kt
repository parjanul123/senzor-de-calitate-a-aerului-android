package com.example.senzordeaer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

class TransportProfileNotifier(private val context: Context) {
    fun notifyIfNeeded(device: Device, profile: TransportProfile, measurement: Measurement) {
        val violations = profile.limits.mapNotNull { (parameterId, limit) ->
            val parameter = transportParameters.firstOrNull { it.id == parameterId } ?: return@mapNotNull null
            val value = measurement.valueFor(parameterId)
            if (profile.isWithinLimit(parameterId, value)) null
            else "${parameter.label}: $value ${parameter.unit} (${limitLabel(limit, parameter.unit)})"
        }
        if (violations.isEmpty() || !canNotify(profile.id, device.device_id)) return

        createChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Avertizare: ${device.name ?: device.device_id}")
            .setContentText("Profil ${profile.cargoName}: ${violations.first()}")
            .setStyle(NotificationCompat.BigTextStyle().bigText(violations.joinToString("\n")))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify((device.device_id + profile.id).hashCode(), notification)
    }

    fun notifyPredictionIfNeeded(device: Device, profile: TransportProfile, response: Map<String, Any>?) {
        if (response?.get("profile_based") != true || response["prediction"] != "În afara limitelor") return

        val featureAssessment = response["feature_assessment"] as? Map<*, *>
        val violations = featureAssessment?.mapNotNull { (parameterId, details) ->
            val values = details as? Map<*, *> ?: return@mapNotNull null
            if (values["status"]?.toString() != "În afara limitei profilului") return@mapNotNull null
            val parameter = transportParameters.firstOrNull { it.id == parameterId.toString() }
                ?: return@mapNotNull null
            "${parameter.label}: ${values["value"]} ${parameter.unit}"
        }?.takeIf { it.isNotEmpty() } ?: listOf("Una sau mai multe valori depășesc pragurile profilului.")

        if (!canNotify(profile.id, device.device_id)) return
        createChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Avertizare profil: ${device.name ?: device.device_id}")
            .setContentText("Situația nu este în limitele profilului selectat.")
            .setStyle(NotificationCompat.BigTextStyle().bigText(violations.joinToString("\n")))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify((device.device_id + profile.id).hashCode(), notification)
    }

    private fun canNotify(profileId: String, deviceId: String): Boolean {
        val preferences = context.getSharedPreferences("TransportAlertNotifications", Context.MODE_PRIVATE)
        val key = "$deviceId.$profileId.last_notification"
        val now = System.currentTimeMillis()
        val lastNotification = preferences.getLong(key, 0L)
        if (now - lastNotification < NOTIFICATION_COOLDOWN_MS) return false
        preferences.edit().putLong(key, now).apply()
        return true
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(CHANNEL_ID, "Alerte profile transport", NotificationManager.IMPORTANCE_HIGH)
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private companion object {
        const val CHANNEL_ID = "transport_profile_alerts"
        const val NOTIFICATION_COOLDOWN_MS = 15 * 60 * 1000L
    }
}