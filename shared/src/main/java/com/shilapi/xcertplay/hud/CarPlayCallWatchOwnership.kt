@file:Suppress("UNUSED_PARAMETER")

package com.shilapi.xcertplay.hud

import java.io.File

/** No-op compatibility interface: no ownership journal, filesystem lock, callback or helper process. */
internal class CarPlayCallWatchOwnership(directory: File = File("/data/local/tmp")) {
    fun claim(path: String, action: (File) -> Unit) = Unit
    fun update(path: String, action: (File) -> Unit): Boolean = false
    fun retire(path: String, action: () -> Unit): Boolean = false
    fun retireOrRetry(path: String, action: () -> Unit): Boolean = false
    fun prepare(path: String, appPid: String, alive: (String) -> Boolean = { true }): Boolean = false
    fun markWatcherReady(path: String, appPid: String, watcherPid: String): Boolean = false
    fun watcherReady(path: String, appPid: String, alive: (String) -> Boolean): Boolean = false
    fun claimReady(path: String, appPid: String, alive: (String) -> Boolean, action: (File) -> Unit): Boolean = false
    fun cancelPrepared(path: String): Boolean = false
    fun retireStaged(path: String, action: () -> Unit): Boolean = false
    fun clearWatcherReady(path: String, watcherPid: String) = Unit
    companion object {
        fun newToken(packageName: String): String = ""
    }
}
