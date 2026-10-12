package com.shilapi.xcertplay.hud

import com.shilapi.xcertplay.hud.CarPlayCallWrites.Result
import org.junit.Assert.*
import org.junit.Test

class CarPlayCallWritesTest {
    private val writes = listOf(
        CarPlayCallWrite("audio", 0, 1),
        CarPlayCallWrite("car", 1, 0),
        CarPlayCallWrite("bt", 2, 5),
        CarPlayCallWrite("state", 1, 2),
    )

    @Test fun pureWriteValuesRetainCallAndIdleStates() {
        assertEquals(listOf("audio", "car", "bt", "state"), writes.map { it.label })
        assertEquals(listOf(0, 1, 2, 1), writes.map { it.call })
        assertEquals(listOf(1, 0, 5, 2), writes.map { it.idle })
    }

    @Test fun disabledGeelyProfileNeverRunsWritesNameOrCompensation() {
        assertFalse(GeelyVendorPolicy.bydCluster)
        assertFalse(CarPlayCallWrites.apply(writes,
            write = { _, _, _ -> error("BYD call write or compensation must not execute") },
            name = { error("BYD caller-name write must not execute") },
        ))
    }

    @Test fun evenAcceptedCallbacksCannotReportASuccessfulVehicleWrite() {
        var invoked = false
        assertFalse(CarPlayCallWrites.apply(writes,
            write = { _, _, _ -> invoked = true; Result.DONE },
            name = { invoked = true; Result.DONE },
        ))
        assertFalse(invoked)
    }

    @Test fun missingAndRefusedResponsesCannotStartCompensation() {
        for (outcome in Result.values()) {
            val log = mutableListOf<String>()
            assertFalse(CarPlayCallWrites.apply(writes,
                write = { step, value, undo -> log += "${step.label}:$value:$undo"; outcome },
                name = { log += "name"; outcome },
            ))
            assertTrue(log.isEmpty())
        }
    }

    @Test fun emptyWritesCannotBypassTheBoundaryForTheCallerName() {
        assertFalse(CarPlayCallWrites.apply(emptyList(),
            write = { _, _, _ -> error("unexpected write") },
            name = { error("empty write list must not reach name callback") },
        ))
    }

    @Test fun disabledWriteCannotMutateAnExistingCallState() {
        val values = writes.associate { it.label to it.idle }.toMutableMap()
        assertFalse(CarPlayCallWrites.apply(writes, { step, value, _ ->
            values[step.label] = value
            Result.REFUSED
        }, { Result.DONE }))
        assertEquals(writes.associate { it.label to it.idle }, values)
    }
}
