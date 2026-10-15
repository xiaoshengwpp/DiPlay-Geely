# DiPlay-Geely unreleased source update, 2026-10-15

Reviewed official main `969f871433b76f6c59f3c93619f7cb5872fdd15d` → `1b1ac5f27986ce83b6d17fdd8c4d1be852fbbb70` (37 commits, 61 upstream paths). Official release remains 0.2.20. This is source integration, not a signed release or vehicle acceptance.

- Count compressed music backlog, bound sustained stale music, and retain music-only clock correction. Fork regressions isolate diagnostic failures, prevent a producer/consumer stale-head race, and reset congestion-contaminated clock history at normal 20 ms packet cadence.
- Re-request media focus only on fresh iPhone playback after transient loss. A newer focus event or replacement request invalidates queued resumes; Android must grant focus before audio is restored.
- Show the actual wireless permission/startup failure and retry exhaustion. Clear the manual hotspot off-report override only after HOTSPOT_NOT_READY while Android still reports off. A working hotspot retains the driver's override.
- Add optional five-second system bars and local black-corner fill. Experimental iPhone mask mode remains off by default, needs the next connection, and has a 24 dp fallback. No real-iPhone or vehicle compatibility claim.
- Improve own-screen launch recovery, diagnostic crash frames, and USB device-list evidence without serial numbers or changed USB permission policy.
- Add default-off, explicitly learned Android-key play/pause and double-Siri while the CarPlay screen is visible. Fresh foreground-only preferences do not activate ignored BYD settings. Existing Geely InputService opt-ins, standard media controls and call/session/AVRCP guards remain. BYD settings, global accessibility capture and ADB stay disabled.
- Backup retains a typed per-setting allowlist: only the temporary-bars boolean and explicit corner-radius choices were added; language, identities, network secrets and consent remain excluded.

SYSTEM language and saved choices, explicit call-speaker preference, microphone selection, navigation gain, public-display consent and all vendor/runtime gates remain. Version stays `0.2.20-geely.1` / `39001` until an authorized release. Owner signing workflow and its historical source pin are unchanged. See [integration and validation](GEELY-UPSTREAM-20261015.md).

## Earlier integration history

# DiPlay-Geely 0.2.20-geely.1 source candidate

The reviewed 0.2.20 update adds USB/CH341 listing diagnostics and a consent-bound call-earpiece fallback. Default calls do not acquire a new route; the existing loudspeaker choice is sampled at call start. See [the current integration record](GEELY-UPSTREAM-0.2.20.md). This is not a signed release or vehicle acceptance.

The earlier source-integration history below is retained for provenance.

# DiPlay-Geely unreleased source changes

Reviewed source integration from official DiPlay main through `0919997b8645b825e4370866d66d455678d2b9da` (2026-10-14), after the existing `c30d3f0` integration. This is source work, not an APK release or hardware acceptance result.

- Separate wired and wireless music buffer choices. Wired USB offers shorter delays while wireless keeps its conservative presets; saved earlier values migrate without changing the other transport.
- Show USB connection state accurately on the home card, and use the app theme while waiting for CarPlay.
- Explain missing Android Bluetooth adapters and switches that do not stay enabled; retain precise diagnostics for unsupported radio implementations.
- Improve Wi-Fi Direct recovery and USBMUX VERSION-frame handling.
- Carry upstream audio-buffer and diagnostic fixes while preserving Geely microphone choice, input ownership and close/reconnect fences.

BYD ambient lighting, OEM Bluetooth suspension and automatic car-hotspot start remain disabled for this product. Upstream hidden-API tethering and shell fallbacks are not activated. Source review details and validation are in [the integration record](GEELY-UPSTREAM-20261014.md).


## Additional reviewed upstream source changes, 2026-10-14

- Add navigation-only guidance attenuation from −20 to 0 dB in Audio settings. Default 0 dB preserves playback; changes apply live on the audio worker without changing music, calls, system stream volume or reconnecting.
- Add bounded audio rebuffer/queue-overflow diagnostics and allow a recovered queue to report a later overflow.
- Remove the placeholder iPhone name before a phone has been selected.
- Preserve Geely's selected Siri microphone source and its existing call sources. Retain upstream's offline telephony source tests without enabling the OEM phone-audio state.
- Keep generic/session-owned voice-key dispatch and Geely input opt-ins. BYD short/long voice-key pair handling remains behind the disabled BYD gate.

BYD ambient policy/text changes are retained behind the inactive OEM gate. Automatic hotspot permission/retry and Wi-Fi scan-pause controls remain unavailable in this product; no ADB, hidden-API exemption or system-permission operation is activated. See [the second integration record](GEELY-UPSTREAM-20261014-SECOND.md) for scope and validation.


## Third reviewed upstream source changes, 2026-10-14

Official main through `f0f1eadca5cb114b8fd469eb6b89f38a0615392f` adds bounded music clock-drift correction, media-key/audio-focus diagnostics and a manual-connect escape from incorrect car-hotspot status reports. Music playback rate may adjust by up to ±1.5%; calls and navigation retain their rates. A driver can choose Connect when Android incorrectly reports the manually enabled car hotspot as off; that choice suppresses repeated off reminders until Android reports it on once. This does not enable the hotspot, grant permissions or change the car-settings button.

The BYD long-press Siri setting remains hidden and inactive in this Geely build, including stale settings and detected BYD packages. Geely input opt-ins, session/call/AVRCP fences, microphone selection, language choices, and previous navigation-gain/layout fixes remain. Version and owner-release files are unchanged. Source-only validation and physical-vehicle limits are in [the third integration record](GEELY-UPSTREAM-20261014-THIRD.md).
