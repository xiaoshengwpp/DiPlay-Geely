# Third reviewed upstream source integration, 2026-10-14

## Source identity

- Geely baseline main: `3781ea7281f07d458244096340de4894f3662d50`.
- Previously integrated official main: `3f554012a5a60817ef18e3e4fcdb2ff9ed8a7069`.
- Reviewed official main: `f0f1eadca5cb114b8fd469eb6b89f38a0615392f`.
- Official increment: 9 commits, 14 paths, merged PRs #786, #788 and #789.
- Version and owner-release preparation/workflows are unchanged. This is source review, not a release.

## Portable changes and conflict resolution

Music audio gets a queue-level PI clock trim, bounded to ±1.5% and updated at most once per second. The learned trim stays in process memory; this may slightly change music pitch and speed. Unlike upstream's channel-only gate, the integration also requires MUSIC content: loudspeaker calls are MEDIA + SPEECH, so they must not be trimmed. Calls, Siri and navigation retain their timing and echo reference. Vendor playback-rate rejection or exception stops further adjustments; the last accepted playback rate remains. A diagnostic callback failure cannot interrupt PCM writes. Previous short-lock navigation gain publication and worker-only volume application are unchanged.

Media-session/Geely-wheel command outcomes and focus changes are added to the existing bounded, redacted, in-memory wheel report. Existing controller ownership, AirPlay session token, deduplication, in-call, AVRCP and reconnect fences remain. Rejected paths are reported without bypassing any fence. No device names, typed text, credentials or persistent key logging are added.

For manually configured car hotspots only, choosing Connect in the off-warning dialog records the driver's choice to ignore a potentially wrong Android off report. The warning is suppressed until Android reports on once; unknown state never blocks and preserves that choice. Cancel/Back/outside dismissal does not save it. This does not switch the hotspot on, grant system permissions, change network configuration or repair the car-settings button. Automatic hotspot, ADB, hidden APIs and scan-pause paths remain disabled, including stale preferences. The new ignore preference is not in the reviewed typed settings backup schema.

The BYD-only long-press Siri control remains hidden behind the disabled product gate, even when a BYD package or stale preference is present. Geely input opt-ins, Siri microphone choice, telephony source behavior, session ownership, display consent, SYSTEM/saved languages and growing-choice WRAP_CONTENT normalization are preserved. Unmerged upstream PR #769 is excluded.

## Validation and remaining acceptance

143 local Python source/product checks pass (110 scripts, 10 UI-boundary, 23 shared contracts), plus the public-source credential-container scan and whitespace check. The full AGENTS Gradle aggregate was attempted locally with a workspace-local Gradle cache, but failed before configuration because Gradle 9.5.0 could not be downloaded in this environment. No local JVM/lint/build success is claimed.

The unchanged Android checks workflow runs all required shared/common/home JVM suites, mobile/home/maphost lint and unsigned source-only debug assemblies on the draft PR. Final exact-head status, counts and screenshot review are recorded in the PR. New coverage includes music-only clock-trim selection (including loudspeaker calls), rate rejection/exception and failed diagnostics, stale/no-session/AVRCP media inputs, stale BYD preferences, repeated hotspot warnings, cancellation, and compact/full/Arabic 1.5× dialog captures.

Target vehicle: 2025 Geely Xingyue L, Lanxing, triple screen, Galaxy OS 2.5.0; underlying Android version is unknown. This work does not establish real-vehicle audio quality, long-drive clock behavior, wheel-key delivery, hotspot behavior or screen compatibility. Those require parked-vehicle acceptance. No authentication keys, signing, owner build, release APK download, installation, device operation, user-computer access or upstream-maintainer contact is included.
