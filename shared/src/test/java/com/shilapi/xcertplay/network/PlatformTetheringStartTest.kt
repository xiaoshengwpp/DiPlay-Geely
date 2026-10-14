package com.shilapi.xcertplay.network

import com.shilapi.xcertplay.network.fakebinder.FakeConnectivity
import com.shilapi.xcertplay.network.fakebinder.fakeCalls
import com.shilapi.xcertplay.network.fakebinder.fakeProxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.lang.reflect.InvocationTargetException

class PlatformTetheringStartTest {
    private val candidate = PlatformTetheringStart.Candidate(
        arrayOf(Int::class.javaPrimitiveType!!, Any::class.java, Boolean::class.javaPrimitiveType!!, String::class.java),
    ) { receiver, packageName -> arrayOf(0, receiver, false, packageName) }

    @Test
    fun onTheJvmTheProxyClassAloneCannotCallItsPublicMethod() {
        val proxy = fakeProxy()
        val method = proxy.javaClass.getMethod("startTethering", *candidate.types)
        try {
            method.invoke(proxy, 0, null, false, "pkg")
            fail("A public method of a non-public class must not be callable through that class")
        } catch (_: IllegalAccessException) {
        }
    }

    @Test
    fun theInterfaceMethodReachesTheProxy() {
        val proxy = fakeProxy()
        val failures = mutableListOf<String>()
        val log = mutableListOf<String>()
        val receiver = Any()

        assertTrue(PlatformTetheringStart.invoke(proxy, listOf(FakeConnectivity::class.java, proxy.javaClass),
            listOf(candidate), receiver, "com.example", failures, log::add))
        assertEquals(listOf(listOf(0, receiver, false, "com.example")), fakeCalls(proxy))
        assertTrue(failures.isEmpty())
        assertTrue(log.single().contains("FakeConnectivity.startTethering/4"))
    }

    @Test
    fun theProxyClassIsUsedWithAccessWhenNoInterfaceIsAvailable() {
        val proxy = fakeProxy()
        val failures = mutableListOf<String>()
        val log = mutableListOf<String>()

        assertTrue(PlatformTetheringStart.invoke(proxy, listOf(proxy.javaClass), listOf(candidate),
            null, "com.example", failures, log::add))
        assertEquals(1, fakeCalls(proxy).size)
        assertTrue(failures.single().contains("IllegalAccessException"))
        assertTrue(log.single().endsWith("(accessible)"))
    }

    @Test
    fun aMissingMethodIsReportedWithoutACall() {
        val proxy = fakeProxy()
        val failures = mutableListOf<String>()
        val wrong = PlatformTetheringStart.Candidate(arrayOf(Int::class.javaPrimitiveType!!)) { _, _ -> arrayOf(0) }

        assertFalse(PlatformTetheringStart.invoke(proxy, listOf(FakeConnectivity::class.java), listOf(wrong),
            null, "com.example", failures) {})
        assertTrue(failures.single().contains("NoSuchMethodException"))
        assertTrue(fakeCalls(proxy).isEmpty())
    }

    @Test
    fun aSystemRejectionIsReportedAndRethrown() {
        val proxy = fakeProxy(SecurityException("not allowed"))
        val log = mutableListOf<String>()
        try {
            PlatformTetheringStart.invoke(proxy, listOf(FakeConnectivity::class.java), listOf(candidate),
                null, "com.example", mutableListOf(), log::add)
            fail("The system's answer must reach the caller")
        } catch (error: InvocationTargetException) {
            assertTrue(error.targetException is SecurityException)
        }
        assertTrue(log.single().contains("rejected by the system: SecurityException: not allowed"))
    }

    @Test
    fun theAppTetheringCallIsNeverOfferedInThisProduct() {
        for (sdk in listOf(0, 22, 23, 25, 28, 29, 30, 34, 37)) {
            assertFalse("platform tethering offered on API $sdk", PlatformTetheringStart.supported(sdk))
        }
        assertFalse(PlatformTetheringStart.supported())
        assertFalse(CarHotspotTethering.appStartSupported())
    }
}

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [28], manifest = org.robolectric.annotation.Config.NONE)
class PlatformTetheringStartBoundaryTest {
    @Test
    fun productionEntryPointRejectsBeforeAccessingPlatformOrCallbacks() {
        val context = object : android.content.ContextWrapper(null) {
            override fun getApplicationContext(): android.content.Context = error("application context accessed")
            override fun getPackageName(): String = error("package name accessed")
            override fun getSystemService(name: String): Any = error("platform service accessed")
        }
        val receiver = object : android.os.ResultReceiver(null) {
            override fun onReceiveResult(resultCode: Int, resultData: android.os.Bundle?) {
                error("platform result callback invoked")
            }
        }
        repeat(3) {
            try {
                PlatformTetheringStart.start(context, receiver) { error("log callback invoked") }
                fail("Automatic hotspot startup must remain unavailable")
            } catch (error: UnsupportedOperationException) {
                assertEquals("Automatic hotspot startup is unavailable in this product", error.message)
            }
        }
    }
}
