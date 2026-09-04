package com.galib.step.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.galib.step.Graph
import com.galib.step.MainActivity
import com.galib.step.R
import com.galib.step.notifications.Notifier
import com.galib.step.util.Formatters
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground step-tracking service: keeps the hardware step counter alive in
 * the background with a live progress notification. Started/stopped from
 * Settings ("Background tracking") or the notification's Stop action.
 */
class StepTrackingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var trackingJob: Job? = null



    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Graph.ensureInit(this)
    }

    @android.annotation.SuppressLint("InlinedApi")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            Graph.appScope.launch { Graph.prefs.setBackgroundTracking(false) }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        // The manifest declares FGS type "health", which on Android 14+ needs a
        // health permission (ACTIVITY_RECOGNITION) granted at runtime. When it
        // isn't, fall back to a plain foreground start so the ongoing count can
        // still run off Health Connect data.
        val healthGranted = android.os.Build.VERSION.SDK_INT < 29 ||
            ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.ACTIVITY_RECOGNITION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val started = runCatching {
            if (healthGranted) {
                androidx.core.app.ServiceCompat.startForeground(
                    this, NOTIF_ID, buildNotification(0, 8000),
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
                )
            } else {
                androidx.core.app.ServiceCompat.startForeground(
                    this, NOTIF_ID, buildNotification(0, 8000),
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            }
        }.onFailure {
            android.util.Log.e("StepTracking", "startForeground failed", it)
        }
        if (started.isFailure) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (trackingJob == null) {
            trackingJob = scope.launch {
                // Hardware counter → repository (max-merged with Health Connect)
                launch {
                    runCatching {
                        Graph.stepSensor.rawSteps().collect { raw ->
                            Graph.repository.onSensorRaw(raw)
                        }
                    }
                }
                // Periodic full refresh
                launch {
                    while (isActive) {
                        runCatching { Graph.repository.syncToday() }
                        delay((5 * 60_000).milliseconds)
                    }
                }

                // Keep the live notification's count fresh
                Graph.repository.observeToday()
                    .map { it.steps to it.goal }
                    .distinctUntilChanged()
                    .collect { (steps, goal) ->
                        runCatching {
                            NotificationManagerCompat.from(this@StepTrackingService)
                                .notify(NOTIF_ID, buildNotification(steps, goal))
                        }
                    }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun buildNotification(steps: Long, goal: Int): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, StepTrackingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val progress = if (goal > 0) ((steps * 100) / goal).toInt().coerceIn(0, 100) else 0

        return NotificationCompat.Builder(this, Notifier.CHANNEL_TRACKING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notif_steps_today, Formatters.steps(steps), Formatters.steps(goal.toLong()), progress))
            .setProgress(100, progress, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openIntent)
            .addAction(0, getString(R.string.stop), stopIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    companion object {
        const val ACTION_STOP = "com.galib.step.action.STOP_TRACKING"
        private const val NOTIF_ID = 2001

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context, Intent(context, StepTrackingService::class.java)
            )
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, StepTrackingService::class.java).setAction(ACTION_STOP)
            )
        }
    }
}
