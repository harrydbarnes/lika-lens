plugins {
    id("com.android.application")
}

android {
    namespace = "com.scope.camera"
    compileSdk = 35
    enableKotlin = false

    defaultConfig {
        applicationId = "com.scope.camera"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
