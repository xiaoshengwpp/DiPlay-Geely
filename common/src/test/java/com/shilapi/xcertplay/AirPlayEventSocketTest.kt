package com.shilapi.xcertplay

import com.shilapi.xcertplay.airplay.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.ServerSocket
import java.net.Socket
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class AirPlayEventSocketTest {
    @Test fun authenticatedEventSocketDisablesNagleBeforeReading() = acceptEvent(authenticated = true)

    @Test fun noDelayDoesNotBypassPairVerification() = acceptEvent(authenticated = false)

    private fun acceptEvent(authenticated: Boolean) {
        val accepted = mock(Socket::class.java)
        val server = mock(ServerSocket::class.java)
        `when`(server.accept()).thenReturn(accepted)
        `when`(accepted.tcpNoDelay).thenReturn(true)
        `when`(accepted.getInputStream()).thenReturn(ByteArrayInputStream(ByteArray(0)))
        `when`(accepted.getOutputStream()).thenReturn(ByteArrayOutputStream())
        val session = AirPlaySession(Socket(),
            AirPlayConfig("test", "test", "", "1", AirPlayDisplayConfig(800, 480)),
            AirPlayIdentity(ByteArray(32), ByteArray(32), "test"), PairingStore(), null,
            object : AirPlaySessionListener {}, object : AirPlayMediaHandler {})
        if (authenticated) {
            val pairVerify = session.javaClass.getDeclaredField("pairVerify").apply { isAccessible = true }.get(session)
            pairVerify.javaClass.getDeclaredField("sharedSecret").apply { isAccessible = true }
                .set(pairVerify, ByteArray(32) { (it + 1).toByte() })
        }
        try {
            session.javaClass.getDeclaredMethod("acceptEvent", ServerSocket::class.java)
                .apply { isAccessible = true }.invoke(session, server)
            val ordered = inOrder(accepted)
            ordered.verify(accepted).setSoLinger(true, 0)
            ordered.verify(accepted).setTcpNoDelay(true)
            if (authenticated) ordered.verify(accepted).getInputStream()
            else {
                verify(accepted, never()).getInputStream()
                verify(accepted, never()).getOutputStream()
                assertFalse(session.sendCommand(mapOf("type" to "test")))
            }
        } finally { session.close() }
        verify(accepted, atLeastOnce()).close()
    }
}
