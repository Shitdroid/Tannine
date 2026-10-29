package com.shidroid.tannine

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper

class WatcherService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var prefs: Prefs
    private lateinit var controller: TimeoutController
    private lateinit var detector: ForegroundAppDetector

    private val tick = object : Runnable {
        override fun run() {
            if (!prefs.enabled) {
                controller.restore()
            } else if (prefs.universalMode) {
                controller.applyNever()
            } else if (ForegroundAppDetector.hasAccess(this@WatcherService)) {
                val fg = detector.poll()
                if (prefs.enabled && fg != null && fg in prefs.triggerApps) {
                    controller.applyNever()
                } else {
                    controller.restore()
                }
            } else {
                controller.restore()
            }
            handler.postDelayed(this, POLL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        controller = TimeoutController(this)
        detector = ForegroundAppDetector(this)
        startInForeground()
        handler.post(tick)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        controller.restore()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startInForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.app_name), NotificationManager.IMPORTANCE_MIN)
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_tea)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notification_text))
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, n)
        }
    }

    companion object {
        private const val CHANNEL = "watcher"
        private const val POLL_MS = 1500L

        fun start(context: Context) {
            context.startForegroundService(Intent(context, WatcherService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, WatcherService::class.java))
        }
    }
}
