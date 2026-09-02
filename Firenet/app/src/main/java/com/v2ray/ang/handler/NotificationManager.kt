package com.v2ray.ang.handler

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.ProfileItem
import com.v2ray.ang.extension.toSpeedString
import com.v2ray.ang.handler.V2RayServiceManager
import com.v2ray.ang.ui.MainActivity
import kotlin.math.min

object NotificationManager {
    private const val NOTIFICATION_ID = 1
    private const val NOTIFICATION_PENDING_INTENT_CONTENT = 0
    private const val NOTIFICATION_PENDING_INTENT_STOP_V2RAY = 1
    private const val NOTIFICATION_PENDING_INTENT_RESTART_V2RAY = 2
    private const val NOTIFICATION_ICON_THRESHOLD = 3000

    private const val SPEED_LISTENER_KEY = "notification"
    private const val REFRESH_EVERY_N_TICKS = 3

    private var mBuilder: NotificationCompat.Builder? = null
    private var mNotificationManager: NotificationManager? = null
    private var speedSubscribed = false
    private var tickCount = 0
    private var lastZeroSpeed = false

    /**
     * Starts the speed notification.
     *
     * The sampling loop itself lives in [SpeedMonitor] because the core's stat
     * counters reset on read; two independent pollers would each see only part
     * of the traffic. Here we only render every third tick, since a foreground
     * notification does not need to be rebuilt once per second.
     *
     * @param currentConfig The current profile configuration.
     */
    fun startSpeedNotification(currentConfig: ProfileItem?) {
        if (MmkvManager.decodeSettingsBool(AppConfig.PREF_SPEED_ENABLED) != true) return
        if (speedSubscribed || V2RayServiceManager.isRunning() == false) return

        SpeedMonitor.setProfile(currentConfig)
        speedSubscribed = true
        tickCount = 0
        lastZeroSpeed = false

        SpeedMonitor.subscribe(SPEED_LISTENER_KEY) { sample ->
            if (++tickCount % REFRESH_EVERY_N_TICKS != 0) return@subscribe

            val proxyTotal = sample.proxyUp + sample.proxyDown
            val directTotal = sample.directUp + sample.directDown
            val zeroSpeed = proxyTotal == 0L && directTotal == 0L

            // وقتی هیچ ترافیکی نیست، نوتیفیکیشن را بی‌جهت بازنمی‌سازیم؛
            // فقط یک بار به حالت صفر می‌رود و بعد ساکت می‌ماند.
            if (!zeroSpeed || !lastZeroSpeed) {
                val text = StringBuilder()
                val active = sample.perTag.filter { it.up + it.down > 0 }
                if (active.isEmpty()) {
                    appendSpeedString(text, sample.perTag.firstOrNull()?.tag, 0.0, 0.0)
                } else {
                    active.forEach { appendSpeedString(text, it.tag, it.up.toDouble(), it.down.toDouble()) }
                }
                appendSpeedString(
                    text, AppConfig.TAG_DIRECT,
                    sample.directUp.toDouble(), sample.directDown.toDouble()
                )
                updateNotification(text.toString(), proxyTotal, directTotal)
            }
            lastZeroSpeed = zeroSpeed
        }
    }

    /**
     * Shows the notification.
     * @param currentConfig The current profile configuration.
     */
    fun showNotification(currentConfig: ProfileItem?) {
        val service = getService() ?: return
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val startMainIntent = Intent(service, MainActivity::class.java)
        val contentPendingIntent = PendingIntent.getActivity(service, NOTIFICATION_PENDING_INTENT_CONTENT, startMainIntent, flags)

        val stopV2RayIntent = Intent(AppConfig.BROADCAST_ACTION_SERVICE)
        stopV2RayIntent.`package` = AppConfig.ANG_PACKAGE
        stopV2RayIntent.putExtra("key", AppConfig.MSG_STATE_STOP)
        val stopV2RayPendingIntent = PendingIntent.getBroadcast(service, NOTIFICATION_PENDING_INTENT_STOP_V2RAY, stopV2RayIntent, flags)

        val restartV2RayIntent = Intent(AppConfig.BROADCAST_ACTION_SERVICE)
        restartV2RayIntent.`package` = AppConfig.ANG_PACKAGE
        restartV2RayIntent.putExtra("key", AppConfig.MSG_STATE_RESTART)
        val restartV2RayPendingIntent = PendingIntent.getBroadcast(service, NOTIFICATION_PENDING_INTENT_RESTART_V2RAY, restartV2RayIntent, flags)

        val channelId =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                createNotificationChannel()
            } else {
                // If earlier version channel ID is not used
                // https://developer.android.com/reference/android/support/v4/app/NotificationCompat.Builder.html#NotificationCompat.Builder(android.content.Context)
                ""
            }

