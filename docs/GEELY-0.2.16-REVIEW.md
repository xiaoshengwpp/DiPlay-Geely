# Geely integration of official DiPlay 0.2.16

## Provenance and scope

- Official repository: https://github.com/shihabal3amri/DiPlay
- Exact official tag commit: `bac419695ca26b7e67d204a279333ca52132c2cb` (`v0.2.16`, upstream version code 35).
- The complete official source snapshot contains 1,186 Git blobs. All 1,101 text/source files were materialized from exact-commit reads or matching existing source, and verified against their Git blob hashes. The remaining 85 files/resources were already present in the previously validated source and verified byte-for-byte against the same official tree; no official APK, signing key, authentication asset or other new binary was downloaded.
- This is a full 0.2.16 source baseline with explicit Geely product adaptations. It is not the earlier selective 0.2.14 patch set with a new version label.
- Product version: `0.2.16-geely.1`, code `35001`; package `io.github.xiaoshengwpp.diplay.geely` (source-debug suffix `.hudtest`). Native ABI set remains arm64-v8a, armeabi-v7a and x86_64.

The complete official tree includes the newer settings and presentation system, theme choices, software Opus encoder, video pacing/backlog recovery, updated native echo processing, transport/network changes, tests and dependency declarations. Their upstream opt-in/default behavior is retained except for the product boundaries below. No generic claim of head-unit or iPhone compatibility follows from a source build.

## Geely product boundaries

- Immutable false capabilities prevent BYD/DiLink cluster/HUD/song/call-card/tyre/battery/wheel/ambient-light hardware access, vendor Bluetooth suspension, ADB commands, automatic privileged permission grants and hotspot repair. Saved or forged preferences cannot re-enable them. Workers are lazy and disabled entry points return before hardware, key storage, callbacks or recovery journals are accessed.
- Disabled BYD Bluetooth recovery returns a successful no-op to the normal wireless startup coordinator; ordinary platform Bluetooth availability checks remain active.
- The controller sanitizes caller-provided configuration: no cluster stream and no parked-video advertisement. Unknown parked state stays null. Map embedding, video player and automatic accessibility services are disabled and non-exported.
- Fresh installs default to Simplified Chinese. Explicit saved languages and the saved system-follow choice survive. Location reporting stays opt-in/default false; launch/resume do not automatically request GPS permission. Full-sensor orientation and 100% default interface scale remain.
- An absent new navigation channel stays distinct from explicit channel 0. The saved legacy stream (including 3 or 14) reaches the actual renderer. Advanced mapping with no new override retains usage routing. Explicit valid channels 0–20 override either mode. Runtime tests exercise the sink-to-renderer wiring, not just a mapper helper.
- The new official-package updater cannot download or install upstream APKs into this Geely profile. No unverified Geely feed is invented. Normal manual hotspot credentials, Same-LAN connection controls, Android sensors and non-vendor features remain available.

These are deliberate product differences, not unresolved textual merge conflicts. Product-facing upstream tests are adapted to fail-closed Geely expectations; pure protocol, parser, planner, media and layout coverage remains. No test bypass can enable the immutable vendor policy. UI tests that select English text explicitly select English instead of assuming a fresh English install.

## Build and validation

Source verification uses `scripts/source-only.init.gradle`, which rejects authentication/signing environment inputs and disables debug signing. The pinned official aggregate check is retained with this safety init. Tests use an explicit test-only Mockito startup agent on JDK 25 instead of relying on dynamic self-attachment. The first aggregate run exposed 188 common-test initialization failures from that environment issue; the formerly failing USB/event-socket smoke suites subsequently passed all five cases with the startup agent. Product test adaptations remain separately reviewed and are not masked by that fix.

The first integrated build completed `:shared:compileDebugKotlin :common:compileDebugKotlin :mobile:assembleDebug` successfully. Subsequent review found and fixed a missing saved navigation-stream argument, added a renderer-level regression test and strengthened external-input validation. Final exact-tree aggregate status is recorded separately in `VALIDATION-0.2.16.json`; do not infer a final pass from the first build alone.

No APK was installed, launched on a car, or used to connect an iPhone. Xingyue L hardware testing remains outstanding.

## Full-release boundary

The public official tag has Android source-check workflows and a website workflow, but no published full-APK release-upload workflow. The actual main-app release entry is `:mobile:assembleRelease` using owner-provisioned external runtime authentication plus stable signing inputs.

This fork preserves that entry and adds a release preflight. The external authentication directory may contain only `offline-mfi/identity.pk8` and `offline-mfi/certificate.p7b`; extra files and symlinks are rejected. Authentication and the existing signing keystore must remain outside the source tree. Source checks do not create, import, read or publish these private inputs. No extraction workflow from a different fork is included.

The owner must supply legitimate authentication and signing through a secure owner-controlled build environment. The assistant has not obtained or handled those inputs. Repository write/release-upload access must separately be available and verified before publication. An unsigned identity-free source-validation APK is not a completed standalone CarPlay APK and must not be uploaded as such.
