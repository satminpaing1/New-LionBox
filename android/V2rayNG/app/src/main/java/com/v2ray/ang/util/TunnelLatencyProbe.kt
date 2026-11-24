package com.v2ray.ang.util

import java.io.DataInputStream
import java.io.IOException
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.util.concurrent.CancellationException
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/** Local SOCKS5 inbound the running core exposes to the app process. */
internal data class Socks5Endpoint(
    val host: String,
    val port: Int,
    val username: String? = null,
    val password: String? = null,
)

/** Successful probe needs a delay; failures carry the reason verbatim for the log. */
internal sealed class TunnelProbeOutcome {
    data class Success(val delayMillis: Long) : TunnelProbeOutcome()
    data class Failure(val reason: String) : TunnelProbeOutcome()
}

/**
 * Measures latency end to end through the tunnel that is already running: the probe speaks SOCKS5
 * to the core's local inbound, so the request takes the exact path a connected device uses
 * (inbound -> outbound -> server) instead of relying on a dialer inside the core.
 *
 * Loopback sockets are never routed into the TUN interface, so a probe to the local inbound
 * cannot loop back into the tunnel it is measuring.
 */
internal object TunnelLatencyProbe {
    const val DEFAULT_TIMEOUT_MILLIS = 6_000L
    private const val MIN_TIMEOUT_MILLIS = 5_000L
    private const val MAX_TIMEOUT_MILLIS = 8_000L

