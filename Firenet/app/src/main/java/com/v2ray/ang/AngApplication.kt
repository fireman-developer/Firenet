package com.v2ray.ang

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.multidex.MultiDexApplication
import androidx.work.Configuration
import androidx.work.WorkManager
import com.tencent.mmkv.MMKV
import com.v2ray.ang.AppConfig.ANG_PACKAGE
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.handler.UiMemoryGovernor
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

class AngApplication : MultiDexApplication() {
    companion object {
        lateinit var application: AngApplication
    }

    private val initScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * کارهای سنگین راه‌اندازی که روی نخ اصلی انجام نمی‌شوند.
     *
     * صفحه‌ی راه‌اندازی پیش از رفتن به صفحه‌ی اصلی روی همین منتظر می‌ماند، پس
     * هیچ کدی به داده‌ی نیمه‌آماده نمی‌رسد.
     */
    private var warmUpJob: Job? = null

    /**
     * Attaches the base context to the application.
     * @param base The base context.
     */
    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        application = this
    }

    private val workManagerConfiguration: Configuration = Configuration.Builder()
        .setDefaultProcessName("${ANG_PACKAGE}:bg")
        .build()

    /**
     * Initializes the application.
     *
     * فقط چیزهایی که واقعاً باید پیش از هر کد دیگری آماده باشند اینجا و روی نخ
     * اصلی انجام می‌شوند: MMKV (چون همه‌ی تنظیمات از آن می‌آید) و WorkManager.
     * بقیه — خواندن قواعد مسیریابی از assets، کپی فایل‌های دیتای روتینگ و ساختن
     * کانال نوتیفیکیشن — به پس‌زمینه می‌روند.
     */
    override fun onCreate() {
        super.onCreate()

        MMKV.initialize(this)
        SettingsManager.setNightMode()
        WorkManager.initialize(this, workManagerConfiguration)

        if (isMainProcess()) {
            // فرایند رابط کاربری: کارهای سنگین به پس‌زمینه می‌روند و صفحه‌ی
            // راه‌اندازی منتظرشان می‌ماند.
            warmUpJob = initScope.launch { warmUp() }

            es.dmoral.toasty.Toasty.Config.getInstance()
                .setGravity(android.view.Gravity.BOTTOM, 0, 200)
                .apply()

            UiMemoryGovernor.install(this)
        } else {
            // فرایند تونل: هیچ صفحه‌ای وجود ندارد که منتظر بماند و ممکن است
            // بلافاصله ساخت کانفیگ شروع شود. اینجا هم‌زمان و کامل انجامش می‌دهیم
            // تا قواعد مسیریابی و فایل‌های ژئو هرگز نیمه‌آماده خوانده نشوند.
            warmUp()
        }
    }

    private fun warmUp() {
        runCatching {
            Utils.ensureGeoAssetsExist(this)
            val extAssets = getExternalFilesDir("assets")?.absolutePath
            val internalAssets = File(filesDir, "assets").absolutePath

            val targetPath = if (!extAssets.isNullOrEmpty() && File(extAssets, "geoip.dat").exists()) {
                extAssets
            } else {
                internalAssets
            }

            System.setProperty("v2ray.location.asset", targetPath)
            System.setProperty("xray.location.asset", targetPath)
        }.onFailure { Log.e(AppConfig.TAG, "Failed to initialize geo assets", it) }

        runCatching { SettingsManager.initRoutingRulesets(this) }
            .onFailure { Log.e(AppConfig.TAG, "Failed to init routing rulesets", it) }

        runCatching { createPushNotificationChannel() }
            .onFailure { Log.e(AppConfig.TAG, "Failed to create push channel", it) }
    }

    /** تا پایان کارهای پس‌زمینه‌ی راه‌اندازی صبر می‌کند. */
    suspend fun awaitWarmUp() {
        warmUpJob?.join()
    }

    /**
     * تشخیص فرایند اصلی.
     *
     * برنامه سه فرایند دارد: اصلی، `:RunSoLibV2RayDaemon` و `:bg`. نام فرایند
     * دمون شامل «:» است، پس نبود «:» یعنی فرایند اصلی.
     */
    private fun isMainProcess(): Boolean {
        val name = currentProcessName() ?: return true
        return !name.contains(':')
    }

    private fun currentProcessName(): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return runCatching { getProcessName() }.getOrNull()
        }
        return runCatching {
            val pid = android.os.Process.myPid()
            val am = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
            @Suppress("DEPRECATION")
            am.runningAppProcesses?.firstOrNull { it.pid == pid }?.processName
        }.getOrNull()
    }

    private fun createPushNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = "push_default"
            val channelName = "Push Notifications"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(channelId, channelName, importance).apply {
                description = "Default channel for FCM push notifications"
                setShowBadge(true)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel) // idempotent
        }
    }
}