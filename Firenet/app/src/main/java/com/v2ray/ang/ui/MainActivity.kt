package com.v2ray.ang.ui

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.Lifecycle
import com.v2ray.ang.AngApplication
import com.v2ray.ang.AppConfig
import com.v2ray.ang.AppConfig.VPN
import com.v2ray.ang.R
import com.v2ray.ang.data.auth.AuthRepository
import com.v2ray.ang.data.auth.TokenStore
import com.v2ray.ang.handler.MigrateManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.UiMemoryGovernor
import com.v2ray.ang.handler.V2RayServiceManager
import com.v2ray.ang.net.ApiClient
import com.v2ray.ang.ui.compose.home.HomeEvent
import com.v2ray.ang.ui.compose.home.HomeRoute
import com.v2ray.ang.ui.compose.home.HomeViewModel
import com.v2ray.ang.ui.compose.home.MenuAction
import com.v2ray.ang.ui.compose.loading.LoadingScreen
import com.v2ray.ang.ui.compose.theme.ConnectionTone
import com.v2ray.ang.ui.compose.theme.FirenetTheme
import com.v2ray.ang.ui.login.LoginActivity
import androidx.compose.ui.Modifier
import com.v2ray.ang.util.MessageUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * صفحه‌ی اصلی برنامه.
 *
 * پس از مهاجرت به Compose، این کلاس فقط سه کار می‌کند: نگه‌داشتن سطح Compose،
 * گرفتن مجوزهایی که تنها یک اکتیویتی می‌تواند بگیرد (مجوز VPN و نوتیفیکیشن) و
 * جابه‌جایی به صفحات دیگر. تمام وضعیت رابط کاربری در [HomeViewModel] است و
 * هسته‌ی تونل دست‌نخورده باقی مانده.
 */
class MainActivity : BaseActivity() {

    private val viewModel: HomeViewModel by viewModels()
    private val repo by lazy { AuthRepository(this) }

