package com.v2ray.ang.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.ProxyInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.StrictMode
import android.util.Log
import androidx.annotation.RequiresApi
import com.v2ray.ang.AppConfig
import com.v2ray.ang.AppConfig.LOOPBACK
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.NotificationManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.handler.V2RayServiceManager
import com.v2ray.ang.util.MessageUtil
import com.v2ray.ang.util.MyContextWrapper
import com.v2ray.ang.util.Utils
import java.lang.ref.SoftReference

class V2RayVpnService : VpnService(), ServiceControl {
    private lateinit var mInterface: ParcelFileDescriptor
    private var isRunning = false
    private var tun2SocksService: Tun2SocksControl? = null

    /** True while the kill switch is holding a black-holed interface that blocks all traffic. */
    @Volatile
    private var killSwitchActive = false

    /** True once a real tunnel has been established; the kill switch only engages after this. */
    @Volatile
    private var connectionEstablished = false

    /** True once the tunnel has been fully torn down, to guard against re-entrant stop calls. */
    @Volatile
    private var teardownComplete = false

    companion object {
        /**
         * Marks whether the next stop was explicitly requested by the user (disconnect / restart / revoke).
         * Unexpected stops (e.g. the core dying) leave this false, which is what triggers the kill switch.
         */
        @Volatile
        var userInitiatedDisconnect = false
    }

    /**destroy
     * Unfortunately registerDefaultNetworkCallback is going to return our VPN interface: https://android.googlesource.com/platform/frameworks/base/+/dda156ab0c5d66ad82bdcf76cda07cbc0a9c8a2e
     *
     * This makes doing a requestNetwork with REQUEST necessary so that we don't get ALL possible networks that
     * satisfies default network capabilities but only THE default network. Unfortunately we need to have
     * android.permission.CHANGE_NETWORK_STATE to be able to call requestNetwork.
     *
     * Source: https://android.googlesource.com/platform/frameworks/base/+/2df4c7d/services/core/java/com/android/server/ConnectivityService.java#887
     */
    @delegate:RequiresApi(Build.VERSION_CODES.P)
    private val defaultNetworkRequest by lazy {
        NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
            .build()
    }

    private val connectivity by lazy { getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager }

    @delegate:RequiresApi(Build.VERSION_CODES.P)
    private val defaultNetworkCallback by lazy {
        object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                setUnderlyingNetworks(arrayOf(network))
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                // it's a good idea to refresh capabilities
                setUnderlyingNetworks(arrayOf(network))
            }

            override fun onLost(network: Network) {
                setUnderlyingNetworks(null)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val policy = StrictMode.ThreadPolicy.Builder().permitAll().build()
        StrictMode.setThreadPolicy(policy)
        V2RayServiceManager.serviceControl = SoftReference(this)
    }

    override fun onRevoke() {
        // The system revoked the VPN (e.g. another VPN started). Treat as an explicit teardown;
        // we can't re-establish a blocking interface once our rights are gone.
        userInitiatedDisconnect = true
        stopV2Ray()
    }

//    override fun onLowMemory() {
//        stopV2Ray()
//        super.onLowMemory()
//    }

    override fun onDestroy() {
        super.onDestroy()
        NotificationManager.cancelNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (V2RayServiceManager.startCoreLoop()) {
            startService()
        }
        return START_STICKY
        //return super.onStartCommand(intent, flags, startId)
    }

    override fun getService(): Service {
        return this
    }

    override fun startService() {
        setupService()
    }

    override fun stopService() {
        stopV2Ray(true)
    }

    override fun vpnProtect(socket: Int): Boolean {
        return protect(socket)
    }

    @RequiresApi(Build.VERSION_CODES.N)
    override fun attachBaseContext(newBase: Context?) {
        val context = newBase?.let {
            MyContextWrapper.wrap(newBase, SettingsManager.getLocale())
        }
        super.attachBaseContext(context)
    }

    /**
     * Sets up the VPN service.
     * Prepares the VPN and configures it if preparation is successful.
     */
    private fun setupService() {
        val prepare = prepare(this)
        if (prepare != null) {
            return
        }

        // A real tunnel is being (re)established, so any previous kill-switch hold is cleared.
        if (killSwitchActive) {
            killSwitchActive = false
            MessageUtil.sendMsg2UI(this, AppConfig.MSG_STATE_KILL_SWITCH_OFF, "")
        }
        teardownComplete = false

        if (configureVpnService() != true) {
            return
        }

        runTun2socks()
        connectionEstablished = true
    }

