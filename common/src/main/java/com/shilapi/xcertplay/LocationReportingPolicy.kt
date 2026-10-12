package com.shilapi.xcertplay

/** Runtime permission gates reporting without changing the user's saved choice. */
internal object LocationReportingPolicy {
    fun enabled(selected: Boolean, finePermission: Boolean): Boolean = selected && finePermission
}