        mBuilder = NotificationCompat.Builder(service, channelId)
            .setSmallIcon(R.drawable.ic_stat_name)
            .setContentTitle(currentConfig?.remarks)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentPendingIntent)
            .addAction(
                R.drawable.ic_delete_24dp,
                service.getString(R.string.notification_action_stop_v2ray),
                stopV2RayPendingIntent
            )
            .addAction(
                R.drawable.ic_delete_24dp,
                service.getString(R.string.title_service_restart),
                restartV2RayPendingIntent
            )

        //mBuilder?.setDefaults(NotificationCompat.FLAG_ONLY_ALERT_ONCE)

        service.startForeground(NOTIFICATION_ID, mBuilder?.build())
    }

    /**
     * Shows the kill switch notification while traffic is being blocked.
     * Keeps the foreground service alive after the core has stopped so that
     * no traffic can leak outside the (now black-holed) tunnel.
     */
    fun showKillSwitchNotification() {
        val service = getService() ?: return
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val startMainIntent = Intent(service, MainActivity::class.java)
        val contentPendingIntent = PendingIntent.getActivity(service, NOTIFICATION_PENDING_INTENT_CONTENT, startMainIntent, flags)

        // "Disconnect" action: an explicit, user-initiated stop that tears the tunnel down.
        val stopIntent = Intent(AppConfig.BROADCAST_ACTION_SERVICE)
        stopIntent.`package` = AppConfig.ANG_PACKAGE
        stopIntent.putExtra("key", AppConfig.MSG_STATE_STOP)
        val stopPendingIntent = PendingIntent.getBroadcast(service, NOTIFICATION_PENDING_INTENT_STOP_V2RAY, stopIntent, flags)

        // "Reconnect" action.
        val restartIntent = Intent(AppConfig.BROADCAST_ACTION_SERVICE)
        restartIntent.`package` = AppConfig.ANG_PACKAGE
        restartIntent.putExtra("key", AppConfig.MSG_STATE_RESTART)
        val restartPendingIntent = PendingIntent.getBroadcast(service, NOTIFICATION_PENDING_INTENT_RESTART_V2RAY, restartIntent, flags)

        val channelId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel()
        } else {
            ""
        }

        val builder = NotificationCompat.Builder(service, channelId)
            .setSmallIcon(R.drawable.ic_stat_name)
            .setContentTitle(service.getString(R.string.kill_switch_notification_title))
            .setContentText(service.getString(R.string.kill_switch_notification_text))
            .setStyle(NotificationCompat.BigTextStyle().bigText(service.getString(R.string.kill_switch_notification_text)))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOngoing(true)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentPendingIntent)
            .addAction(R.drawable.ic_delete_24dp, service.getString(R.string.notification_action_stop_v2ray), stopPendingIntent)
            .addAction(R.drawable.ic_action_done, service.getString(R.string.title_service_restart), restartPendingIntent)

        mBuilder = builder
        service.startForeground(NOTIFICATION_ID, builder.build())
    }

    /**
     * Cancels the notification.
     */
    fun cancelNotification() {
        val service = getService() ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            service.stopForeground(Service.STOP_FOREGROUND_REMOVE)
        } else {
            service.stopForeground(true)
        }

        mBuilder = null
        if (speedSubscribed) {
            SpeedMonitor.unsubscribe(SPEED_LISTENER_KEY)
            speedSubscribed = false
        }
        mNotificationManager = null
    }

    /**
     * Stops the speed notification.
     * @param currentConfig The current profile configuration.
     */
    fun stopSpeedNotification(currentConfig: ProfileItem?) {
        if (!speedSubscribed) return
        SpeedMonitor.unsubscribe(SPEED_LISTENER_KEY)
        speedSubscribed = false
        updateNotification(currentConfig?.remarks, 0, 0)
    }

    /**
     * Creates a notification channel for Android O and above.
     * @return The channel ID.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel(): String {
        val channelId = AppConfig.RAY_NG_CHANNEL_ID
        val channelName = AppConfig.RAY_NG_CHANNEL_NAME
        val chan = NotificationChannel(
            channelId,
            channelName, NotificationManager.IMPORTANCE_HIGH
        )
        chan.lightColor = Color.DKGRAY
        chan.importance = NotificationManager.IMPORTANCE_NONE
        chan.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        getNotificationManager()?.createNotificationChannel(chan)
        return channelId
    }

    /**
     * Updates the notification with the given content text and traffic data.
     * @param contentText The content text.
     * @param proxyTraffic The proxy traffic.
     * @param directTraffic The direct traffic.
     */
    private fun updateNotification(contentText: String?, proxyTraffic: Long, directTraffic: Long) {
        if (mBuilder != null) {
            if (proxyTraffic < NOTIFICATION_ICON_THRESHOLD && directTraffic < NOTIFICATION_ICON_THRESHOLD) {
                mBuilder?.setSmallIcon(R.drawable.ic_stat_name)
            } else if (proxyTraffic > directTraffic) {
                mBuilder?.setSmallIcon(R.drawable.ic_stat_proxy)
            } else {
                mBuilder?.setSmallIcon(R.drawable.ic_stat_direct)
            }
            mBuilder?.setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            mBuilder?.setContentText(contentText)
            getNotificationManager()?.notify(NOTIFICATION_ID, mBuilder?.build())
        }
    }

    /**
     * Gets the notification manager.
     * @return The notification manager.
     */
    private fun getNotificationManager(): NotificationManager? {
        if (mNotificationManager == null) {
            val service = getService() ?: return null
            mNotificationManager = service.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        }
        return mNotificationManager
    }

    /**
     * Appends the speed string to the given text.
     * @param text The text to append to.
     * @param name The name of the tag.
     * @param up The uplink speed.
     * @param down The downlink speed.
     */
    private fun appendSpeedString(text: StringBuilder, name: String?, up: Double, down: Double) {
        var n = name ?: "no tag"
        n = n.substring(0, min(n.length, 6))
        text.append(n)
        for (i in n.length..6 step 2) {
            text.append("\t")
        }
        text.append("•  ${up.toLong().toSpeedString()}↑  ${down.toLong().toSpeedString()}↓\n")
    }

    /**
     * Gets the service instance.
     * @return The service instance.
     */
    private fun getService(): Service? {
        return V2RayServiceManager.serviceControl?.get()?.getService()
    }
}