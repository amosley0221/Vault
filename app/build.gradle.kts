import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

/**
 * Signing material resolution order:
 *   1. -PvaultKeystoreFile=... (or the matching VAULT_* environment variables) — used by CI
 *      when the repository defines release-signing secrets.
 *   2. keystore/vault-ci.jks — the convenience key committed to this repository so that every
 *      build produces an installable, upgrade-compatible APK. It is deliberately not a secret.
 */
fun signingValue(property: String, environment: String, fallback: String): String {
    val fromProperty = providers.gradleProperty(property).orNull
    if (!fromProperty.isNullOrBlank()) return fromProperty
    val fromEnvironment = providers.environmentVariable(environment).orNull
    if (!fromEnvironment.isNullOrBlank()) return fromEnvironment
    return fallback
}

val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore/vault-ci.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

val releaseStoreFile = signingValue(
    "vaultKeystoreFile", "VAULT_KEYSTORE_FILE",
    rootProject.file("keystore/vault-ci.jks").absolutePath,
)
val releaseStorePassword = signingValue(
    "vaultKeystorePassword", "VAULT_KEYSTORE_PASSWORD",
    keystoreProperties.getProperty("storePassword", "vaultci"),
)
val releaseKeyAlias = signingValue(
    "vaultKeyAlias", "VAULT_KEY_ALIAS",
    keystoreProperties.getProperty("keyAlias", "vault"),
)
val releaseKeyPassword = signingValue(
    "vaultKeyPassword", "VAULT_KEY_PASSWORD",
    keystoreProperties.getProperty("keyPassword", "vaultci"),
)

/** Optional: an OAuth app client id enables the GitHub device-authorization flow. */
val githubClientId = signingValue("vaultGithubClientId", "VAULT_GITHUB_CLIENT_ID", "")

android {
    namespace = "com.crownedpixel.vault"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.crownedpixel.vault"
        minSdk = 26
        targetSdk = 35
        versionCode = (signingValue("vaultVersionCode", "VAULT_VERSION_CODE", "1")).toInt()
        versionName = signingValue("vaultVersionName", "VAULT_VERSION_NAME", "0.1.0")
        buildConfigField("String", "GITHUB_CLIENT_ID", "\"$githubClientId\"")
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        create("vault") {
            storeFile = file(releaseStoreFile)
            storePassword = releaseStorePassword
            keyAlias = releaseKeyAlias
            keyPassword = releaseKeyPassword
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("vault")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui-tooling-preview")

    debugImplementation(composeBom)
    debugImplementation("androidx.compose.ui:ui-tooling")
}
