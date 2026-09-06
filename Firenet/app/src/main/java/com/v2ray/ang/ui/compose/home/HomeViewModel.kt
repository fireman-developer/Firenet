package com.v2ray.ang.ui.compose.home

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.data.auth.AuthRepository
import com.v2ray.ang.data.auth.TokenStore
import com.v2ray.ang.extension.serializable
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.net.ApiClient
import com.v2ray.ang.net.StatusResponse
import com.v2ray.ang.ui.compose.globe.CountryCoordinates
import com.v2ray.ang.ui.compose.theme.ConnectionTone
import com.v2ray.ang.ui.main.StatusFormatter
import com.v2ray.ang.util.CountryUtils
import com.v2ray.ang.util.MessageUtil
import com.v2ray.ang.util.Utils
import com.v2ray.ang.work.KeepAliveScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

/**
 * منطق صفحه‌ی اصلی.
 *
 * این کلاس تنها نقطه‌ای است که صفحه‌ی اصلی با هسته‌ی برنامه حرف می‌زند: پیام‌های
 * سرویس تونل را می‌گیرد، پهنای باند مصرفی را اندازه می‌گیرد، وضعیت اشتراک را از
 * سرور می‌خواند و همه را به یک [HomeUiState] تبدیل می‌کند.
 *
 * هسته‌ی VPN دست‌نخورده باقی مانده است؛ اینجا فقط مصرف‌کننده‌ی همان
 * broadcastfactorهایی هستیم که از قبل وجود داشتند.
 */
