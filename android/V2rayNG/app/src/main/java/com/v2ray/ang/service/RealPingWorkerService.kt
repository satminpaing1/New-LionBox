package com.v2ray.ang.service

import android.content.Context
import com.v2ray.ang.core.CoreConfigManager
import com.v2ray.ang.core.CoreNativeManager
import com.v2ray.ang.dto.RealPingEvent
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.extension.isComplexType
import com.v2ray.ang.extension.isNotNullEmpty
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.handler.SpeedtestManager
import com.v2ray.ang.util.LatencyProbe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.Executors

internal object RealPingExecutionLimiter {
    private val customConfigMutex = Mutex()

    suspend fun <T> run(configType: EConfigType, block: () -> T): T {
        // Custom profiles bypass speed-test trimming and start complete Xray configs.
        // Parallel teardown can abort the native probe process, so serialize their
        // JNI measurements globally across batches.
        return if (configType == EConfigType.CUSTOM) {
            customConfigMutex.withLock { block() }
        } else {
            block()
        }
    }
}

/**
 * Worker that runs a batch of real-ping tests independently.
 * Each batch owns its own CoroutineScope/dispatcher and can be cancelled separately.
 *
 * Every server is measured with a real delay: an HTTP request to the configured test URL is sent
 * through that server's own outbound, so a key whose tunnel, TLS/WS handshake or remote worker is
 * dead reports a timeout instead of a TCP-connect time.
 *
 * @param concurrency probes in flight at once; 0 uses the user's real-ping setting.
 * @param probeTimeoutMillis per-server budget; 0 keeps the legacy unbounded probe. A positive
 * budget also trims the fallback URL chain to the primary URL so one server cannot burn the
 * whole budget on retries.
 */
class RealPingWorkerService(
    private val context: Context,
    private val guids: List<String>,
    private val concurrency: Int = 0,
    private val probeTimeoutMillis: Long = 0L,
    private val onEvent: (RealPingEvent) -> Unit = {}
) {
    private val job = SupervisorJob()
    private val parallel = if (concurrency > 0) concurrency else SettingsManager.getRealPingConcurrency()
    private val dispatcher = Executors.newFixedThreadPool(parallel).asCoroutineDispatcher()
    private val scope = CoroutineScope(job + dispatcher + CoroutineName("RealPingBatchWorker"))

    fun start() {
        scope.launch {
            try {
                ProbeBatchRunner(
                    guids = guids,
                    concurrency = parallel,
                    timeoutMillis = probeTimeoutMillis,
                ).run(
                    onResult = { guid, result ->
                        if (scope.isActive) {
                            onEvent(RealPingEvent.Result(guid, result))
                        }
                    },
                    onProgress = { left, total ->
                        if (scope.isActive) {
                            onEvent(RealPingEvent.Progress("$left / $total"))
                        }
                    },
                    probe = { guid -> startRealPing(guid) },
                )
                if (scope.isActive) {
                    onEvent(RealPingEvent.Finish("0"))
                }
            } catch (_: CancellationException) {
                // If cancelled, don't send finish event to avoid confusion
            } finally {
                close()
            }
        }
    }

    fun cancel() {
        job.cancel()
    }

    private fun close() {
        try {
            dispatcher.close()
        } catch (_: Throwable) {
            // ignore
        }
    }

    private suspend fun startRealPing(guid: String): Long {
        val retFailure = -1L

        val config = MmkvManager.decodeServerConfig(guid) ?: return retFailure
        if (!config.configType.isComplexType()
            && config.configType != EConfigType.HYSTERIA2
            && config.configType != EConfigType.WIREGUARD
            && config.alpn?.startsWith("h3") != true
            && config.server.isNotNullEmpty()
            && config.serverPort?.toIntOrNull() != null
        ) {
            val url = config.server.orEmpty()
            val port = config.serverPort.orEmpty().toInt()
            val tcpTime = SpeedtestManager.socketConnectTime(url, port, 1000)
            if (tcpTime <= -1L) {
                return retFailure
            }
        }

        val configResult = CoreConfigManager.getV2rayConfig4Speedtest(context, guid)
        if (!configResult.status) {
            return retFailure
        }
        val maxUrls = if (probeTimeoutMillis > 0L) 1 else 3
        return RealPingExecutionLimiter.run(config.configType) {
            LatencyProbe.measure(
                SettingsManager.getDelayTestUrl(),
                SettingsManager.getDelayTestUrl(true),
                maxUrls,
                probeTimeoutMillis,
            ) { url ->
                if (!scope.isActive) throw CancellationException()
                CoreNativeManager.measureOutboundDelay(configResult.content, url)
            }
        }
    }
}
