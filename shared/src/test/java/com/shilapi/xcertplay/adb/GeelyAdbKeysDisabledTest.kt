package com.shilapi.xcertplay.adb

import android.content.Context
import android.content.ContextWrapper
import java.io.File
import java.security.KeyPair
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class GeelyAdbKeysDisabledTest {
    private var contextAccesses = 0
    private fun rejectContextAccess(): Nothing {
        contextAccesses++
        error("Disabled ADB must not access context or storage")
    }
    private val context = object : ContextWrapper(null) {
        override fun getApplicationContext(): Context = rejectContextAccess()
        override fun getNoBackupFilesDir(): File = rejectContextAccess()
        override fun getFilesDir(): File = rejectContextAccess()
    }

    @Test fun loadRejectsBeforeAccessingContextOrStorage() {
        val failure = assertThrows(IllegalStateException::class.java) { AdbKeys.load(context) }
        assertEquals("Local ADB is disabled for the Geely profile", failure.message)
        assertEquals(0, contextAccesses)
    }

    @Test fun existingCallContractAndRepeatedCallsRemainDisabled() {
        val loader: (Context) -> KeyPair = AdbKeys::load
        repeat(3) {
            assertThrows(IllegalStateException::class.java) { loader(context) }
        }
        assertEquals(0, contextAccesses)
    }
}
