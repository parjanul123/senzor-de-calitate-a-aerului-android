package com.example.senzordeaer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AlertMonitoringService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var monitoringJob = serviceScope.launch { }

    override fun onCreate() {
        super.onCreate()
        createServiceChannel()
        startForeground(SERVICE_NOTIFICATION_ID, buildServiceNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!monitoringJob.isActive) {
            monitoringJob = serviceScope.launch { monitorDevices() }
        }
        return START_STICKY
    }

    private suspend fun monitorDevices() {
        val sessionManager = SessionManager(applicationContext)
        val tokenManager = TokenManager(sessionManager)
        val dbService = SupabaseService()
        val profileStore = TransportProfileStore(applicationContext)
        val notifier = TransportProfileNotifier(applicationContext)

        while (serviceScope.isActive) {
            val userId = sessionManager.userId
            val token = tokenManager.getValidAccessToken()
            if (token.isNullOrBlank() || userId.isNullOrBlank()) {
                stopSelf()
                return
            }

            try {
                val devices = dbService.getMyDevices(token, userId)
                devices.forEach { device ->
                    val measurement = dbService.getDeviceMeasurements(token, device.device_id)
                        .firstOrNull()
                    if (measurement != null) {
                        notifier.notifyIfNeeded(device, profileStore.get(device.device_id), measurement)
                    }
                }
            } catch (_: CancellationException) {
                throw CancellationException()
            } catch (_: Exception) {
                // The next interval retries transient network or session errors.
            }

            delay(CHECK_INTERVAL_MS)
        }
    }

    private fun createServiceChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                SERVICE_CHANNEL_ID,
                "Monitorizare senzori",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildServiceNotification(): Notification = NotificationCompat.Builder(this, SERVICE_CHANNEL_ID)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle("Monitorizare senzori activă")
        .setContentText("Verificare automată a pragurilor")
        .setOngoing(true)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .build()

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val SERVICE_CHANNEL_ID = "sensor_monitoring"
        private const val SERVICE_NOTIFICATION_ID = 1001
        private const val CHECK_INTERVAL_MS = 10_000L

        fun start(context: Context) {
            val intent = Intent(context, AlertMonitoringService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AlertMonitoringService::class.java))
        }
    }
}