    fun measure(
        endpoint: Socks5Endpoint,
        url: String,
        timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
    ): TunnelProbeOutcome {
        val target = parseTarget(url)
            ?: return TunnelProbeOutcome.Failure("unsupported latency test url: $url")
        val budget = timeoutMillis.coerceIn(MIN_TIMEOUT_MILLIS, MAX_TIMEOUT_MILLIS)
        val deadline = System.nanoTime() + budget * 1_000_000L
        var raw: Socket? = null
        var channel: Socket? = null
        return try {
            val started = System.nanoTime()
            raw = Socket()
            raw.connect(InetSocketAddress(endpoint.host, endpoint.port), remainingMillis(deadline))
            raw.soTimeout = remainingMillis(deadline)
            raw.tcpNoDelay = true
            answerGreeting(raw, endpoint, deadline)
            requestConnect(raw, target, deadline)
            val elapsedSocket = raw
            channel = if (target.tls) {
                val ssl = SSLSocketFactory.getDefault() as SSLSocketFactory
                (ssl.createSocket(elapsedSocket, target.host, target.port, true) as SSLSocket).also { socket ->
                    socket.soTimeout = remainingMillis(deadline)
                    socket.sslParameters = socket.sslParameters.apply {
                        endpointIdentificationAlgorithm = "HTTPS"
                    }
                    socket.startHandshake()
                }
            } else {
                elapsedSocket
            }
            channel.getOutputStream().apply {
                write(httpRequest(target))
                flush()
            }
            val statusLine = readLine(channel.getInputStream())
            val delay = (System.nanoTime() - started) / 1_000_000L
            val code = parseStatusCode(statusLine)
            if (code == null) {
                TunnelProbeOutcome.Failure("unexpected http response: ${statusLine.take(60)}")
            } else {
                TunnelProbeOutcome.Success(delay)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            TunnelProbeOutcome.Failure(describe(e, endpoint, budget))
        } finally {
            closeQuietly(channel)
            if (channel !== raw) closeQuietly(raw)
        }
    }

    private fun describe(e: Exception, endpoint: Socks5Endpoint, budget: Long): String = when (e) {
        is java.net.SocketTimeoutException ->
            "timed out after ${budget}ms via ${endpoint.host}:${endpoint.port}"
        is java.net.ConnectException ->
            "local socks inbound ${endpoint.host}:${endpoint.port} unreachable: ${e.message ?: "connection refused"}"
        else -> "${e.javaClass.simpleName}: ${e.message ?: "no detail"}"
    }

    private fun remainingMillis(deadline: Long): Int =
        ((deadline - System.nanoTime()) / 1_000_000L).coerceIn(1L, MAX_TIMEOUT_MILLIS).toInt()

    /** RFC 1928 greeting plus an optional RFC 1929 username/password exchange. */
    private fun answerGreeting(socket: Socket, endpoint: Socks5Endpoint, deadline: Long) {
        val usesAuth = !endpoint.username.isNullOrEmpty()
        socket.getOutputStream().apply {
            write(greeting(usesAuth))
            flush()
        }
        val input = DataInputStream(socket.getInputStream())
        val version = readFully(socket, input, 1, deadline)[0].toInt() and 0xFF
        if (version != SOCKS_VERSION) throw IOException("socks version mismatch: $version")
        val method = readFully(socket, input, 1, deadline)[0].toInt() and 0xFF
        when (method) {
            METHOD_NO_AUTH -> Unit
            METHOD_USER_PASS -> {
                if (!usesAuth) throw IOException("socks inbound requires authentication")
                writeFully(
                    socket.getOutputStream(),
                    authRequest(endpoint.username.orEmpty(), endpoint.password.orEmpty()),
                )
                val reply = readFully(socket, input, 2, deadline)
                val status = reply[1].toInt() and 0xFF
                if (status != 0) throw IOException("socks authentication rejected (status $status)")
            }
            METHOD_NONE -> throw IOException("socks inbound accepted no offered auth method")
            else -> throw IOException("socks inbound chose unsupported auth method $method")
        }
    }

    private fun requestConnect(socket: Socket, target: ProbeTarget, deadline: Long) {
        writeFully(socket.getOutputStream(), connectRequest(target.host, target.port))
        val input = DataInputStream(socket.getInputStream())
        val head = readFully(socket, input, 4, deadline)
        if ((head[0].toInt() and 0xFF) != SOCKS_VERSION) {
            throw IOException("socks reply version mismatch: ${head[0].toInt() and 0xFF}")
        }
        val rep = head[1].toInt() and 0xFF
        if (rep != 0) throw IOException("socks connect failed: ${socksReplyReason(rep)}")
        when (head[3].toInt() and 0xFF) {
            ATYP_IPV4 -> readFully(socket, input, 4 + 2, deadline)
            ATYP_IPV6 -> readFully(socket, input, 16 + 2, deadline)
            ATYP_DOMAIN -> {
                val length = readFully(socket, input, 1, deadline)[0].toInt() and 0xFF
                readFully(socket, input, length + 2, deadline)
            }
            else -> throw IOException("socks reply has unknown address type ${head[3].toInt() and 0xFF}")
        }
    }

    private fun readLine(input: java.io.InputStream): String {
        val builder = StringBuilder()
        while (true) {
            val value = input.read()
            if (value < 0) break
            if (value == '\n'.code) break
            if (value != '\r'.code) builder.append(value.toChar())
            if (builder.length > 512) break
        }
        if (builder.isEmpty()) throw IOException("empty http response")
        return builder.toString()
    }

    private fun readFully(socket: Socket, input: DataInputStream, size: Int, deadline: Long): ByteArray {
        val buffer = ByteArray(size)
        var read = 0
        while (read < size) {
            // Re-arm each read from the overall deadline so the whole probe stays inside its budget.
            socket.soTimeout = remainingMillis(deadline)
            val count = input.read(buffer, read, size - read)
            if (count < 0) throw IOException("socks connection closed after $read/$size bytes")
            read += count
        }
        return buffer
    }

    private fun writeFully(output: OutputStream, bytes: ByteArray) {
        output.write(bytes)
        output.flush()
    }

    private fun closeQuietly(socket: Socket?) {
        try {
            socket?.close()
        } catch (_: Exception) {
        }
    }

    // ---- wire format, kept pure for unit tests ----

    fun greeting(usesAuth: Boolean): ByteArray =
        if (usesAuth) {
            byteArrayOf(SOCKS_VERSION.toByte(), 0x02, METHOD_NO_AUTH.toByte(), METHOD_USER_PASS.toByte())
        } else {
            byteArrayOf(SOCKS_VERSION.toByte(), 0x01, METHOD_NO_AUTH.toByte())
        }

    fun authRequest(username: String, password: String): ByteArray {
        val user = username.toByteArray(Charsets.UTF_8)
        val pass = password.toByteArray(Charsets.UTF_8)
        // RFC 1929: VER | ULEN | UNAME | PLEN | PASSWD
        return byteArrayOf(0x01, user.size.toByte()) + user +
            byteArrayOf(pass.size.toByte()) + pass
    }

    fun connectRequest(host: String, port: Int): ByteArray {
        val portBytes = byteArrayOf((port shr 8 and 0xFF).toByte(), (port and 0xFF).toByte())
        val ipv4 = parseIpv4(host)
        val payload = when {
            ipv4 != null -> byteArrayOf(ATYP_IPV4.toByte()) + ipv4
            else -> {
                val name = host.toByteArray(Charsets.UTF_8)
                byteArrayOf(ATYP_DOMAIN.toByte(), name.size.toByte()) + name
            }
        }
        return byteArrayOf(SOCKS_VERSION.toByte(), CMD_CONNECT.toByte(), 0x00) + payload + portBytes
    }

    fun socksReplyReason(rep: Int): String = when (rep) {
        0x01 -> "general socks server failure"
        0x02 -> "connection not allowed by ruleset"
        0x03 -> "network unreachable"
        0x04 -> "host unreachable"
        0x05 -> "connection refused"
        0x06 -> "TTL expired"
        0x07 -> "command not supported"
        0x08 -> "address type not supported"
        else -> "unknown socks error 0x${rep.toString(16)}"
    }

    fun parseStatusCode(statusLine: String): Int? {
        val parts = statusLine.trim().split(' ')
        if (parts.size < 2 || !parts[0].startsWith("HTTP/")) return null
        return parts[1].toIntOrNull()?.takeIf { it in 100..599 }
    }

    fun httpRequest(target: ProbeTarget): ByteArray =
        ("GET ${target.path} HTTP/1.1\r\n" +
            "Host: ${target.host}\r\n" +
            "User-Agent: LionBox/1.0\r\n" +
            "Accept: */*\r\n" +
            "Cache-Control: no-cache\r\n" +
            "Connection: close\r\n\r\n").toByteArray(Charsets.UTF_8)

    fun parseTarget(url: String): ProbeTarget? {
        val parsed = try {
            URL(url.trim())
        } catch (_: Exception) {
            return null
        }
        val tls = when (parsed.protocol.lowercase()) {
            "https" -> true
            "http" -> false
            else -> return null
        }
        val host = parsed.host
        if (host.isNullOrBlank()) return null
        val port = if (parsed.port > 0) parsed.port else if (tls) 443 else 80
        val path = parsed.file.takeIf { it.isNotBlank() } ?: "/generate_204"
        return ProbeTarget(host, port, path, tls)
    }

    private fun parseIpv4(host: String): ByteArray? {
        val parts = host.split('.')
        if (parts.size != 4) return null
        val octets = parts.map { it.toIntOrNull()?.takeIf { value -> value in 0..255 } ?: return null }
        return ByteArray(4) { octets[it].toByte() }
    }

    internal data class ProbeTarget(
        val host: String,
        val port: Int,
        val path: String,
        val tls: Boolean,
    )

    private const val SOCKS_VERSION = 0x05
    private const val CMD_CONNECT = 0x01
    private const val ATYP_IPV4 = 0x01
    private const val ATYP_DOMAIN = 0x03
    private const val ATYP_IPV6 = 0x04
    private const val METHOD_NO_AUTH = 0x00
    private const val METHOD_USER_PASS = 0x02
    private const val METHOD_NONE = 0xFF
}
