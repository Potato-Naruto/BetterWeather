plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.example.betterweather.core"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 30
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    api(libs.kotlinx.coroutines.android)
    implementation(libs.play.services.wearable)
}
