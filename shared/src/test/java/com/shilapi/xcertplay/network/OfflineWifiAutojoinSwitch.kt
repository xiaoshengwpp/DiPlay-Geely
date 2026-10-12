package com.shilapi.xcertplay.network

import android.os.Binder
import android.os.Bundle
import android.os.Parcel
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Test-only adapter for synthetic Wi-Fi objects; never included in the application. */
internal class OfflineWifiAutojoinSwitch(
    private val wifi: Any,
    listenerType: Class<*>? = null,
    private val queryTimeoutMillis: Long = 5_000,
) {
    private val methods = wifi.javaClass.methods
    private val setter: Method? = methods.firstOrNull(::isAutojoinSetter)
        ?: methods.firstOrNull { it.name == "enableWifiConnectivityManager" && it.parameterTypes.contentEquals(arrayOf(BOOLEAN)) }
    private val query: Method? = listenerType?.let { type ->
        methods.firstOrNull { it.name == "queryAutojoinGlobal" && it.parameterTypes.contentEquals(arrayOf(type)) }
    }

    /** The framework method this firmware offers, or null when there is none. */
    val method: String? get() = setter?.name

    fun set(enabled: Boolean): WifiScanSwitchResult {
        val target = setter ?: return WifiScanSwitchResult.UNSUPPORTED
        try {
            // A refused query refuses the change too; any other query failure only skips the check.
            val before = try { read() } catch (denied: SecurityException) { throw denied } catch (_: Exception) { null }
            if (!enabled && before == false) return WifiScanSwitchResult.ALREADY_OFF
            call(target, *arguments(target, enabled))
        } catch (_: SecurityException) {
            // Every release checks the caller before it changes anything.
            return WifiScanSwitchResult.DENIED
        }
        // A clean void reply; Android 13+ also confirms the new state.
        val after = runCatching { read() }.getOrNull()
        return if (after == null || after == enabled) WifiScanSwitchResult.DONE else WifiScanSwitchResult.UNKNOWN
    }

    /** Android 13+: the current setting; null when this release cannot report it in time. */
    internal fun read(): Boolean? {
        val method = query ?: return null
        val type = method.parameterTypes[0]
        val answer = AtomicReference<Boolean>()
        val done = CountDownLatch(1)
        val callback = object : Binder() {
            override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                if (code != FIRST_CALL_TRANSACTION) return super.onTransact(code, data, reply, flags)
                data.enforceInterface(type.name)
                answer.set(data.readInt() != 0)
                done.countDown()
                return true
            }
        }.apply { attachInterface(null, type.name) }
        val listener = Proxy.newProxyInstance(type.classLoader ?: javaClass.classLoader, arrayOf(type)) { proxy, called, args ->
            when (called.name) {
                "asBinder" -> callback
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.firstOrNull()
                "toString" -> "DiPlayAutojoinListener"
                else -> null
            }
        }
        call(method, listener)
        return if (done.await(queryTimeoutMillis, TimeUnit.MILLISECONDS)) answer.get() else null
    }

    private fun call(method: Method, vararg args: Any) {
        try {
            method.invoke(wifi, *args)
        } catch (failure: InvocationTargetException) {
            throw failure.targetException
        }
    }

    private fun arguments(method: Method, enabled: Boolean): Array<Any> =
        Array(method.parameterTypes.size) { index ->
            when (method.parameterTypes[index]) {
                BOOLEAN -> enabled
                String::class.java -> SHELL_PACKAGE
                else -> Bundle()
            }
        }

    private fun isAutojoinSetter(method: Method): Boolean {
        val types = method.parameterTypes
        return method.name == "allowAutojoinGlobal" && types.firstOrNull() == BOOLEAN &&
            types.drop(1).all { it == String::class.java || it == Bundle::class.java }
    }

    private companion object {
        const val SHELL_PACKAGE = "com.android.shell"
        val BOOLEAN: Class<*> = java.lang.Boolean.TYPE
    }
}
