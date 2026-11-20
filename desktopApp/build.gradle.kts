import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.util.zip.ZipFile

buildscript {
    repositories { mavenCentral() }
    dependencies { classpath("org.ow2.asm:asm:9.7") }
}

// Duet for Mac and Windows: the shared app in a desktop window, with JavaFX playing the audio.
// Build the installer on the system it is for (see docs/DESKTOP.md): `./gradlew :desktopApp:packageDistributionForCurrentOS`.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.javafx)
}

kotlin { jvmToolchain(17) }

javafx {
    version = libs.versions.javafx.get()
    // media plays the audio; web is the browser window for signing in (it brings controls and graphics).
    modules = listOf("javafx.media", "javafx.web", "javafx.controls")
}

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(kotlin("test"))
}

// Compose asks the window system for the Caps Lock state on EVERY mouse event (and for Num and Scroll Lock, which a Mac
// answers by throwing). On a Mac each ask blocks the UI thread until the system's main thread answers, hundreds of times
// a second while scrolling with a trackpad: cut frames and delayed clicks. Duet uses none of it, so the two Compose
// functions that ask (`getLockingKeyStateSafe`) are rewritten to return false, and the rewritten classes are put in
// Duet's own jar, which comes first on the class path. The build fails if Compose renames them, so a change is noticed.
val patchedCompose = layout.buildDirectory.dir("patched-compose")
val patchComposeLockKeys = tasks.register("patchComposeLockKeys") {
    inputs.files(configurations.named("runtimeClasspath"))
    outputs.dir(patchedCompose)
    doLast {
        val ui = configurations.getByName("runtimeClasspath").files.first { it.name.startsWith("ui-desktop-") && it.name.endsWith(".jar") }
        val targets = listOf(
            "androidx/compose/ui/scene/ComposeSceneMediator_desktopKt",
            "androidx/compose/ui/input/key/KeyEvent_desktopKt__KeyEvent_desktopKt",
        )
        var patched = 0
        ZipFile(ui).use { zip ->
            for (target in targets) {
                val reader = org.objectweb.asm.ClassReader(zip.getInputStream(zip.getEntry("$target.class")).readBytes())
                val writer = org.objectweb.asm.ClassWriter(org.objectweb.asm.ClassWriter.COMPUTE_MAXS)
                reader.accept(object : org.objectweb.asm.ClassVisitor(org.objectweb.asm.Opcodes.ASM9, writer) {
                    override fun visitMethod(access: Int, name: String, descriptor: String, signature: String?, exceptions: Array<out String>?): org.objectweb.asm.MethodVisitor? {
                        val mv = super.visitMethod(access, name, descriptor, signature, exceptions)
                        if (!name.startsWith("getLockingKeyStateSafe") || descriptor != "(I)Z") return mv
                        patched++
                        mv.visitCode()
                        mv.visitInsn(org.objectweb.asm.Opcodes.ICONST_0)
                        mv.visitInsn(org.objectweb.asm.Opcodes.IRETURN)
                        mv.visitMaxs(1, 1)
                        mv.visitEnd()
                        return null   // the original body is dropped
                    }
                }, 0)
                val out = patchedCompose.get().file("$target.class").asFile
                out.parentFile.mkdirs()
                out.writeBytes(writer.toByteArray())
            }
        }
        check(patched == targets.size) { "Compose changed: patched $patched of ${targets.size} lock-key functions (see the comment above patchComposeLockKeys)" }
    }
}
sourceSets.main { output.dir(mapOf("builtBy" to patchComposeLockKeys), patchedCompose) }

// Mac only: the small helper that publishes Duet's song to macOS's Now Playing (src/macMain/swift), built here with swiftc
// as one program for both Apple Silicon and Intel, and shipped inside the app as a resource.
val isMac = System.getProperty("os.name").lowercase().contains("mac")
val appResources = layout.buildDirectory.dir("appResources")
appResources.get().asFile.mkdirs()   // Windows has no helper, but the folder must exist for packaging
val nowPlayingHelper = tasks.register<Exec>("buildNowPlayingHelper") {
    onlyIf { isMac }
    val source = layout.projectDirectory.file("src/macMain/swift/NowPlaying.swift")
    val out = appResources.get().dir("macos").asFile
    inputs.file(source)
    outputs.dir(appResources)
    doFirst { out.mkdirs() }
    // Under Rosetta (the Intel build on an Apple Silicon Mac) Apple's tools have no Intel slice, so they run as Apple Silicon.
    commandLine("/bin/sh", "-c", "A=''; [ \"\$(sysctl -n hw.optional.arm64 2>/dev/null)\" = 1 ] && A='arch -arm64'; " +
        "\$A swiftc -O -target arm64-apple-macos13 '${source.asFile}' -o '$out/np-arm64' && " +
        "\$A swiftc -O -target x86_64-apple-macos13 '${source.asFile}' -o '$out/np-x64' && " +
        "\$A lipo -create '$out/np-arm64' '$out/np-x64' -output '$out/DuetNowPlaying' && rm '$out/np-arm64' '$out/np-x64'")
}
tasks.matching { it.name == "prepareAppResources" }.configureEach { dependsOn(nowPlayingHelper) }

val desktopVersion = "1.0.4"

compose.desktop {
    application {
        mainClass = "app.duet.desktop.MainKt"
        jvmArgs += listOf("-Xmx640m", "-XX:+UseSerialGC", "-XX:MinHeapFreeRatio=10", "-XX:MaxHeapFreeRatio=30", "-Xss1m")
        nativeDistributions {
            appResourcesRootDir.set(appResources)
            targetFormats(TargetFormat.Dmg, TargetFormat.Exe)
            packageName = "Duet"
            packageVersion = desktopVersion
            description = "One music app for Spotify and YouTube Music"
            vendor = "Duet"
            // The whole Java runtime: JavaFX's browser needs modules (jdk.jsobject and others) that trimming drops, and
            // the app then crashes instead of failing (found with Spotify's page in the packaged app).
            includeAllModules = true
            macOS { bundleID = "app.duet.music.desktop"; iconFile.set(project.file("icons/Duet.icns")) }
            windows { iconFile.set(project.file("icons/Duet.ico")); menuGroup = "Duet"; shortcut = true; dirChooser = true; upgradeUuid = "6f1b7c36-1f3c-4e86-9a0a-5d1c0d2b7a11" }
        }
    }
}
