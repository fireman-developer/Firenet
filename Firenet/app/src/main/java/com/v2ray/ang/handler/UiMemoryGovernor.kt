package com.v2ray.ang.handler

import android.app.Activity
import android.app.Application
import android.content.ComponentCallbacks2
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.v2ray.ang.AppConfig
import java.lang.ref.WeakReference

/**
 * آزادکننده‌ی حافظه‌ی رابط کاربری.
 *
 * ایده: بخش‌های برنامه دو عمر متفاوت دارند.
 *
 *  - **هسته** — سرویس VPN، تونل، هسته‌ی Xray. اینها در فرایند جداگانه‌ی
 *    `:RunSoLibV2RayDaemon` زندگی می‌کنند و باید تا وقتی کاربر خودش قطع نکرده
 *    زنده و دست‌نخورده بمانند. این کلاس هرگز به آن‌ها دست نمی‌زند.
 *  - **رابط کاربری** — درخت Compose، بیت‌مپ پرچم‌ها، وضعیت صفحه، ViewModel‌ها.
 *    اینها فقط وقتی ارزش حافظه دارند که کاربر به آن‌ها نگاه می‌کند.
 *
 * وقتی برنامه به پس‌زمینه می‌رود، Android اکتیویتی‌ها را زنده نگه می‌دارد تا
 * برگشتن سریع باشد. برای برنامه‌ای که ساعت‌ها در پس‌زمینه می‌ماند، این یعنی چند
 * ده مگابایت حافظه‌ی بلااستفاده که سیستم‌عامل را وادار می‌کند فرایندهای دیگر —
 * از جمله گاهی خود سرویس تونل — را بکشد.
 *
 * پس بعد از [IDLE_TIMEOUT_MS] در پس‌زمینه، اکتیویتی‌ها بسته می‌شوند و کش‌ها خالی.
 * برگشت کاربر، برنامه را از نو با صفحه‌ی راه‌اندازی بالا می‌آورد. اگر زودتر از
 * این مهلت برگردد، هیچ اتفاقی نیفتاده و صفحه دقیقاً همان‌جاست که رهایش کرده بود.
 */
object UiMemoryGovernor : Application.ActivityLifecycleCallbacks, ComponentCallbacks2 {

    /** مهلتی که برنامه پس از آن، رابط کاربری‌اش را از حافظه بیرون می‌گذارد. */
    const val IDLE_TIMEOUT_MS = 40_000L

    private val handler = Handler(Looper.getMainLooper())
    private val activities = mutableListOf<WeakReference<Activity>>()

    /**
     * تعداد اکتیویتی‌های شروع‌شده.
     *
     * شمارش به‌جای یک پرچم ساده لازم است: هنگام رفتن از صفحه‌ی اصلی به تنظیمات،
     * `onStart` صفحه‌ی جدید پیش از `onStop` صفحه‌ی قبلی می‌آید. با یک پرچم،
     * برنامه در آن لحظه اشتباهاً «پس‌زمینه» تشخیص داده می‌شد.
     */
    private var startedCount = 0

    /** رابط کاربری در آخرین رفتن به پس‌زمینه آزاد شد. */
    @Volatile
    var uiWasReleased = false
        private set

    private val releaseTask = Runnable { releaseUi() }

    fun install(app: Application) {
        app.registerActivityLifecycleCallbacks(this)
        app.registerComponentCallbacks(this)
    }

    /**
     * پس از بازسازی رابط کاربری صدا زده می‌شود تا پرچم برای دور بعد پاک شود.
     */
    fun consumeReleasedFlag(): Boolean {
        val was = uiWasReleased
        uiWasReleased = false
        return was
    }

    // ─────────────────────────────────────────────────────────────────────────
    // چرخه‌ی عمر اکتیویتی‌ها
    // ─────────────────────────────────────────────────────────────────────────

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        activities.add(WeakReference(activity))
    }

    override fun onActivityStarted(activity: Activity) {
        startedCount++
        if (startedCount == 1) {
            // کاربر برگشت؛ هر آزادسازی زمان‌بندی‌شده‌ای لغو می‌شود.
            handler.removeCallbacks(releaseTask)
        }
    }

    override fun onActivityStopped(activity: Activity) {
        startedCount = (startedCount - 1).coerceAtLeast(0)
        if (startedCount == 0) {
            handler.removeCallbacks(releaseTask)
            handler.postDelayed(releaseTask, IDLE_TIMEOUT_MS)
        }
    }

    override fun onActivityDestroyed(activity: Activity) {
        activities.removeAll { it.get() == null || it.get() === activity }
    }

    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    // ─────────────────────────────────────────────────────────────────────────
    // فشار حافظه از طرف سیستم‌عامل
    // ─────────────────────────────────────────────────────────────────────────

    override fun onTrimMemory(level: Int) {
        if (startedCount > 0) return

        when {
            // فشار واقعی حافظه. منتظر پایان مهلت نمی‌مانیم؛ این همان لحظه‌ای است
            // که نگه داشتن رابط کاربری می‌تواند به کشته شدن فرایند تونل منجر شود.
            level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> {
                handler.removeCallbacks(releaseTask)
                releaseUi()
            }

            // ‏`TRIM_MEMORY_UI_HIDDEN` هیچ ربطی به کمبود حافظه ندارد؛ درست همان
            // لحظه‌ای می‌آید که برنامه کوچک می‌شود. اگر اینجا رابط کاربری را
            // آزاد کنیم، مهلت ۴۰ ثانیه‌ای بی‌معنا می‌شود و کاربری که ۵ ثانیه
            // بعد برمی‌گردد، بی‌دلیل صفحه‌ی راه‌اندازی می‌بیند. پس فقط کش‌های
            // ارزان را خالی می‌کنیم و صفحه را دست‌نخورده نگه می‌داریم.
            level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> {
                runCatching { MmkvManager.trimMemory() }
            }
        }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) = Unit

    /** روی نسخه‌های قدیمی‌تر، سیستم‌عامل به‌جای `onTrimMemory` این را صدا می‌زند. */
    override fun onLowMemory() {
        if (startedCount == 0) {
            handler.removeCallbacks(releaseTask)
            releaseUi()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────

    /**
     * اکتیویتی‌ها را می‌بندد و کش‌ها را خالی می‌کند.
     *
     * `finish()` تمام درخت Compose، ViewModel‌ها و بیت‌مپ‌های وابسته به آن را رها
     * می‌کند. سرویس تونل در فرایند دیگری اجرا می‌شود و اصلاً متوجه این اتفاق
     * نمی‌شود؛ اتصال قطع نمی‌شود.
     */
    private fun releaseUi() {
        if (startedCount > 0) return

        var closed = 0
        activities.toList().forEach { ref ->
            val activity = ref.get()
            if (activity != null && !activity.isFinishing && !activity.isDestroyed) {
                runCatching { activity.finish() }
                closed++
            }
        }
        activities.clear()

        runCatching { MmkvManager.trimMemory() }

        uiWasReleased = true
        Log.i(AppConfig.TAG, "UI released after idle timeout ($closed activities closed)")
    }
}
