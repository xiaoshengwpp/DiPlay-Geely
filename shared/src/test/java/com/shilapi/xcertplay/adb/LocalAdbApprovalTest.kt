package com.shilapi.xcertplay.adb

import com.shilapi.xcertplay.hud.GeelyVendorPolicy
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.security.KeyPair
import java.security.PrivateKey
import java.security.PublicKey
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import org.junit.Assert.*
import org.junit.Test

class LocalAdbApprovalTest {
    private val keyAccesses = AtomicInteger()
    private fun forbiddenKeyAccess(): Nothing {
        keyAccesses.incrementAndGet()
        error("disabled ADB must not sign or offer a key")
    }
    // Synthetic test keys have no encoded credential material. Any authentication use fails.
    private val key = KeyPair(object : PublicKey {
        override fun getAlgorithm(): String = forbiddenKeyAccess()
        override fun getFormat(): String = forbiddenKeyAccess()
        override fun getEncoded(): ByteArray = forbiddenKeyAccess()
    }, object : PrivateKey {
        override fun getAlgorithm(): String = forbiddenKeyAccess()
        override fun getFormat(): String = forbiddenKeyAccess()
        override fun getEncoded(): ByteArray = forbiddenKeyAccess()
    })

    @Test fun mayAskTrueCannotOpenASocketSignATokenOrOfferAKey() {
        assertFalse(GeelyVendorPolicy.bydAdb)
        DisabledAdbConnectionObserver().use { server ->
            LocalAdb(key, port = server.port).use { adb ->
                assertEquals(LocalAdb.Access.UNSUPPORTED, adb.connect(mayAsk = true))
                assertNull(adb.shell("echo ok"))
                assertNull(adb.openShell("echo ok"))
                assertNoTransport(adb)
            }
            server.assertNoConnection()
        }
        assertEquals(0, keyAccesses.get())
    }

    @Test fun repeatedQuietAndAskingChecksCannotPromptOrRetryApproval() {
        DisabledAdbConnectionObserver().use { server ->
            LocalAdb(key, port = server.port).use { adb ->
                repeat(3) {
                    assertEquals(LocalAdb.Access.UNSUPPORTED, adb.connect(mayAsk = false))
                    assertEquals(LocalAdb.Access.UNSUPPORTED, adb.connect(mayAsk = true))
                }
                assertNoTransport(adb)
            }
            server.assertNoConnection()
        }
        assertEquals(0, keyAccesses.get())
    }

    @Test fun implicitShellAndCancelledClientsCannotEnterApproval() {
        DisabledAdbConnectionObserver().use { server ->
            LocalAdb(key, port = server.port).use { adb ->
                assertNull(adb.shell("echo ok"))
                assertNull(adb.shell("echo ok", 0))
                assertNull(adb.openShell("echo ok"))
                adb.cancelPendingOperations()
                assertEquals(LocalAdb.Access.UNSUPPORTED, adb.connect(mayAsk = true))
                assertNoTransport(adb)
            }
            server.assertNoConnection()
        }
        assertEquals(0, keyAccesses.get())
    }

    private fun assertNoTransport(adb: LocalAdb) {
        for (name in listOf("socket", "input", "output")) {
            assertNull(name, LocalAdb::class.java.getDeclaredField(name).apply { isAccessible = true }.get(adb))
        }
    }
}

/**
 * Observes forbidden socket creation without running an ADB handshake or waiting for a key.
 * Unexpected connections are immediately closed, so a regression cannot stall on server reads.
 * Closing the observer always closes the listener and joins its bounded, non-daemon thread.
 */
internal class DisabledAdbConnectionObserver : AutoCloseable {
    private val server = ServerSocket(0, 1, InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1)))
        .apply { soTimeout = 100 }
    val port = server.localPort
    private val closed = AtomicBoolean()
    private val connected = CountDownLatch(1)
    private val failure = AtomicReference<Throwable?>()
    private val acceptor = thread(name = "test-disabled-adb-listener", isDaemon = false) {
        while (!closed.get()) {
            try {
                server.accept().use { connected.countDown() }
            } catch (_: SocketTimeoutException) {
                // The short timeout also bounds shutdown if close and accept race.
            } catch (error: SocketException) {
                if (!closed.get()) failure.compareAndSet(null, error)
                break
            } catch (error: Throwable) {
                failure.compareAndSet(null, error)
                break
            }
        }
    }

    fun assertNoConnection() {
        assertFalse("disabled LocalAdb opened a socket; no auth/key offer is permitted",
            connected.await(100, TimeUnit.MILLISECONDS))
        assertNull(failure.get())
    }

    override fun close() {
        closed.set(true)
        server.close()
        acceptor.join(1_000)
        assertFalse("test listener did not shut down", acceptor.isAlive)
        assertNull(failure.get())
    }
}
