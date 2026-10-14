# Reviewed upstream source integration, 2026-10-14

## Source identity and scope

- Geely baseline: `3288f0a7ec38d4abbac5ba938d86283048e6964b`.
- Previously integrated official main: `c30d3f0df18dd132739d864a9e054c73b3d57d8c`.
- Reviewed official main: `0919997b8645b825e4370866d66d455678d2b9da`.
- Upstream incremental comparison: 19 commits, 50 paths, including merged PRs #735, #740, #744, #745, #746, #747, #748 and #749. Unmerged PRs #752 and #757 are not included.
- Upstream remains version 0.2.19. This source sync does not create a release, change delivery version metadata or change the owner's independently pinned release workflow.

## Portable changes

The home card identifies a running USB session without wireless instructions. Waiting-screen colors follow app appearance. Missing or unusable Android Bluetooth receives more precise setup guidance and diagnostics. Wi-Fi Direct recovery handles missing 5 GHz support; USBMUX parsing accepts an exact VERSION frame after the four-byte trailer. Wired and wireless music buffers are stored independently, with legacy saved delay migration and conservative wireless presets. Capture/playback workers receive upstream audio-thread priority; vocoder diagnostics include the non-secret setup value.

Saved language selection and SYSTEM behavior, Geely Siri microphone source, steering/input opt-ins, session ownership and close/reconnect fences, secondary-display capability controls and explicit sharing consent remain intact. No forced Chinese locale is introduced. The typed settings-backup allowlist is preserved: upstream's broader preferences export/import policy is not adopted.

## Deliberate boundary adaptations

- BYD ambient redesign and portable text/swatch helpers remain behind the existing disabled BYD gate. Offline color/description tests remain; positive hardware-dialog tests are replaced with a Geely non-admission regression.
- BYD Bluetooth suspend/resume accept upstream diagnostic callback signatures but remain no-ops. No callback, radio operation, ADB transport or vendor worker is invoked.
- PR #748 automatic hotspot execution is deferred for Geely. The old backend was deliberately inert. Importing the full upstream path would activate firmware-specific tethering and persistent hidden-API exemptions. `CarHotspotTethering.appStartSupported()` and the new compatibility adapter report false; its production start rejects immediately. Only the explicitly injected offline reflection helper and tests are retained. No system service discovery, hidden-API exemption, permission grant UI, shell fallback or new ADB operation is imported.
- Existing manual network setup, generic Android input, microphone selection and secondary-display controls are preserved. The exclusion above is specific to unverified automatic hotspot/OEM execution.

## Validation and limits

Local verification: 129 Python product/source contracts passed; public credential-container scan and whitespace checks passed. Full required Gradle aggregate was attempted but stopped before project configuration because the cloud workspace could not download Gradle 9.5.0 (`Network is unreachable`). Therefore local JVM tests, lint, Android build and screenshots are not claimed as passed.

The draft PR uses the unchanged Android checks workflow to run shared/common/home JVM tests, mobile/home/maphost lint and all three unsigned source-only debug assemblies. The workflow uploads test reports and screenshots only. Added UI coverage includes compact/full/Arabic audio settings at 1.5x font scale, alongside existing Geely capability, consent and locale coverage. Exact-head CI status and final test counts are recorded on the PR.

No user computer, vehicle, authentication material, signing key, authenticated build, release asset, APK installation, owner workflow dispatch, permission grant or external contact is part of this source verification. Physical microphone/routing, steering, secondary-display and USB reconnect acceptance still require a parked vehicle.
