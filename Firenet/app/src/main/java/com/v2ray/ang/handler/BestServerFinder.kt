package com.v2ray.ang.handler

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.EConfigType
import com.v2ray.ang.dto.V2rayConfig
import com.v2ray.ang.util.HttpUtil
import com.v2ray.ang.util.JsonUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import libv2ray.CoreCallbackHandler
import libv2ray.Libv2ray
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.math.max
import kotlin.math.sqrt

/**
 * انتخاب خودکار بهترین لوکیشن.
 *
 * «بهترین» را نمی‌شود فقط با پینگ فهمید. سروری که ۴۰ میلی‌ثانیه تأخیر دارد ولی
 * پهنای باندش بسته است، از سروری با ۱۲۰ میلی‌ثانیه تأخیر و لینک باز بدتر است.
 * پس اندازه‌گیری در دو مرحله انجام می‌شود:
 *
 *  ۱. **تأخیر** — روی همه‌ی سرورها و به‌صورت موازی. ارزان است و اکثر سرورهای
 *     مرده را همین‌جا کنار می‌گذارد.
 *  ۲. **پهنای باند واقعی** — فقط روی چند نامزد برتر و به‌صورت ترتیبی. برای هر
 *     نامزد یک نمونه‌ی کامل از هسته با یک ورودی HTTP روی پورت آزاد بالا می‌آید،
 *     یک فایل واقعی از آن عبور داده می‌شود و بایت بر ثانیه شمرده می‌شود. ترتیبی
 *     بودنش عمدی است: دو دانلود هم‌زمان پهنای باند همدیگر را می‌خورند و هر دو
 *     عدد را خراب می‌کنند.
 *
 * امتیاز نهایی، توان عبوری را با یک جریمه‌ی تأخیر ترکیب می‌کند تا سروری که هم
 * سریع است و هم نزدیک، برنده شود.
 *
 * این کلاس فقط در فرایند `:RunSoLibV2RayDaemon` اجرا می‌شود؛ جایی که
 * `Libv2ray.initCoreEnv` قبلاً صدا زده شده است.
 */
object BestServerFinder {

    /** چند سرور برتر وارد مرحله‌ی پهنای باند می‌شوند. */
    private const val BANDWIDTH_CANDIDATES = 4

    /** سرورهایی با تأخیر بیش از این مقدار، نامزد مرحله‌ی دوم نمی‌شوند. */
    private const val LATENCY_CUTOFF_MS = 2_000L

    /** موازی‌سازی مرحله‌ی تأخیر. بالاتر از این، خودِ دستگاه گلوگاه می‌شود. */
    private const val PING_PARALLELISM = 6

    /** بودجه‌ی زمانی و حجمی هر تست دانلود. */
    private const val PROBE_BUDGET_MS = 3_500L
    private const val PROBE_BUDGET_BYTES = 4L * 1024 * 1024
    private const val PROBE_WARMUP_MS = 600L

    /** نام `Outcome` عمدی است تا با `kotlin.Result` اشتباه گرفته نشود. */
    data class Outcome(
        val guid: String,
        val latencyMs: Long,
        val throughputBps: Long,
        val score: Double
    )

    private data class Measured(
        val guid: String,
        val latencyMs: Long,
        var throughputBps: Long = -1L
    )

