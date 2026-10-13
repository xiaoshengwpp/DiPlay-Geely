package com.shilapi.xcertplay

/** Android observations only. A display ID or name never establishes a vehicle-screen role. */
internal data class GeelyDisplayInfo(
    val displayId: Int,
    val name: String,
    val width: Int,
    val height: Int,
    val densityDpi: Int,
    val rotation: Int,
    val state: String,
    val flags: Int,
    val isDefault: Boolean,
    val valid: Boolean,
    val publicPresentation: Boolean,
) {
    val role: String get() = "unconfirmed"
    val unavailableReason: String? get() = when {
        isDefault -> "default_display_not_a_secondary_target"
        !valid -> "display_removed_or_invalid"
        !publicPresentation -> "public_presentation_surface_not_exposed"
        state != "ON" -> "display_not_confirmed_on"
        width <= 0 || height <= 0 || densityDpi <= 0 -> "display_metrics_unavailable"
        else -> null
    }
    val eligibleForManualHud: Boolean get() = unavailableReason == null
}

internal data class GeelyDisplayInventory(
    val displays: List<GeelyDisplayInfo>,
    val issues: List<String> = emptyList(),
)

/** Session-only explicit selection. Never falls back by name, index, size, or a presumed HUD ID. */
internal class GeelyHudSelectionPolicy {
    private var selected: GeelyDisplayInfo? = null
    private var sawConnected = false
    private var observedDisconnectEpoch: Long? = null

    fun selectedDisplayId(): Int? = selected?.displayId

    fun select(displays: List<GeelyDisplayInfo>, displayId: Int): Boolean {
        val target = displays.singleOrNull { it.displayId == displayId && it.eligibleForManualHud }
            ?: return false
        selected = target
        sawConnected = false
        observedDisconnectEpoch = null
        return true
    }

    fun clear() {
        selected = null
        sawConnected = false
        observedDisconnectEpoch = null
    }

    fun removed(displayId: Int) {
        if (selected?.displayId == displayId) clear()
    }

    fun resolve(displays: List<GeelyDisplayInfo>): GeelyDisplayInfo? {
        val previous = selected ?: return null
        // Any observed identity/capability/geometry change requires the driver to choose again.
        val current = displays.singleOrNull { it == previous && it.eligibleForManualHud }
        if (current == null) clear()
        return current
    }

    fun observeConnection(connected: Boolean, disconnectEpoch: Long = 0L) {
        if (selected == null) return
        val previous = observedDisconnectEpoch
        if (previous != null && previous != disconnectEpoch) {
            clear()
            return
        }
        observedDisconnectEpoch = disconnectEpoch
        if (connected) sawConnected = true else if (sawConnected) clear()
    }
}

/** Physical displays are not summed. This describes only the host Activity's current window. */
internal data class GeelyWindowGeometry(
    val width: Int,
    val height: Int,
    val insetLeft: Int,
    val insetTop: Int,
    val insetRight: Int,
    val insetBottom: Int,
) {
    val contentWidth: Int get() = (width.toLong() - insetLeft.coerceAtLeast(0) - insetRight.coerceAtLeast(0))
        .coerceIn(0, width.coerceAtLeast(0).toLong()).toInt()
    val contentHeight: Int get() = (height.toLong() - insetTop.coerceAtLeast(0) - insetBottom.coerceAtLeast(0))
        .coerceIn(0, height.coerceAtLeast(0).toLong()).toInt()
}
