# DiPlay-Geely 0.2.21 source integration

## Source and scope

- Prior accepted fork: `c9491a0ee03e53e2ff8bfd83baa34ceb441fc577` (tree `1c6a945117257f9597ffcac748a33e1ea4fad252`).
- Prior upstream: `1b1ac5f27986ce83b6d17fdd8c4d1be852fbbb70`.
- Reviewed upstream: official main `6d3b8a8ce927cb4b115b8c3881f17619433e93b1`; identical tree `4b640d99daf545b22bad7b2328b7d5a394f29ee4` to release tag commit `49bf3bc2b011cb5ee7c7f4da9b99b18cbd3a23a2`. The comparison contains 81 commits and 132 paths.
- Source version: `0.2.21-geely.1`, versionCode `40001`. This is a review candidate, not a signed release. Application ID, three ABI filters, app minimum API25, owner signing workflow and historical source pin are preserved. Shared/common/native upstream minimum API24 does not lower the application's API25 minimum.

## Behavior and preserved boundaries

Wired startup gains a bounded watchdog, USB mode-request recovery and NCM packet counters. Existing USB availability, permission, transition-attempt and control-session fences remain. Watchdog timeout requires its own control session, availability generation and watchdog instance; cleanup terminates only that run's local watchdog. Session acceptance retains the fork's call lock and ownership handling.

Navigation-only attenuation now scales signed 16-bit PCM instead of relying on a potentially ineffective AudioTrack volume call. Default 0 dB and non-navigation channels pass through untouched. The Geely MEDIA/MUSIC route, short publication lock, nonblocking Settings setter, stale-media recovery and call/render ownership remain. Regression tests retain initial saved gain, concurrent renderer publication and decoder-monitor isolation checks.

The source includes nonfatal software Opus failures, Bluetooth turning-on waits, manual VLAN hotspot interface selection, connection diagnostics, Italian and an optional default-off launcher bubble. Launcher overlay permission is still an explicit user flow. Screenshots cover compact/full English, Arabic RTL, Italian and Simplified Chinese at large font scale.

Missing app language preferences default to Simplified Chinese. Explicit stored SYSTEM and other languages, existing Android13+ platform locales, and completed platform language migrations are preserved. Tests cover the unset default, legacy explicit SYSTEM and platform settings. A pre-Android13 install with no saved app-language choice takes the new default; no explicit choice is overwritten.

### Excluded upstream capabilities

The new car-hotspot credential read/write adapters always report unsupported. Their production implementations contain no reflection, ADB transport, key loading or executable helper. UI entry points and stale saved opt-ins are also gated. The previously removed CarHotspotStartMain remains absent; LocalAdb remains inert. Manual hotspot networking fixes are independent of this exclusion.

BYD native music-rhythm public/helper entry points are inert and support checks return false. Pure band calculations remain testable, but vehicle mutation and shell transport do not ship. Upstream transport-positive tests for excluded adapters are replaced by negative Geely boundary tests; this does not claim that the upstream transport feature passed.

BYD popup implementations, private vendor entry points, automatic hotspot start, upstream APK updates and their settings gates stay disabled. Existing Geely per-capability opt-ins and temporary public-display consent remain. Unmerged official PR843 and external reference-fork changes are not imported.

## Validation status

The initial cloud static checks pass: public credential-container scan, XML parsing/no duplicate resources and whitespace validation. Python contract suites have 155 cases across scripts, common UI boundaries and shared source boundaries. These do not substitute for Kotlin/Android tests.

The full required source-only Gradle aggregate was attempted locally but could not download Gradle9.5.0 (`Network is unreachable`) before configuration. The candidate must pass the existing GitHub Android checks workflow's JVM/Robolectric, three lint and three unsigned assembly tasks before acceptance. Screenshot assertions and generated images still require that run and visual review; no successful JVM/build result is claimed at this stage.

No user computer, vehicle, authentication material, signed owner build, release publication, APK installation/run or maintainer contact is part of this source integration. Source-only tests do not establish authentication readiness or real 2025 Xingyue L triple-screen GalaxyOS2.5 compatibility.
