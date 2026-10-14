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
