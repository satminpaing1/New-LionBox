package com.v2ray.ang.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.system.measureTimeMillis

class ProbeBatchRunnerTest {

    @Test
    fun fastResultEndsTheBatchWithoutWaitingForSlowServers() = runBlocking {
        val guids = (1..200).map { "server-$it" }
        val started = AtomicInteger(0)
        val inFlight = AtomicInteger(0)
        val peak = AtomicInteger(0)
        val results = mutableMapOf<String, Long>()
        var fastSeen = false

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val elapsed = measureTimeMillis {
            val job = scope.launch {
                ProbeBatchRunner(guids, concurrency = 15, timeoutMillis = 1_500L).run(
                    onResult = { guid, delay ->
                        results[guid] = delay
                        if (delay in 1..250L) fastSeen = true
                    },
                    probe = { guid ->
                        started.incrementAndGet()
                        peak.accumulateAndGet(inFlight.incrementAndGet(), ::maxOf)
                        try {
                            if (guid == "server-7") {
                                delay(40)
                                90L
                            } else {
                                // Dead servers that never finish inside the budget.
                                delay(5_000)
                                5_000L
                            }
                        } finally {
                            inFlight.decrementAndGet()
                        }
                    },
                )
            }
            while (!fastSeen) delay(5)
            // Auto-connect cancels the sweep as soon as a fast server answers.
            job.cancelAndJoin()
        }

        println("AUTO-CONNECT early exit: elapsed=${elapsed}ms started=${started.get()} peakConcurrency=${peak.get()}")
        assertTrue(elapsed < 500, "expected an early connect under 500ms, measured ${elapsed}ms")
        assertTrue(started.get() < guids.size, "expected the sweep to stop early, started ${started.get()}")
        assertTrue(peak.get() <= 15, "expected at most 15 probes in flight, saw ${peak.get()}")
        assertEquals(90L, results["server-7"])
    }

    @Test
    fun slowServerIsReportedAsFailureInsideItsBudget() = runBlocking {
        val results = mutableMapOf<String, Long>()
        val elapsed = measureTimeMillis {
            ProbeBatchRunner(listOf("dead", "alive"), concurrency = 2, timeoutMillis = 150L).run(
                onResult = { guid, delay -> results[guid] = delay },
                probe = { guid ->
                    if (guid == "dead") {
                        delay(5_000)
                        5_000L
                    } else {
                        60L
                    }
                },
            )
        }

        println("AUTO-CONNECT timeout: elapsed=${elapsed}ms results=$results")
        assertEquals(-1L, results["dead"])
        assertEquals(60L, results["alive"])
        assertTrue(elapsed < 1_000, "a 150ms budget must not wait for the dead probe, measured ${elapsed}ms")
    }

    @Test
    fun probesAreCappedAtTheConfiguredConcurrency() = runBlocking {
        val inFlight = AtomicInteger(0)
        val peak = AtomicInteger(0)

        ProbeBatchRunner((1..60).map { "s$it" }, concurrency = 15).run(
            onResult = { _, _ -> },
            probe = {
                peak.accumulateAndGet(inFlight.incrementAndGet(), ::maxOf)
                delay(20)
                inFlight.decrementAndGet()
                100L
            },
        )

        assertEquals(15, peak.get())
    }
}
