plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "Akari.Add.RSA"
    compileSdk = 37

    defaultConfig {
        applicationId = "Akari.Add.RSA"
        minSdk = 26
        targetSdk = 37

        val gitCommitSha = runCatching {
            val proc = ProcessBuilder("git", "rev-parse", "--short", "HEAD").start()
            proc.inputStream.bufferedReader().readText().trim()
        }.getOrDefault("")
        val buildRunNumber = System.getenv("GITHUB_RUN_NUMBER")
        val envVersionName = System.getenv("APP_VERSION_NAME") ?: System.getenv("RELEASE_TAG")
        val envReleaseTag = System.getenv("LATEST_RELEASE_TAG") ?: System.getenv("RELEASE_TAG")

        val gitLatestTag = runCatching {
            val proc = ProcessBuilder("git", "describe", "--tags", "--abbrev=0").start()
            proc.inputStream.bufferedReader().readText().trim()
        }.getOrDefault("")

        val latestReleaseTag = when {
            !envReleaseTag.isNullOrBlank() -> envReleaseTag
            gitLatestTag.isNotBlank() -> gitLatestTag
            else -> "release-1"
        }

        val resolvedVersionName = when {
            !envVersionName.isNullOrBlank() -> envVersionName
            !buildRunNumber.isNullOrBlank() -> "Action-$buildRunNumber"
            else -> "release-1"
        }

        val configVersionCode = 300
        val envVersionCode = System.getenv("APP_VERSION_CODE")?.toIntOrNull()
        val actionOffset = buildRunNumber?.toIntOrNull()?.let { (it % 100).takeIf { n -> n > 0 } ?: 1 } ?: 0
        versionCode = envVersionCode ?: (if (!buildRunNumber.isNullOrBlank()) configVersionCode + actionOffset else configVersionCode)
        versionName = resolvedVersionName

        buildConfigField("String", "GIT_COMMIT_SHA", "\"$gitCommitSha\"")
        buildConfigField("String", "GITHUB_RUN_NUMBER", "\"${buildRunNumber ?: ""}\"")
        buildConfigField("String", "BUILT_LATEST_RELEASE_TAG", "\"$latestReleaseTag\"")
    }

    signingConfigs {
        create("release") {
            val keystoreFile = rootProject.file("release.keystore")
            if (keystoreFile.exists()) {
                storeFile = keystoreFile
                storePassword = System.getenv("KEYSTORE_PASS") ?: "add_rsa_release_2026"
                keyAlias = System.getenv("ALIAS_NAME") ?: "add_rsa"
                keyPassword = System.getenv("ALIAS_PASS") ?: "add_rsa_release_2026"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            val keystoreFile = rootProject.file("release.keystore")
            if (keystoreFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)

    debugImplementation(libs.androidx.ui.tooling)
}
