package app.duet.desktop

import app.duet.music.update.AppUpdates
import app.duet.music.update.UpdateInfo
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import kotlin.system.exitProcess

/**
 * Updates for the computer apps (docs/DESKTOP.md): the same feed check as Android, one feed per system, and an
 * installer step that downloads the new build, quits Duet, swaps it in and starts it again.
 */
object Updates {
    /** The build's number as the feed compares it: 1.0.0 is 10000, 1.2.3 is 10203 (the installer version, `desktopVersion`). */
    const val VERSION_CODE = 10006

    /** Which download this computer takes: `mac-arm64`, `mac-x64` or `windows` (anything else gets no updates). */
    val platform: String? = System.getProperty("os.name").lowercase().let { os ->
        when {
            "mac" in os -> if (System.getProperty("os.arch") == "aarch64") "mac-arm64" else "mac-x64"
            "windows" in os -> "windows"
            else -> null
        }
    }

    fun feedUrl(platform: String) = "https://raw.githubusercontent.com/SzymonSzmulik/duet-releases/main/feeds/$platform.json"

    /** Null where updates are not offered: an unknown system, or a run from the build folder instead of an installed app. */
    fun create(http: HttpClient, scope: CoroutineScope): AppUpdates? {
        val platform = platform ?: return null
        if (installedApp() == null) return null
        return AppUpdates(http, scope, VERSION_CODE, install = ::install, feedUrl = feedUrl(platform))
    }

    /** The folder or bundle the running app was installed as: `Duet.app` on a Mac, the folder holding `Duet.exe` on Windows. */
    fun installedApp(): File? {
        val exe = ProcessHandle.current().info().command().orElse(null)?.let(::File) ?: return null
        return when (platform) {
            "mac-arm64", "mac-x64" -> generateSequence(exe) { it.parentFile }.firstOrNull { it.name.endsWith(".app") }
            "windows" -> exe.parentFile?.takeIf { exe.name.equals("Duet.exe", ignoreCase = true) }
            else -> null
        }
    }

    private suspend fun install(info: UpdateInfo, onProgress: (Float) -> Unit): Unit = withContext(Dispatchers.IO) {
        val app = installedApp() ?: throw AppUpdates.UpdateRefused("Duet was not installed from a download, so it can't update itself.")
        val ext = if (platform == "windows") "zip" else "dmg"
        val download = File.createTempFile("duet-update-", ".$ext")
        download(info.apkUrl, download, onProgress)
        launchSwap(app, download)
        // The swap script waits for this process to end, so end it.
        exitProcess(0)
    }

    private fun download(url: String, to: File, onProgress: (Float) -> Unit) {
        OkHttpClient().newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (!r.isSuccessful) error("HTTP ${r.code}")
            val body = r.body!!
            val total = body.contentLength()
            var done = 0L
            body.byteStream().use { input ->
                to.outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        done += n
                        onProgress(if (total > 0) done.toFloat() / total else -1f)
                    }
                }
            }
        }
    }

    /** Starts a script that outlives Duet: it waits for Duet to quit, puts the new build in place and opens it. */
    private fun launchSwap(app: File, download: File) {
        val pid = ProcessHandle.current().pid().toString()
        val builder = if (platform == "windows") {
            val script = File.createTempFile("duet-update-", ".bat").apply { writeText(WINDOWS_SCRIPT.replace("\n", "\r\n")) }
            ProcessBuilder("cmd", "/c", "start", "\"\"", "/b", script.absolutePath, pid, download.absolutePath, app.absolutePath)
        } else {
            val script = File.createTempFile("duet-update-", ".sh").apply { writeText(MAC_SCRIPT) }
            ProcessBuilder("/bin/sh", script.absolutePath, pid, download.absolutePath, app.absolutePath)
        }
        builder.redirectErrorStream(true).redirectOutput(File.createTempFile("duet-update-", ".log")).start()
    }

    /** Arguments: Duet's process id, the downloaded DMG, the installed Duet.app. Keeps the old app if the copy fails. */
    internal val MAC_SCRIPT = """
        #!/bin/sh
        PID="${'$'}1"; DMG="${'$'}2"; APP="${'$'}3"
        while kill -0 "${'$'}PID" 2>/dev/null; do sleep 0.5; done
        MP="${'$'}(mktemp -d /tmp/duet-update.XXXXXX)"
        hdiutil attach -nobrowse -quiet -mountpoint "${'$'}MP" "${'$'}DMG" || exit 1
        if [ -d "${'$'}MP/Duet.app" ]; then
          rm -rf "${'$'}APP.old"
          mv "${'$'}APP" "${'$'}APP.old"
          if cp -R "${'$'}MP/Duet.app" "${'$'}APP"; then rm -rf "${'$'}APP.old"; else rm -rf "${'$'}APP"; mv "${'$'}APP.old" "${'$'}APP"; fi
        fi
        hdiutil detach -quiet "${'$'}MP"
        rm -f "${'$'}DMG"
        open "${'$'}APP"
    """.trimIndent() + "\n"

    /** Arguments: Duet's process id, the downloaded zip, the folder holding Duet.exe. */
    internal val WINDOWS_SCRIPT = """
        @echo off
        :wait
        tasklist /FI "PID eq %1" 2>nul | find "%1" >nul
        if not errorlevel 1 (
          timeout /t 1 /nobreak >nul
          goto wait
        )
        set "TMPDIR=%TEMP%\duet-update"
        rmdir /s /q "%TMPDIR%" 2>nul
        powershell -NoProfile -Command "Expand-Archive -Force -LiteralPath '%~2' -DestinationPath '%TMPDIR%'"
        robocopy "%TMPDIR%\Duet" "%~3" /E /NFL /NDL /NJH /NJS >nul
        del "%~2" 2>nul
        start "" "%~3\Duet.exe"
    """.trimIndent() + "\n"
}
