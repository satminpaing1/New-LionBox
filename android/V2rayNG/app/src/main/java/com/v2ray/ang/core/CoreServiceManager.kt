package com.v2ray.ang.core

import android.app.Activity
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.os.Build
import android.os.ParcelFileDescriptor
import android.system.OsConstants
import androidx.core.content.ContextCompat
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.contracts.IDialerService
import com.v2ray.ang.contracts.ServiceControl
import com.v2ray.ang.dto.ConnectionTestResult
import com.v2ray.ang.dto.OutboundTrafficStat
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.BrowserDialerMode
import com.v2ray.ang.extension.delay
import com.v2ray.ang.extension.isNotNullEmpty
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.NotificationManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.handler.SpeedtestManager
import com.v2ray.ang.helper.MessageHelper
import com.v2ray.ang.service.DialerNativeService
import com.v2ray.ang.service.DialerWebviewService
import com.v2ray.ang.service.NetworkMonitor
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.SocketProtector
import com.v2ray.ang.util.Socks5Endpoint
import com.v2ray.ang.util.TunnelLatencyProbe
import com.v2ray.ang.util.TunnelProbeOutcome
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.ProcessFinder
import java.lang.ref.SoftReference
import java.net.InetSocketAddress
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/** Upper bound for the native start call; the core is up in well under a second in practice. */
private const val CORE_START_TIMEOUT_MS = 30_000L

/** Upper bound for the native stop call, so a stuck core cannot block the caller forever. */
private const val CORE_STOP_TIMEOUT_MS = 10_000L

object CoreServiceManager {

    private val coreController: CoreController = CoreNativeManager.newCoreController(CoreCallback())
    private val mMsgReceive = ReceiveMessageHandler()
    private var currentConfig: ProfileItem? = null
    private var processFinder: XrayProcessFinder? = null
    private var browserDialer: IDialerService? = null
    private var networkMonitor: NetworkMonitor? = null
    private val runtimeSession = RuntimeSession()
    private var wakeLock: android.os.PowerManager.WakeLock? = null
    private var ipLookupJob: kotlinx.coroutines.Job? = null
    private val runtimeLookupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var underlyingNetwork: android.net.Network? = null
    @Volatile private var runtimeEndpoint: SpeedtestManager.RemoteEndpointInfo? = null
    @Volatile private var ipLookupState = "loading"
    private val runtimeGeneration = java.util.concurrent.atomic.AtomicLong()