    private val requestVpnPermission =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                V2RayServiceManager.startVService(this)
            } else {
                viewModel.refreshToggles()
            }
        }

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val forceLogoutReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AppConfig.ACTION_FORCE_LOGOUT) {
                goToLogin(showExpiredNotice = true)
            }
        }
    }
    private var forceLogoutRegistered = false

    /**
     * وقتی توکنی نیست، این اکتیویتی بلافاصله به صفحه‌ی ورود می‌رود و هیچ‌کدام از
     * اجزای صفحه‌ی اصلی ساخته نمی‌شوند. بقیه‌ی چرخه‌ی عمر باید همین را بداند و
     * سراغ ViewModel نرود.
     */
    private var homeActive = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (TokenStore.token(this).isNullOrEmpty()) {
            goToLogin(showExpiredNotice = false)
            return
        }
        homeActive = true

        registerForceLogout()
        requestNotificationPermissionIfNeeded()
        migrateLegacyConfigs()

        viewModel.start()
        viewModel.loadAccount()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect(::handleEvent)
            }
        }

        setContent {
            FirenetTheme {
                // صفحه‌ی راه‌اندازی روی صفحه‌ی اصلی می‌نشیند، نه به جای آن. این
                // ترتیب باعث می‌شود چیدمان و اندازه‌گیری صفحه‌ی اصلی پشت انیمیشن
                // انجام شود و لحظه‌ی محو شدن لودینگ، همه‌چیز از قبل آماده باشد.
                var ready by remember { mutableStateOf(false) }

                LaunchedWarmUp { ready = true }

                val state by viewModel.state.collectAsStateWithLifecycle()

                Box(modifier = Modifier.fillMaxSize()) {
                    HomeRoute(
                        state = state,
                        onConnect = viewModel::requestConnect,
                        onDisconnect = viewModel::requestDisconnect,
                        onSelectServer = viewModel::selectServer,
                        onSelectAutoLocation = viewModel::selectAutoLocation,
                        onTestAllServers = ::testAllServers,
                        onPing = viewModel::measurePing,
                        onToggleKillSwitch = viewModel::toggleKillSwitch,
                        onMenuAction = ::handleMenuAction,
                        onMessageShown = viewModel::consumeMessage,
                        onOpenUpdate = ::openLink,
                        onDismissOptionalUpdate = viewModel::dismissUpdatePrompt,
                        onRetryAccount = viewModel::retryAccount,
                        onSignOutConfirmed = ::signOut
                    )

                    AnimatedVisibility(
                        visible = !ready,
                        enter = fadeIn(tween(0)),
                        exit = fadeOut(tween(420))
                    ) {
                        LoadingScreen()
                    }
                }
            }
        }

        handleNotificationIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        // جریان سرعت فقط تا وقتی باز است که صفحه دیده می‌شود؛ در پس‌زمینه،
        // نوتیفیکیشن همان اطلاعات را با یک‌سوم نرخ نمونه‌برداری نشان می‌دهد.
        if (homeActive) viewModel.onScreenVisible()
    }

    override fun onStop() {
        if (homeActive) viewModel.onScreenHidden()
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        if (!homeActive) return
        viewModel.refreshServers()
        viewModel.refreshToggles()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleNotificationIntent(intent)
    }

    override fun onDestroy() {
        if (forceLogoutRegistered) {
            runCatching { unregisterReceiver(forceLogoutReceiver) }
            forceLogoutRegistered = false
        }
        super.onDestroy()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // رویدادها
    // ─────────────────────────────────────────────────────────────────────────

    private fun handleEvent(event: HomeEvent) {
        when (event) {
            HomeEvent.StartTunnel -> startTunnel()
            HomeEvent.StopTunnel -> V2RayServiceManager.stopVService(this)
            HomeEvent.RestartTunnel -> restartTunnel()
            HomeEvent.RequireLogin -> goToLogin(showExpiredNotice = true)

            HomeEvent.FindBestServer ->
                MessageUtil.sendMsg2TestService(this, AppConfig.MSG_MEASURE_BEST, "")

            HomeEvent.CancelBestServerSearch ->
                MessageUtil.sendMsg2TestService(this, AppConfig.MSG_MEASURE_BEST_CANCEL, "")
        }
    }

    /**
     * صفحه‌ی راه‌اندازی را تا آماده شدن برنامه نگه می‌دارد.
     *
     * سه شرط با هم: کارهای پس‌زمینه‌ی [AngApplication] تمام شده باشد، اولین
     * ترکیب صفحه‌ی اصلی رسم شده باشد، و دست‌کم [MIN_LOADING_MS] گذشته باشد.
     * شرط سوم برای این است که لودینگ روی دستگاه‌های سریع پلک نزند؛ یک صفحه که
     * ۸۰ میلی‌ثانیه ظاهر و ناپدید می‌شود، بدتر از نبودنش است.
     *
     * سقف [MAX_LOADING_MS] هم هست تا اگر چیزی گیر کرد، کاربر پشت یک صفحه‌ی
     * لودینگ ابدی نماند.
     */
    @androidx.compose.runtime.Composable
    private fun LaunchedWarmUp(onReady: () -> Unit) {
        androidx.compose.runtime.LaunchedEffect(Unit) {
            val startedAt = android.os.SystemClock.elapsedRealtime()

            // اگر فقط رابط کاربری آزاد شده بود، فرایند هنوز گرم است و بازسازی
            // درخت Compose سریع‌تر از یک شروع سرد تمام می‌شود؛ پس کف زمان را
            // کوتاه‌تر می‌گیریم و کاربر بی‌جهت منتظر نمی‌ماند.
            val floor = if (UiMemoryGovernor.consumeReleasedFlag()) {
                MIN_LOADING_WARM_MS
            } else {
                MIN_LOADING_COLD_MS
            }

            withTimeoutOrNull(MAX_LOADING_MS) {
                (application as? AngApplication)?.awaitWarmUp()
            }

            val elapsed = android.os.SystemClock.elapsedRealtime() - startedAt
            if (elapsed < floor) delay(floor - elapsed)
            onReady()
        }
    }

    /**
     * تونل را بالا می‌آورد. در حالت VPN، سیستم‌عامل پیش از هر چیز باید اجازه‌ی
     * ساخت رابط شبکه را از کاربر بگیرد.
     */
    private fun startTunnel() {
        val mode = MmkvManager.decodeSettingsString(AppConfig.PREF_MODE) ?: VPN
        if (mode == VPN) {
            val consent = VpnService.prepare(this)
            if (consent == null) {
                V2RayServiceManager.startVService(this)
            } else {
                requestVpnPermission.launch(consent)
            }
        } else {
            V2RayServiceManager.startVService(this)
        }
    }

    private fun restartTunnel() {
        V2RayServiceManager.stopVService(this)
        lifecycleScope.launch {
            delay(600)
            startTunnel()
        }
    }

    private fun testAllServers() {
        MessageUtil.sendMsg2TestService(this, AppConfig.MSG_MEASURE_CONFIG_CANCEL, "")
        val guids = MmkvManager.decodeServerList()
        // نتایج پینگ قبلی در MMKV باقی می‌مانند و با رسیدن هر نتیجه‌ی تازه به‌روز می‌شوند؛
        // پیش از تست، همه را پاک نمی‌کنیم تا با خروج/ورود به صفحه، پینگ‌ها از بین نروند.
        viewModel.refreshServers()
        lifecycleScope.launch(Dispatchers.Default) {
            guids.forEach { guid ->
                MessageUtil.sendMsg2TestService(this@MainActivity, AppConfig.MSG_MEASURE_CONFIG, guid)
            }
        }
    }

    private fun handleMenuAction(action: MenuAction) {
        val target = when (action) {
            MenuAction.PerAppProxy -> PerAppProxyActivity::class.java
            MenuAction.Routing -> RoutingSettingActivity::class.java
            MenuAction.Assets -> UserAssetActivity::class.java
            MenuAction.Settings -> SettingsActivity::class.java
            MenuAction.Logs -> LogcatActivity::class.java
            MenuAction.About -> AboutActivity::class.java
            MenuAction.CheckUpdate -> CheckUpdateActivity::class.java
            MenuAction.Logout -> null
        } ?: return

        val intent = Intent(this, target)
        if (action == MenuAction.Settings) {
            intent.putExtra("isRunning", viewModel.state.value.tone == ConnectionTone.Connected)
        }
        startActivity(intent)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // حساب کاربری
    // ─────────────────────────────────────────────────────────────────────────

    private fun signOut() {
        val token = TokenStore.token(this)
        V2RayServiceManager.stopVService(this)
        if (token != null) {
            repo.logout(token) { }
        }
        goToLogin(showExpiredNotice = false)
    }

    private fun goToLogin(showExpiredNotice: Boolean) {
        val jwt = TokenStore.token(applicationContext)
        if (!jwt.isNullOrBlank()) {
            ApiClient.postLogout(jwt) { }
        }
        TokenStore.clear(applicationContext)
        lifecycleScope.launch(Dispatchers.IO) { MmkvManager.removeAllServer() }

        startActivity(
            Intent(this, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                if (showExpiredNotice) putExtra(EXTRA_SESSION_EXPIRED, true)
            }
        )
        finish()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // کارهای جانبی
    // ─────────────────────────────────────────────────────────────────────────

    private fun registerForceLogout() {
        val filter = IntentFilter(AppConfig.ACTION_FORCE_LOGOUT)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(forceLogoutReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(forceLogoutReceiver, filter)
        }
        forceLogoutRegistered = true
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    /** مهاجرت کانفیگ‌های ذخیره‌شده با قالب قدیمی به قالب فعلی. */
    private fun migrateLegacyConfigs() {
        lifecycleScope.launch(Dispatchers.IO) {
            if (MigrateManager.migrateServerConfig2Profile()) {
                launch(Dispatchers.Main) { viewModel.refreshServers() }
            }
        }
    }

    /** پیام‌های push می‌توانند لینکی همراه داشته باشند که باید در مرورگر باز شود. */
    private fun handleNotificationIntent(intent: Intent?) {
        val url = intent?.getStringExtra("link") ?: intent?.getStringExtra("url") ?: return
        if (url.isNotEmpty()) openLink(url)
    }

    private fun openLink(url: String) {
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }.onFailure {
            Log.e(AppConfig.TAG, "Failed to open link: $url", it)
        }
    }

    companion object {
        const val EXTRA_SESSION_EXPIRED = "session_expired"

        /** کف و سقف زمان نمایش صفحه‌ی راه‌اندازی. */
        private const val MIN_LOADING_COLD_MS = 1_500L
        private const val MIN_LOADING_WARM_MS = 900L
        private const val MAX_LOADING_MS = 5_000L
    }
}
