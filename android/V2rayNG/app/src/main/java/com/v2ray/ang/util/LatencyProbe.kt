package com.v2ray.ang.util

import java.util.concurrent.Callable
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

internal object LatencyProbe {
    /**
     * The native outbound probe blocks in JNI and its HTTP client gives up only after ~12 s, so a
     * budget is enforced by abandoning the call at its deadline instead of waiting for it.
     */
    private const val MAX_CONCURRENT_PROBES = 32

    private val probeSlots = Semaphore(MAX_CONCURRENT_PROBES)
    private val probeExecutor = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "latency-outbound-probe").apply { isDaemon = true }
    }

    /**
     * Probes the configured URLs in order and returns the first non-negative delay.
     *
     * @param maxUrls how many URLs may be tried; a bounded probe uses 1 so a single server
     * cannot spend the whole per-server budget on the fallback chain.
     * @param budgetMillis hard per-server budget. A positive budget caps the wall-clock time:
     * a blocked or dead outbound is reported as a timeout at the deadline even though its native
     * call keeps running until its own client gives up. 0 keeps the unbounded in-process probe.
     */
    fun measure(
        primaryUrl: String,
        fallbackUrl: String,
        maxUrls: Int = 3,
        budgetMillis: Long = 0L,
        probe: (String) -> Long,
    ): Long {
        val urls = listOf(primaryUrl, fallbackUrl, "https://cp.cloudflare.com/generate_204")
            .distinct()
            .take(maxUrls.coerceAtLeast(1))
        val deadline = if (budgetMillis > 0L) System.nanoTime() + budgetMillis * 1_000_000L else 0L
        for (url in urls) {
            val delay = try {
                if (deadline > 0L) {
                    val remaining = (deadline - System.nanoTime()) / 1_000_000L
                    if (remaining <= 0L) return -1L
                    probeWithBudget(remaining, url, probe)
                } else {
                    probe(url)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                -1L
            }
            if (delay >= 0) return delay
        }
        return -1L
    }

    /** Runs [probe] on its own thread and stops waiting at [budgetMillis]. */
    private fun probeWithBudget(budgetMillis: Long, url: String, probe: (String) -> Long): Long {
        val startedAt = System.nanoTime()
        val budget = budgetMillis.coerceAtLeast(1L)
        if (!probeSlots.tryAcquire(budget, TimeUnit.MILLISECONDS)) {
            return -1L
        }
        var future: Future<Long>? = null
        try {
            future = probeExecutor.submit(
                Callable {
                    try {
                        probe(url)
                    } finally {
                        // Held until the native call really returns, so abandoned probes stay
                        // counted against the concurrency cap.
                        probeSlots.release()
                    }
                }
            )
            val remaining = budget - (System.nanoTime() - startedAt) / 1_000_000L
            return future.get(remaining.coerceAtLeast(0L), TimeUnit.MILLISECONDS)
        } catch (_: TimeoutException) {
            // The native call cannot be interrupted; leave it to finish and free its slot.
            return -1L
        } catch (failed: ExecutionException) {
            val cause = failed.cause
            if (cause is CancellationException) throw cause
            return if (cause is Exception) -1L else throw failed
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            return -1L
        } finally {
            if (future == null) probeSlots.release()
        }
    }
}
