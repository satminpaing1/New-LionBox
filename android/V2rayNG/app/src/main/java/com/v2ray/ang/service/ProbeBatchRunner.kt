package com.v2ray.ang.service

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicInteger

/**
 * Runs real-ping probes for [guids] in parallel, with at most [concurrency] probes in flight and
 * optionally a [timeoutMillis] budget per probe. A probe that runs out of budget, or throws, is
 * reported as [failureMillis] so the caller still gets one result per guid.
 */
internal class ProbeBatchRunner(
    private val guids: List<String>,
    private val concurrency: Int,
    private val timeoutMillis: Long = 0L,
    private val failureMillis: Long = -1L,
) {
    suspend fun run(
        onResult: (guid: String, delayMillis: Long) -> Unit,
        onProgress: (left: Int, total: Int) -> Unit = { _, _ -> },
        probe: suspend (guid: String) -> Long,
    ) {
        val permits = Semaphore(concurrency.coerceAtLeast(1))
        val left = AtomicInteger(guids.size)
        val total = guids.size

        coroutineScope {
            guids.map { guid ->
                async {
                    val result = permits.withPermit {
                        try {
                            if (timeoutMillis > 0L) {
                                withTimeout(timeoutMillis) { probe(guid) }
                            } else {
                                probe(guid)
                            }
                        } catch (_: TimeoutCancellationException) {
                            failureMillis
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Throwable) {
                            failureMillis
                        }
                    }
                    onResult(guid, result)
                    onProgress(left.decrementAndGet(), total)
                }
            }.awaitAll()
        }
    }
}
