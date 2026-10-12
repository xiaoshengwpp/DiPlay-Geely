package com.shilapi.xcertplay.hud

import java.io.File

/** No-op compatibility handle. No filesystem lock or worker ownership implementation is shipped. */
internal class AmbientWorkerOwnerLock private constructor() : AutoCloseable {
    override fun close() = Unit
    companion object {
        @Suppress("UNUSED_PARAMETER")
        fun tryAcquire(path: File): AmbientWorkerOwnerLock? = null
    }
}
