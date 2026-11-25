package com.v2ray.ang

import com.v2ray.ang.core.CoreLifecycleGate
import com.v2ray.ang.core.CoreLifecycleState
import com.v2ray.ang.core.CoreStartFailure
import com.v2ray.ang.core.SessionEpoch
import com.v2ray.ang.handler.SessionPortPolicy
import com.v2ray.ang.util.FreePortAllocator
import com.v2ray.ang.util.Utils
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.ServerSocket
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class UtilsTest {

    @Test
    fun test_parseInt() {
        assertEquals(Utils.parseInt("1234"), 1234)
    }

    @Test
    fun test_isIpAddress() {
        assertFalse(Utils.isIpAddress("114.113.112.266"))
        assertFalse(Utils.isIpAddress("666.666.666.666"))
        assertFalse(Utils.isIpAddress("256.0.0.0"))
        assertFalse(Utils.isIpAddress("::ffff:127.0.0.0.1"))
        assertFalse(Utils.isIpAddress("baidu.com"))
        assertFalse(Utils.isIpAddress(""))

        assertTrue(Utils.isIpAddress("127.0.0.1"))
        assertTrue(Utils.isIpAddress("127.0.0.1:80"))
        assertTrue(Utils.isIpAddress("0.0.0.0/0"))
        assertTrue(Utils.isIpAddress("::1"))
        assertTrue(Utils.isIpAddress("[::1]:80"))
        assertTrue(Utils.isIpAddress("2605:2700:0:3::4713:93e3"))
        assertTrue(Utils.isIpAddress("[2605:2700:0:3::4713:93e3]:80"))
        assertTrue(Utils.isIpAddress("::ffff:192.168.173.22"))
        assertTrue(Utils.isIpAddress("[::ffff:192.168.173.22]:80"))
        assertTrue(Utils.isIpAddress("1::"))
        assertTrue(Utils.isIpAddress("::"))
        assertTrue(Utils.isIpAddress("::/0"))
        assertTrue(Utils.isIpAddress("10.24.56.0/24"))
        assertTrue(Utils.isIpAddress("2001:4321::1"))
        assertTrue(Utils.isIpAddress("240e:1234:abcd:12::6666"))
        assertTrue(Utils.isIpAddress("240e:1234:abcd:12::/64"))
    }

    @Test
    fun test_IsIpInCidr() {
        assertTrue(Utils.isIpInCidr("192.168.1.1", "192.168.1.0/24"))
        assertTrue(Utils.isIpInCidr("192.168.1.254", "192.168.1.0/24"))
        assertFalse(Utils.isIpInCidr("192.168.2.1", "192.168.1.0/24"))

        assertTrue(Utils.isIpInCidr("10.0.0.0", "10.0.0.0/8"))
        assertTrue(Utils.isIpInCidr("10.255.255.255", "10.0.0.0/8"))
        assertFalse(Utils.isIpInCidr("11.0.0.0", "10.0.0.0/8"))

        assertFalse(Utils.isIpInCidr("invalid-ip", "192.168.1.0/24"))
        assertFalse(Utils.isIpInCidr("192.168.1.1", "invalid-cidr"))
    }

    @Test
    fun test_FreePortAllocator_returnsPortThatCanBeBound() {
        val port = FreePortAllocator().allocate()

        assertTrue(port in 1..65535)
        assertTrue(FreePortAllocator.isPortFree(port))
    }

    @Test
    fun test_FreePortAllocator_skipsPortHeldByAnotherSocket() {
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { held ->
            val occupied = held.localPort
            var calls = 0
            val allocator = FreePortAllocator(
                pickCandidate = { if (calls++ == 0) occupied else FreePortAllocator.candidate() },
            )

            val allocated = allocator.allocate()

            // The busy port is rejected and the next OS-assigned port is used instead.
            assertNotEquals(occupied, allocated)
            assertTrue(FreePortAllocator.isPortFree(allocated))
        }
    }

    @Test
    fun test_FreePortAllocator_skipsPortHeldOnUdp() {
        val port = FreePortAllocator().allocate()
        DatagramSocket(port, InetAddress.getByName("127.0.0.1")).use {
            // A SOCKS inbound listens on TCP and UDP, so a UDP-only clash must also be rejected.
            assertFalse(FreePortAllocator.isPortFree(port))
        }
    }

    @Test
    fun test_FreePortAllocator_allocatesDistinctSocksAndHttpPorts() {
        val (socks, http) = FreePortAllocator().allocatePair()

        assertNotEquals(socks, http)
        assertTrue(FreePortAllocator.isPortFree(socks))
    }

    @Test
    fun test_FreePortAllocator_throwsWhenEveryCandidateIsBusy() {
        val allocator = FreePortAllocator(
            maxAttempts = 3,
            pickCandidate = { 1 },
            isAvailable = { false },
        )

        assertThrows(IOException::class.java) { allocator.allocate() }
    }

    @Test
    fun test_CoreStartFailure_detectsBindClash() {
        val goError = "startup failed: app/proxyman/inbound: failed to listen TCP on 10808 > " +
            "listen tcp 127.0.0.1:10808: bind: address already in use"

        assertTrue(CoreStartFailure.isAddressInUse(goError))
        assertTrue(CoreStartFailure.isAddressInUse("listen tcp4 127.0.0.1:41234: bind: address already in use"))
        assertFalse(CoreStartFailure.isAddressInUse("Failed to get V2Ray config"))
        assertFalse(CoreStartFailure.isAddressInUse(null))

        // Exactly one retry, on the first attempt only.
        assertTrue(CoreStartFailure.shouldRetryWithNewPort(0, goError))
        assertFalse(CoreStartFailure.shouldRetryWithNewPort(1, goError))
        assertFalse(CoreStartFailure.shouldRetryWithNewPort(0, "no server selected"))
    }

    @Test
    fun test_CoreLifecycleGate_serializesStartAndStop() {
        val gate = CoreLifecycleGate()
        val order = Collections.synchronizedList(mutableListOf<String>())
        val startEntered = CountDownLatch(1)
        val stopEntered = CountDownLatch(1)
        val releaseStart = CountDownLatch(1)

        val starter = Thread {
            gate.runExclusive {
                order.add("start")
                gate.markStarting()
                startEntered.countDown()
                releaseStart.await(5, TimeUnit.SECONDS)
                gate.markRunning()
            }
        }
        starter.start()
        assertTrue(startEntered.await(5, TimeUnit.SECONDS))

        val stopper = Thread {
            gate.runExclusive {
                order.add("stop")
                gate.markStopped()
                stopEntered.countDown()
            }
        }
        stopper.start()

        // The stop must wait for the start to finish instead of racing it.
        assertFalse(stopEntered.await(300, TimeUnit.MILLISECONDS))
        assertEquals(CoreLifecycleState.STARTING, gate.state)

        releaseStart.countDown()
        starter.join(5000)
        stopper.join(5000)
        assertTrue(stopEntered.await(1, TimeUnit.SECONDS))
        assertEquals(listOf("start", "stop"), order)
        assertEquals(CoreLifecycleState.STOPPED, gate.state)
    }

    @Test
    fun test_SessionPortPolicy_keepsPinnedPortsWhileFree() {
        var allocated = false

        val ports = SessionPortPolicy.resolve(
            preferredSocks = 21808,
            preferredHttp = 21809,
            isFree = { true },
            allocateFreePair = {
                allocated = true
                31000 to 31001
            },
        )

        assertEquals(21808 to 21809, ports)
        assertFalse(allocated)
    }

    @Test
    fun test_SessionPortPolicy_replacesPinnedPortHeldByAnotherApp() {
        val heldByOtherApp = 21808

        val ports = SessionPortPolicy.resolve(
            preferredSocks = heldByOtherApp,
            preferredHttp = 21809,
            isFree = { it != heldByOtherApp },
            allocateFreePair = { 31000 to 31001 },
        )

        // The port another VPN may still hold is dropped; the free configured HTTP port is kept.
        assertEquals(31000 to 21809, ports)
    }

    @Test
    fun test_SessionPortPolicy_usesFreshPairWhenPinnedPortsAreBothHeld() {
        val ports = SessionPortPolicy.resolve(
            preferredSocks = 21808,
            preferredHttp = 21809,
            isFree = { false },
            allocateFreePair = { 31000 to 31001 },
        )

        assertEquals(31000 to 31001, ports)
    }

    @Test
    fun test_SessionPortPolicy_keepsEqualPortsForSingleInboundCore() {
        val ports = SessionPortPolicy.resolve(
            preferredSocks = 21808,
            preferredHttp = 21808,
            isFree = { true },
            allocateFreePair = { 31000 to 31001 },
        )

        assertEquals(21808 to 21808, ports)
    }

    @Test
    fun test_FreePortAllocator_keepsConfiguredPortWhileFree() {
        val configured = FreePortAllocator().allocate()

        val (socks, http) = FreePortAllocator().allocatePair(preferredSocks = configured)

        assertEquals(configured, socks)
        assertNotEquals(socks, http)
        assertTrue(FreePortAllocator.isPortFree(http))
    }

    @Test
    fun test_FreePortAllocator_dropsConfiguredPortHeldByAnotherSocket() {
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { held ->
            val occupied = held.localPort

            val (socks, http) = FreePortAllocator().allocatePair(preferredSocks = occupied)

            assertNotEquals(occupied, socks)
            assertTrue(FreePortAllocator.isPortFree(socks))
            assertTrue(FreePortAllocator.isPortFree(http))
        }
    }

    @Test
    fun test_SessionEpoch_newSessionInvalidatesOlderTeardown() {
        val epoch = SessionEpoch()

        val first = epoch.begin()
        assertTrue(epoch.isCurrent(first))

        val second = epoch.begin()
        assertTrue(epoch.isCurrent(second))
        // A revoke teardown launched for the first session must not touch the second one.
        assertFalse(epoch.isCurrent(first))
        assertEquals(second, epoch.current())
    }

}