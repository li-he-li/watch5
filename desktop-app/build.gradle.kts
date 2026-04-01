plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    jvm("desktop")

    jvmToolchain(17)

    sourceSets {
        val desktopMain by getting {
            kotlin.srcDir("src/main/kotlin")
            resources.srcDir("src/main/resources")

            dependencies {
                implementation(project(":shared"))
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.coroutines.swing)

                // Compose Multiplatform
                implementation(compose.desktop.currentOs)
                implementation(compose.material3)

                // Koin
                implementation(libs.koin.core)
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.heartrate.desktop.MainKt"
        val tmpDir = rootProject.projectDir.resolve(".tmp").absolutePath.replace("\\", "/")
        jvmArgs += listOf("-Djava.io.tmpdir=$tmpDir")

        nativeDistributions {
            targetFormats = setOf(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb
            )

            packageName = "HeartRateMonitor"
            packageVersion = "1.0.0"
        }
    }
}
