plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "io.github.libxposed"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}
