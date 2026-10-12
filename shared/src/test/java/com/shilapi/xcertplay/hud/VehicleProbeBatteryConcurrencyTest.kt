package com.shilapi.xcertplay.hud

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Concurrent callers cannot turn disabled reads or probe results into hardware readiness. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class VehicleProbeBatteryConcurrencyTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Test fun concurrentReadsAndProbeCandidatesStayFailClosed() {
        val reads = AtomicInteger()
        val workers = Executors.newFixedThreadPool(4)
        try {
            val tasks = (1..20).map { index -> workers.submit {
                val candidate = capabilities(index.toDouble())
                assertNull(BydBatteryStatus.read(context) {
                    reads.incrementAndGet()
                    BydBatteryReading(25.0, 150, 20.0, false)
                })
                BydVehicleFieldStore.save(context, candidate)
                assertFalse(BydVehicleFieldStore.replaceAutomatically(context, null, candidate).saved)
                assertFalse(BydVehicleFieldStore.replaceAnyway(context, null, candidate))
                BydBatteryStatus.publishProbeReading(context, BydBatteryReading(90.0, 150, 20.0, false))
                assertNull(BydVehicleFieldStore.load(context))
                assertNull(BydBatteryStatus.snapshot())
            } }
            tasks.forEach { it.get(5, TimeUnit.SECONDS) }
            assertEquals(0, reads.get())
        } finally {
            workers.shutdownNow()
            assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS))
        }
    }

    private fun capabilities(percent: Double) = BydVehicleCapabilities(
        fields = BydVehicleField.entries.associateWith { field ->
            BydFieldProbeResult(field, true, BydVehicleFieldStore.defaultAddress(field), when (field) {
                BydVehicleField.SOC -> percent
                BydVehicleField.RANGE -> 150.0
                BydVehicleField.REMAINING_KWH -> 20.0
                else -> 1.0
            })
        }, catalogAvailable = true, firmwareKey = BydVehicleFieldStore.firmwareKey())
}
