package com.shilapi.xcertplay

import org.junit.Assert.*
import org.junit.Test

class LocationReportingPolicyTest {
    @Test fun reportsOnlyWithExplicitChoiceAndFinePermission() {
        assertFalse(LocationReportingPolicy.enabled(false, false))
        assertFalse(LocationReportingPolicy.enabled(false, true))
        assertFalse(LocationReportingPolicy.enabled(true, false))
        assertTrue(LocationReportingPolicy.enabled(true, true))
    }
}
