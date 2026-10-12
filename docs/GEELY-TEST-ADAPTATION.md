# Test adaptation audit for the Geely product profile

The baseline is the complete official 0.2.16 source. Disabling vendor features changes the expected product behavior; enabled-BYD integration tests cannot truthfully remain positive. No policy bypass, skip, assumption, test exclusion or `ignoreFailures` was added.

The first aggregate results are preserved separately: shared 1,183 tests / 10 failures; common 1,053 / 374 failures; home 4 / 0 failures. Of the common failures, 188 explicitly reported Mockito startup instrumentation failure and 186 required per-case product/locale/assertion review. The JDK 25 startup-agent fix was verified separately by the five previously failing USB/event-socket tests.

Pure protocol, packet, parsing, routing, rendering and planner suites remain. UI tests with English text selectors select English explicitly, so they remain independent of the system locale. Fresh installs follow the upstream system-language default; saved language choices are preserved. Disabled-feature tests call real product entry points and verify no backend, key, setting, callback or IPC side effects, including forged persisted state.

## Method-count differences

These are source annotation counts, not execution counts (some suites run on multiple Android SDKs). Overall changed/added files: 76; annotations 569 → 585. Final executed counts are in the validation record.

| Test file | Official annotations | Geely annotations |
| --- | ---: | ---: |
| `ClusterTurnOverlayExpiryTest.kt` | 5 | 4 |
| `CarPlayCallWritesTest.kt` | 8 | 6 |
| `BydClusterMapPauseTest.kt` | 2 | 1 |
| `DiLink3ClusterRecoveryTest.kt` | 4 | 1 |
| `BluetoothSuspendLifecycleTest.kt` | 10 | 4 |
| `Byd13CatalogProbeTest.kt` | 19 | 7 |
| `VehicleProbeBatteryConcurrencyTest.kt` | 5 | 1 |
| `BydBatteryTest.kt` | 10 | 5 |
| `BydClusterSongTimerTest.kt` | 13 | 5 |
| `BydCarPlayCallLifecycleTest.kt` | 21 | 4 |
| `CenterMapOverlayTest.kt` | 12 | 7 |

The reduced HUD suites exercise vendor-facing call-card writes, Bluetooth suspension, cluster recovery, catalogue/battery probes and timer lifecycles. Those real entry points are unconditionally disabled. Their replacement cases verify no initialization, I/O, callbacks, preference changes or stale recovery despite forged inputs. Independent pure state, lease, parser and planner tests remain active.

The center-map overlay has no reachable gesture view in this product. Twelve enabled gesture cases were replaced with seven actual denial cases: overlay permission granted/denied, virtual aspect, repeated aspect changes, invalid aspects, repeated scheduling and repeated hide/foreground transitions. Separate map crop, geometry and capability tests remain. No hidden test switch makes the disabled overlay available.

For accessibility/wheel test changes, 12 pure/non-vendor method bodies were compared byte-for-byte with the official source and retained unchanged: WheelKeyService (2), WheelSiriWindow (4), WheelJoystickSession (6). Real disabled service actions are checked with forged preferences and zero backend interactions.

A source-only build and passing unit tests do not establish actual Xingyue L hardware behavior or standalone iPhone authentication.

The remaining WheelSiri call-frame scenario was vendor-dependent: it now verifies ignored BYD frames, no assigned-key capture, no Siri request or preference change, while explicitly retaining Android MODE_IN_CALL and MODE_IN_COMMUNICATION detection. This corrects the two final R3 SDK failures without changing production behavior.

Aggregate R3 ran 1,051 common cases versus 1,053 initially. This reflects CenterMapOverlay 12→7, MapEmbed 1→2 and ClusterSettingsConsent 4→5 method reshaping across SDKs, together with new Geely boundary tests; it is not a test exclusion. All replaced overlay cases exercised unreachable vendor UI, and active pure geometry/crop coverage remains.
