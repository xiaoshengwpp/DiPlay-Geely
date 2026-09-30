plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

apply(from = rootProject.file("gradle/geely-version.gradle.kts"))
val geelyVersionCode: Int by extra
val geelyVersionName: String by extra

// Optional local-only input. CI and ordinary source builds contain no accessory identity.
val localAuthenticationAssets = providers.environmentVariable("DIPLAY_AUTH_ASSETS_DIR")
    .orNull?.let { file(it).canonicalFile }

android {
    namespace = "com.shilapi.xcertplay"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "io.github.xiaoshengwpp.diplay.geely"
        minSdk = 28
        targetSdk = 37
        versionCode = geelyVersionCode
        versionName = geelyVersionName

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
    val filesToCheck = credentialAssets
    val allowed = localAuthenticationAssets?.let { dir ->
        listOf("identity.pk8", "certificate.p7b").map { dir.resolve("offline-mfi/$it").canonicalFile }.toSet()
    } ?: emptySet()
    inputs.files(filesToCheck)
    doLast {
        check(allowed.all { it.isFile }) { "Explicit local authentication assets are incomplete" }
        val unexpected = filesToCheck.files.filter { it.canonicalFile !in allowed }
        check(unexpected.isEmpty()) { "Unexpected credential files in APK assets" }
    }
}
tasks.named("preBuild") { dependsOn(rejectBundledCredentials) }

// Installable releases need explicit runtime inputs. Source/CI builds stay identity-free.
val verifyStandaloneAuthentication by tasks.registering {
    group = "verification"
    description = "Require the explicit runtime authentication input for a standalone APK."
    val directory = localAuthenticationAssets
    doLast {
        check(directory != null) {
            "Standalone builds require DIPLAY_AUTH_ASSETS_DIR; assembleDebug alone is source-only."
        }
        check(listOf("identity.pk8", "certificate.p7b").all {
            directory.resolve("offline-mfi/$it").let { file -> file.isFile && file.length() > 0 }
        }) { "Standalone CarPlay authentication files are missing or empty" }
    }
}
tasks.named("preBuild") { mustRunAfter(verifyStandaloneAuthentication) }
tasks.register("assembleStandaloneDebug") {
    group = "build"
    description = "Developer-only debug build with explicitly provisioned authentication; not a release channel."
    dependsOn(verifyStandaloneAuthentication, "assembleDebug")
}

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
val verifyStandaloneReleaseInputs by tasks.registering {
    group = "verification"
    description = "Check external authentication and stable signing inputs before standalone release packaging."
    dependsOn(verifyStandaloneAuthentication, verifyExistingReleaseSigningInputs)
    // Do not retain configuration containing the caller's release signing inputs.
    notCompatibleWithConfigurationCache("Standalone release uses private caller-supplied inputs")
    val directory = localAuthenticationAssets
    val source = sourceDirectory
    doLast {
        check(directory != null && !directory.toPath().startsWith(source)) {
            "Keep standalone authentication assets outside the source tree"
        }
        check(listOf("identity.pk8", "certificate.p7b").all {
            !directory.resolve("offline-mfi/$it").canonicalFile.toPath().startsWith(source)
        }) { "Standalone authentication files must not link into the source tree" }
    }
}
// Ordering applies only when the standalone verifier is in the task graph.
// Ordinary source/release task behavior stays unchanged.
tasks.matching { it.name in setOf("preReleaseBuild", "validateSigningRelease", "packageRelease") }
    .configureEach { mustRunAfter(verifyStandaloneReleaseInputs) }
tasks.register("assembleStandaloneRelease") {
    group = "build"
    description = "Build a standalone APK using only existing external authentication and signing inputs."
    dependsOn(verifyStandaloneReleaseInputs, "assembleRelease")
}
