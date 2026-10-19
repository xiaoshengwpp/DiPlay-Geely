package com.shilapi.xcertplay.network

import android.content.ContextWrapper
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class CarHotspotConfigReadTest {
    @Test fun everySdkRejectsWithoutAccessingContextOrLoggingCredentials() {
        // Any delegated Context operation would fail: there is no base context.
        val context = ContextWrapper(null)
        for (sdk in listOf(25, 28, 29, 30, 33, 35)) {
            val outcome = CarHotspotConfigRead.read(context, sdk) { error("Unexpected diagnostic callback") }
            assertEquals(CarHotspotConfigRead.Reason.UNSUPPORTED_SDK, outcome.reason)
            assertNull(outcome.credentials)
            assertNull(outcome.access)
        }
    }
}
