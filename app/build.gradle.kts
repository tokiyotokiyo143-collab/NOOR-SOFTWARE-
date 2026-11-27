plugins {
  alias(libs.plugins.android.application)
}

android {
  namespace = "com.annoor.service"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.annoor.service"
    minSdk = 26
    targetSdk = (project.findProperty("targetSdkOverride") as String?)?.toInt() ?: 35
    versionCode = (project.findProperty("appVersionCode") as String?)?.toInt() ?: 1
    versionName = "1.0"
  }

  signingConfigs {
    create("debugConfig") {
      storeFile = file("${projectDir}/noor-debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  signingConfigs {
    if (System.getenv("RELEASE_KEYSTORE_PATH") != null) {
      create("releaseConfig") {
        storeFile = file(System.getenv("RELEASE_KEYSTORE_PATH"))
        storePassword = System.getenv("RELEASE_STORE_PASSWORD")
        keyAlias = System.getenv("RELEASE_KEY_ALIAS")
        keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
      }
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      signingConfigs.findByName("releaseConfig")?.let { signingConfig = it }
    }
    debug {
      signingConfig = signingConfigs.getByName("debugConfig")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
}

dependencies {
  implementation("androidx.webkit:webkit:1.12.1")
}
