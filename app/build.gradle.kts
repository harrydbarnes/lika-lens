plugins {
    id("com.android.application")
}

android {
    namespace = "com.scope.camera"
    compileSdk = 35
    enableKotlin = false

    val runNum = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()
    val vCode = (project.findProperty("versionCode") as String?)?.toIntOrNull()
        ?: runNum ?: 1
    val vName = (project.findProperty("versionName") as String?)
        ?: (if (runNum != null) "0.1.$runNum" else "0.1.0")

    defaultConfig {
        applicationId = "com.scope.camera"
        minSdk = 26
        targetSdk = 35
        versionCode = vCode
        versionName = vName
    }

    val keystoreFile = file("likalens.keystore")
    signingConfigs {
        create("likalens") {
            storeFile = keystoreFile
            storePassword = "androiddebugkey"
            keyAlias = "likalens"
            keyPassword = "androiddebugkey"
        }
    }

    buildTypes {
        release {
            if (keystoreFile.exists()) {
                signingConfig = signingConfigs.getByName("likalens")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            if (keystoreFile.exists()) {
                signingConfig = signingConfigs.getByName("likalens")
            }
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
