import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Brand parameters: -P gradle property > environment variable > app.properties > neutral default.
val appFile = Properties().apply {
    rootProject.file("app.properties").takeIf { it.exists() }?.reader()?.use { load(it) }
}
fun brand(key: String, default: String): String =
    (findProperty(key) as String?)
        ?: System.getenv(key)?.takeIf { it.isNotBlank() }
        ?: appFile.getProperty(key)
        ?: default

// Release signing: environment variables > keystore.properties (git-ignored) > unsigned.
val signingProps = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }?.reader()?.use { load(it) }
}
fun signing(envName: String, propName: String): String? =
    System.getenv(envName)?.takeIf { it.isNotBlank() } ?: signingProps.getProperty(propName)
val signingStoreFile = signing("SIGNING_KEYSTORE", "storeFile")

android {
    // The language is chosen inside the app, not by Play: every language must ship in every install, or a player who
    // picks one their phone is not set to would find no strings for it.
    bundle { language { enableSplit = false } }
    namespace = "app.habitrpg"
    compileSdk = 37

    defaultConfig {
        applicationId = brand("APP_ID", "io.github.desvelao.hearthbound")
        minSdk = 26
        targetSdk = 37
        versionCode = brand("APP_VERSION_CODE", "1").toInt()
        versionName = brand("APP_VERSION_NAME", "0.1.0")
        resValue("string", "app_name", brand("APP_NAME", "Hearthbound"))
        // Link shown in Settings > About. Blank hides the link.
        val aboutUrl = brand("APP_ABOUT_URL", "https://example.com").replace("\\", "\\\\").replace("\"", "\\\"")
        buildConfigField("String", "ABOUT_URL", "\"$aboutUrl\"")
    }

    signingConfigs {
        if (signingStoreFile != null) {
            create("release") {
                storeFile = rootProject.file(signingStoreFile)
                storePassword = signing("SIGNING_STORE_PASSWORD", "storePassword")
                keyAlias = signing("SIGNING_KEY_ALIAS", "keyAlias")
                keyPassword = signing("SIGNING_KEY_PASSWORD", "keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }

    buildFeatures {
        compose = true
        resValues = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions { unitTests.isIncludeAndroidResources = true }
}

kotlin { jvmToolchain(17) }

// A release build without signing details would silently produce an unsigned artifact; refuse instead.
gradle.taskGraph.whenReady {
    val releaseRequested = allTasks.any { it.path == ":app:assembleRelease" || it.path == ":app:bundleRelease" }
    if (releaseRequested && signingStoreFile == null) {
        throw GradleException(
            "Release signing is not configured: set SIGNING_KEYSTORE (+ SIGNING_STORE_PASSWORD, " +
                "SIGNING_KEY_ALIAS, SIGNING_KEY_PASSWORD) or create keystore.properties.",
        )
    }
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:data"))
    implementation(project(":feature:tasks"))
    implementation(project(":feature:character"))
    implementation(project(":feature:rewards"))
    implementation(project(":feature:settings"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.navigation.compose)
    implementation(libs.work.runtime)
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.compiler)
    ksp(libs.hilt.work.compiler)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test)
    debugImplementation(libs.compose.ui.test.manifest)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.work.testing)
    testImplementation(libs.room.runtime)
    testImplementation(libs.datastore.preferences)
}