    /**
     * Configures the VPN service.
     * @return True if the VPN service was configured successfully, false otherwise.
     */
    private fun configureVpnService(): Boolean {
        val builder = Builder()

        // Configure network settings (addresses, routing and DNS)
        configureNetworkSettings(builder)

        // Configure app-specific settings (session name and per-app proxy)
        configurePerAppProxy(builder)

        // Close the old interface since the parameters have been changed
        try {
            mInterface.close()
        } catch (ignored: Exception) {
            // ignored
        }

        // Configure platform-specific features
        configurePlatformFeatures(builder)

        // Create a new interface using the builder and save the parameters
        try {
            mInterface = builder.establish()!!
            isRunning = true
            return true
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Failed to establish VPN interface", e)
            stopV2Ray()
        }
        return false
    }

    /**
     * Configures the basic network settings for the VPN.
     * This includes IP addresses, routing rules, and DNS servers.
     *
     * @param builder The VPN Builder to configure
     */
    private fun configureNetworkSettings(builder: Builder) {
        val vpnConfig = SettingsManager.getCurrentVpnInterfaceAddressConfig()
        val bypassLan = SettingsManager.routingRulesetsBypassLan()

        // Configure IPv4 settings
        builder.setMtu(SettingsManager.getVpnMtu())
        builder.addAddress(vpnConfig.ipv4Client, 30)

        // Configure routing rules
        if (bypassLan) {
            AppConfig.ROUTED_IP_LIST.forEach {
                val addr = it.split('/')
                builder.addRoute(addr[0], addr[1].toInt())
            }
        } else {
            builder.addRoute("0.0.0.0", 0)
        }

        // Configure IPv6 if enabled
        if (MmkvManager.decodeSettingsBool(AppConfig.PREF_PREFER_IPV6) == true) {
            builder.addAddress(vpnConfig.ipv6Client, 126)
            if (bypassLan) {
                builder.addRoute("2000::", 3) // Currently only 1/8 of total IPv6 is in use
                builder.addRoute("fc00::", 18) // Xray-core default FakeIPv6 Pool
            } else {
                builder.addRoute("::", 0)
            }
        }

        // Configure DNS servers
        //if (MmkvManager.decodeSettingsBool(AppConfig.PREF_LOCAL_DNS_ENABLED) == true) {
        //  builder.addDnsServer(PRIVATE_VLAN4_ROUTER)
        //} else {
        SettingsManager.getVpnDnsServers().forEach {
            if (Utils.isPureIpAddress(it)) {
                builder.addDnsServer(it)
            }
        }

        builder.setSession(V2RayServiceManager.getRunningServerName())
    }

