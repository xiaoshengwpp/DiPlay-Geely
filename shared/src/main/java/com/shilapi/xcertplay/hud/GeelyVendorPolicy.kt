package com.shilapi.xcertplay.hud

/**
 * This fork targets unverified Geely head units. BYD-specific integrations must not
 * run merely because a package, display name, fingerprint or old preference matches.
 * BYD gates are product boundaries. Geely adapter flags only make capability-gated implementations
 * available; they do not assert that a particular vehicle is supported.
 * Core CarPlay transport, authentication, audio and main-screen rendering are unchanged.
 */
object GeelyVendorPolicy {
    const val PROFILE = "geely-experimental"
    // Available adapters still require their own runtime capability and lifecycle checks.
    val geelyInput: Boolean = true
    val geelyMicrophoneOverride: Boolean = true
    val phoneAudioRoute: Boolean = false
    val fceCluster: Boolean = false
    val foregroundMediaKeys: Boolean = true
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
