// `-PdesktopOnly` builds just the desktop parts (the Windows PC has no Android SDK): see docs/DESKTOP.md.
val desktopOnly = providers.gradleProperty("desktopOnly").isPresent

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

if (!desktopOnly) apply(plugin = libs.plugins.android.library.get().pluginId)

kotlin {
    jvmToolchain(17)

    if (!desktopOnly) androidTarget()
    // JVM target: runs the shared tests on the dev machine now, hosts the desktop app later (SPEC §3).
    jvm("desktop")

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.components.resources)
            // In public signatures (DuetGraph, StateFlow), so apps see them too.
            api(libs.kotlinx.coroutines.core)
            api(libs.ktor.client.core)
            implementation(libs.kotlinx.serialization.json)
            api(libs.multiplatform.settings) // in AppSettings' constructor
            implementation(libs.compose.backhandler)
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor3)
        }
        // JVM code shared by Android and desktop: NewPipe Extractor is a Java library.
        val jvmShared by creating {
            dependsOn(commonMain.get())
            dependencies {
                api(libs.newpipe.extractor)
                implementation(libs.okhttp)
                implementation(libs.ktor.client.okhttp)
            }
        }
        if (!desktopOnly) androidMain {
            dependsOn(jvmShared)
            dependencies {
                implementation(libs.kotlinx.coroutines.android)
            }
        }
        val desktopMain by getting {
            dependsOn(jvmShared)
        }
        val desktopTest by getting {
            dependencies {
                // Renders screens to PNG on the dev machine (ScreenshotTest).
                implementation(compose.desktop.currentOs)
                implementation("com.russhwolf:multiplatform-settings-test:${libs.versions.multiplatform.settings.get()}")
                implementation("io.ktor:ktor-client-mock:${libs.versions.ktor.get()}")
            }
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:${libs.versions.coroutines.get()}")
        }
    }
}

if (!desktopOnly) extensions.configure<com.android.build.api.dsl.LibraryExtension>("android") {
    namespace = "app.duet.music.shared"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

compose.resources {
    packageOfResClass = "app.duet.music.resources"
}
