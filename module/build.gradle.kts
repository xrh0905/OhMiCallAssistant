import java.util.Properties

plugins {
    id("com.android.application")
}

val signingProps = rootProject.file("signing/keystore.properties")
    .takeIf { it.exists() }
    ?.let { file ->
        Properties().apply { file.inputStream().use { load(it) } }
    }

fun signingValue(key: String): String? =
    System.getenv(key) ?: signingProps?.getProperty(key)

android {
    namespace = "io.github.xrh0905.ohmicall"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.xrh0905.ohmicall"
        minSdk = 26
        targetSdk = 36
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionName = System.getenv("GITHUB_REF_NAME")
            ?.takeIf { it.startsWith("v") }
            ?.removePrefix("v")
            ?: "0.1.0"
    }

    signingConfigs {
        create("release") {
            val path = signingValue("KEYSTORE_PATH")
            val store = path?.let { rootProject.file(it) }
            if (store != null && store.exists()) {
                storeFile = store
                storePassword = signingValue("KEYSTORE_PASSWORD")
                keyAlias = signingValue("KEY_ALIAS")
                keyPassword = signingValue("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (signingConfigs.getByName("release").storeFile != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            merges += "META-INF/xposed/*"
        }
    }
}

dependencies {
    compileOnly("io.github.libxposed:api:102.0.0")
}
