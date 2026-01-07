package com.v2ray.ang.fcm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.tencent.mmkv.MMKV
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.data.auth.TokenStore
import com.v2ray.ang.net.ApiClient
import com.v2ray.ang.ui.MainActivity
import com.v2ray.ang.ui.login.LoginActivity
import com.v2ray.ang.util.MessageUtil

class PushMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val data = remoteMessage.data

        // 1. بررسی پیام‌های دیتا (دستورات سیستمی مثل Force Logout)
        if (data.isNotEmpty()) {
            val action = data["action"]
            if (action == "FORCE_LOGOUT") {
                performForceLogout()
                return
            }
        }

        // 2. دریافت لینک از دیتا (اگر موجود باشد)
        val link = data["link"] ?: data["url"]

        // 3. دریافت محتوای نوتیفیکیشن (از Notification Payload یا Data Payload)
        val title = remoteMessage.notification?.title ?: data["title"] ?: getString(R.string.app_name)
        val body = remoteMessage.notification?.body ?: data["body"] ?: data["message"]

        // اگر پیامی برای نمایش وجود دارد (یا بدنه دارد یا لینک)
        if (!body.isNullOrEmpty() || !link.isNullOrEmpty()) {
            showLocalNotification(
                title = title,
                body = body ?: "", // اگر فقط لینک باشد، متن خالی رد می‌شود
                intentTarget = MainActivity::class.java,
                link = link
            )
        }
    }

    override fun onNewToken(token: String) {
        Log.d("FCM", "new token: $token")
        val jwt = TokenStore.token(applicationContext) ?: return
        ApiClient.postUpdateFcmToken(jwt, token) { r ->
            if (r.isFailure) {
                Thread {
                    try {
                        Thread.sleep(1500)
                        ApiClient.postUpdateFcmToken(jwt, token) { }
                    } catch (_: Exception) {}
                }.start()
            }
        }
    }

    private fun performForceLogout() {
        try {
            Log.d("FCM", "Received FORCE_LOGOUT command. Clearing data...")

            // الف) پاک‌سازی کامل داده‌ها
            val mmkv = MMKV.defaultMMKV()
            mmkv.clearAll()
            TokenStore.clear(applicationContext)

            // ب) قطع اتصال VPN
            MessageUtil.sendMsg2Service(applicationContext, AppConfig.MSG_STATE_STOP, "")

            // ج) نمایش نوتیفیکیشن
            showLocalNotification(
                title = getString(R.string.app_name),
                body = "نشست شما توسط مدیر بسته شد. لطفاً مجدداً وارد شوید.",
                intentTarget = LoginActivity::class.java
            )

            // د) هدایت آنی به صفحه لاگین
            val intent = Intent(applicationContext, LoginActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            startActivity(intent)

        } catch (e: Exception) {
            Log.e("FCM", "Error executing force logout", e)
        }
    }

    private fun showLocalNotification(
        title: String,
        body: String,
        intentTarget: Class<*>,
        link: String? = null
    ) {
        val channelId = "push_default"

        val intent = Intent(this, intentTarget).apply {
            if (intentTarget == LoginActivity::class.java) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            } else {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            // اضافه کردن لینک به اینتنت
            if (!link.isNullOrEmpty()) {
                putExtra("notification_link", link)
            }
        }

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val pendingIntent = PendingIntent.getActivity(this, System.currentTimeMillis().toInt(), intent, flags)
        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // ایجاد کانال نوتیفیکیشن برای اندروید ۸ به بالا
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                getString(R.string.app_name),
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_stat_name)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setSound(defaultSoundUri)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)

        try {
            notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
        } catch (e: SecurityException) {
            Log.e("FCM", "Permission denied showing notification", e)
        } catch (e: Exception) {
            Log.e("FCM", "Error showing notification", e)
        }
    }
}