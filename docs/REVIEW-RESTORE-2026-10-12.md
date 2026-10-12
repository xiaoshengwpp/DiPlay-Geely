# Review checkpoint restoration, 2026-10-12

This review branch restores the previously validated 0.2.16 Geely source checkpoint before considering upstream 0.2.18. It is not a 0.2.18 integration or a release.

## Provenance and preservation

- Checkpoint archive SHA-256: `feff790f577565dd1442dbbe55596b536a60634fc0bcedaf736bc92e686f7088`. All 1,232 manifest entries verified; four generated Python bytecode files are intentionally excluded from Git.
- Official 0.2.16 source: `bac419695ca26b7e67d204a279333ca52132c2cb`.
- Compared complete current main (`a69470c6ba00c70035f8c259f266334c3bc63bb6`), PR1 (`7861ef31b0245fcb20051be295489e8486359f28`) and PR2 (`7d1491b208bc1dd6a46dc61824476d7e83c4cce1`) trees. Custom code deltas against official 0.2.8 were separately reviewed; immutable Geely boundaries retain or strengthen the older guards.
- Preserved the three Geely issue templates byte-for-byte, the target scope (Xingyue L 2025 Lanxing only), privacy/reporting guidance, independent package, version rules and release-channel policy. README version examples now match 0.2.16-geely.1 / 35001.
- Restored the Geely debug label and removed unnecessary BYD debug package visibility. Added a repository guard preventing this fork from deploying the upstream Pages site.
- Historical release/extraction scripts are not copied to this review tree. Existing PRs and their histories are retained unchanged. The ordinary release entry remains subject to external owner-provisioned inputs, outside this source-check task.

## Verification for this restoration

- Fresh local pass: public-tree credential/distribution guard; 29 script tests; 9 UI-boundary source tests (38 total). An independent read-only review found no lost runtime Geely safety or saved-language/audio behavior.
- At the owner's request, removed the dormant ADB credential read/generate/persist implementation from `AdbKeys.load`. It retains its `Context -> KeyPair` call contract but always fails before context/storage access, even if a future capability flag changes. Added two JVM regression cases for direct/repeated calls and one source-level no-storage/no-generation contract. The JVM cases require the final PR's fresh CI; they are not counted as locally passed.
- The historical 2,242 JVM tests and three-module build/lint result in `VALIDATION-0.2.16.json` describe the original checkpoint only. They are not fresh CI results for this review commit.
- This current executor has Java 21 runtime, no javac, no Gradle wrapper cache, and no configured Android SDK. No new Android aggregate run was claimed locally. The safe PR workflow runs identity-free unit tests, lint and unsigned debug builds; it uploads reports only.
- Restored upstream third-party source preserves its original CRLF/whitespace. An empty-repository whole-tree whitespace check reports inherited third-party formatting; it is not an Android test failure.
- No real authentication/signing inputs were read, created, extracted or uploaded. No release workflow, APK publication, installation, main merge or vehicle test was performed.

## First exact-commit CI and cache correction

The first [PR CI run](https://github.com/xiaoshengwpp/DiPlay-Geely/actions/runs/38202466805) on `bdce97d4abeef1801bcde745a1b1129e5c256d6e` failed overall while storing Gradle's configuration cache. The error identified `:mobile:rejectBundledCredentials` retaining build-script references. Its credential scan itself was not removed or ignored.

The uploaded reports independently show 2,244 JVM tests (shared 1,189, common 1,051, home 4), zero failures and zero skips, including both new disabled-ADB tests. The logs also show all three requested lint and debug assembly tasks executed. These successful parts do not turn the failed aggregate into a pass.

The correction captures plain input/source path values during configuration so the task action no longer captures the build script or Project. It retains the strict allowlist, symlink rejection and preBuild dependency, adds a regression contract, and keeps configuration-cache failure checking enabled. The corrected exact commit requires a fresh full CI result.

## Next review

Review official 0.2.18 (`e8d23603ccf5aa9c32ad5c999656c5fb492c83e6`) as a separate change set. In particular, broad Geely/Lynk device matching, automatic wheel-key/Siri registration, microphone source, ADB VPN fallback, foreground service rebinding and system-language defaults require explicit adaptation and validation before any merge decision.
