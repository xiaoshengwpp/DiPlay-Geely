package com.shilapi.xcertplay

import com.shilapi.xcertplay.airplay.AirPlayDisplayConfig
import java.util.concurrent.ExecutorService
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class VirtualClusterDisplayTest {
    private lateinit var activity: CarPlayHostActivity

    @Before fun setUp() {
        activity = Robolectric.buildActivity(CarPlayHostActivity::class.java).get()
        AirPlayPersistence.saveClusterMapEnabled(activity, false)
    }

    @After fun tearDown() {
        MapMirrors.streamAspect = MapMirrors.PHYSICAL_STREAM_ASPECT
        for (name in listOf("teardownExecutor", "airPlayCommandExecutor")) {
            val executor = activity.javaClass.getDeclaredField(name).apply { isAccessible = true }
                .get(activity) as ExecutorService
            executor.shutdownNow()
        }
    }

    @Test fun virtualStreamRemainsOptIn() { assertNull(config()) }

    @Test fun forgedClusterEnableDoesNotNegotiateOrChangeMirrorAspect() {
        AirPlayPersistence.saveClusterMapEnabled(activity, true)
        MapMirrors.streamAspect = MapMirrors.PHYSICAL_STREAM_ASPECT
        assertNull(config())
        assertEquals(MapMirrors.PHYSICAL_STREAM_ASPECT, MapMirrors.streamAspect, 0.00001)
    }

    private fun config(): AirPlayDisplayConfig? = activity.javaClass
        .getDeclaredMethod("clusterDisplayConfig").apply { isAccessible = true }.invoke(activity) as AirPlayDisplayConfig?
}
