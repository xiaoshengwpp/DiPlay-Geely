package com.shilapi.xcertplay.hud

/**
 * This fork targets unverified Geely head units. BYD-specific integrations must not
 * run merely because a package, display name, fingerprint or old preference matches.
 * These are build-time product boundaries, not user-toggleable compatibility claims.
 * Core CarPlay transport, authentication, audio and main-screen rendering are unchanged.
 */
object GeelyVendorPolicy {
    const val PROFILE = "geely-experimental"
    val bydNavigation: Boolean = false
    val bydCluster: Boolean = false
    val bydBattery: Boolean = false
    val bydAdb: Boolean = false
    val bydDiagnostics: Boolean = false
}
