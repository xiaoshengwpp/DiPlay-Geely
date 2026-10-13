package com.shilapi.xcertplay

import org.junit.Assert.*
import org.junit.Test

class GeelyDisplayPolicyTest {
    private fun display(id: Int = 4, name: String = "secondary") = GeelyDisplayInfo(
        id, name, 1280, 480, 160, 0, "ON", 8, false, true, true,
    )

    @Test fun noNameOrNumberAutomaticallySelectsOrConfirmsAScreenRole() {
        val displays = listOf(display(1, "HUD"), display(2, "cluster"), display(3, "passenger"))
        val policy = GeelyHudSelectionPolicy()
        assertNull(policy.resolve(displays))
        assertNull(policy.selectedDisplayId())
        assertTrue(displays.all { it.role == "unconfirmed" })
    }

    @Test fun onlyExplicitEligibleSelectionCanResolve() {
        val target = display()
        val policy = GeelyHudSelectionPolicy()
        assertTrue(policy.select(listOf(target), target.displayId))
        assertEquals(target, policy.resolve(listOf(target)))
        policy.clear()
        assertNull(policy.resolve(listOf(target)))
    }

    @Test fun defaultPrivateUnexposedInvalidOffUnknownAndBadMetricsCannotBeChosen() {
        val target = display()
        val rejected = listOf(
            target.copy(isDefault = true), target.copy(publicPresentation = false),
            target.copy(valid = false), target.copy(state = "OFF"), target.copy(state = "UNKNOWN"),
            target.copy(state = "DOZE"), target.copy(width = 0), target.copy(height = -1),
            target.copy(densityDpi = 0),
        )
        rejected.forEach {
            assertFalse(it.eligibleForManualHud)
            assertNotNull(it.unavailableReason)
            assertFalse(GeelyHudSelectionPolicy().select(listOf(it), it.displayId))
        }
    }

    @Test fun duplicateIdsAndMissingIdsAreRejected() {
        val target = display()
        val policy = GeelyHudSelectionPolicy()
        assertFalse(policy.select(listOf(target, target), target.displayId))
        assertFalse(policy.select(listOf(target), 99))
    }

    @Test fun disappearanceRequiresANewExplicitSelectionEvenIfTheSameIdReappears() {
        val target = display()
        val policy = GeelyHudSelectionPolicy()
        policy.select(listOf(target), target.displayId)
        assertNull(policy.resolve(emptyList()))
        assertNull(policy.resolve(listOf(target)))
    }

    @Test fun removalEventPreventsIdReuseWithoutANewSelection() {
        val target = display()
        val policy = GeelyHudSelectionPolicy()
        policy.select(listOf(target), target.displayId)
        policy.removed(target.displayId)
        assertNull(policy.resolve(listOf(target)))
    }

    @Test fun unrelatedDisplayRemovalDoesNotChangeTheSelectedDisplay() {
        val target = display()
        val policy = GeelyHudSelectionPolicy()
        policy.select(listOf(target), target.displayId)
        policy.removed(99)
        assertEquals(target, policy.resolve(listOf(target)))
    }

    @Test fun renamedResizedRotatedOrReclassifiedDisplayRequiresSelectionAgain() {
        val target = display()
        val changed = listOf(target.copy(name = "other"), target.copy(width = 1920),
            target.copy(rotation = 1), target.copy(publicPresentation = false), target.copy(state = "OFF"))
        changed.forEach {
            val policy = GeelyHudSelectionPolicy()
            policy.select(listOf(target), target.displayId)
            assertNull(policy.resolve(listOf(it)))
            assertNull(policy.resolve(listOf(target)))
        }
    }

    @Test fun pendingConnectionIsAllowedButDisconnectRevokesSelection() {
        val target = display()
        val policy = GeelyHudSelectionPolicy()
        policy.select(listOf(target), target.displayId)
        policy.observeConnection(false)
        assertEquals(target, policy.resolve(listOf(target)))
        policy.observeConnection(true)
        policy.observeConnection(false)
        assertNull(policy.resolve(listOf(target)))
        policy.observeConnection(true)
        assertNull(policy.resolve(listOf(target)))
    }

    @Test fun aNewProcessStartsDisabled() {
        val target = display()
        GeelyHudSelectionPolicy().select(listOf(target), target.displayId)
        assertNull(GeelyHudSelectionPolicy().resolve(listOf(target)))
    }
}
