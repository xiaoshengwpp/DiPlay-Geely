# Upstream source integration, 2026-10-13

## Exact source pins

- Geely base: `xiaoshengwpp/DiPlay-Geely@2290496948aa67a0da8fe6d1505f3c124a314b2e`.
- Previous upstream base: `e8d23603ccf5aa9c32ad5c999656c5fb492c83e6` (0.2.18).
- Integrated upstream source: `shihabal3amri/DiPlay@c30d3f0df18dd132739d864a9e054c73b3d57d8c`, including 0.2.19 and post-release changes through PR #734.
- Geely source version: `0.2.19-geely.1`, versionCode `38001`. This is a source integration candidate, not a published or vehicle-validated release.

## Included application work

- Microphone voice-message startup, no-audio/read-error recovery, bounded decoder input and RTP audio-clock fixes. Geely Siri source/AEC choices remain intact. Restart/close ownership and partial capture resets receive additional regression coverage.
- USB configuration selection without forced driver reattachment; remembered completed wired sessions can request CarPlay mode again. Transition and open results are fenced against stale reconnect work.
- Hardware HEVC decoder selection falls back to H.264. FFmpeg-backed Codec2 uses SurfaceView. Surface-return picture/reverse-window sizing and grouped swipe controls are integrated while preserving Geely display consent and session fences.
- Bluetooth preflight/failure diagnosis, manual-hotspot companion matching, crash records, translated Japanese UI, Traditional Chinese corrections, phone-page concurrency/diagnostic export and code access.
- Existing Geely wheel-input gates, neutral call guard, SYSTEM default and saved language/preferences, manual secondary-display navigation projection, package identity and source-only build boundary are retained.

## Intentional Geely exclusions

This is not byte-for-byte official DiPlay or a claim of complete OEM feature parity. BYD/OEM live backends remain disabled. Upstream ADB USB permission grants (#689), accessibility auto-grants/USB dialog clicks, shell hotspot start and BYD gateway binding are excluded, together with their positive runtime tests/helpers. Pure navigation/cluster data or view helpers do not imply an enabled OEM backend. BYD-generation navigation audio defaults cannot overwrite Geely routing.

Official upstream installer/update paths remain disabled; Geely signing and application IDs are unchanged. Upstream product/site/release claims and reference screenshots are not republished as Geely validation evidence.

## Privacy and consent

Phone access starts only when the driver opens the page. It uses plaintext HTTP on the local network; the ordinary backup QR link or six-digit code grants settings read/write only. Diagnostic download is disabled by default and enabled only when the driver explicitly opens the separate report-on-phone action. A new ordinary backup opening revokes that report grant. Five wrong code attempts lock code entry until reopen; the QR link remains valid for that opening. All supported locales warn about this scope. Closing/reopening revokes the old listener and credentials; stale callbacks are fenced. Crash data stays on the head unit unless the user exports a diagnostic report. Calls-on-loudspeaker remains an explicit opt-in, off by default.

## Verification and release boundary

Required validation is the unchanged source-only Android CI aggregate from `AGENTS.md`, all Geely Python contracts, resource parity and an independent source review for the final commit. Local Gradle is blocked before configuration by unavailable Gradle distribution download; CI results must name the exact checked commit. Source tests and unsigned debug assembly do not establish real microphone/audio routing, wheel Binder behavior, physical display roles, reverse-camera behavior or parked-car USB reconnection acceptance.

The owner-release workflow remains pinned to its separately verified prior source. No authentication assets, keys, signed/runtime-authenticated build, release, device installation or OEM permission grant is part of this integration.