    /**
     * بهترین سرور را پیدا می‌کند.
     *
     * @param onLatency پس از اندازه‌گیری تأخیر هر سرور صدا زده می‌شود تا فهرست
     *        روی صفحه بلافاصله به‌روز شود و کاربر منتظر پایان کل کار نماند.
     * @param onProgress (تعداد انجام‌شده، تعداد کل).
     * @return بهترین نتیجه، یا `null` اگر هیچ سروری پاسخ نداد.
     */
    suspend fun find(
        context: Context,
        guids: List<String>,
        onLatency: (String, Long) -> Unit = { _, _ -> },
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): Outcome? {
        if (guids.isEmpty()) return null

        // مرحله‌ی دوم فقط وقتی معنا دارد که تونل بالا نباشد؛ وگرنه ترافیک تست
        // از داخل تونل فعلی عبور می‌کند و عدد به‌دست‌آمده مربوط به سرور نامزد
        // نیست. در آن حالت به رتبه‌بندی بر اساس تأخیر بسنده می‌کنیم.
        val tunnelUp = runCatching { V2RayServiceManager.isRunning() }.getOrDefault(false)

        // مرحله‌ی ۱ — تأخیر
        val total = guids.size + (if (tunnelUp) 0 else minOf(BANDWIDTH_CANDIDATES, guids.size))
        var done = 0
        val gate = Semaphore(PING_PARALLELISM)

        val measured = coroutineScope {
            guids.map { guid ->
                async(Dispatchers.IO) {
                    val latency = gate.withPermit { pingOne(context, guid) }
                    synchronized(this@BestServerFinder) {
                        done++
                        onProgress(done, total)
                    }
                    onLatency(guid, latency)
                    Measured(guid, latency)
                }
            }.awaitAll()
        }

        val reachable = measured.filter { it.latencyMs in 1..LATENCY_CUTOFF_MS }
        if (reachable.isEmpty()) {
            // هیچ سروری پاسخ نداد؛ اگر عددی از آزمایش‌های قبلی مانده، از آن استفاده می‌کنیم.
            val fallback = guids
                .mapNotNull { guid ->
                    val stored = MmkvManager.decodeServerAffiliationInfo(guid)?.testDelayMillis ?: 0L
                    if (stored > 0) Measured(guid, stored) else null
                }
                .minByOrNull { it.latencyMs }
                ?: return null
            return Outcome(fallback.guid, fallback.latencyMs, -1L, latencyFactor(fallback.latencyMs))
        }

        val candidates = reachable
            .sortedBy { it.latencyMs }
            .take(BANDWIDTH_CANDIDATES)

        // مرحله‌ی ۲ — پهنای باند واقعی
        if (!tunnelUp) {
            for (candidate in candidates) {
                candidate.throughputBps = probeThroughput(context, candidate.guid)
                done++
                onProgress(done, total)
            }
        }

        // برای نامزدهایی که پهنای باندشان اندازه‌گیری نشد (مثلاً Hysteria2 که
        // فرایند کمکی جدا لازم دارد) میانه‌ی بقیه را فرض می‌گیریم. حذف کردنشان
        // ناعادلانه بود؛ فرض خوش‌بینانه هم ناعادلانه است. میانه یعنی «مثل بقیه»
        // و تصمیم را به تأخیرشان واگذار می‌کند.
        val measuredRates = candidates.map { it.throughputBps }.filter { it > 0 }.sorted()
        val assumed = if (measuredRates.isEmpty()) -1L else measuredRates[measuredRates.size / 2]

        val scored = candidates.map { m ->
            val rate = if (m.throughputBps > 0) m.throughputBps else assumed
            Outcome(
                guid = m.guid,
                latencyMs = m.latencyMs,
                throughputBps = m.throughputBps,
                score = score(m.latencyMs, rate)
            )
        }

        // اگر برای هیچ نامزدی پهنای باند اندازه‌گیری نشد، رتبه‌بندی خودبه‌خود به
        // تأخیر برمی‌گردد؛ چون امتیاز همه از همان یک متغیر می‌آید.
        return scored.maxByOrNull { it.score }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // مرحله‌ی ۱: تأخیر
    // ─────────────────────────────────────────────────────────────────────────

    private fun pingOne(context: Context, guid: String): Long {
        return try {
            val config = MmkvManager.decodeServerConfig(guid) ?: return -1L
            if (config.configType == EConfigType.HYSTERIA2) {
                PluginServiceManager.realPingHy2(context, config)
            } else {
                val result = V2rayConfigManager.getV2rayConfig4Speedtest(context, guid)
                if (!result.status) -1L else SpeedtestManager.realPing(result.content)
            }
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Latency probe failed for $guid", e)
            -1L
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // مرحله‌ی ۲: پهنای باند
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * یک نمونه‌ی مستقل از هسته را با ورودی HTTP روی یک پورت آزاد بالا می‌آورد،
     * یک فایل واقعی از آن دانلود می‌کند و توان عبوری را بر حسب بایت بر ثانیه
     * برمی‌گرداند. اگر چیزی سر جایش نبود، `-1` برمی‌گردد.
     */
    private suspend fun probeThroughput(context: Context, guid: String): Long {
        val profile = MmkvManager.decodeServerConfig(guid) ?: return -1L

        // Hysteria2 از یک فرایند کمکی جدا استفاده می‌کند و اینجا قابل برپا کردن
        // نیست؛ این نامزد فقط با تأخیرش سنجیده می‌شود.
        if (profile.configType == EConfigType.HYSTERIA2) return -1L

        val base = V2rayConfigManager.getV2rayConfig4Speedtest(context, guid)
        if (!base.status || base.content.isBlank()) return -1L

        val config = runCatching { JsonUtil.fromJson(base.content, V2rayConfig::class.java) }
            .getOrNull() ?: return -1L

        val port = reserveFreePort() ?: return -1L

        config.inbounds.clear()
        config.inbounds.add(
            V2rayConfig.InboundBean(
                tag = "probe",
                port = port,
                protocol = "http",
                listen = AppConfig.LOOPBACK,
                settings = V2rayConfig.InboundBean.InSettingsBean(userLevel = 8)
            )
        )
        // بدون آمار و سیاست، نمونه سبک‌تر بالا می‌آید.
        config.stats = null
        config.policy = null

        val json = JsonUtil.toJsonPretty(config) ?: return -1L
        val controller = Libv2ray.newCoreController(SilentCallback())

        // بالا آوردن و پایین آوردن هسته تماس‌های مسدودکننده‌ی Go هستند و باید
        // روی نخ IO بنشینند، نه روی نخی که فراخوان به ما داده است.
        return try {
            withContext(Dispatchers.IO) {
                controller.startLoop(json)
                if (!controller.isRunning) return@withContext -1L
                if (!awaitPort(port, PROBE_WARMUP_MS)) return@withContext -1L
                download(port)
            }
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Bandwidth probe failed for $guid", e)
            -1L
        } finally {
            // ‏`NonCancellable` لازم است: اگر کاربر وسط کار جست‌وجو را لغو کند،
            // `withContext` معمولی بلافاصله پرتاب می‌کند و نمونه‌ی هسته روشن
            // می‌ماند — یعنی یک نشت واقعی حافظه و سوکت.
            withContext(NonCancellable + Dispatchers.IO) {
                runCatching { controller.stopLoop() }
            }
        }
    }

    /**
     * فایل آزمون را از میان پروکسی محلی می‌کشد و بایت بر ثانیه را می‌شمارد.
     *
     * زمان تا اولین بایت از محاسبه بیرون گذاشته می‌شود؛ آن بخش تأخیر است و در
     * مرحله‌ی اول جداگانه سنجیده شده. اگر اینجا هم حساب شود، سرورهای دور دو بار
     * جریمه می‌شوند.
     */
    private fun download(port: Int): Long {
        for (url in arrayOf(AppConfig.BANDWIDTH_TEST_URL, AppConfig.BANDWIDTH_TEST_URL2)) {
            val bps = downloadFrom(url, port)
            if (bps > 0) return bps
        }
        return -1L
    }

    private fun downloadFrom(url: String, port: Int): Long {
        val conn = HttpUtil.createProxyConnection(
            urlStr = url,
            port = port,
            connectTimeout = 5_000,
            readTimeout = 5_000,
            needStream = true
        ) ?: return -1L

        conn.setRequestProperty("Cache-Control", "no-cache, no-store")
        conn.setRequestProperty("Pragma", "no-cache")

        try {
            val requestedAt = SystemClock.elapsedRealtime()
            if (conn.responseCode !in 200..299) return -1L

            var total = 0L
            var firstByteAt = 0L
            val buffer = ByteArray(32 * 1024)

            conn.inputStream.use { input ->
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    if (firstByteAt == 0L) {
                        firstByteAt = SystemClock.elapsedRealtime()
                    } else {
                        total += read
                    }
                    val elapsed = SystemClock.elapsedRealtime() - requestedAt
                    if (elapsed >= PROBE_BUDGET_MS || total >= PROBE_BUDGET_BYTES) break
                }
            }

            if (firstByteAt == 0L || total <= 0L) return -1L
            val window = max(SystemClock.elapsedRealtime() - firstByteAt, 1L)
            // نمونه‌ی خیلی کوتاه قابل اتکا نیست.
            if (window < 400L && total < 256 * 1024) return -1L
            return total * 1000L / window
        } catch (e: Exception) {
            Log.i(AppConfig.TAG, "Bandwidth download failed on $url: ${e.message}")
            return -1L
        } finally {
            runCatching { conn.disconnect() }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // امتیازدهی
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * ترکیب توان عبوری و تأخیر در یک عدد.
     *
     * توان عبوری با ریشه‌ی دوم وارد می‌شود تا تفاوت ۸ و ۱۰ مگابیت، سروری با
     * تأخیر دو برابر را برنده نکند؛ برای کاربر، تفاوت ۴۰ و ۲۰۰ میلی‌ثانیه در
     * باز شدن صفحات محسوس‌تر از چند مگابیت اضافه است.
     */
    private fun score(latencyMs: Long, throughputBps: Long): Double {
        if (throughputBps <= 0L) return latencyFactor(latencyMs)
        val mbps = throughputBps * 8.0 / 1_000_000.0
        return sqrt(mbps.coerceAtLeast(0.05)) * 100.0 * latencyFactor(latencyMs)
    }

    /** سهم تأخیر در امتیاز: عددی بین ۰ و ۱ که با بالا رفتن تأخیر افت می‌کند. */
    private fun latencyFactor(latencyMs: Long): Double =
        1.0 / (1.0 + max(latencyMs, 1L) / 250.0)

    // ─────────────────────────────────────────────────────────────────────────
    // کمکی‌ها
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * یک پورت آزاد می‌گیرد و بلافاصله رهایش می‌کند تا هسته بتواند رویش بنشیند.
     * فاصله‌ی کوتاهی بین آزاد شدن و گرفتن دوباره وجود دارد، ولی چون فقط روی
     * لوپ‌بک و برای چند ثانیه است، در عمل مشکلی نمی‌سازد.
     */
    private fun reserveFreePort(): Int? = try {
        ServerSocket(0).use { it.localPort }
    } catch (e: Exception) {
        Log.e(AppConfig.TAG, "Could not reserve a probe port", e)
        null
    }

    /** منتظر می‌ماند تا ورودی هسته واقعاً روی پورت گوش بدهد. */
    private suspend fun awaitPort(port: Int, timeoutMs: Long): Boolean {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            val open = withContext(Dispatchers.IO) {
                runCatching {
                    Socket().use { it.connect(InetSocketAddress(AppConfig.LOOPBACK, port), 300) }
                    true
                }.getOrDefault(false)
            }
            if (open) return true
            delay(60)
        }
        return false
    }

    /** نمونه‌ی آزمایشی هسته هیچ رویدادی برای گزارش کردن ندارد. */
    private class SilentCallback : CoreCallbackHandler {
        override fun startup(): Long = 0
        override fun shutdown(): Long = 0
        override fun onEmitStatus(l: Long, s: String?): Long = 0
    }
}
