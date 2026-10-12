package com.shilapi.xcertplay.adb

import java.io.ByteArrayInputStream
import java.math.BigInteger
import java.net.ServerSocket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.interfaces.RSAPublicKey
import java.util.Base64
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test

class LocalAdbTest {
    private val key by lazy { KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair() }

    @Test
    fun packetsRoundTripWithChecksumAndMagic() {
        val bytes = AdbPacket(AdbPacket.OPEN, 1, 0, "shell:id\u0000".toByteArray()).encode()
        val header = ByteBuffer.wrap(bytes, 0, 24).order(ByteOrder.LITTLE_ENDIAN)

        assertEquals(AdbPacket.OPEN, header.int)
        header.int; header.int
        assertEquals(9, header.int)
        assertEquals("shell:id\u0000".toByteArray().sumOf { it.toInt() }, header.int)
        assertEquals(AdbPacket.OPEN.inv(), header.int)
        val read = AdbPacket.read(ByteArrayInputStream(bytes))
        assertArrayEquals("shell:id\u0000".toByteArray(), read.payload)
    }

    @Test
    fun publicKeyUsesTheAdbdLayout() {
        val message = String(AdbKeys.publicKeyMessage(key.public), Charsets.UTF_8)
        assertTrue(message.endsWith(" diplay@headunit\u0000"))
        val blob = ByteBuffer.wrap(Base64.getDecoder().decode(message.substringBefore(' '))).order(ByteOrder.LITTLE_ENDIAN)
        val n = (key.public as RSAPublicKey).modulus

        assertEquals(524, blob.capacity())
        assertEquals(64, blob.int)
        // n0inv * n == -1 (mod 2^32)
        val r32 = BigInteger.ONE.shiftLeft(32)
        assertEquals(r32 - BigInteger.ONE, BigInteger.valueOf(blob.int.toLong() and 0xffffffffL).multiply(n).mod(r32))
        assertEquals(n, littleEndian(blob, 256))
        assertEquals(BigInteger.ONE.shiftLeft(4096).mod(n), littleEndian(blob, 256))
        assertEquals(65537, blob.int)
    }

    @Test
    fun tokenSigningStillUsesTheAdbdRsaDigestInfo() {
        val token = ByteArray(20) { it.toByte() }
        val signed = AdbKeys.sign(token, key.private)
        val digestInfo = byteArrayOf(0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e,
            0x03, 0x02, 0x1a, 0x05, 0x00, 0x04, 0x14)
        val verifier = Signature.getInstance("NONEwithRSA")
        verifier.initVerify(key.public)
        verifier.update(digestInfo + token)
        assertTrue(verifier.verify(signed))
        verifier.initVerify(key.public)
        verifier.update(digestInfo + token.copyOf().apply { this[0] = 99 })
        assertFalse(verifier.verify(signed))
    }

    @Test
    fun disabledProfileCannotConnectOrRunShellEvenWithAKnownKey() {
        DisabledAdbConnectionObserver().use { server ->
            LocalAdb(key, port = server.port).use { adb ->
                assertEquals(LocalAdb.Access.UNSUPPORTED, adb.connect(mayAsk = false))
                assertNull(adb.shell("service call autoservice 5"))
                assertNull(adb.shell("id", 30_000))
                assertNull(adb.openShell("id"))
                assertNoTransport(adb)
            }
            server.assertNoConnection()
        }
    }

    @Test
    fun disabledProfileWinsBeforeInvalidAddressOrTimeoutValidation() {
        LocalAdb(key, host = "not-an-adb-host.invalid", port = -1).use { adb ->
            assertEquals(LocalAdb.Access.UNSUPPORTED, adb.connect(mayAsk = true))
            assertNull(adb.shell("id", -1))
            assertNull(adb.openShell("id"))
            assertNoTransport(adb)
        }
    }

    @Test
    fun closedPortIsStillUnsupportedInsteadOfAConnectionAttempt() {
        val port = ServerSocket(0).use { it.localPort }
        LocalAdb(key, port = port).use { adb ->
            assertEquals(LocalAdb.Access.UNSUPPORTED, adb.connect(mayAsk = true))
            assertNoTransport(adb)
        }
    }

    @Test
    fun cancellationAndCloseCannotCauseAnImplicitReconnect() {
        DisabledAdbConnectionObserver().use { server ->
            LocalAdb(key, port = server.port).use { adb ->
                assertNull(adb.shell("cmd connectivity help"))
                adb.cancelPendingOperations()
                assertEquals(LocalAdb.Access.UNSUPPORTED, adb.connect(mayAsk = false))
                assertNull(adb.shell("cmd connectivity start-tethering wifi"))
                assertNull(adb.openShell("id"))
                adb.close()
                assertEquals(LocalAdb.Access.UNSUPPORTED, adb.connect(mayAsk = true))
                assertNoTransport(adb)
            }
            server.assertNoConnection()
        }
    }

    private fun assertNoTransport(adb: LocalAdb) {
        for (name in listOf("socket", "input", "output")) {
            assertNull(name, LocalAdb::class.java.getDeclaredField(name).apply { isAccessible = true }.get(adb))
        }
        assertEquals(1, LocalAdb::class.java.getDeclaredField("nextStreamId").apply { isAccessible = true }.get(adb))
    }

    private fun littleEndian(buffer: ByteBuffer, size: Int): BigInteger {
        val bytes = ByteArray(size).also { buffer.get(it) }
        return BigInteger(1, bytes.reversedArray())
    }

}
