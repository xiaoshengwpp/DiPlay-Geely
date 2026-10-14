package com.shilapi.xcertplay.network

import android.content.Context
import android.os.ResultReceiver
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

/**
 * Disabled compatibility adapter. Generic platform hotspot control is not enabled in this product.
 * The injected reflection helper is retained for offline tests; no service lookup, hidden API
 * exemption, ADB access, permission request, or platform call is wired to the production entry point.
 */
@Suppress("UNUSED_PARAMETER")
internal object PlatformTetheringStart {
    class Candidate(val types: Array<Class<*>>, val arguments: (receiver: Any?, packageName: String) -> Array<Any?>)

    fun supported(sdk: Int = 0): Boolean = false

    /** Reject before reading context or invoking any supplied callback. */
    fun start(context: Context, receiver: ResultReceiver, log: (String) -> Unit): Unit =
        throw UnsupportedOperationException("Automatic hotspot startup is unavailable in this product")

    /**
     * Calls the first reachable startTethering on an explicitly supplied service. No platform
     * service is discovered here. A supplied method that throws ([InvocationTargetException]) is
     * reported and rethrown; false means no candidate could be called.
     */
    internal fun invoke(
        service: Any,
        owners: List<Class<*>>,
        candidates: List<Candidate>,
        receiver: Any?,
        packageName: String,
        failures: MutableList<String>,
        log: (String) -> Unit,
    ): Boolean {
        for (owner in owners) for (candidate in candidates) {
            val label = "${owner.name}.startTethering/${candidate.types.size}"
            val method: Method = try {
                owner.getMethod("startTethering", *candidate.types)
            } catch (error: ReflectiveOperationException) {
                failures += "$label: ${describe(error)}"
                continue
            } catch (error: SecurityException) {
                failures += "$label: ${describe(error)}"
                continue
            }
            val arguments = candidate.arguments(receiver, packageName)
            for (forceAccess in listOf(false, true)) {
                try {
                    if (forceAccess) method.isAccessible = true
                    method.invoke(service, *arguments)
                    log("car hotspot start: called $label${if (forceAccess) " (accessible)" else ""}")
                    return true
                } catch (error: InvocationTargetException) {
                    log("car hotspot start: $label rejected by the system: ${describe(error.targetException)}")
                    throw error
                } catch (error: IllegalAccessException) {
                    failures += "$label${if (forceAccess) " (accessible)" else ""}: ${describe(error)}"
                } catch (error: SecurityException) {
                    failures += "$label${if (forceAccess) " (accessible)" else ""}: ${describe(error)}"
                }
            }
        }
        return false
    }

    internal fun describe(error: Throwable): String {
        val cause = (error as? InvocationTargetException)?.targetException ?: error.cause
        return error.javaClass.simpleName + (error.message?.let { ": ${it.take(160)}" } ?: "") +
            (cause?.takeIf { it !== error }?.let { " <- ${it.javaClass.simpleName}" } ?: "")
    }
}
