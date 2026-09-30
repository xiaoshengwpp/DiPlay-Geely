# Building DiPlay Geely

Requirements: JDK 25, Android SDK 37, NDK 28.2.13676358 and the included Gradle wrapper.

## Source and CI builds

```sh
python3 scripts/check_public_tree.py
python3 scripts/test_geely_version.py
python3 scripts/test_geely_release_contract.py
python3 scripts/test_standalone_build_contract.py
python3 scripts/test_geely_vendor_boundary.py
./gradlew -I scripts/source-only.init.gradle :shared:testDebugUnitTest :common:testDebugUnitTest :mobile:lintDebug :mobile:assembleDebug
```

The opt-in init script requires all authentication/signing environment inputs to be unset and disables debug signing for every application module. It neither uses nor generates a debug keystore. The resulting source-only APK is unsigned, contains no accessory identity and is not an installable standalone CarPlay deliverable. Tests generate disposable synthetic identities at runtime; no test private-key files are tracked. Developer builds without this init script retain Android's normal debug-signing behavior.

The normal Geely release uses `io.github.xiaoshengwpp.diplay.geely`. The debug variant uses `io.github.xiaoshengwpp.diplay.geely.hudtest`. Neither is an in-place replacement for upstream `com.shihab.diplay`. Namespace/class names retain their upstream names and do not define the installed application's identity.

## Version metadata

`gradle/geely-version.properties` carries the upstream version name/code and the Geely revision. The initial normal version is `0.2.7-geely.1`, code `26001`, tag `v0.2.7-geely.1`. The display version keeps upstream's `0.2.7` intact and adds `.1` as the Geely revision. See [RELEASE.md](RELEASE.md) for the increment policy.

These commands inspect public metadata only:

```sh
python3 scripts/geely_version.py --tag v0.2.7-geely.1
./gradlew --no-configuration-cache :mobile:writeGeelyVersionMetadata
python3 scripts/geely_version.py --gradle-metadata mobile/build/outputs/geely/version.json
```

## Normal standalone release

The owner-dispatched GitHub workflow builds and publishes a normal, signed APK to public Releases using existing owner-provisioned inputs. It is documented in [RELEASE.md](RELEASE.md). Ordinary pushes remain source-only checks. No package is published merely by committing the workflow.

The same normal release can be built locally after the owner securely supplies these existing inputs outside the source tree:

- `DIPLAY_AUTH_ASSETS_DIR`: a directory containing the authorized `offline-mfi/identity.pk8` and `offline-mfi/certificate.p7b` runtime files
- `ANDROID_KEYSTORE_PATH`: an existing, backed-up Android signing keystore
- `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`: the values for that same existing signing identity

Never commit these values/files or send them in chat. Secure provisioning is separate from source review. The build does not download or extract identities from another APK, generate a signing key, save secrets to a service, or publish a release.

```sh
./gradlew --no-daemon --no-configuration-cache --no-build-cache :mobile:assembleStandaloneRelease
```

This dedicated task rejects missing, empty or in-tree inputs before release signing/packaging. It does not change ordinary `assembleRelease` behavior. Avoid shared caches. Output: `mobile/build/outputs/apk/release/mobile-release.apk`.

Before distribution, run the public-metadata/signature validator with the actual source SHA and expected public signing fingerprint, substituting those two public values below:

```sh
python3 scripts/geely_version.py > /tmp/geely-version.json
python3 scripts/validate_geely_apk.py mobile/build/outputs/apk/release/mobile-release.apk \
  --version-metadata /tmp/geely-version.json \
  --signer-sha256 EXPECTED_PUBLIC_CERTIFICATE_SHA256 \
  --source-sha FULL_SOURCE_COMMIT_SHA \
  --aapt "$ANDROID_HOME/build-tools/36.0.0/aapt" \
  --apksigner "$ANDROID_HOME/build-tools/36.0.0/apksigner" \
  --output /tmp/geely-release-manifest.json
```

The validator checks release package/version, public certificate/signature, all configured native architectures and nonempty authentication entries without extracting or printing identity contents. These metadata checks do not establish pairing/authentication validity or physical Xingyue L compatibility. Those require authorized real-device testing.

The APK deliberately embeds its runtime accessory private key, which recipients can extract; the Android signing keystore is not embedded. Public package distribution is an owner-controlled action with that exposure understood. The public source archive contains only the tagged source, excluding runtime identities, signing keys, local configuration and build output.

Keep the same Android signing identity for future Geely releases. In-place updates require this same package ID, signing certificate and a higher version code. Upstream/debug installations can remain alongside it; settings do not migrate automatically. Back up supported reports/settings before changing installations, and remember that uninstalling can erase app data.

## Development-only standalone debug build

`:mobile:assembleStandaloneDebug` remains available for deliberate local development with existing runtime inputs:

```sh
DIPLAY_AUTH_ASSETS_DIR=/absolute/path/to/runtime-assets ./gradlew :mobile:assembleStandaloneDebug
```

It refuses missing or empty authentication files. It uses Android's ordinary debug signing unless explicitly overridden; a fresh machine may generate a different debug key. This `.hudtest` package is separate from the normal Geely release and is unsuitable for a stable distribution/update identity. Use the normal release path above for public APKs.
