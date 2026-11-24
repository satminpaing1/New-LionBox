package com.v2ray.ang

import com.v2ray.ang.util.Socks5Endpoint
import com.v2ray.ang.util.TunnelLatencyProbe
import com.v2ray.ang.util.TunnelProbeOutcome
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.DataInputStream
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicReference

class TunnelLatencyProbeTest {

    @Test
    fun `greeting offers only the auth method that will be used`() {
        assertArrayEquals(byteArrayOf(5, 1, 0), TunnelLatencyProbe.greeting(usesAuth = false))
        assertArrayEquals(byteArrayOf(5, 2, 0, 2), TunnelLatencyProbe.greeting(usesAuth = true))
    }

    @Test
    fun `connect request encodes a domain target with big endian port`() {
        assertArrayEquals(
            byteArrayOf(5, 1, 0, 3, "www.gstatic.com".length.toByte()) + "www.gstatic.com".toByteArray() + byteArrayOf(0x01, 0xBB.toByte()),
            TunnelLatencyProbe.connectRequest("www.gstatic.com", 443),
        )
    }

    @Test
    fun `connect request encodes an ipv4 target without a name`() {
        assertArrayEquals(
            byteArrayOf(5, 1, 0, 1, 127, 0, 0, 1, 0x1F, 0x90.toByte()),
            TunnelLatencyProbe.connectRequest("127.0.0.1", 8080),
        )
    }

    @Test
    fun `auth request carries both credentials`() {
        assertArrayEquals(
            byteArrayOf(1, 4) + "user".toByteArray() + byteArrayOf(3) + "pas".toByteArray(),
            TunnelLatencyProbe.authRequest("user", "pas"),
        )
    }

    @Test
    fun `socks reply codes map to readable reasons`() {
        assertEquals("connection refused", TunnelLatencyProbe.socksReplyReason(0x05))
        assertEquals("host unreachable", TunnelLatencyProbe.socksReplyReason(0x04))
        assertEquals("unknown socks error 0x9", TunnelLatencyProbe.socksReplyReason(0x09))
    }

    @Test
    fun `http status line parsing rejects noise`() {
        assertEquals(204, TunnelLatencyProbe.parseStatusCode("HTTP/1.1 204 No Content"))
        assertEquals(200, TunnelLatencyProbe.parseStatusCode("HTTP/1.0 200 OK"))
        assertEquals(null, TunnelLatencyProbe.parseStatusCode("garbage"))
        assertEquals(null, TunnelLatencyProbe.parseStatusCode("HTTP/1.1 abc OK"))
    }

    @Test
    fun `probe returns a delay through a socks5 inbound`() {
        val request = AtomicReference<String>()
        withSocksServer(request = request) { port ->
            val outcome = TunnelLatencyProbe.measure(
                Socks5Endpoint("127.0.0.1", port),
                "http://www.gstatic.com/generate_204",
            )
            val success = outcome as? TunnelProbeOutcome.Success
            assertTrue(success != null, "expected success, got $outcome")
            assertTrue(success!!.delayMillis >= 0)
        }
        assertTrue(request.get().startsWith("GET /generate_204 HTTP/1.1"))
        assertTrue(request.get().contains("Host: www.gstatic.com"))
    }

    @Test
    fun `probe reports the socks refusal reason`() {
        withSocksServer(replyCode = 0x05) { port ->
            val outcome = TunnelLatencyProbe.measure(Socks5Endpoint("127.0.0.1", port), "http://example.com/")
            assertTrue(outcome is TunnelProbeOutcome.Failure)
            assertTrue((outcome as TunnelProbeOutcome.Failure).reason.contains("connection refused"), outcome.reason)
        }
    }

    @Test
    fun `probe reports a missing inbound instead of a silent dash`() {
        val port = ServerSocket(0).use { it.localPort }
        val outcome = TunnelLatencyProbe.measure(Socks5Endpoint("127.0.0.1", port), "http://example.com/")
        assertTrue(outcome is TunnelProbeOutcome.Failure)
        assertTrue((outcome as TunnelProbeOutcome.Failure).reason.contains("unreachable"), outcome.reason)
    }

    @Test
    fun `probe reports authentication required by the inbound`() {
        withSocksServer(requireAuth = true) { port ->
            val outcome = TunnelLatencyProbe.measure(Socks5Endpoint("127.0.0.1", port), "http://example.com/")
            assertTrue(outcome is TunnelProbeOutcome.Failure)
            assertTrue((outcome as TunnelProbeOutcome.Failure).reason.contains("requires authentication"), outcome.reason)
        }
    }

    @Test
    fun `probe authenticates when credentials are configured`() {
        withSocksServer(requireAuth = true) { port ->
            val outcome = TunnelLatencyProbe.measure(
                Socks5Endpoint("127.0.0.1", port, username = "user", password = "secret"),
                "http://example.com/",
            )
            assertTrue(outcome is TunnelProbeOutcome.Success, "expected success, got $outcome")
        }
    }

    /** Minimal SOCKS5 inbound that answers one CONNECT and one HTTP request. */
    private fun withSocksServer(
        requireAuth: Boolean = false,
        replyCode: Int = 0,
        request: AtomicReference<String>? = null,
        block: (Int) -> Unit,
    ) {
        ServerSocket(0).use { server ->
            val worker = Thread {
                server.accept().use { client ->
                    client.soTimeout = 5_000
                    val input = DataInputStream(client.getInputStream())
                    val output = client.getOutputStream()
                    val greeting = ByteArray(2)
                    input.readFully(greeting)
                    val methods = ByteArray(greeting[1].toInt() and 0xFF)
                    input.readFully(methods)
                    if (requireAuth) {
                        output.write(byteArrayOf(5, 2))
                        output.flush()
                        val head = ByteArray(2)
                        input.readFully(head)
                        val user = ByteArray(head[1].toInt() and 0xFF)
                        input.readFully(user)
                        val passLength = input.read()
                        input.readFully(ByteArray(passLength))
                        output.write(byteArrayOf(1, 0))
                        output.flush()
                    } else {
                        output.write(byteArrayOf(5, 0))
                        output.flush()
                    }
                    val head = ByteArray(4)
                    input.readFully(head)
                    when (head[3].toInt() and 0xFF) {
                        1 -> input.readFully(ByteArray(4))
                        3 -> input.readFully(ByteArray(input.read()))
                        4 -> input.readFully(ByteArray(16))
                    }
                    input.readFully(ByteArray(2))
                    output.write(byteArrayOf(5, replyCode.toByte(), 0, 1, 0, 0, 0, 0, 0, 0))
                    output.flush()
                    if (replyCode == 0) {
                        val text = StringBuilder()
                        while (!text.endsWith("\r\n\r\n")) {
                            val value = input.read()
                            if (value < 0) break
                            text.append(value.toChar())
                        }
                        request?.set(text.toString())
                        output.write("HTTP/1.1 204 No Content\r\nContent-Length: 0\r\n\r\n".toByteArray())
                        output.flush()
                    }
                }
            }
            worker.isDaemon = true
            worker.start()
            try {
                block(server.localPort)
            } finally {
                worker.join(5_000)
            }
        }
    }
}
