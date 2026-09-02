package com.v2ray.ang.handler

import android.os.SystemClock
import android.util.Log
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.ProfileItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

/**
 * تنها مالک شمارنده‌های ترافیک هسته.
 *
 * `coreController.queryStats` در Xray یک شمارنده‌ی **خواندن-و-صفر-کردن** است:
 * هر بار که خوانده می‌شود، مقدارش صفر می‌شود. اگر دو مصرف‌کننده (نوتیفیکیشن و
 * صفحه‌ی اصلی) جداگانه آن را بخوانند، بایت‌های همدیگر را می‌دزدند و هر دو عدد
 * غلط نشان می‌دهند. به همین دلیل اینجا فقط یک حلقه‌ی نمونه‌برداری وجود دارد و
 * نتیجه‌اش بین همه‌ی شنونده‌ها پخش می‌شود.
 *
 * این شیء فقط در فرایند `:RunSoLibV2RayDaemon` معنا دارد؛ همان جایی که هسته
 * زندگی می‌کند.
 */
object SpeedMonitor {

    /** یک نمونه‌ی سرعت بر حسب بایت بر ثانیه. */
    data class Sample(
        val proxyUp: Long,
        val proxyDown: Long,
        val directUp: Long,
        val directDown: Long,
        val perTag: List<TagSpeed>
    )

    data class TagSpeed(val tag: String, val up: Long, val down: Long)

    private const val INTERVAL_MS = 1000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val listeners = ConcurrentHashMap<String, (Sample) -> Unit>()

    @Volatile
    private var job: Job? = null

    /** تگ‌های خروجی پروکسی؛ `direct` و `block` عمداً بیرون‌اند. */
    @Volatile
    private var proxyTags: List<String> = listOf(AppConfig.TAG_PROXY)

    private var lastQueryAt = 0L

    /**
     * تگ‌های خروجی را از روی کانفیگ فعال به‌روز می‌کند.
     * پیش از شروع حلقه صدا زده می‌شود تا نمونه‌ی اول هم درست باشد.
     */
    fun setProfile(config: ProfileItem?) {
        val tags = config?.getAllOutboundTags()
        if (tags == null) {
            proxyTags = listOf(AppConfig.TAG_PROXY)
            return
        }
        tags.remove(AppConfig.TAG_DIRECT)
        tags.remove(AppConfig.TAG_BLOCKED)
        proxyTags = if (tags.isEmpty()) listOf(AppConfig.TAG_PROXY) else tags.toList()
    }

    /**
     * یک شنونده اضافه می‌کند. [key] یکتا است، پس صدا زدن دوباره با همان کلید
     * شنونده‌ی قبلی را جایگزین می‌کند و باعث دو بار اشتراک نمی‌شود.
     */
    @Synchronized
    fun subscribe(key: String, listener: (Sample) -> Unit) {
        listeners[key] = listener
        ensureRunning()
    }

    @Synchronized
    fun unsubscribe(key: String) {
        listeners.remove(key)
        if (listeners.isEmpty()) stopLoop()
    }

    /** با خاموش شدن هسته صدا زده می‌شود؛ همه‌چیز را رها می‌کند. */
    @Synchronized
    fun reset() {
        listeners.clear()
        stopLoop()
        proxyTags = listOf(AppConfig.TAG_PROXY)
    }

    fun hasListeners(): Boolean = listeners.isNotEmpty()

    // ─────────────────────────────────────────────────────────────────────────

    private fun ensureRunning() {
        if (job != null) return
        lastQueryAt = SystemClock.elapsedRealtime()
        // نمونه‌ی اول را دور می‌ریزیم: شمارنده‌ها ممکن است از قبل پر باشند و
        // بایت‌های انباشته‌ی گذشته به عنوان سرعت لحظه‌ای نشان داده شوند.
        drainCounters()
        job = scope.launch {
            while (isActive) {
                delay(INTERVAL_MS)
                val sample = collect() ?: continue
                listeners.values.forEach { listener ->
                    try {
                        listener(sample)
                    } catch (e: Exception) {
                        Log.e(AppConfig.TAG, "Speed listener failed", e)
                    }
                }
            }
        }
    }

    private fun stopLoop() {
        job?.cancel()
        job = null
    }

    /** شمارنده‌ها را می‌خواند و دور می‌ریزد تا اندازه‌گیری از صفر شروع شود. */
    private fun drainCounters() {
        runCatching {
            proxyTags.forEach {
                V2RayServiceManager.queryStats(it, AppConfig.UPLINK)
                V2RayServiceManager.queryStats(it, AppConfig.DOWNLINK)
            }
            V2RayServiceManager.queryStats(AppConfig.TAG_DIRECT, AppConfig.UPLINK)
            V2RayServiceManager.queryStats(AppConfig.TAG_DIRECT, AppConfig.DOWNLINK)
        }
    }

    private fun collect(): Sample? {
        if (!V2RayServiceManager.isRunning()) return null

        val now = SystemClock.elapsedRealtime()
        val seconds = max(now - lastQueryAt, 1L) / 1000.0
        lastQueryAt = now

        var proxyUp = 0L
        var proxyDown = 0L
        val perTag = ArrayList<TagSpeed>(proxyTags.size)

        try {
            proxyTags.forEach { tag ->
                val up = (V2RayServiceManager.queryStats(tag, AppConfig.UPLINK) / seconds).toLong()
                val down = (V2RayServiceManager.queryStats(tag, AppConfig.DOWNLINK) / seconds).toLong()
                proxyUp += up
                proxyDown += down
                perTag.add(TagSpeed(tag, up, down))
            }
            val directUp =
                (V2RayServiceManager.queryStats(AppConfig.TAG_DIRECT, AppConfig.UPLINK) / seconds).toLong()
            val directDown =
                (V2RayServiceManager.queryStats(AppConfig.TAG_DIRECT, AppConfig.DOWNLINK) / seconds).toLong()

            return Sample(proxyUp, proxyDown, directUp, directDown, perTag)
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Failed to query core stats", e)
            return null
        }
    }
}
