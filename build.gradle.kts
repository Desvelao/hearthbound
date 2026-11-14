plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}

// In the container the build user has no passwd entry, so the JVM's user.home is "?". Robolectric needs a
// writable home for its SDK download cache; compose sets HOME=/gradle (the shared cache volume).
subprojects {
    tasks.withType<Test>().configureEach {
        failOnNoDiscoveredTests = false // feature modules are empty until their milestone
        System.getenv("HOME")?.takeIf { it.isNotBlank() }?.let { jvmArgs("-Duser.home=$it") }
    }
}
