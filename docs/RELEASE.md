# Normal Geely releases

The release artifact is the normal installable `DiPlay-Geely-v0.2.7-geely.1.apk`, published in this repository's public GitHub Releases. It uses `io.github.xiaoshengwpp.diplay.geely`, version name `0.2.7-geely.1`, and version code `26001`. It is not the `.hudtest` development package. The workflow sets `prerelease=false` explicitly, even though the tag contains the Geely suffix.

This documents a prepared release path, not evidence that an APK has already been built, tested in a car, or published. The workflow does not run on pushes, pull requests, tags or schedules. Normal source CI has no authentication/signing secrets and never publishes an APK.

## Versioning and update compatibility

`gradle/geely-version.properties` retains the original upstream version and adds a small Geely revision:

- `upstreamVersionName=0.2.7`, `upstreamVersionCode=26`, `geelyRevision=1`
- Display version: `0.2.7-geely.1`; Git tag: `v0.2.7-geely.1`
- Android version code: `upstreamVersionCode * 1000 + geelyRevision`
- For another adaptation of the same upstream version, increment only `geelyRevision` (`1..999`)
- When adopting a newer upstream version, carry over its actual version name/code and reset the Geely revision to `1`; both upstream version fields and the resulting Android code must increase

Check with `python3 scripts/geely_version.py`. The release workflow compares this result with Gradle's `:mobile:writeGeelyVersionMetadata` output and the built APK. Later releases must also increase from the previous Geely tag's properties and public release manifest, with the same package ID and signing certificate.

The Geely application installs alongside upstream DiPlay. It cannot replace upstream or `.hudtest` installations in place, and their settings do not migrate automatically. Once this Geely package is installed, future in-place updates require the same signing key and an increasing version code. Back up supported reports/settings before changing installations; uninstalling can erase app data.

## Owner setup and release

Use the normal GitHub-hosted workflow. No separate private builder is required.

1. Have the existing, authorized runtime `identity.pk8` and `certificate.p7b`, an existing backed-up Android signing keystore, and its passwords/alias ready on your own computer. Do not obtain these by extracting somebody else's APK. The project does not supply or generate those credentials. Independently record the signing certificate's public SHA-256 fingerprint.
2. In this repository's GitHub Settings, create the `geely-release` environment, restrict it to the protected `main` branch, and configure any available required-reviewer protection. Personally enter the following environment secrets through GitHub's secure interface. Uploading them gives this release workflow ongoing access; never put their values in a commit, issue, chat, dispatch input, build log or ordinary CI configuration:
   - `DIPLAY_AUTH_IDENTITY_BASE64`: single-line Base64 of the existing runtime `identity.pk8`
   - `DIPLAY_AUTH_CERTIFICATE_BASE64`: single-line Base64 of the corresponding `certificate.p7b`
   - `ANDROID_KEYSTORE_BASE64`: single-line Base64 of the existing signing keystore
   - `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`
   - Set the environment variable `GEELY_RELEASE_ENABLED` to `true` only after reviewing the workflow and the disclosure below. This is an Actions configuration variable, not another secret
3. After the reviewed source is on `main` and its source-only checks pass, the repository owner personally selects **Actions → Publish normal Geely release (owner only) → Run workflow**, selects `main`, and enters:
   - The exact unused tag, initially `v0.2.7-geely.1`
   - The full 40-character commit SHA of that reviewed `main` revision
   - The expected **public signing-certificate SHA-256 fingerprint**, never a private key or password
   - `PUBLISH-EXTRACTABLE-IDENTITY` in the acknowledgement field

Only the repository owner's dispatch or rerun can execute the job. The selected SHA must match the workflow's exact `main` commit. The workflow requires existing inputs; it never generates an Android key, imports credentials from another APK, configures secrets, or activates itself. Without these owner steps it fails closed. An assistant may prepare and review this source; the credential-entry and release-dispatch steps remain with the owner.

### Public identity disclosure

The standalone APK intentionally embeds the runtime accessory private key and certificate. Anyone who downloads it can extract that identity. Publishing an APK permanently gives recipients access to the bundled key; deleting a release cannot revoke downloaded copies. The separate Android signing keystore and its passwords are not embedded. The owner must have the right to distribute the runtime identity and understand this exposure before personally dispatching publication.

## What the workflow checks and publishes

Before credentials are loaded, the job checks the public source tree, version policy, release history, offline regression contracts, unit tests and release lint, and produces an unsigned identity-free debug build. It then builds `:mobile:assembleStandaloneRelease` using the existing environment inputs. Authentication/signing files are temporary, outside the source tree. The credential-bearing build log stays temporary and is removed instead of uploaded; caching, configuration-cache, build-cache, daemon reuse and Actions APK artifact upload are disabled on this path.

APK verification checks:

- Exact release application ID, version name/code, SDK levels and non-debuggable manifest
- Valid Android APK signature with v2/v3 support, exactly one signer, and the expected public certificate fingerprint; Android debug signing is refused
- Nonempty required native libraries for `arm64-v8a`, `armeabi-v7a` and `x86_64`
- Both nonempty authentication ZIP entries, with no unexpected credential containers, duplicate entries or unsafe paths; it never extracts or prints identity contents

Presence checks do not prove that a runtime identity is authorized, valid for pairing, or supported by a particular iPhone/head unit. CI does not physically test Xingyue L hardware. The release notes explicitly preserve that limitation.

The workflow creates a new tag atomically and stages four assets in a draft release. It verifies their GitHub-reported sizes and SHA-256 digests before publishing the normal release and verifies them again afterwards:

- `DiPlay-Geely-<tag>.apk`
- `DiPlay-Geely-<tag>-source.zip`, archived from the exact tagged source
- `release-manifest.json`, containing only public package/version/ABI/source/checksum/certificate metadata
- `SHA256SUMS`

There are no tag deletions, force pushes, asset replacements, or release overwrites. Existing releases, drafts and tags cannot be reused. If a run fails after tag/draft creation, it deliberately leaves that state for the owner to inspect. Do not delete/recreate a published version to retry; resolve the failure and use the next Geely revision. An incomplete draft reserves its version but is not used as the previous published APK/signing baseline; its presence does not require deleting it to publish a higher revision. A success report requires a non-draft, non-prerelease result with all four matching assets.

For local source and owner-controlled packaging commands, see [BUILD.md](BUILD.md). GitHub's [workflow dispatch documentation](https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows#workflow_dispatch), [release CLI reference](https://cli.github.com/manual/gh_release_create), and Android's [APK signature verification documentation](https://developer.android.com/tools/apksigner) describe the underlying tools.
