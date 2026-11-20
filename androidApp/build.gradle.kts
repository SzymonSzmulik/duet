import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    jvmToolchain(17)
}

android {
    namespace = "app.duet.music"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "app.duet.music"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 45
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        versionName = "0.25.1"
    }
    val localProps = Properties().apply { rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) } }
    // Feed document ids live in local.properties (duet.feed.normal / .beta / .owner), not in git. Empty means no update feed.
    fun feed(docId: String) = if (docId.isBlank()) "\"\"" else "\"https://docs.google.com/document/d/$docId/export?format=txt\""
    signingConfigs {
        // Release key lives outside git (path + password in local.properties); see docs/RELEASING.md.
        val store = localProps.getProperty("duet.storeFile")
        if (store != null) create("duet") {
            storeFile = file(store)
            storePassword = localProps.getProperty("duet.storePassword")
            keyAlias = "duet"
            keyPassword = localProps.getProperty("duet.storePassword")
        }
    }
    buildTypes {
        release {
            // Shrunk build for sideloading, signed with the Duet release key only. Without the key the APK
            // comes out unsigned (uninstallable) rather than debug-signed: a build with another key can't
            // update anyone's app ("package conflicts"), so it must never be shipped.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("duet")
        }
    }
    // Three apps that install side by side, each following its own update feed (docs/RELEASING.md).
    flavorDimensions += "channel"
    productFlavors {
        create("normal") {
            dimension = "channel"
            resValue("string", "app_name", "Duet")
            buildConfigField("String", "UPDATE_FEED", feed(localProps.getProperty("duet.feed.normal", "")))
        }
        create("beta") {
            dimension = "channel"
            applicationIdSuffix = ".tester"
            versionNameSuffix = " tester"
            resValue("string", "app_name", "Duet Tester")
            buildConfigField("String", "UPDATE_FEED", feed(localProps.getProperty("duet.feed.beta", "")))
        }
        create("owner") {
            dimension = "channel"
            applicationIdSuffix = ".owner"
            versionNameSuffix = " owner"
            resValue("string", "app_name", "Duet Owner")
            buildConfigField("String", "UPDATE_FEED", feed(localProps.getProperty("duet.feed.owner", "")))
        }
    }
    productFlavors.configureEach {
        // Only the owner app carries the key for the release buttons; it lives in local.properties, not in git.
        val key = if (name == "owner") localProps.getProperty("duet.ownerKey", "") else ""
        buildConfigField("String", "OWNER_KEY", "\"$key\"")
        buildConfigField("String", "TESTER_FEED", feed(localProps.getProperty("duet.feed.beta", "")))
        buildConfigField("String", "RELEASE_FEED", feed(localProps.getProperty("duet.feed.normal", "")))
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1,INDEX.LIST,io.netty.versions.properties}"
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.activity.compose)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.webkit)
    implementation(libs.okhttp)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.junit)
}
