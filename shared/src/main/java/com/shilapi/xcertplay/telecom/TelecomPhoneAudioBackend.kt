@file:Suppress("UNUSED_PARAMETER", "unused")

package com.shilapi.xcertplay.telecom

import android.content.Context
import android.telecom.Connection
import android.telecom.ConnectionRequest
import android.telecom.ConnectionService
import android.telecom.DisconnectCause
import android.telecom.PhoneAccountHandle
import java.util.concurrent.CountDownLatch

/** Compatibility interfaces only: no Telecom registration, placement or active connections. */
internal class TelecomPhoneAudioBackend(context: Context) : PhoneAudioBackend {
    override fun start(): Boolean = false
    private fun place(): Boolean = false
    override fun stop() = Unit
    override fun isActive(): Boolean = false
}
internal object PhoneAudioCall {
    private var created: CountDownLatch? = null
    private var connection: Connection? = null
    fun expect(): CountDownLatch = CountDownLatch(0)
    fun isActive(): Boolean = false
    fun create(): Connection = disabledConnection()
    fun failed() = Unit
    fun released(owner: Connection) = Unit
    fun end() = Unit
}
class PhoneAudioConnectionService : ConnectionService() {
    override fun onCreateOutgoingConnection(connectionManagerPhoneAccount: PhoneAccountHandle?, request: ConnectionRequest?): Connection = disabledConnection()
    override fun onCreateIncomingConnection(connectionManagerPhoneAccount: PhoneAccountHandle?, request: ConnectionRequest?): Connection = disabledConnection()
    override fun onCreateOutgoingConnectionFailed(connectionManagerPhoneAccount: PhoneAccountHandle?, request: ConnectionRequest?) = Unit
    override fun onCreateIncomingConnectionFailed(connectionManagerPhoneAccount: PhoneAccountHandle?, request: ConnectionRequest?) = Unit
}
private fun disabledConnection(): Connection =
    Connection.createFailedConnection(DisconnectCause(DisconnectCause.ERROR, "Phone audio route disabled for this product"))
