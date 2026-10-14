# Reviewed official 0.2.20 source integration

## Identity and scope

- Geely base main: `cede3beb36ef237a5b76a79ea8d2d7932ac1d756`, tree `cb2d1601406cf9f08534dbd8f44ad208b73e5146`.
- Previously integrated official main: `f0f1eadca5cb114b8fd469eb6b89f38a0615392f`.
- Official 0.2.20 tag: `3c95c936859e8a9153f0040caf2337adddb9e3e4`; official merged main: `969f871433b76f6c59f3c93619f7cb5872fdd15d`. Both point to source tree `7910229e6c4786f160327b37083566385d8268ad`.
- Reviewed increment: 10 commits / 26 upstream paths, merged PRs #793, #791, #797, #798 and #799.
- Source version: `0.2.20-geely.1`, versionCode `39001`, using the existing upstream-code × 1000 + Geely revision convention. No tag, signed build, release or installation is part of this source update.

## Portable diagnostics and bounded call routing

CH341 waiting diagnostics now record the USB vendor/product IDs and device nodes Android lists, sorted and only when the listing changes. A found adapter is identified by its node. No USB serial number is requested; no new permission, discovery matching, automatic upload or connection behavior is introduced.

Upstream #797 automatically moves an earpiece-routed call to the loudspeaker. This Geely integration intentionally narrows that behavior: the existing **Calls on the loudspeaker** preference must already be enabled when the call begins. Its default remains off. An off call can report its earpiece route once but never requests a new speaker route. The preference is sampled at call start, preserving the setting's existing **next call** behavior; changing it during a call does not alter that call's choice.

The existing option still selects the speaker before microphone setup when possible. If that initial request cannot select a speaker, one earpiece-triggered retry is allowed for that opted-in call. Failures keep the route and do not loop. Before retrying, the callback must match the current call generation and renderer, the renderer must remain running, its current track must still be routed to the built-in earpiece, and Android must still be in communication mode. On Android 12+, a newer non-earpiece communication device prevents the retry. Call/renderer replacement, audio stop, sink close and a replaced track invalidate stale callbacks, including reuse of the same stream ID.

The downlink-before-microphone order binds to the call which actually starts. Route changes and call restoration share the audio-mode lock. Existing `CallSpeaker` restoration preserves a route the driver selected later. Diagnostic failures do not escape into playback or microphone handling. These source-level controls do not establish safety or compatibility of any particular vehicle audio HAL.

## Preserved boundaries

- SYSTEM/default language behavior and previously saved language selections are unchanged.
- Geely Siri microphone selection, media/session ownership, call/AVRCP fences and input opt-ins remain.
- Music clock correction still requires MEDIA **and** MUSIC content, so loudspeaker calls (MEDIA + SPEECH) cannot be rate-adjusted.
- The navigation-gain short lock and atomic renderer registration remain. No native audio operation is added inside that gain lock.
- BYD/DiLink control, ADB, OEM phone-audio state, automatic hotspot, hidden-API permissions and Wi-Fi scan-pause execution stay disabled. Unmerged upstream #769 remains excluded.
- Manual hotspot Connect override, display consent and WRAP_CONTENT layout normalization are retained.
- Owner-release scripts, authentication/signing workflow and files are unchanged. No authentication material, signing inputs or release/APK assets are read, downloaded or used.

Official release notes are kept as an explicitly labeled upstream reference. README source/version descriptions are refreshed while keeping the fork introduction and capability-adaptation guidance. The official website/download marketing, deleted Traditional Chinese documentation and installation/validation publication claims are not imported into this product. The fork's website files and site builder are unchanged; no UI layout or application strings change in this increment.

## Verification and limits

143 local Python checks pass: 110 scripts, 10 UI-boundary cases and 23 shared source contracts. The public-tree credential-container check and whitespace checks pass. The existing Python opt-in contract now checks both call-start selection and earpiece retry, plus generation/renderer/current-route fences.

The full AGENTS Gradle aggregate is required: shared/common/home JVM tests, mobile/home/maphost lint and all three source-only debug assemblies. The local attempt cannot download Gradle 9.5.0 in this cloud environment and stops before configuration; no local JVM, lint or build pass is claimed. The unchanged Android PR workflow performs the full unsigned aggregate on the exact candidate head; final status and test counts belong in the PR verification record.

The new call-route suite has 16 cases covering default-off diagnostics, explicit retry, refusal, once-per-call behavior, the real downlink-before-microphone ordering, next-call preference semantics, old call/renderer/track callbacks, removed renderer, sink close, current route/mode changes, driver-selected route restoration and failed diagnostics. Existing `CallSpeakerphoneTest`, clock-trim, navigation-gain and lifecycle suites remain. No UI change needs new screenshots.

Official main CI run [38633067184](https://github.com/shihabal3amri/DiPlay/actions/runs/38633067184) failed `AndroidMediaSinkCodecDetachTest.anUnconfirmedDetachRetiresTheDecoderAndTheNextFrameStartsAFreshOneOnNoSurface` at its retired-thread-exit assertion. The same-tree tag PR had passed, but this does not prove the failure is harmless. That lifecycle test is kept unchanged and included in this fork's full CI; no lowered assertion or blind rerun is used as a substitute for diagnosis.

Target: 2025 Geely Xingyue L Lanxing, triple screen, Galaxy OS 2.5.0; underlying Android version unknown. All real-vehicle call transitions, microphone/echo quality, audio speed/pitch, extended playback, steering input, hotspot and display behavior remain unverified. Parked-vehicle acceptance is separate. This work uses only public source and unsigned verification, with no user-computer or vehicle operation and no upstream contact.
