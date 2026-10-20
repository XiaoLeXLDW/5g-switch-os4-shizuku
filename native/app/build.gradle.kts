plugins { id("com.android.application") }

val signingVariables = listOf("FIVEG_STORE_FILE", "FIVEG_STORE_PASSWORD", "FIVEG_KEY_ALIAS", "FIVEG_KEY_PASSWORD")
val releaseSecrets = signingVariables.associateWith { providers.environmentVariable(it).orNull }
val signingConfigured = releaseSecrets.values.all { !it.isNullOrEmpty() }
val validateReleaseSigning by tasks.registering {
    group = "verification"
    description = "Require explicit external credentials before producing a signed release."
    doLast {
        val missing = signingVariables.filter { releaseSecrets[it].isNullOrEmpty() }
        if (missing.isNotEmpty()) {
            throw GradleException("Signed release requires environment variables: ${missing.joinToString()}. " +
                    "See docs/BUILDING.md; use :app:assembleDebug without release credentials.")
        }
        if (!rootProject.file(releaseSecrets.getValue("FIVEG_STORE_FILE")!!).isFile) {
            throw GradleException("FIVEG_STORE_FILE must point to an existing external keystore. See docs/BUILDING.md.")
        }
    }
}
tasks.matching { it.name == "preReleaseBuild" }.configureEach { dependsOn(validateReleaseSigning) }

val localRobolectricDirectory = providers.gradleProperty("fiveg.robolectric.dir")
    .orElse(providers.environmentVariable("FIVEG_ROBOLECTRIC_DIR")).orNull
val localRobolectricOffline = providers.gradleProperty("fiveg.robolectric.offline")
    .orElse(providers.environmentVariable("FIVEG_ROBOLECTRIC_OFFLINE"))
    .map { value ->
        when (value.lowercase()) {
            "true" -> true
            "false" -> false
            else -> throw GradleException("fiveg.robolectric.offline / FIVEG_ROBOLECTRIC_OFFLINE must be true or false.")
        }
    }.getOrElse(false)
if (localRobolectricOffline && (localRobolectricDirectory.isNullOrBlank() ||
            !rootProject.file(localRobolectricDirectory).isDirectory)) {
    throw GradleException("Offline Robolectric requires an existing fiveg.robolectric.dir / FIVEG_ROBOLECTRIC_DIR directory.")
}

android {
    namespace = "dev.xiaolexldw.fiveg"
    compileSdk = 37
    buildToolsVersion = "36.0.0"
    defaultConfig {
        applicationId = "com.ysy.switcherfiveg.hyperos4"
        minSdk = 30
        targetSdk = 35
        versionCode = 26102001
        versionName = "1.0.0"
    }
    signingConfigs {
        if (signingConfigured) {
            create("release") {
                storeFile = rootProject.file(releaseSecrets.getValue("FIVEG_STORE_FILE")!!)
                storePassword = releaseSecrets.getValue("FIVEG_STORE_PASSWORD")
                keyAlias = releaseSecrets.getValue("FIVEG_KEY_ALIAS")
                keyPassword = releaseSecrets.getValue("FIVEG_KEY_PASSWORD")
                enableV1Signing = false
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            if (signingConfigured) signingConfig = signingConfigs.getByName("release")
        }
    }
    buildFeatures { buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.systemProperty("robolectric.offline", localRobolectricOffline.toString())
            if (localRobolectricOffline) {
                it.systemProperty("robolectric.dependency.dir", rootProject.file(localRobolectricDirectory!!).absolutePath)
            }
            it.systemProperty("fiveg.screenshot.dir", layout.buildDirectory.dir("test-screenshots").get().asFile.absolutePath)
            it.maxHeapSize = "2g"
            it.jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED", "--add-opens=java.base/java.util=ALL-UNNAMED")
        }
    }
    lint { abortOnError = true; checkReleaseBuilds = true }
}

dependencies {
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    implementation("org.lsposed.hiddenapibypass:hiddenapibypass:6.2")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16.1")
}
