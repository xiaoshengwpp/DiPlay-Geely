package com.shilapi.xcertplay.adb

import java.io.Closeable
import java.io.IOException
import java.net.SocketTimeoutException
import java.security.KeyPair

/**
 * Compatibility interface for the unsupported local ADB feature in the Geely build.
 * Construction and every live entry point are inert: no keys, sockets, or protocol connection.
 * The injected interactive response helper remains available for in-memory protocol tests.
 */
@Suppress("UNUSED_PARAMETER")
class LocalAdb(
    key: KeyPair,
    host: String = "127.0.0.1",
    port: Int = 5555,
) : Closeable {
    enum class Access { READY, NOT_APPROVED, UNREACHABLE, UNSUPPORTED }

    @Synchronized
    fun connect(mayAsk: Boolean): Access = Access.UNSUPPORTED

    fun shell(command: String): String? = null

    @Synchronized
    fun shell(command: String, readTimeoutMillis: Int): String? = null

    @Synchronized
    fun openShell(command: String): InteractiveShell? = null

    /** One request at a time, with strict stream ownership, byte/packet limits and a total deadline. */
    class InteractiveShell internal constructor(
        private val local: Int,
        private val remote: Int,
        private val sendPacket: (AdbPacket) -> Unit,
        private val readPacket: () -> AdbPacket,
        private val setTimeout: (Int) -> Unit,
        private val closeConnection: () -> Unit,
    ) : Closeable {
        @Volatile private var closed = false
        private companion object {
            val deadlines = java.util.concurrent.Executors.newSingleThreadScheduledExecutor { runnable ->
                Thread(runnable, "diplay-adb-stream-deadline").apply { isDaemon = true }
            }
        }
        @Synchronized
        fun exchangeBounded(request: String, timeoutMillis: Int = 5000): String? {
            if (closed || request.toByteArray(Charsets.UTF_8).size > 1024 || '\n' in request || '\r' in request) return null
            val boundedMillis = timeoutMillis.coerceIn(1, 10000)
            // Socket read timeout alone restarts per read; an independent deadline also bounds drips.
            val deadlineTask = deadlines.schedule({
                closed = true
                closeConnection()
            }, boundedMillis.toLong(), java.util.concurrent.TimeUnit.MILLISECONDS)
            return try {
                val deadline = System.nanoTime() + boundedMillis * 1_000_000L
                sendPacket(AdbPacket(AdbPacket.WRTE, local, remote, "$request\n".toByteArray(Charsets.UTF_8)))
                var acknowledged = false
                val response = InteractiveResponseBuffer()
                var packets = 0
                while (!acknowledged || !response.complete) {
                    if (++packets > 64) throw IOException("interactive packet limit")
                    val remaining = (deadline - System.nanoTime()) / 1_000_000L
                    if (remaining <= 0) throw SocketTimeoutException("interactive deadline")
                    setTimeout(remaining.toInt().coerceAtLeast(1))
                    val packet = readPacket()
                    if (packet.arg0 != remote || packet.arg1 != local) throw IOException("wrong interactive stream")
                    when (packet.command) {
                        AdbPacket.OKAY -> acknowledged = true
                        AdbPacket.WRTE -> {
                            sendPacket(AdbPacket(AdbPacket.OKAY, local, remote, ByteArray(0)))
                            response.accept(packet.payload)
                        }
                        AdbPacket.CLSE -> throw IOException("interactive stream ended")
                        else -> throw IOException("unexpected interactive packet")
                    }
                }
                response.text()
            } catch (_: IOException) {
                close()
                null
            } finally {
                deadlineTask.cancel(false)
            }
        }
        @Synchronized
        override fun close() {
            if (closed) return
            closed = true
            runCatching { sendPacket(AdbPacket(AdbPacket.CLSE, local, remote, ByteArray(0))) }
            closeConnection()
        }
    }

    @Synchronized
    override fun close() = Unit

    fun cancelPendingOperations() = Unit
}

/** ADB payload boundaries need not coincide with UTF-8 characters or response lines. */
internal class InteractiveResponseBuffer {
    private val bytes = java.io.ByteArrayOutputStream()
    var complete = false
        private set
    fun accept(payload: ByteArray) {
        if (complete || bytes.size() + payload.size > 4096) throw IOException("interactive response limit")
        bytes.write(payload)
        val text = bytes.toString("UTF-8")
        if (text == "done\n" || text.endsWith("\ndone\n")) complete = true
    }
    fun text(): String = bytes.toString("UTF-8").removeSuffix("done\n").trimEnd('\n', '\r')
}
