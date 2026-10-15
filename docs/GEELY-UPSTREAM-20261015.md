# Geely source integration: 2026-10-15

## Provenance and release state

- Fork baseline: `832e2604ff2eff4a738ab06a6a03e6e89a8d30f7`, reviewed tree `07bfb0713792cc21416c8cf9a576e634f23c8c5d`.
- Official source range: [`969f8714...1b1ac5f2`](https://github.com/shihabal3amri/DiPlay/compare/969f871433b76f6c59f3c93619f7cb5872fdd15d...1b1ac5f27986ce83b6d17fdd8c4d1be852fbbb70), 37 commits and 61 changed upstream paths.
- This includes merged PRs 794, 795, 804, 811, 813, 814, 818, 823, 824, 827, 830 and release-note updates 833/834. Unmerged PR822 is excluded.
- Official published version is still 0.2.20. Fork source version remains `0.2.20-geely.1` / `39001`; this integration does not publish a release or change the owner-signing workflow's historical source pin.

## Adaptation decisions

### Music and focus

Compressed music is included in clock-load estimation and stale backlog is bounded. Diagnostic failures cannot escape into the packet receive path. Queue recovery removes only the observed stale head, so concurrent consumer progress cannot cause a newer packet to be discarded. A zero-valued monotonic clock is valid. Clock averaging is reset entering and leaving compressed backlog; 20 ms-cadence regressions cover the history contamination missed by stale-sample resets. Calls and navigation are excluded by the existing MEDIA + MUSIC factory, and navigation gain remains independently locked.

Fresh iPhone playback can re-request Android media focus after transient loss. The queued resume captures focus request ownership and event revision; a newer loss or recreated request invalidates it. Only an Android grant restores playback. Existing call-route generation, current-renderer and explicit call-speaker preference boundaries remain.

### Display

Five-second system bars and black-corner fill are optional and off by default. Native iPhone mask mode takes effect on the next connection and falls back to a local 24 dp fill. Mask retention, replay and teardown follow controller/session ownership. Cancelling a manual preview restores the active native mask. Large-font compact/full/RTL screenshot tests cover the added controls. These checks do not establish iPhone negotiation or vehicle screen compatibility.

### Generic foreground keys

The new default-off Android-key options use their own `diplay_foreground_wheel_keys` preference namespace. Ignored legacy BYD preferences cannot enable them. A driver may explicitly assign an eligible key to play/pause or double-press Siri while the CarPlay screen is visible. Learning is confined to the focused settings window. Protected system/TV/media keys cannot be stolen for play/pause; an explicitly chosen Android assistant key can use double-press observation.

The entire first/second/release/delay/queued-command sequence retains controller, AirPlay session, primary-stream token, physical key, lifecycle generation and settings revision. Changing ownership, key/timing preferences, call state or leaving the window cancels pending actions. Media keeps Bluetooth AVRCP priority and the shared media ownership path. The existing private Geely InputService subscription is unchanged. No accessibility service, ADB, BYD joystick/map controls or private Geely double-click subscription is enabled.

### Connection, diagnostics and safety

Wireless screens show the actual missing permission, startup failure and exhausted retries. A manually overridden false hotspot-off report is cleared only after HOTSPOT_NOT_READY while Android still reports off; working hotspots keep their override. USB listing records VID/PID/node without reading serial numbers or changing permissions. Own-screen launch fallback and crash-frame diagnostics are retained.

BYD-only settings components and legacy home-screen hops stay behind the disabled product gate, even if an Android 7.1 package pretends to provide the relevant activity. Automatic hotspot/scan-pause and privileged ADB paths remain disabled. SYSTEM/saved language, microphone selection, public-display consent, location permission choices and Geely capability opt-ins are preserved. Backup adds only a typed temporary-bars boolean and an explicit corner-radius enum; no new consent, language, key assignment, identity or network secret is imported.

## Validation and limits

Use the exact commit's Android checks for the required shared/common/home unit suites, three Android lint tasks and three source-only unsigned debug assemblies. The local cloud checkout can run the Python contract suites and public-tree/whitespace checks. Local Gradle was attempted but cannot download Gradle 9.5.0 because the network is unreachable; no local Kotlin/build pass is claimed.

No authentication material, owner build, signing, APK download/install/run, release, maintainer contact, Mac or vehicle operation is part of this update. Source-only builds do not establish authentication readiness or actual 2025 Xingyue L triple-screen Galaxy OS 2.5 compatibility. Vehicle audio latency, routing, screen rendering, permissions and foreground keys still require real-device acceptance.