class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<HomeEvent>(extraBufferCapacity = 8)
    val events = _events.asSharedFlow()

    private val repo by lazy { AuthRepository(getApplication()) }

    private var registered = false
    private var userInitiatedStop = false
    private var speedSubscribed = false
    private var autoSearchTimeout: Job? = null

    /** پس از پیدا شدن بهترین سرور، بلافاصله وصل شویم یا فقط انتخابش کنیم. */
    private var connectAfterAutoSearch = false

    /**
     * آیا هنگام شروع جست‌وجو تونل بالا بود.
     *
     * جدا نگه داشتنش لازم است چون خودِ جست‌وجو وضعیت را روی «در حال اتصال»
     * می‌برد؛ اگر در پایان از روی وضعیت تصمیم بگیریم، تونلِ روشن را «خاموش»
     * می‌بینیم و به‌جای برپا کردن دوباره، یک شروع تکراری می‌فرستیم.
     */
    private var tunnelWasRunningBeforeSearch = false

    // ─────────────────────────────────────────────────────────────────────────
    // چرخه‌ی عمر
    // ─────────────────────────────────────────────────────────────────────────

    fun start() {
        if (!registered) {
            ContextCompat.registerReceiver(
                getApplication(),
                serviceReceiver,
                IntentFilter(AppConfig.BROADCAST_ACTION_ACTIVITY),
                Utils.receiverFlags()
            )
            registered = true
        }
        MessageUtil.sendMsg2Service(getApplication(), AppConfig.MSG_REGISTER_CLIENT, "")
        refreshServers()
        refreshToggles()
    }

    /**
     * صفحه جلوی چشم کاربر آمد: جریان سرعت را باز کن.
     *
     * این کار عمداً به چرخه‌ی عمر گره خورده. هسته در فرایند دیگری می‌چرخد و
     * نمونه‌برداری از شمارنده‌هایش وقتی هیچ صفحه‌ای نمایش داده نمی‌شود، فقط
     * پردازنده و باتری می‌سوزاند.
     */
    fun onScreenVisible() {
        if (speedSubscribed) return
        speedSubscribed = true
        MessageUtil.sendMsg2Service(getApplication(), AppConfig.MSG_SPEED_SUBSCRIBE, "")
    }

    fun onScreenHidden() {
        if (!speedSubscribed) return
        speedSubscribed = false
        MessageUtil.sendMsg2Service(getApplication(), AppConfig.MSG_SPEED_UNSUBSCRIBE, "")
    }

    override fun onCleared() {
        onScreenHidden()
        autoSearchTimeout?.cancel()
        if (registered) {
            runCatching { getApplication<Application>().unregisterReceiver(serviceReceiver) }
            registered = false
        }
        super.onCleared()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // اکشن‌های کاربر
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * درخواست اتصال.
     *
     * در حالت خودکار، پیش از بالا آوردن تونل یک دور کامل سنجش اجرا می‌شود تا
     * سروری که همین حالا بهترین است انتخاب شود — نه سروری که دفعه‌ی قبل بهترین
     * بود. شرایط شبکه بین دو اتصال به‌کلی عوض می‌شود.
     */
    fun requestConnect() {
        userInitiatedStop = false

        if (_state.value.autoLocation) {
            if (MmkvManager.decodeServerList().isEmpty()) {
                postMessage(string(R.string.home_pick_server_first), isError = true)
                return
            }
            connectAfterAutoSearch = true
            beginAutoSearch()
            return
        }

        if (MmkvManager.getSelectServer().isNullOrEmpty()) {
            postMessage(string(R.string.home_pick_server_first), isError = true)
            return
        }
        _state.update { it.copy(tone = ConnectionTone.Connecting) }
        _events.tryEmit(HomeEvent.StartTunnel)
    }

    fun requestDisconnect() {
        userInitiatedStop = true
        cancelAutoSearch()
        _events.tryEmit(HomeEvent.StopTunnel)
    }

    /** پس از انتخاب سرور جدید، اگر تونل بالا باشد با کانفیگ تازه دوباره برپا می‌شود. */
    fun selectServer(guid: String) {
        // انتخاب دستی، حالت خودکار را خاموش می‌کند؛ وگرنه اتصال بعدی انتخاب
        // کاربر را بی‌سروصدا دور می‌ریخت.
        setAutoLocation(false)
        MmkvManager.setSelectServer(guid)
        refreshServers()
        if (_state.value.tone == ConnectionTone.Connected) {
            _events.tryEmit(HomeEvent.RestartTunnel)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // بهترین لوکیشن (خودکار)
    // ─────────────────────────────────────────────────────────────────────────

    /** کاربر ردیف «بهترین لوکیشن» را در فهرست سرورها انتخاب کرد. */
    fun selectAutoLocation() {
        setAutoLocation(true)
        refreshServers()
        connectAfterAutoSearch = _state.value.tone == ConnectionTone.Connected
        beginAutoSearch()
    }

    private fun setAutoLocation(enabled: Boolean) {
        MmkvManager.encodeSettings(AppConfig.PREF_AUTO_BEST_LOCATION, enabled)
        _state.update { it.copy(autoLocation = enabled) }
    }

    private fun beginAutoSearch() {
        if (_state.value.autoSearching) return

        tunnelWasRunningBeforeSearch = _state.value.tone == ConnectionTone.Connected

        _state.update {
            it.copy(
                autoSearching = true,
                autoDone = 0,
                autoTotal = 0,
                tone = if (connectAfterAutoSearch) ConnectionTone.Connecting else it.tone,
                statusDetail = string(R.string.home_auto_searching)
            )
        }
        _events.tryEmit(HomeEvent.FindBestServer)

        // اگر سرویس آزمون به هر دلیلی جواب نداد، کاربر نباید تا ابد روی
        // «در حال اتصال» بماند. با بهترین چیزی که در حافظه داریم جلو می‌رویم.
        autoSearchTimeout?.cancel()
        autoSearchTimeout = viewModelScope.launch {
            delay(AUTO_SEARCH_TIMEOUT_MS)
            if (_state.value.autoSearching) {
                _events.tryEmit(HomeEvent.CancelBestServerSearch)
                finishAutoSearch(bestKnownGuid())
            }
        }
    }

    private fun cancelAutoSearch() {
        autoSearchTimeout?.cancel()
        autoSearchTimeout = null
        connectAfterAutoSearch = false
        tunnelWasRunningBeforeSearch = false
        if (_state.value.autoSearching) {
            _events.tryEmit(HomeEvent.CancelBestServerSearch)
            _state.update { it.copy(autoSearching = false, autoDone = 0, autoTotal = 0) }
        }
    }

    /**
     * پایان جست‌وجو: سرور برنده را می‌نشاند و در صورت نیاز وصل می‌شود.
     * اگر هیچ سروری پیدا نشد، حالت خودکار روشن می‌ماند ولی اتصال انجام نمی‌شود.
     */
    private fun finishAutoSearch(guid: String?) {
        autoSearchTimeout?.cancel()
        autoSearchTimeout = null

        val shouldConnect = connectAfterAutoSearch
        val alreadyRunning = tunnelWasRunningBeforeSearch
        connectAfterAutoSearch = false
        tunnelWasRunningBeforeSearch = false

        if (guid.isNullOrEmpty()) {
            _state.update {
                it.copy(
                    autoSearching = false,
                    autoDone = 0,
                    autoTotal = 0,
                    tone = if (shouldConnect && !alreadyRunning) ConnectionTone.Idle else it.tone,
                    statusDetail = string(R.string.home_idle_detail)
                )
            }
            postMessage(string(R.string.home_auto_failed), isError = true)
            return
        }

        MmkvManager.setSelectServer(guid)
        _state.update { it.copy(autoSearching = false, autoDone = 0, autoTotal = 0) }
        refreshServers()

        postMessage(string(R.string.home_auto_picked, _state.value.server.name))

        if (shouldConnect) {
            if (alreadyRunning) {
                _events.tryEmit(HomeEvent.RestartTunnel)
            } else {
                _state.update { it.copy(tone = ConnectionTone.Connecting) }
                _events.tryEmit(HomeEvent.StartTunnel)
            }
        }
    }

    /** کم‌تأخیرترین سروری که از آزمایش‌های قبلی در حافظه مانده. */
    private fun bestKnownGuid(): String? =
        MmkvManager.decodeServerList()
            .mapNotNull { guid ->
                val delay = MmkvManager.decodeServerAffiliationInfo(guid)?.testDelayMillis ?: 0L
                if (delay > 0) guid to delay else null
            }
            .minByOrNull { it.second }
            ?.first
            ?: MmkvManager.decodeServerList().firstOrNull()

    fun measurePing() {
        if (_state.value.tone != ConnectionTone.Connected) return
        _state.update { it.copy(ping = string(R.string.connection_test_testing)) }
        MessageUtil.sendMsg2Service(getApplication(), AppConfig.MSG_MEASURE_DELAY, "")
    }

    fun toggleKillSwitch() {
        val next = !MmkvManager.decodeSettingsBool(AppConfig.PREF_KILL_SWITCH, false)
        MmkvManager.encodeSettings(AppConfig.PREF_KILL_SWITCH, next)
        refreshToggles()
        postMessage(string(if (next) R.string.kill_switch_on_toast else R.string.kill_switch_off_toast))
    }

    fun refreshToggles() {
        _state.update {
            it.copy(
                killSwitchEnabled = MmkvManager.decodeSettingsBool(AppConfig.PREF_KILL_SWITCH, false),
                perAppProxyEnabled = MmkvManager.decodeSettingsBool(AppConfig.PREF_PER_APP_PROXY, false),
                autoLocation = MmkvManager.decodeSettingsBool(AppConfig.PREF_AUTO_BEST_LOCATION, false)
            )
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    fun dismissUpdatePrompt() =
        _state.update { it.copy(optionalUpdateUrl = null) }

    // ─────────────────────────────────────────────────────────────────────────
    // فهرست سرورها
    // ─────────────────────────────────────────────────────────────────────────

    fun refreshServers() {
        val ctx = getApplication<Application>()
        val selectedGuid = MmkvManager.getSelectServer().orEmpty()
        val guids = MmkvManager.decodeServerList()

        // اگر هنوز سروری انتخاب نشده، اولین مورد فهرست را برمی‌گزینیم تا کاربر
        // بدون هیچ کار اضافه‌ای بتواند وصل شود.
        val effectiveGuid = when {
            selectedGuid.isNotEmpty() && guids.contains(selectedGuid) -> selectedGuid
            guids.isNotEmpty() -> guids.first().also { MmkvManager.setSelectServer(it) }
            else -> ""
        }

        val rows = guids.mapNotNull { guid ->
            val profile = MmkvManager.decodeServerConfig(guid) ?: return@mapNotNull null
            ServerRow(
                guid = guid,
                name = cleanRemark(profile.remarks),
                protocol = profile.configType.name,
                flagResId = CountryUtils.getFlagResId(ctx, profile.remarks),
                delayMillis = MmkvManager.decodeServerAffiliationInfo(guid)?.testDelayMillis ?: 0L,
                selected = guid == effectiveGuid
            )
        }

        val profile = if (effectiveGuid.isEmpty()) null else MmkvManager.decodeServerConfig(effectiveGuid)
        val remark = profile?.remarks.orEmpty()
        val code = countryCodeOf(remark)
        val auto = MmkvManager.decodeSettingsBool(AppConfig.PREF_AUTO_BEST_LOCATION, false)

        val lastUpdateMillis = MmkvManager.decodeSettingsLong(PREF_LAST_CONFIG_UPDATE, 0L)
        val formattedLastUpdate = if (lastUpdateMillis > 0L) {
            val sdf = SimpleDateFormat("yyyy/MM/dd - HH:mm", Locale.getDefault())
            sdf.format(Date(lastUpdateMillis))
        } else null

        _state.update {
            it.copy(
                servers = rows,
                lastUpdated = formattedLastUpdate,
                autoLocation = auto,
                server = SelectedServer(
                    guid = effectiveGuid,
                    name = if (profile == null) string(R.string.home_no_server) else cleanRemark(remark),
                    subtitle = when {
                        profile == null -> string(R.string.home_no_server_hint)
                        // در حالت خودکار، مهم‌تر از نام پروتکل این است که کاربر
                        // بداند این سرور را برنامه انتخاب کرده است.
                        auto -> string(R.string.home_auto_subtitle)
                        else -> profile.configType.name.uppercase()
                    },
                    flagResId = CountryUtils.getFlagResId(ctx, remark),
                    countryCode = code,
                    coordinates = CountryCoordinates.getOrFallback(code),
                    auto = auto
                )
            )
        }
    }

    /**
     * کد کشور را از نام کانفیگ درمی‌آورد تا نشانگر روی کره سر جای درست بنشیند.
     * از همان منطق پرچم استفاده می‌کنیم تا پرچم و نشانگر هرگز به دو کشور اشاره نکنند.
     */
    private fun countryCodeOf(remark: String): String? {
        if (remark.isBlank()) return null
        val ctx = getApplication<Application>()
        val flagId = CountryUtils.getFlagResId(ctx, remark)
        val name = runCatching { ctx.resources.getResourceEntryName(flagId) }.getOrNull() ?: return null
        val code = name.removePrefix("flag_")
        return if (code == "unknown") null else code
    }

    /** ایموجی پرچم و فاصله‌های اضافی را از نام کانفیگ پاک می‌کند. */
    private fun cleanRemark(remark: String): String {
        if (remark.isBlank()) return remark
        val flag = Pattern.compile("[\\uD83C][\\uDDE6-\\uDDFF][\\uD83C][\\uDDE6-\\uDDFF]")
        return flag.matcher(remark).replaceAll("").trim().ifBlank { remark.trim() }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // وضعیت اشتراک
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * بروزرسانی دستی کانفیگ‌ها به همراه نمایش ۲ ثانیه‌ای لودینگ
     */
    fun refreshConfigsManually() {
        viewModelScope.launch {
            _state.update { it.copy(isUpdatingConfigs = true) }
            loadAccount()
            delay(2000L)
            _state.update { it.copy(isUpdatingConfigs = false) }
        }
    }

    /**
     * وضعیت حساب را از سرور می‌گیرد و کانفیگ‌های تازه را جایگزین می‌کند.
     * اگر سرور در دسترس نباشد، آخرین وضعیت ذخیره‌شده به کار می‌آید تا برنامه در
     * شرایط قطعی شبکه هم بلااستفاده نشود.
     */
    fun loadAccount() {
        val token = TokenStore.token(getApplication()) ?: run {
            _events.tryEmit(HomeEvent.RequireLogin)
            return
        }
        repo.reportAppUpdateIfNeeded(token) {}
        _state.update { it.copy(busy = true) }

        repo.status(token) { result ->
            viewModelScope.launch {
                result.fold(
                    onSuccess = { applyStatus(it, token) },
                    onFailure = { error ->
                        val msg = error.message.orEmpty()
                        when {
                            msg.contains("HTTP_403", true) || msg.contains("suspended", true) -> {
                                replaceConfigs(emptyList())
                                MmkvManager.removeLastStatus()
                                _state.update { it.copy(busy = false, accountSuspended = true) }
                            }

                            msg.contains("HTTP_401", true) || msg.contains("invalid or expired", true) -> {
                                TokenStore.clear(getApplication())
                                _state.update { it.copy(busy = false) }
                                _events.tryEmit(HomeEvent.RequireLogin)
                            }

                            else -> {
                                val cached = MmkvManager.loadLastStatus()
                                if (cached != null) {
                                    postMessage(string(R.string.home_offline_cached))
                                    applyStatus(cached, token)
                                } else {
                                    _state.update { it.copy(busy = false) }
                                    postMessage(string(R.string.home_status_failed), isError = true)
                                }
                            }
                        }
                    }
                )
            }
        }
    }

    fun retryAccount() {
        _state.update { it.copy(accountSuspended = false) }
        loadAccount()
    }

    private suspend fun applyStatus(status: StatusResponse, token: String) {
        val ctx = getApplication<Application>()
        val traffic = StatusFormatter.traffic(status.data_limit, status.used_traffic)
        val days = StatusFormatter.days(ctx, status.expire)
        val unlimitedData = status.data_limit == null
        val used = status.used_traffic ?: 0L
        val total = status.data_limit

        _state.update {
            it.copy(
                account = AccountSummary(
                    username = status.username ?: "—",
                    statusLabel = status.status.orEmpty(),
                    dataUsed = traffic.used,
                    dataTotal = traffic.total,
                    dataRemaining = traffic.remain,
                    dataProgress = if (total != null && total > 0L) {
                        (used.toDouble() / total.toDouble()).toFloat().coerceIn(0f, 1f)
                    } else null,
                    unlimitedData = unlimitedData,
                    daysRemaining = days.remainDays,
                    unlimitedDays = days.remainDays == UNLIMITED_FA,
                    loaded = true
                )
            )
        }

        replaceConfigs(status.links.orEmpty())

        val link = status.update_link
        if (status.need_to_update == true && !link.isNullOrEmpty()) {
            repo.updatePromptSeen(token) {}
            if (status.is_ignoreable == true) {
                _state.update { it.copy(optionalUpdateUrl = link, forcedUpdateUrl = null) }
            } else {
                _state.update { it.copy(forcedUpdateUrl = link, optionalUpdateUrl = null) }
            }
        } else {
            _state.update { it.copy(forcedUpdateUrl = null, optionalUpdateUrl = null) }
        }

        _state.update { it.copy(busy = false) }
    }

    /**
     * فهرست کانفیگ‌ها را با آنچه سرور داده جایگزین می‌کند.
     *
     * سرور مرجع نهایی است؛ نگه داشتن کانفیگ‌های قدیمی باعث می‌شود کاربر به سروری
     * وصل بماند که دیگر در اشتراکش نیست.
     */
    private suspend fun replaceConfigs(links: List<String>) = withContext(Dispatchers.IO) {
        val previous = MmkvManager.getSelectServer()
        val previousRemark = previous?.let { MmkvManager.decodeServerConfig(it)?.remarks }

        MmkvManager.removeAllServer()
        val payload = links.filter { it.isNotBlank() }.distinct().joinToString("\n")
        if (payload.isNotEmpty()) {
            runCatching { AngConfigManager.importBatchConfig(payload, "", true) }
                .onFailure { Log.e(AppConfig.TAG, "Failed to import subscription links", it) }
        }

        // ثبت زمان آخرین به‌روزرسانی موفق کانفیگ‌ها
        MmkvManager.encodeSettings(PREF_LAST_CONFIG_UPDATE, System.currentTimeMillis())

        // تلاش می‌کنیم همان سروری که کاربر قبلاً انتخاب کرده بود دوباره انتخاب شود.
        if (previousRemark != null) {
            val match = MmkvManager.decodeServerList().firstOrNull { guid ->
                MmkvManager.decodeServerConfig(guid)?.remarks == previousRemark
            }
            if (match != null) MmkvManager.setSelectServer(match)
        }

        withContext(Dispatchers.Main) { refreshServers() }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // پایش سرعت
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * سرعت واقعی تونل.
     *
     * قبلاً این عدد از `TrafficStats.getUidRxBytes` می‌آمد و واقعی نبود: در حالت
     * VPN هر بسته دو بار شمرده می‌شود (یک بار موقع خواندن از رابط `tun` و یک بار
     * موقع نوشتن روی سوکت بیرونی)، و ترافیک خود برنامه — درخواست‌های وضعیت
     * حساب، به‌روزرسانی‌ها، پوش — هم داخلش بود. نتیجه‌اش عددی بود که با هیچ
     * اندازه‌گیری دیگری جور درنمی‌آمد.
     *
     * حالا عدد مستقیم از شمارنده‌های خروجی خود هسته می‌آید: دقیقاً همان بایت‌هایی
     * که از تونل عبور کرده‌اند، بدون ترافیک مستقیم و بدون شمارش مضاعف.
     */
    private fun onSpeedTick(uploadBps: Long, downloadBps: Long) {
        _state.update {
            it.copy(
                downloadSpeed = formatSpeed(downloadBps),
                uploadSpeed = formatSpeed(uploadBps)
            )
        }
    }

    private fun resetSpeed() {
        _state.update { it.copy(downloadSpeed = ZERO_SPEED, uploadSpeed = ZERO_SPEED) }
    }

    private fun formatSpeed(bytesPerSecond: Long): String {
        val v = bytesPerSecond.toDouble()
        return when {
            v < 1024 -> "%.0f B/s".format(v)
            v < 1024 * 1024 -> "%.1f KB/s".format(v / 1024)
            v < 1024L * 1024 * 1024 -> "%.1f MB/s".format(v / (1024 * 1024))
            else -> "%.2f GB/s".format(v / (1024.0 * 1024 * 1024))
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // پیام‌های سرویس تونل
    // ─────────────────────────────────────────────────────────────────────────

    private val serviceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.getIntExtra("key", 0)) {
                AppConfig.MSG_STATE_RUNNING -> onRunning()
                AppConfig.MSG_STATE_NOT_RUNNING -> onStopped()
                AppConfig.MSG_STATE_START_SUCCESS -> {
                    onRunning()
                    postMessage(string(R.string.toast_services_success))
                }

                AppConfig.MSG_STATE_START_FAILURE -> {
                    resetSpeed()
                    _state.update {
                        it.copy(
                            tone = ConnectionTone.Idle,
                            statusHeadline = string(R.string.not_connected),
                            statusDetail = string(R.string.connection_not_connected),
                            ping = EMPTY_METRIC
                        )
                    }
                    postMessage(string(R.string.toast_services_failure), isError = true)
                }

                AppConfig.MSG_STATE_STOP_SUCCESS -> onStopped()

                AppConfig.MSG_STATE_KILL_SWITCH_ON -> {
                    resetSpeed()
                    KeepAliveScheduler.stop(getApplication())
                    _state.update {
                        it.copy(
                            tone = ConnectionTone.Blocked,
                            statusHeadline = string(R.string.kill_switch_active),
                            statusDetail = string(R.string.kill_switch_tap_reconnect),
                            ping = EMPTY_METRIC
                        )
                    }
                }

                AppConfig.MSG_STATE_KILL_SWITCH_OFF -> {
                    if (_state.value.tone == ConnectionTone.Blocked) onStopped()
                }

                AppConfig.MSG_MEASURE_DELAY_SUCCESS -> {
                    val content = intent.getStringExtra("content").orEmpty()
                    _state.update { it.copy(ping = extractPing(content)) }
                }

                AppConfig.MSG_MEASURE_CONFIG_SUCCESS -> {
                    val pair = intent.serializable<Pair<String, Long>>("content") ?: return
                    MmkvManager.encodeServerTestDelayMillis(pair.first, pair.second)
                    refreshServers()
                }

                AppConfig.MSG_SPEED_TICK -> {
                    val pair = intent.serializable<Pair<Long, Long>>("content") ?: return
                    onSpeedTick(uploadBps = pair.first, downloadBps = pair.second)
                }

                AppConfig.MSG_MEASURE_BEST_PROGRESS -> {
                    val pair = intent.serializable<Pair<Int, Int>>("content") ?: return
                    if (!_state.value.autoSearching) return
                    _state.update { it.copy(autoDone = pair.first, autoTotal = pair.second) }
                }

                AppConfig.MSG_MEASURE_BEST_SUCCESS -> {
                    val pair = intent.serializable<Pair<String, Long>>("content") ?: return
                    if (!_state.value.autoSearching) return
                    finishAutoSearch(pair.first.ifEmpty { bestKnownGuid() })
                }
            }
        }
    }

    private fun onRunning() {
        // اگر صفحه در حال نمایش است، جریان سرعت را دوباره باز می‌کنیم؛ ممکن است
        // تونل بعد از باز شدن صفحه بالا آمده باشد.
        if (speedSubscribed) {
            MessageUtil.sendMsg2Service(getApplication(), AppConfig.MSG_SPEED_SUBSCRIBE, "")
        }
        KeepAliveScheduler.start(getApplication())
        _state.update {
            it.copy(
                tone = ConnectionTone.Connected,
                statusHeadline = string(R.string.connected),
                statusDetail = string(R.string.home_connected_detail)
            )
        }
        measurePing()
    }

    private fun onStopped() {
        resetSpeed()
        KeepAliveScheduler.stop(getApplication())

        // اگر همین حالا در حال جست‌وجوی بهترین سرور هستیم، «قطع بودن» یک حالت
        // گذراست و نباید متن وضعیت را از روی پیام جست‌وجو بردارد.
        if (_state.value.autoSearching) return

        _state.update {
            it.copy(
                tone = ConnectionTone.Idle,
                statusHeadline = string(R.string.not_connected),
                statusDetail = string(R.string.home_idle_detail),
                ping = EMPTY_METRIC
            )
        }
    }

    /** از رشته‌ی خروجی آزمون تأخیر، فقط عدد میلی‌ثانیه را جدا می‌کند. */
    private fun extractPing(raw: String): String {
        val digits = Regex("-?\\d+").find(raw)?.value ?: return raw.ifBlank { EMPTY_METRIC }
        val value = digits.toLongOrNull() ?: return raw
        return if (value < 0) string(R.string.home_ping_failed) else "$value ms"
    }

    // ─────────────────────────────────────────────────────────────────────────

    private fun postMessage(text: String, isError: Boolean = false) {
        _state.update {
            it.copy(message = UiMessage(text, isError, System.currentTimeMillis()))
        }
    }

    private fun string(resId: Int): String = getApplication<Application>().getString(resId)

    private fun string(resId: Int, vararg args: Any): String =
        getApplication<Application>().getString(resId, *args)

    private companion object {
        const val ZERO_SPEED = "0 KB/s"
        const val EMPTY_METRIC = "—"
        const val UNLIMITED_FA = "نامحدود"
        const val PREF_LAST_CONFIG_UPDATE = "pref_last_config_update"

        /**
         * سقف زمانی جست‌وجوی خودکار. مرحله‌ی تأخیر روی فهرست‌های بزرگ چند ثانیه
         * و مرحله‌ی پهنای باند حدود ۴ ثانیه به ازای هر نامزد طول می‌کشد؛ این عدد
         * با حاشیه‌ی امن بالای آن است.
         */
        const val AUTO_SEARCH_TIMEOUT_MS = 45_000L
    }
}

/** رویدادهایی که فقط اکتیویتی می‌تواند انجامشان بدهد (مجوز VPN، جابه‌جایی صفحه). */
sealed interface HomeEvent {
    data object StartTunnel : HomeEvent
    data object StopTunnel : HomeEvent
    data object RestartTunnel : HomeEvent
    data object RequireLogin : HomeEvent
    data object FindBestServer : HomeEvent
    data object CancelBestServerSearch : HomeEvent
}