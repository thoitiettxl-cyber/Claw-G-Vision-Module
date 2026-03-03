plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.lsplugin.resopt)
    // alias(libs.plugins.lsplugin.cmaker)
    alias(libs.plugins.lsparanoid)
}

lsparanoid {
    seed = 10721
    classFilter = { true }
    includeDependencies = false
    variantFilter = { variant ->
        variant.buildType != "debug"
    }
}

android {
    namespace = "io.github.libxposed.vision"
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    defaultConfig {
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        create("release") {
            val keystoreFile = project.rootProject.file("keystore.jks")
            storeFile = if (keystoreFile.exists()) keystoreFile else null
            storePassword = System.getenv("SIGNING_STORE_PASSWORD")
            keyAlias = System.getenv("SIGNING_KEY_ALIAS")
            keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig =
                signingConfigs
                    .getByName("release")
                    .takeIf { it.storeFile != null }
        }
        debug {
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    buildFeatures {
        buildConfig = true
    }

    kotlin {
        jvmToolchain(21)
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    packaging {
        resources {
            pickFirsts += "META-INF/xposed/*"
        }
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = false
        disable.addAll(listOf("PrivateApi", "DiscouragedPrivateApi", "QueryAllPackagesPermission"))
        ignoreTestSources = true
    }
}

dependencies {
    compileOnly(libs.libxposed.api)
    compileOnly(project(":libxposed-compat"))
    implementation(libs.libxposed.service)
    implementation(libs.hiddenapibypass)
    implementation(libs.androidx.annotation)
}
