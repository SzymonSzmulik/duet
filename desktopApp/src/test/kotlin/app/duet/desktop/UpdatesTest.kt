package app.duet.desktop

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class UpdatesTest {
    @Test
    fun theFeedVersionNumberFollowsTheInstallerVersion() {
        val version = Regex("""val desktopVersion = "(\d+)\.(\d+)\.(\d+)"""").find(File("build.gradle.kts").readText())
        assertNotNull(version, "desktopVersion not found in desktopApp/build.gradle.kts")
        val (major, minor, patch) = version.destructured
        // scripts/release-desktop.sh computes the same number for the feeds.
        assertEquals(major.toInt() * 10000 + minor.toInt() * 100 + patch.toInt(), Updates.VERSION_CODE)
    }

    @Test
    fun swapScriptsWaitForDuetToQuitAndKeepTheOldAppIfTheCopyFails() {
        assertTrue(Updates.MAC_SCRIPT.startsWith("#!/bin/sh") && "kill -0" in Updates.MAC_SCRIPT && "mv \"\$APP.old\" \"\$APP\"" in Updates.MAC_SCRIPT)
        assertTrue("tasklist" in Updates.WINDOWS_SCRIPT && "robocopy" in Updates.WINDOWS_SCRIPT)
    }

    @Test
    fun aRunFromTheBuildFolderIsNotOfferedUpdates() {
        // Tests run on the java launcher, which is not an installed Duet.app or Duet.exe.
        assertEquals(null, Updates.installedApp())
    }
}

class MacNowPlayingTest {
    @Test
    fun theHelpersMessageIsOneJsonLineInSecondsAndEscapesText() {
        val line = MacNowPlaying.message("Say \"Hi\"\nagain", "Dua Lipa", null, 203_000, 12_500, true, "/tmp/a.img")
        assertTrue('\n' !in line)
        val o = kotlinx.serialization.json.Json.parseToJsonElement(line) as kotlinx.serialization.json.JsonObject
        assertEquals("Say \"Hi\"\nagain", o["title"]!!.toString().let { kotlinx.serialization.json.Json.decodeFromString<String>(it) })
        assertEquals("203.0", o["duration"].toString()); assertEquals("12.5", o["position"].toString()); assertEquals("true", o["playing"].toString())
        assertEquals(null, o["album"])
    }

    @Test
    fun aPackagedHelperIsCopiedAndMadeRunnable() {
        val dir = java.nio.file.Files.createTempDirectory("duet-np").toFile()
        val packaged = File(dir, "res/DuetNowPlaying").also { it.parentFile.mkdirs(); it.writeText("binary"); it.setExecutable(false) }
        val folder = File(dir, "home")
        val copy = MacNowPlaying.runnableCopy(packaged, folder)
        assertTrue(copy.isFile && copy.canExecute() && copy.readText() == "binary")
        assertTrue(File(folder, "DuetNowPlaying.new").exists().not())
    }
}
