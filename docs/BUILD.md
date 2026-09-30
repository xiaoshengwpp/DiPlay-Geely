# Building DiPlay

Requirements: JDK 25, Android SDK 37, NDK 28.2.13676358 and the included Gradle wrapper.

## Source and CI builds

```sh
./gradlew -I scripts/source-only.init.gradle :shared:testDebugUnitTest :common:testDebugUnitTest :mobile:lintDebug :mobile:assembleDebug
```

The opt-in init script requires all authentication/signing environment inputs to be unset and disables debug signing for every application module. It neither uses nor generates a debug keystore. The resulting source-only APK is unsigned, contains no accessory identity and is not an installable standalone CarPlay deliverable. Tests generate disposable synthetic identities at runtime; no test private-key files are tracked. Developer builds without this init script retain Android's normal debug-signing behavior.

## Local release packaging

Provide an external asset directory using `DIPLAY_AUTH_ASSETS_DIR`. The directory must contain exactly the intended runtime files under `offline-mfi/identity.pk8` and `offline-mfi/certificate.p7b`. Neither file belongs in Git. The build permits those two files only when this explicit input is set and rejects unexpected credential containers elsewhere in APK assets.

Set `ANDROID_KEYSTORE_PATH`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, and `ANDROID_KEY_PASSWORD` locally for your Android signing key. Never commit these values or the keystore. Different signing keys cannot update an existing project-signed installation.

```sh
./gradlew :shared:testDebugUnitTest :common:testDebugUnitTest :mobile:lintRelease :mobile:assembleRelease
```

Output: `mobile/build/outputs/apk/release/mobile-release.apk`. The release APK deliberately contains the experimental identity described in the notices; it is extractable by recipients. The separate Android signing key is not included. The retired build-beta.py helper is not used; this Gradle workflow uses explicit environment inputs.

The public release source archive corresponds to the tagged source and excludes runtime identities, signing keys, local configuration and build output.

## Standalone car-test APK

Use `:mobile:assembleStandaloneDebug` for a test APK that must connect to an iPhone:

```sh
DIPLAY_AUTH_ASSETS_DIR=/absolute/path/to/runtime-assets ./gradlew :mobile:assembleStandaloneDebug
```

This task refuses missing or empty runtime inputs. `assembleDebug` remains an identity-free
source/CI build when the explicit asset input is absent; do not install that output as a
standalone car-test package. Before delivery, verify both `assets/offline-mfi/identity.pk8`
and `assets/offline-mfi/certificate.p7b` in the APK against the selected local inputs.
Back up any reports/settings the app supports exporting first. Update an existing test app without uninstalling it only after confirming the package ID and signing certificate match and the version code is compatible; otherwise Android may reject the update. Uninstalling can erase app data.

The debug task uses Android's ordinary debug signing unless explicitly overridden. A fresh build machine can generate a different debug key, so do not use that task for a stable distribution or assume it can update a previous APK.

## Stable standalone release (external inputs required)

Prepare the following inputs yourself in the chosen private build environment, outside this source tree:

- `DIPLAY_AUTH_ASSETS_DIR`: an existing directory containing the authorized `offline-mfi/identity.pk8` and `offline-mfi/certificate.p7b` runtime files
- `ANDROID_KEYSTORE_PATH`: an existing, backed-up Android signing keystore
- `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, and `ANDROID_KEY_PASSWORD`: values for that same existing signing identity

Keep these values out of chat, Git, build logs and public CI. The standalone release path does not download or extract identities from another APK, generate a signing key, save secrets to a service, or publish a release. Supplying existing environment inputs is a separate owner-controlled step; the checks below only validate presence and file metadata, not authorization or cryptographic validity.

After those inputs have been securely provided, the owner-controlled packaging command is:

```sh
./gradlew --no-configuration-cache --no-build-cache :mobile:assembleStandaloneRelease
```

This dedicated task rejects missing, empty or in-tree inputs before release signing/packaging. It does not change ordinary `assembleRelease` behavior. Avoid shared caches for this command. The output remains `mobile/build/outputs/apk/release/mobile-release.apk`.

Before distribution, verify the APK's package ID, version code and public signing-certificate fingerprint against the intended installation. Release currently uses `com.shihab.diplay`; debug uses `com.shihab.diplay.hudtest`. A new key cannot update an upstream-signed installation with the same package ID. Keep the same signing identity for future Geely releases; switching from debug to release also changes package ID. Do not claim an update path until those facts are checked.

The standalone APK embeds its runtime accessory private key, which recipients can extract; the Android signing keystore is not embedded. Keep package transfer/distribution owner-controlled, with that exposure understood. Passing source tests or building this APK does not establish successful iPhone pairing or Xingyue L head-unit compatibility; those need authorized physical testing.
