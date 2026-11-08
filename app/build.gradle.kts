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
        versionCode = 1
        versionName = "1.0.0"

        val gitCommitSha = runCatching {
            val proc = ProcessBuilder("git", "rev-parse", "--short", "HEAD").start()
            proc.inputStream.bufferedReader().readText().trim()
        }.getOrDefault("")
        val buildRunNumber = System.getenv("GITHUB_RUN_NUMBER") ?: ""

        buildConfigField("String", "GIT_COMMIT_SHA", "\"$gitCommitSha\"")
        buildConfigField("String", "GITHUB_RUN_NUMBER", "\"$buildRunNumber\"")
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
