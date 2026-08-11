package com.v2ray.ang.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.v2ray.ang.AppConfig.MSG_MEASURE_BEST
import com.v2ray.ang.AppConfig.MSG_MEASURE_BEST_CANCEL
import com.v2ray.ang.AppConfig.MSG_MEASURE_BEST_PROGRESS
import com.v2ray.ang.AppConfig.MSG_MEASURE_BEST_SUCCESS
import com.v2ray.ang.AppConfig.MSG_MEASURE_CONFIG
import com.v2ray.ang.AppConfig.MSG_MEASURE_CONFIG_CANCEL
import com.v2ray.ang.AppConfig.MSG_MEASURE_CONFIG_SUCCESS
import com.v2ray.ang.dto.EConfigType
import com.v2ray.ang.extension.serializable
import com.v2ray.ang.handler.BestServerFinder
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.PluginServiceManager
import com.v2ray.ang.handler.SpeedtestManager
import com.v2ray.ang.handler.V2rayConfigManager
import com.v2ray.ang.util.MessageUtil
import com.v2ray.ang.util.Utils
import go.Seq
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import libv2ray.Libv2ray
import java.util.concurrent.Executors

class V2RayTestService : Service() {
    private val realTestScope by lazy { CoroutineScope(Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors()).asCoroutineDispatcher()) }

    /**
     * جست‌وجوی بهترین سرور دامنه‌ی جدا دارد تا «آزمایش همه» آن را لغو نکند و
     * برعکس؛ این دو کار برای کاربر دو چیز متفاوت‌اند.
     */
    private val bestScope by lazy { CoroutineScope(Executors.newSingleThreadExecutor().asCoroutineDispatcher()) }

    /**
     * Initializes the V2Ray environment.
     */
    override fun onCreate() {
        super.onCreate()
        Seq.setContext(this)
        Libv2ray.initCoreEnv(Utils.userAssetPath(this), Utils.getDeviceIdForXUDPBaseKey())
    }

    /**
     * Handles the start command for the service.
     * @param intent The intent.
     * @param flags The flags.
     * @param startId The start ID.
     * @return The start mode.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.getIntExtra("key", 0)) {
            MSG_MEASURE_CONFIG -> {
                val guid = intent.serializable<String>("content") ?: ""
                realTestScope.launch {
                    val result = startRealPing(guid)
                    MessageUtil.sendMsg2UI(this@V2RayTestService, MSG_MEASURE_CONFIG_SUCCESS, Pair(guid, result))
                }
            }

            MSG_MEASURE_CONFIG_CANCEL -> {
                realTestScope.coroutineContext[Job]?.cancelChildren()
            }

            MSG_MEASURE_BEST -> {
                // یک جست‌وجوی در جریان را کنار می‌گذاریم؛ دو جست‌وجوی هم‌زمان
                // پهنای باند همدیگر را می‌خورند و هر دو نتیجه را بی‌اعتبار می‌کنند.
                bestScope.coroutineContext[Job]?.cancelChildren()
                bestScope.launch { findBestServer() }
            }

            MSG_MEASURE_BEST_CANCEL -> {
                bestScope.coroutineContext[Job]?.cancelChildren()
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    /**
     * بهترین سرور را پیدا و به رابط کاربری اعلام می‌کند.
     *
     * نتایج تأخیر همان‌جا در حافظه ذخیره می‌شوند تا فهرست انتخاب سرور هم
     * به‌روز شود؛ کاربر با یک بار اجرا هر دو چیز را می‌گیرد.
     */
    private suspend fun findBestServer() {
        val guids = MmkvManager.decodeServerList()
        if (guids.isEmpty()) {
            MessageUtil.sendMsg2UI(this, MSG_MEASURE_BEST_SUCCESS, Pair("", -1L))
            return
        }

        val result = try {
            BestServerFinder.find(
                context = this,
                guids = guids,
                onLatency = { guid, delay ->
                    MmkvManager.encodeServerTestDelayMillis(guid, delay)
                    MessageUtil.sendMsg2UI(this, MSG_MEASURE_CONFIG_SUCCESS, Pair(guid, delay))
                },
                onProgress = { done, total ->
                    MessageUtil.sendMsg2UI(this, MSG_MEASURE_BEST_PROGRESS, Pair(done, total))
                }
            )
        } catch (e: Exception) {
            android.util.Log.e(com.v2ray.ang.AppConfig.TAG, "Best server search failed", e)
            null
        }

        MessageUtil.sendMsg2UI(
            this,
            MSG_MEASURE_BEST_SUCCESS,
            Pair(result?.guid.orEmpty(), result?.throughputBps ?: -1L)
        )
    }

    /**
     * Binds the service.
     * @param intent The intent.
     * @return The binder.
     */
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    /**
     * Starts the real ping test.
     * @param guid The GUID of the configuration.
     * @return The ping result.
     */
    private fun startRealPing(guid: String): Long {
        val retFailure = -1L

        val config = MmkvManager.decodeServerConfig(guid) ?: return retFailure
        if (config.configType == EConfigType.HYSTERIA2) {
            val delay = PluginServiceManager.realPingHy2(this, config)
            return delay
        } else {
            val configResult = V2rayConfigManager.getV2rayConfig4Speedtest(this, guid)
            if (!configResult.status) {
                return retFailure
            }
            return SpeedtestManager.realPing(configResult.content)
        }
    }
}
