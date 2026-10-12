import java.nio.file.Files

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

apply(from = rootProject.file("gradle/geely-version.gradle.kts"))
val geelyVersionCode: Int by extra
val geelyVersionName: String by extra

// Optional local-only input. CI and ordinary source builds contain no accessory identity.
val localAuthenticationInputPath = providers.environmentVariable("DIPLAY_AUTH_ASSETS_DIR")
    .orNull?.let { file(it).absoluteFile }
val localAuthenticationAssets = localAuthenticationInputPath?.canonicalFile

android {
    namespace = "com.shilapi.xcertplay"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "io.github.xiaoshengwpp.diplay.geely"
        minSdk = 25
        targetSdk = 37
        versionCode = geelyVersionCode
        versionName = geelyVersionName
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }

    }


    localAuthenticationAssets?.let { sourceSets.getByName("main").assets.srcDir(it) }

    signingConfigs {
        create("release") {
            storeFile = file(
                providers.environmentVariable("ANDROID_KEYSTORE_PATH")
                    .getOrElse("missing-release-keystore.jks"),
            )
            storePassword = providers.environmentVariable("ANDROID_KEYSTORE_PASSWORD").getOrElse("")
            keyAlias = providers.environmentVariable("ANDROID_KEY_ALIAS").getOrElse("")
            keyPassword = providers.environmentVariable("ANDROID_KEY_PASSWORD").getOrElse("")
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".hudtest"
            versionNameSuffix = "-hud-test"
        }
        release {
            optimization {
                enable = false
            }
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(project(":common"))
    implementation(project(":shared"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.app.projected)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

// No implicit import. Only the two explicitly selected local runtime assets are allowed.
val credentialAssets = files(android.sourceSets.flatMap { source ->
    source.assets.directories.map { directory ->
        fileTree(directory) {
            include("**/offline-mfi/**", "**/*.pk8", "**/*.p7b", "**/*.key",
                "**/*.pem", "**/*.p12", "**/*.pfx", "**/*.jks", "**/*.keystore")
        }
    }
})
val rejectBundledCredentials by tasks.registering {
    group = "verification"
    description = "Reject unexpected credential files in APK assets."
    // Capture plain path values during configuration; never retain the build script/Project in doLast.
    val inputPath = localAuthenticationInputPath
    val sourceRoot = rootProject.projectDir.canonicalFile
    val filesToCheck = credentialAssets
    val externalAssets = localAuthenticationAssets?.let { fileTree(it) }
    val allowed = localAuthenticationAssets?.let { dir ->
        listOf("identity.pk8", "certificate.p7b").map { dir.resolve("offline-mfi/$it").canonicalFile }.toSet()
    } ?: emptySet()
    // Validate paths before Gradle asset merging; do not fingerprint private input contents here.
    doLast {
        inputPath?.let { input ->
            check(!input.canonicalFile.toPath().startsWith(sourceRoot.toPath())) {
                "Keep runtime authentication inputs outside the source tree"
            }
            check(!Files.isSymbolicLink(input.toPath())) { "Runtime authentication root must not be a symlink" }
            Files.walk(input.toPath()).use { paths ->
                check(paths.noneMatch { Files.isSymbolicLink(it) }) { "Runtime authentication directory must not contain symlinks" }
            }
        }
        check(allowed.all { it.isFile }) { "Explicit local authentication assets are incomplete" }
        val unexpected = filesToCheck.files.filter { it.canonicalFile !in allowed }
        check(unexpected.isEmpty()) { "Unexpected credential files in APK assets" }
        check(externalAssets?.files.orEmpty().all { it.canonicalFile in allowed }) {
            "The external authentication directory must contain only the two explicitly selected runtime assets"
        }
    }
}
tasks.named("preBuild") { dependsOn(rejectBundledCredentials) }

// Car-test packages must be standalone. Keep ordinary source/CI builds identity-free.
val verifyStandaloneAuthentication by tasks.registering {
    group = "verification"
    description = "Require the explicit runtime authentication input for a standalone car-test APK."
    val directory = localAuthenticationAssets
    doLast {
        check(directory != null) {
            "Standalone car builds require DIPLAY_AUTH_ASSETS_DIR; assembleDebug alone is source-only."
        }
        check(!directory.toPath().startsWith(rootProject.projectDir.canonicalFile.toPath())) {
            "Keep runtime authentication inputs outside the source tree"
        }
        check(listOf("identity.pk8", "certificate.p7b").all {
            directory.resolve("offline-mfi/$it").let { file -> file.isFile && file.length() > 0 }
        }) { "Standalone CarPlay authentication files are missing or empty" }
    }
}
tasks.named("preBuild") { mustRunAfter(verifyStandaloneAuthentication) }
tasks.register("assembleStandaloneDebug") {
    group = "build"
    description = "Build a standalone car-test APK with explicitly provisioned authentication."
    dependsOn(verifyStandaloneAuthentication, "assembleDebug")
}

// Geely release guard: retain the official assembleRelease entry, require complete owner inputs.
// Validate only caller-provided paths/presence here. Never create a keystore or
// import runtime authentication material as part of a build.
val releaseSigningInputNames = listOf(
    "ANDROID_KEYSTORE_PATH", "ANDROID_KEYSTORE_PASSWORD", "ANDROID_KEY_ALIAS", "ANDROID_KEY_PASSWORD",
)
val releaseSigningInputsPresent = releaseSigningInputNames.map { name ->
    providers.environmentVariable(name).map { it.isNotBlank() }.getOrElse(false)
}
val existingReleaseKeystore = providers.environmentVariable("ANDROID_KEYSTORE_PATH")
    .orNull?.takeIf { it.isNotBlank() }?.let { file(it).canonicalFile }
val sourceDirectory = rootProject.projectDir.canonicalFile.toPath()
val verifyExistingReleaseSigningInputs by tasks.registering {
    group = "verification"
    description = "Require an existing external signing keystore without creating or importing credentials."
    notCompatibleWithConfigurationCache("Release signing uses private owner-supplied inputs")
    val present = releaseSigningInputsPresent
    val keystore = existingReleaseKeystore
    val source = sourceDirectory
    doLast {
        check(present.all { it }) { "Standalone release requires all four ANDROID_KEYSTORE/KEY signing inputs" }
        check(keystore != null && keystore.isFile && keystore.length() > 0) {
            "Standalone release requires an existing nonempty signing keystore"
        }
        check(!keystore.toPath().startsWith(source)) { "Keep the signing keystore outside the source tree" }
    }
}


// Validate complete external input paths before Android can validate signing or package release.
tasks.configureEach {
    if (name in setOf("preReleaseBuild", "validateSigningRelease", "packageRelease")) {
        dependsOn(verifyStandaloneAuthentication, verifyExistingReleaseSigningInputs)
    }
}
