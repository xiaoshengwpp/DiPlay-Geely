# Second reviewed upstream source integration, 2026-10-14

## Source identity

- Geely baseline: `bd1eb852dbe43632442240aa04423d824957dbcd`; tree `abedf06b30067cf105ec7beb515eccc2bec15a86`.
- Previously integrated official main: `0919997b8645b825e4370866d66d455678d2b9da`.
- Reviewed official main: `3f554012a5a60817ef18e3e4fcdb2ff9ed8a7069`.
- Increment: 25 commits, 44 upstream paths, merged PRs #752, #759, #770, #772, #774, #775, #777, #778, #768 and #765.
- Version remains 0.2.19. No owner-release preparation, signing or release workflow is updated by this source integration.

## Portable changes and boundaries

Navigation guidance gains a −20 to 0 dB attenuation slider in Audio. Zero is the neutral default. The callback saves the value and publishes it to the current sink; the audio worker alone adjusts a navigation track. No system volume, music/call gain or session restart is changed. Gain failures are throttled and diagnosed. Rebuffer and queue-overflow diagnostics are capped, and the queue can report a later overflow after recovering.

The phone picker no longer names a phone before selection. Explicitly text-growing audio/capability choices retain WRAP_CONTENT through settings normalization; narrow Japanese/Arabic real-page regressions and normalized large-font card captures cover the previous fixed-height override. Saved phone selection, SYSTEM/saved languages, independent wired/wireless buffers, typed backup policy, Geely capability/consent settings and session ownership/close fences remain. The new navigation attenuation preference is deliberately not added to settings backup, matching upstream’s omission and preserving the existing reviewed typed schema.

The microphone policy retains the saved Siri source independently of telephony. Upstream's phone-state source helper excludes Geely/Lynk hardware from VOICE_RECOGNITION. The Geely OEM phone-audio route remains inert, including stale BYD preferences; ordinary calls retain their prior sources and effects.

BYD voice-key pair suppression and the BYD short-versus-long preference stay behind the existing disabled BYD gate. Generic voice keys and explicit Geely input adapters retain their session/call guards. Ambient idle behavior is kept as offline policy/text and behind the disabled OEM ambient gate.

Automatic hotspot runtime, permission grant/retry UI and Wi-Fi scan-pause execution remain deferred. The existing platform adapter rejects before accessing context/callbacks; the scan backend remains a no-op. Upstream #775's Android version restriction does not turn this product's backend on. No hidden API exemption, ADB operation, permission grant, credential change or shell fallback is introduced.

## Validation and limits

All 138 local Python source/product contracts passed, along with the public-source scan and whitespace checks. The required full local Gradle aggregate was attempted but failed before configuration while downloading Gradle 9.5.0 because the cloud workspace has no route to that download. Local Android/JVM/lint/build success is not claimed.

The unchanged Android checks workflow runs all AGENTS-required shared/common/home unit suites, mobile/home/maphost lint and unsigned source-only debug assemblies for the draft PR. Exact-head status and final test counts are recorded on the PR. UI coverage includes the live slider with no reconnect, compact Japanese/full English/Arabic RTL screenshots and the existing 1.5× large-font Geely Audio card screenshots. New negative coverage checks that a forged hotspot result, system access and stale automatic-start preferences cannot activate hotspot startup or open a permission flow.

No authentication assets, keys, signed build, APK installation, release, owner workflow dispatch, user computer, vehicle operation or external contact is part of this work. Physical audio/input and reconnect acceptance still require a parked vehicle.
