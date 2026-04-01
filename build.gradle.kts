// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
}

tasks.register("smokeTest") {
    group = "verification"
    description = "Runs cross-platform smoke checks (shared unit tests plus desktop and Android Kotlin compile checks)."
    dependsOn(
        ":shared:testDebugUnitTest",
        ":desktop-app:compileKotlinDesktop",
        ":wear-app:compileDebugKotlin",
        ":phone-app:compileDebugKotlin"
    )
}
