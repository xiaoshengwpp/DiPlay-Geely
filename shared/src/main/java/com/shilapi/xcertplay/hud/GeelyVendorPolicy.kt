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
    val bydClusterSong: Boolean = false
    val launcherMapSharing: Boolean = false
    val bydTyres: Boolean = false
    val bydAmbientLamp: Boolean = false
    val bydBattery: Boolean = false
    val bydWheelSpeed: Boolean = false
    // No verified Geely parked-state source exists. Never advertise local video playback.
    val videoWhileParked: Boolean = false
    val bydAdb: Boolean = false
    val bydDiagnostics: Boolean = false
    // Upstream APKs target BYD and must never replace this fail-closed product.
    val upstreamApkUpdates: Boolean = false
}