    private fun startRuntime(service: Service) {
        if (!runtimeSession.start(android.os.SystemClock.elapsedRealtime())) return
        val power = service.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        wakeLock = power.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "LionBox:VpnRuntime").apply {
            setReferenceCounted(false)
            acquire(12 * 60 * 60 * 1000L)
        }
        refreshRuntimeEndpoint()
    }

    private fun refreshRuntimeEndpoint() {
        val generation = runtimeGeneration.incrementAndGet()
        ipLookupJob?.cancel()
        runtimeEndpoint = null
        ipLookupState = "loading"
        ipLookupJob = runtimeLookupScope.launch(Dispatchers.IO) {
            val endpoint = try {
                SpeedtestManager.getRemoteIPInfo(useDefaultEndpoint = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            withContext(Dispatchers.Main) {
                if (isRunning() && runtimeGeneration.get() == generation) {
                    runtimeEndpoint = endpoint
                    ipLookupState = if (endpoint?.ipAddress != null && countryFlag(endpoint.countryCode) != null) "ready" else "error"
                }
            }
        }
    }

    @Synchronized
    fun publishRuntime(service: Service, downloadBytes: Long, uploadBytes: Long, intervalMillis: Long) {
        val sample = runtimeSession.sample(android.os.SystemClock.elapsedRealtime(), downloadBytes, uploadBytes, intervalMillis) ?: return
        val lock = wakeLock
        if (lock != null && !lock.isHeld) lock.acquire(12 * 60 * 60 * 1000L)
        val manager = service.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = underlyingNetwork ?: manager.activeNetwork
        val capabilities = network?.let { manager.getNetworkCapabilities(it) }
            ?.takeIf { it.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_NOT_VPN) }
        val transport = when {
            capabilities?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true -> "wifi"
            capabilities?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "cellular"
            capabilities?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "ethernet"
            else -> "unknown"
        }
        val endpoint = runtimeEndpoint
        MessageHelper.sendMsg2UI(service, AppConfig.MSG_RUNTIME_TELEMETRY, sample.copy(
            transport = transport,
            ipAddress = endpoint?.ipAddress,
            countryCode = endpoint?.countryCode,
            countryName = endpoint?.countryName ?: endpoint?.country,
            ipLookupState = ipLookupState,
        ))
    }

    @Synchronized
    private fun stopRuntime() {
        runtimeSession.stop()
        runtimeGeneration.incrementAndGet()
        ipLookupJob?.cancel()
        ipLookupJob = null
        runtimeEndpoint = null
        underlyingNetwork = null
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }
    private val connectionTestScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Every start, stop and reload of the native core runs exclusively through this gate, so a
     * rapid Connect/Disconnect can never hold two Xray instances open or start one while the
     * previous one still owns the local port.
     */
    private val coreLifecycle = CoreLifecycleGate()

    /**
     * Single worker for native start/stop. Even if a caller bypassed the gate, the two native calls
     * can never overlap, and the caller waits (bounded) until the core has really exited.
     */
    private val coreWorker = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "lionbox-core-lifecycle").apply { isDaemon = true }
    }

    @Volatile
    private var isReloading = false

    private fun startNativeCore(config: String, tunFd: Int) {
        val task = coreWorker.submit { coreController.startLoop(config, tunFd) }
        try {
            task.get(CORE_START_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (e: ExecutionException) {
            throw e.cause as? Exception ?: e
        } catch (e: TimeoutException) {
            throw IllegalStateException("Core start timed out after ${CORE_START_TIMEOUT_MS}ms")
        }
    }

    /**
     * Waits until the native loop has exited, so its sockets and the local inbound port are
     * released before the caller returns. Bounded, so a stuck core cannot hang the caller.
     */
    private fun stopNativeCore() {
        val task = coreWorker.submit { coreController.stopLoop() }
        try {
            task.get(CORE_STOP_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (e: TimeoutException) {
            LogUtil.e(AppConfig.TAG, "StartCore-Manager: core stop timed out after ${CORE_STOP_TIMEOUT_MS}ms")
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "StartCore-Manager: Failed to stop V2Ray loop", e.cause ?: e)
        }
    }

    /** Tun descriptor the core was started with, null in the proxy only and root run modes. */
    private var currentVpnInterface: ParcelFileDescriptor? = null

    var serviceControl: SoftReference<ServiceControl>? = null
        set(value) {
            field = value
            val service = value?.get()?.getService()
            CoreNativeManager.initCoreEnv(service)
            if (service != null && processFinder == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                processFinder = XrayProcessFinder(service)
                coreController.registerProcessFinder(processFinder)
            }
        }

    /**
     * Checks if the V2Ray service is running.
     * @return True if the service is running, false otherwise.
     */
    fun isRunning() = coreController.isRunning

    /**
     * Gets the name of the currently running server.
     * @return The name of the running server.
     */
    fun getRunningServerName() = currentConfig?.remarks.orEmpty()

    /**
     * Refer to the official documentation for [registerReceiver](https://developer.android.com/reference/androidx/core/content/ContextCompat#registerReceiver(android.content.Context,android.content.BroadcastReceiver,android.content.IntentFilter,int):
     * `registerReceiver(Context, BroadcastReceiver, IntentFilter, int)`.
     * Starts the V2Ray core service.
     */
    fun startCoreLoop(vpnInterface: ParcelFileDescriptor?): Boolean {
        return coreLifecycle.runExclusive {
            if (isRunning()) {
                LogUtil.w(AppConfig.TAG, "StartCore-Manager: Core already running")
                return@runExclusive false
            }

            val service = getService()
            if (service == null) {
                LogUtil.e(AppConfig.TAG, "StartCore-Manager: Service is null")
                return@runExclusive false
            }

            coreLifecycle.markStarting()
            try {
                doStartCoreLoop(service, vpnInterface)
                SocketProtector.protector = { socket -> serviceControl?.get()?.vpnProtect(socket) ?: false }
                coreLifecycle.markRunning()
                true
            } catch (e: Exception) {
                val message = e.message?.takeUnless { it.isBlank() } ?: e.javaClass.simpleName
                // Full technical detail belongs to the Logs tab; the dashboard gets one short line.
                LogUtil.e(AppConfig.TAG, "StartCore-Manager: $message", e)
                MessageHelper.sendMsg2UI(
                    service,
                    AppConfig.MSG_STATE_START_FAILURE,
                    service.getString(R.string.toast_core_start_failed),
                )
                stopCoreLoopLocked()
                NotificationManager.cancelNotification()
                false
            }
        }
    }

    @Throws(Exception::class)
    private fun doStartCoreLoop(service: Service, vpnInterface: ParcelFileDescriptor?) {
        val mFilter = IntentFilter(AppConfig.BROADCAST_ACTION_SERVICE)
        mFilter.addAction(Intent.ACTION_SCREEN_ON)
        mFilter.addAction(Intent.ACTION_SCREEN_OFF)
        mFilter.addAction(Intent.ACTION_USER_PRESENT)
        ContextCompat.registerReceiver(service, mMsgReceive, mFilter, Utils.receiverFlags())

        currentVpnInterface = vpnInterface
        launchCore(service, vpnInterface)
        startNetworkMonitor(service)
    }

    @Throws(Exception::class)
    private fun launchCore(service: Service, vpnInterface: ParcelFileDescriptor?, isReload: Boolean = false) {
        val guid = MmkvManager.getSelectServer() ?: error("No server selected")
        val config = MmkvManager.decodeServerConfig(guid) ?: error("Failed to decode server config")

        LogUtil.i(AppConfig.TAG, "StartCore-Manager: Starting core loop for ${config.remarks}")
        if (!isReload) {
            // A fresh session always gets freshly checked free ports: the previous session's port
            // may still be held by a dying core or by another proxy app.
            SettingsManager.prepareRuntimePorts()
        }
        var result = CoreConfigManager.getV2rayConfig(service, guid)
        LogUtil.d(AppConfig.TAG, result.content)
        if (!result.status) {
            error(result.errorMessage.ifBlank { "Failed to get V2Ray config" })
        }

        currentConfig = config
        var tunFd = vpnInterface?.fd ?: 0
        val dialerMode = BrowserDialerMode.from(config.browserDialerMode)
        val dialerAddr = if (dialerMode != null) {
            "127.0.0.1:${Utils.findRandomFreePort()}"
        } else {
            ""
        }
        if (SettingsManager.isUsingHevTun()) {
            tunFd = 0
        }

        NotificationManager.showNotification(currentConfig)
        if (dialerAddr.isNotNullEmpty()) {
            CoreNativeManager.reconcileBrowserDialer(dialerAddr)
        }

        var attempt = 0
        while (true) {
            try {
                startNativeCore(result.content, tunFd)
                break
            } catch (e: Exception) {
                val message = e.message?.takeUnless { it.isBlank() } ?: e.javaClass.simpleName
                if (!CoreStartFailure.shouldRetryWithNewPort(attempt, message)) throw e

                LogUtil.w(
                    AppConfig.TAG,
                    "StartCore-Manager: local inbound could not bind, retrying on a fresh port: $message",
                )
                SettingsManager.forceFreeRuntimePorts()
                // tun2socks already reads the SOCKS port, so it has to be re-pointed at the new one.
                serviceControl?.get()?.refreshTun2Socks()
                result = CoreConfigManager.getV2rayConfig(service, guid)
                if (!result.status) {
                    error(result.errorMessage.ifBlank { "Failed to get V2Ray config" })
                }
                attempt++
            }
        }

        if (!isRunning()) {
            error("Core failed to start")
        }

        if (browserDialer != null) {
            browserDialer!!.stop()
            browserDialer = null
        }
        when (dialerMode) {
            BrowserDialerMode.OKHTTP -> {
                browserDialer = DialerNativeService()
                browserDialer!!.start(service, dialerAddr)
            }

            BrowserDialerMode.WEBVIEW -> {
                browserDialer = DialerWebviewService()
                browserDialer!!.start(service, dialerAddr)
            }

            else -> {}
        }

        if (!isReload) {
            MessageHelper.sendMsg2UI(service, AppConfig.MSG_STATE_START_SUCCESS, "")
        }
        startRuntime(service)
        if (isReload) refreshRuntimeEndpoint()
        NotificationManager.startSpeedNotification()
        LogUtil.i(AppConfig.TAG, "StartCore-Manager: Core started successfully")
    }

    /**
     * Stops the V2Ray core service.
     * Unregisters broadcast receivers, stops notifications, and shuts down plugins.
     * @return True if the core was stopped successfully, false otherwise.
     */
    fun stopCoreLoop(): Boolean = coreLifecycle.runExclusive { stopCoreLoopLocked() }

    private fun stopCoreLoopLocked(): Boolean {
        stopRuntime()
        connectionTestScope.coroutineContext.cancelChildren()
        SocketProtector.clear()

        coreLifecycle.markStopping()
        try {
            if (isRunning()) {
                stopNativeCore()
            }
        } finally {
            // The next session must allocate again: this port may still be in TIME_WAIT.
            SettingsManager.releaseRuntimePorts()
            coreLifecycle.markStopped()
        }

        val service = getService() ?: return false

        networkMonitor?.unregister()
        networkMonitor = null
        currentVpnInterface = null

        // Close existing browser dialer
        CoreNativeManager.reconcileBrowserDialer("")
        if (browserDialer != null) {
            browserDialer!!.stop()
            browserDialer = null
        }

        MessageHelper.sendMsg2UI(service, AppConfig.MSG_STATE_STOP_SUCCESS, "")
        NotificationManager.cancelNotification()

        try {
            service.unregisterReceiver(mMsgReceive)
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "StartCore-Manager: Failed to unregister receiver", e)
        }

        return true
    }

    /**
     * Subscribes to upstream network changes for whichever run mode is active.
     * All three services share this manager, so the tunnel recovers from a handover in proxy only
     * and root mode as well, not just behind the VPN interface.
     */
    private fun startNetworkMonitor(service: Service) {
        if (networkMonitor != null) return

        val connectivity = service.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        networkMonitor = NetworkMonitor(
            connectivity = connectivity,
            onUnderlyingNetworksChanged = { networks ->
                underlyingNetwork = networks?.firstOrNull()
                serviceControl?.get()?.setUnderlyingNetworks(networks)
            },
            onHandover = { reloadCore() },
        ).also { it.register() }
    }

    /**
     * Restarts the core in place after the upstream network changed: the service, the notification
     * and the VPN interface all stay up, so nothing of this is visible.
     *
     * The config is rebuilt on purpose, outbound server domains are resolved while building it and
     * an address resolved on a network that is gone can be unusable on the new one.
     *
     * @return True if the core is running again.
     */
    private fun reloadCore(): Boolean {
        return coreLifecycle.runExclusive {
            if (isReloading) return@runExclusive false
            val service = getService() ?: return@runExclusive false
            if (!isRunning()) return@runExclusive false

            try {
                val tunFd = currentVpnInterface

                isReloading = true
                connectionTestScope.coroutineContext.cancelChildren()
                LogUtil.i(AppConfig.TAG, "StartCore-Manager: Core reload start...")

                // Wait for the old loop to exit before starting the replacement; the port and the
                // TUN descriptors must be free first.
                coreLifecycle.markStopping()
                stopNativeCore()
                launchCore(service, tunFd, isReload = true)
                coreLifecycle.markRunning()

                LogUtil.i(AppConfig.TAG, "StartCore-Manager: Core reload finished")
                true
            } catch (e: Exception) {
                val message = e.message?.takeUnless { it.isBlank() } ?: e.javaClass.simpleName
                LogUtil.e(AppConfig.TAG, "StartCore-Manager: Failed to reload core: $message", e)
                MessageHelper.sendMsg2UI(
                    service,
                    AppConfig.MSG_STATE_START_FAILURE,
                    service.getString(R.string.toast_core_start_failed),
                )
                false
            } finally {
                isReloading = false
            }
        }
    }

    /**
     * Queries and resets all outbound traffic counters in one core call.
     * Go side format: tag,direction,value;tag,direction,value;
     */
    fun queryAllOutboundTrafficStats(): List<OutboundTrafficStat> {
        // The stats manager is gone once the core stops, querying it then reaches into freed state.
        if (!isRunning()) return emptyList()

        val payload = coreController.queryAllOutboundTrafficStats()

        val result = ArrayList<OutboundTrafficStat>()

        payload.split(';').forEach { entry ->
            if (entry.isBlank()) return@forEach

            val parts = entry.split(',', limit = 3)
            if (parts.size != 3) return@forEach

            val value = parts[2].toLongOrNull() ?: return@forEach

            result.add(
                OutboundTrafficStat(
                    tag = parts[0],
                    direction = parts[1],
                    value = value,
                )
            )
        }
//        LogUtil.d(AppConfig.TAG, "Queried outbound traffic stats: $result")
        return result
    }

    /**
     * Measures the connection delay for the current V2Ray configuration.
     *
     * While the core runs, the probe goes through the tunnel that is already up: SOCKS5 to the
     * core's local inbound and an HTTP GET to the delay test URL, with a hard 5-8 s budget. That
     * path mirrors real device traffic and avoids dialing from inside the core (which could be
     * routed back into the tunnel). The core's own dialer is kept as a fallback.
     *
     * Every failed attempt is written to the log with its exact reason, and the reason is also
     * attached to the result so a timeout is never a silent dash.
     */
    private fun measureV2rayDelay(requestId: String) {
        val service = getService() ?: return
        if (!isRunning() || isReloading) {
            MessageHelper.sendMsg2UI(service, AppConfig.MSG_MEASURE_DELAY_CANCEL, "", requestId)
            return
        }

        connectionTestScope.coroutineContext.cancelChildren()
        connectionTestScope.launch {
            val failures = mutableListOf<String>()
            val tunnelTime = measureThroughActiveTunnel(failures)
            val time = if (tunnelTime >= 0) tunnelTime else measureThroughCore(failures)

            currentCoroutineContext().ensureActive()
            if (time < 0) {
                LogUtil.w(
                    AppConfig.TAG,
                    "Latency test failed while connected: ${failures.joinToString("; ").ifBlank { "unknown reason" }}",
                )
            }
            val endpoint = if (time >= 0) SpeedtestManager.getRemoteIPInfo() else null
            val result = ConnectionTestResult(
                delayMillis = time,
                errorMessage = if (time >= 0) "" else failures.joinToString("; "),
                country = endpoint?.country,
                ipAddress = endpoint?.ipAddress,
            )
            withContext(Dispatchers.Main.immediate) {
                if (isRunning()) {
                    MessageHelper.sendMsg2UI(service, AppConfig.MSG_MEASURE_DELAY_RESULT, result, requestId)
                } else {
                    MessageHelper.sendMsg2UI(service, AppConfig.MSG_MEASURE_DELAY_CANCEL, "", requestId)
                }
            }
        }.invokeOnCompletion { cause ->
            if (cause is CancellationException) {
                MessageHelper.sendMsg2UI(service, AppConfig.MSG_MEASURE_DELAY_CANCEL, "", requestId)
            }
        }
    }

    /**
     * Probes the delay test URLs over the running core's local SOCKS5 inbound.
     * @return The delay in milliseconds, or -1 with the reasons appended to [failures].
     */
    private suspend fun measureThroughActiveTunnel(failures: MutableList<String>): Long {
        val port = SettingsManager.getSocksPort()
        if (port <= 0) {
            failures += "tunnel probe skipped: local socks inbound is disabled"
            return -1L
        }
        val endpoint = Socks5Endpoint(
            host = AppConfig.LOOPBACK,
            port = port,
            username = SettingsManager.getSocksUsername(),
            password = SettingsManager.getSocksPassword(),
        )
        for (url in listOf(SettingsManager.getDelayTestUrl(), SettingsManager.getDelayTestUrl(true))) {
            currentCoroutineContext().ensureActive()
            when (val outcome = TunnelLatencyProbe.measure(endpoint, url)) {
                is TunnelProbeOutcome.Success -> {
                    LogUtil.i(
                        AppConfig.TAG,
                        "Latency test through the active tunnel: ${outcome.delayMillis}ms ($url)",
                    )
                    return outcome.delayMillis
                }
                is TunnelProbeOutcome.Failure -> {
                    val reason = "tunnel probe $url: ${outcome.reason}"
                    failures += reason
                    LogUtil.w(AppConfig.TAG, "Latency test through the active tunnel failed: $reason")
                }
            }
        }
        return -1L
    }

    /**
     * Fallback used when the local inbound cannot be reached: asks the running core to dial.
     * @return The delay in milliseconds, or -1 with the reasons appended to [failures].
     */
    private suspend fun measureThroughCore(failures: MutableList<String>): Long {
        for (url in listOf(SettingsManager.getDelayTestUrl(), SettingsManager.getDelayTestUrl(true))) {
            currentCoroutineContext().ensureActive()
            try {
                val delay = coreController.measureDelay(url)
                if (delay >= 0) return delay
                val reason = "core measureDelay $url: no delay returned"
                failures += reason
                LogUtil.w(AppConfig.TAG, "Latency test through the core failed: $reason")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                val reason = "core measureDelay $url: ${e.javaClass.simpleName}: ${e.message ?: "no detail"}"
                failures += reason
                LogUtil.w(AppConfig.TAG, "Latency test through the core failed: $reason", e)
            }
        }
        return -1L
    }

    /**
     * Gets the current service instance.
     * @return The current service instance, or null if not available.
     */
    private fun getService(): Service? {
        return serviceControl?.get()?.getService()
    }

    /**
     * Core callback handler implementation for handling V2Ray core events.
     * Handles startup, shutdown, socket protection, and status emission.
     */
    private class CoreCallback : CoreCallbackHandler {
        /**
         * Called when V2Ray core starts up.
         * @return 0 for success, any other value for failure.
         */
        override fun startup(): Long {
            LogUtil.i(AppConfig.TAG, "StartCore-Manager: CoreCallback startup")
            return 0
        }

        /**
         * Called when V2Ray core shuts down.
         * @return 0 for success, any other value for failure.
         */
        override fun shutdown(): Long {
            LogUtil.i(AppConfig.TAG, "StartCore-Manager: CoreCallback shutdown")
            return 0
        }

        /**
         * Called when V2Ray core emits status information.
         * @param l Status code.
         * @param s Status message.
         * @return Always returns 0.
         */
        override fun onEmitStatus(l: Long, s: String?): Long {
            LogUtil.i(AppConfig.TAG, "StartCore-Manager: CoreCallback onEmitStatus $s")
            return 0
        }
    }

    /**
     * Process finder implementation for Xray core.
     * Uses ConnectivityManager to find the owning UID of a connection based on network parameters.
     */
    private class XrayProcessFinder(context: Context) : ProcessFinder {
        private val cm: ConnectivityManager? = context.getSystemService(ConnectivityManager::class.java)

        override fun findProcessByConnection(network: String, srcIP: String, srcPort: Long, destIP: String, destPort: Long): Long {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return -1L
            if (cm == null) return -1L
            val proto = when (network) {
                "tcp" -> OsConstants.IPPROTO_TCP
                "udp" -> OsConstants.IPPROTO_UDP
                else -> return -1L
            }

            if (destIP.isBlank() || destPort == 0L) {
                LogUtil.d(AppConfig.TAG, "ProcessFinder: Find $network connection from $srcIP:$srcPort to :$destPort, (no dest)")
                return -1L
            }

            return try {
                val uid = cm.getConnectionOwnerUid(
                    proto,
                    InetSocketAddress(srcIP, srcPort.toInt()),
                    InetSocketAddress(destIP, destPort.toInt())
                ).toLong()
                LogUtil.d(AppConfig.TAG, "ProcessFinder: Find $network connection from $srcIP:$srcPort to $destIP:$destPort, uid=$uid")
                //LogUtil.d(AppConfig.TAG, "ProcessFinder: Find $network connection from $srcIP:$srcPort to $destIP:$destPort, uid=$uid,${PackageUidResolver.uidToPackageName(uid.toString())}")

                uid
            } catch (_: Exception) {
                -1L
            }
        }
    }

    /**
     * Broadcast receiver for handling messages sent to the service.
     * Handles registration, service control, and screen events.
     */
    private class ReceiveMessageHandler : BroadcastReceiver() {
        /**
         * Handles received broadcast messages.
         * Processes service control messages and screen state changes.
         * @param ctx The context in which the receiver is running.
         * @param intent The intent being received.
         */
        override fun onReceive(ctx: Context?, intent: Intent?) {
            val serviceControl = serviceControl?.get() ?: return
            when (intent?.getIntExtra("key", 0)) {
                AppConfig.MSG_REGISTER_CLIENT -> {
                    if (isRunning()) {
                        MessageHelper.sendMsg2UI(serviceControl.getService(), AppConfig.MSG_STATE_RUNNING, "")
                    } else {
                        MessageHelper.sendMsg2UI(serviceControl.getService(), AppConfig.MSG_STATE_NOT_RUNNING, "")
                    }
                }

                AppConfig.MSG_UNREGISTER_CLIENT -> {
                    // nothing to do
                }

                AppConfig.MSG_STATE_START -> {
                    // nothing to do
                }

                AppConfig.MSG_STATE_STOP -> {
                    LogUtil.i(AppConfig.TAG, "StartCore-Manager: Stop service")
                    serviceControl.stopService()
                }

                AppConfig.MSG_STATE_RESTART -> {
                    LogUtil.i(AppConfig.TAG, "StartCore-Manager: Restart service")
                    // The UI and daemon run in separate processes, so acknowledge the active
                    // daemon before stopping it instead of relying on possibly stale UI state.
                    if (isOrderedBroadcast) resultCode = Activity.RESULT_OK

                    val pendingResult = goAsync()
                    CoroutineScope(Dispatchers.Default).launch {
                        try {
                            serviceControl.stopService()
                            delay(500L)
                            LauncherManager.startService(serviceControl.getService())
                        } finally {
                            pendingResult.finish()
                        }
                    }
                }

                AppConfig.MSG_MEASURE_DELAY -> {
                    if (isOrderedBroadcast) resultCode = Activity.RESULT_OK
                    measureV2rayDelay(intent.getStringExtra("content").orEmpty())
                }
            }

            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    LogUtil.i(AppConfig.TAG, "StartCore-Manager: Screen off")
                    NotificationManager.startSpeedNotification()
                }

                Intent.ACTION_SCREEN_ON -> {
                    LogUtil.i(AppConfig.TAG, "StartCore-Manager: Screen on")
                    NotificationManager.startSpeedNotification()
                }
            }
        }
    }
}
