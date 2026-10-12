# Geely 0.2.18 source integration review

## Exact sources and scope

- Validated Geely 0.2.16 baseline: `f8756aa571573a6618dd705df447f6a5a1d88343` (PR #7).
- Official 0.2.16 ancestor: `bac419695ca26b7e67d204a279333ca52132c2cb`.
- Official 0.2.18 target: `e8d23603ccf5aa9c32ad5c999656c5fb492c83e6`.
- [Official comparison](https://github.com/shihabal3amri/DiPlay/compare/bac419695ca26b7e67d204a279333ca52132c2cb...e8d23603ccf5aa9c32ad5c999656c5fb492c83e6): 134 commits, 213 changed paths.

Every changed source file was retrieved at the exact target and checked against its Git blob SHA and byte count. Thirty-three fork-modified paths used independently verified 0.2.16 ancestors for three-way integration. Thirteen files needed explicit conflict resolution. This is a code integration, not merely a version-number change. Core changes include USB configuration/identification recovery, charging-current selection, Bluetooth link diagnostics, video queue/decoder improvements, RTP/feedback handling, album-art recovery and upstream regression tests.

The review branch is based on PR #7 and must not be merged or published by this validation workflow. Version metadata is `0.2.18-geely.1`, Android code `37001`.

## Preserved product behavior

- Simplified Chinese on a new installation; existing English, traditional Chinese, system and other language choices retained. Official AppLocale did not change between 0.2.16 and 0.2.18; the fork implementation remains intact.
- Nullable navigation-channel override, explicit channel zero and legacy audio stream choices remain separate.
- GPS sharing stays opt-in; startup/resume does not request GPS sharing permission.
- Full-sensor orientation, 100% interface default, three ABIs and distinct Geely/debug application labels remain.
- Existing BYD, ADB, Wi-Fi mutation, automatic permission, vehicle-property, cluster, HUD and APK replacement boundaries remain fail-closed.
- AdbKeys has no key reading, generation or saving implementation. WheelKeyService also contains no ADB key-loading, transport or accessibility-setting mutation implementation; its facade returns UNSUPPORTED. Public source never includes accessory identity or signing materials.
- Release builds still require owner-provided external inputs; the strict two-file allowlist, symlink checks and configuration-cache fix remain.
- Fork Pages deployment remains disabled, and public review CI is read-only/source-only.

## Withheld 0.2.18 paths

Broad brand/model matching is not verified firmware compatibility. The Geely vendor input bridge has no context, binding, listener or retry state. Its start/stop methods are intentionally inactive; no OEM HiCar application is changed. The Geely-specific Siri microphone override remains disabled; ordinary voice-recognition and the existing generic recorder-failure fallback are preserved.

VPN permission cannot be granted through ADB. The fallback returns UNSUPPORTED without evaluating any credential-loading default argument. Normal Android consent remains required.

PhoneAudioRoute, Telecom registration/service callbacks, VoIP watching and FCE vehicle output are inactive at live entry points and direct backends. The Telecom service is disabled/unexported, with no MANAGE_OWN_CALLS permission. New BYD call-popup, speed-observer and Wi-Fi shell helper paths are gated too.

The new foreground next/previous bridge remains off because it adds an unverified competing event route. The existing Android MediaSession path is preserved. Tests cover window down/repeat/up, interrupted calls/sessions, failed-send setup and single MediaSession delivery. Vendor-only wheel keys are not claimed compatible.

Official automatic update scheduling and its misleading toggle are disabled. Neither cached update state nor direct installer entry points may install an official APK over the Geely product.

## Approved inactive-backend cleanup

After source upload review rejected retained inactive ADB/BYD implementations, the owner explicitly approved replacing those already-disabled backends with no-op interfaces on 12 October 2026. The cleanup removes the actual credential-loading, connection, shell permission changes and vehicle-control execution bodies, rather than relying only on entry guards. Public signatures, refusal results and pure protocol/model helpers remain where needed; negative tests assert that fake callbacks and backends are never reached. Automatic USB accessibility confirmation is also an inert interface; pure prompt matchers remain for regression coverage. No credential was read, generated or transmitted, and no vehicle operation was performed.

The original Geely PR #7 remains unchanged. The cleanup does not extend the source target beyond the pinned official 0.2.18 release. Pre-cleanup comparison verifies all 95 transport/iAP2/MFi/AirPlay implementation files remain byte-identical; final CI must still verify API compatibility and the complete build.

## Settings backup hardening

The upstream backup importer inferred missing preference types from JSON and could store a string in an integer navigation preference. It also migrated GPS sharing consent. The fork uses a small explicit type/range schema for reviewed display/audio and picture values. There are no wildcard prefixes or whole-file policies. Identity, network secrets, languages, GPS, automatic startup/connection, hotspot consent and vehicle settings are excluded in both directions. Unset navigation remains unset; malformed values are skipped.

Losing every network address now stops an existing backup server before rendering, rather than indexing an empty address list. Tests cover active loss, reopening and another loss. The service only starts from its explicit backup page; no background backup was added.

## Validation status

After inactive-backend cleanup: 46 root Python product/source contracts, 9 common UI source contracts and 10 shared network source contracts pass (65 total). All three suites run in the PR workflow. Public-source and whitespace checks pass after all candidate files are staged. JVM tests, lint and three-module debug assembly require the exact-commit GitHub PR workflow; local Gradle bootstrap could not download its distribution in this executor. CI results must be recorded before calling this candidate validated.

Source-only CI outputs are unsigned and have no accessory identity. They are not a usable standalone CarPlay APK. Runtime authentication, stable APK signing, real-device connection/audio/permission behavior and target Geely firmware testing remain outside this source review and have not been claimed complete.