    /**
     * Configures platform-specific VPN features for different Android versions.
     *
     * @param builder The VPN Builder to configure
     */
    private fun configurePlatformFeatures(builder: Builder) {
        // Android P (API 28) and above: Configure network callbacks
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                connectivity.requestNetwork(defaultNetworkRequest, defaultNetworkCallback)
            } catch (e: Exception) {
                Log.e(AppConfig.TAG, "Failed to request default network", e)
            }
        }

        // Android Q (API 29) and above: Configure metering and HTTP proxy
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setMetered(false)
            if (MmkvManager.decodeSettingsBool(AppConfig.PREF_APPEND_HTTP_PROXY)) {
                builder.setHttpProxy(ProxyInfo.buildDirectProxy(LOOPBACK, SettingsManager.getHttpPort()))
            }
        }
    }

    /**
     * Configures per-app proxy rules for the VPN builder.
     *
     * - If per-app proxy is not enabled, disallow the VPN service's own package.
     * - If no apps are selected, disallow the VPN service's own package.
     * - If bypass mode is enabled, disallow all selected apps (including self).
     * - If proxy mode is enabled, only allow the selected apps (excluding self).
     *
     * @param builder The VPN Builder to configure.
     */
    private fun configurePerAppProxy(builder: Builder) {
        val selfPackageName = BuildConfig.APPLICATION_ID

        // If per-app proxy is not enabled, disallow the VPN service's own package and return
        if (MmkvManager.decodeSettingsBool(AppConfig.PREF_PER_APP_PROXY) == false) {
            builder.addDisallowedApplication(selfPackageName)
            return
        }

        // If no apps are selected, disallow the VPN service's own package and return
        val apps = MmkvManager.decodeSettingsStringSet(AppConfig.PREF_PER_APP_PROXY_SET)
        if (apps.isNullOrEmpty()) {
            builder.addDisallowedApplication(selfPackageName)
            return
        }

        val bypassApps = MmkvManager.decodeSettingsBool(AppConfig.PREF_BYPASS_APPS)
        // Handle the VPN service's own package according to the mode
        if (bypassApps) apps.add(selfPackageName) else apps.remove(selfPackageName)

        apps.forEach {
            try {
                if (bypassApps) {
                    // In bypass mode, disallow the selected apps
                    builder.addDisallowedApplication(it)
                } else {
                    // In proxy mode, only allow the selected apps
                    builder.addAllowedApplication(it)
                }
            } catch (e: PackageManager.NameNotFoundException) {
                Log.e(AppConfig.TAG, "Failed to configure app in VPN: ${e.localizedMessage}", e)
            }
        }
    }

    /**
     * Runs the tun2socks process.
     * Starts the tun2socks process with the appropriate parameters.
     */
    private fun runTun2socks() {
        if (MmkvManager.decodeSettingsBool(AppConfig.PREF_USE_HEV_TUNNEL) == true) {
            tun2SocksService = TProxyService(
                context = applicationContext,
                vpnInterface = mInterface,
                isRunningProvider = { isRunning },
                restartCallback = { runTun2socks() }
            )
        } else {
            tun2SocksService = Tun2SocksService(
                context = applicationContext,
                vpnInterface = mInterface,
                isRunningProvider = { isRunning },
                restartCallback = { runTun2socks() }
            )
        }

        tun2SocksService?.startTun2Socks()
    }

    /**
     * Stops the V2Ray service.
     *
     * If the kill switch is enabled and the stop was NOT explicitly requested by the user
     * (i.e. the core died unexpectedly), the tunnel is not torn down. Instead a black-holed
     * interface is established that captures every route but forwards nothing, so no traffic
     * can leak to the underlying network until the user reconnects or disconnects manually.
     *
     * @param isForced Whether to force stop the service.
     */
    private fun stopV2Ray(isForced: Boolean = true) {
        if (teardownComplete) return

        val userStop = userInitiatedDisconnect
        val killSwitchEnabled = MmkvManager.decodeSettingsBool(AppConfig.PREF_KILL_SWITCH, false)

        // Already holding a blocking interface and this isn't a user-initiated stop: ignore re-entrant calls.
        if (killSwitchActive && !userStop) return

        isRunning = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                connectivity.unregisterNetworkCallback(defaultNetworkCallback)
            } catch (ignored: Exception) {
                // ignored
            }
        }

        tun2SocksService?.stopTun2Socks()
        tun2SocksService = null

        V2RayServiceManager.stopCoreLoop()

        // Kill switch: unexpected drop while enabled, after a real connection -> hold a blocking
        // interface instead of tearing down, so no traffic can leak.
        if (killSwitchEnabled && !userStop && !killSwitchActive && connectionEstablished) {
            if (establishBlockingInterface()) {
                killSwitchActive = true
                NotificationManager.showKillSwitchNotification()
                MessageUtil.sendMsg2UI(this, AppConfig.MSG_STATE_KILL_SWITCH_ON, "")
                return
            }
        }

        val wasBlocking = killSwitchActive
        teardownComplete = true
        killSwitchActive = false
        connectionEstablished = false
        if (wasBlocking) {
            MessageUtil.sendMsg2UI(this, AppConfig.MSG_STATE_KILL_SWITCH_OFF, "")
        }

        if (isForced) {
            //stopSelf has to be called ahead of mInterface.close(). otherwise v2ray core cannot be stooped
            //It's strage but true.
            //This can be verified by putting stopself() behind and call stopLoop and startLoop
            //in a row for several times. You will find that later created v2ray core report port in use
            //which means the first v2ray core somehow failed to stop and release the port.
            stopSelf()

            try {
                mInterface.close()
            } catch (e: Exception) {
                Log.e(AppConfig.TAG, "Failed to close VPN interface", e)
            }
        }

        // Reset for the next session.
        userInitiatedDisconnect = false
    }

    /**
     * Establishes a "black hole" VPN interface used by the kill switch.
     *
     * It captures the full IPv4/IPv6 route table but runs no tun2socks process, so every packet
     * entering the tunnel is silently dropped. The app's own package is excluded so the user can
     * still reach the panel/servers to reconnect.
     *
     * @return True if the blocking interface was established successfully.
     */
    private fun establishBlockingInterface(): Boolean {
        try {
            mInterface.close()
        } catch (ignored: Exception) {
            // ignored
        }

        return try {
            val builder = Builder()
            builder.setMtu(AppConfig.VPN_MTU)
            builder.addAddress("10.255.0.1", 30)
            builder.addRoute("0.0.0.0", 0)
            try {
                builder.addAddress("fdfe:dcba:9876::1", 126)
                builder.addRoute("::", 0)
            } catch (e: Exception) {
                Log.e(AppConfig.TAG, "Kill switch: failed to add IPv6 route", e)
            }
            // Let our own app bypass the block so the user can reconnect from inside Firenet.
            try {
                builder.addDisallowedApplication(BuildConfig.APPLICATION_ID)
            } catch (e: PackageManager.NameNotFoundException) {
                Log.e(AppConfig.TAG, "Kill switch: failed to exclude self", e)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                builder.setMetered(false)
            }
            builder.setSession("Firenet Kill Switch")
            mInterface = builder.establish()!!
            Log.i(AppConfig.TAG, "Kill switch active: traffic blocked")
            true
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Kill switch: failed to establish blocking interface", e)
            false
        }
    }
}

