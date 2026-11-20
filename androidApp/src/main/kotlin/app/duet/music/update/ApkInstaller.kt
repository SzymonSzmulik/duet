package app.duet.music.update

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads an update APK into the cache and opens the system installer on it. Android itself checks
 * that the APK is signed with the same key as the installed app, and asks the user to allow
 * installs from Duet the first time.
 */
class ApkInstaller(private val context: Context) {
    suspend fun install(info: UpdateInfo, onProgress: (Float) -> Unit) {
        val apk = try {
            withContext(Dispatchers.IO) { download(info.apkUrl, onProgress) }
        } catch (e: NotPublic) {
            // Owner APKs sit in a private Drive folder: the browser, signed in as the owner, can fetch them.
            context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(info.apkUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        if (!sameSigner(apk)) throw AppUpdates.UpdateRefused(
            "This update is signed with a different key and can't install over this app. Uninstall it, then install the new version from the download link.",
        )
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        // The installer kills this process; write the WebView sign-in cookies (Spotify, Google rotate
        // them while you listen) to disk first, or the update comes back signed out.
        android.webkit.CookieManager.getInstance().flush()
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    /** Android installs an update only when it's signed like the installed app; checked here so the person gets a clear message. */
    @Suppress("DEPRECATION")
    private fun sameSigner(apk: File): Boolean {
        val pm = context.packageManager
        fun certs(info: android.content.pm.PackageInfo?): Set<String>? =
            (if (android.os.Build.VERSION.SDK_INT >= 28) info?.signingInfo?.apkContentsSigners else info?.signatures)
                ?.map { it.toCharsString() }?.toSet()
        val flag = if (android.os.Build.VERSION.SDK_INT >= 28) android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES
            else android.content.pm.PackageManager.GET_SIGNATURES
        val update = certs(pm.getPackageArchiveInfo(apk.path, flag)) ?: return true // unreadable: let the system installer decide
        val installed = certs(pm.getPackageInfo(context.packageName, flag)) ?: return true
        return update == installed
    }

    private suspend fun download(url: String, onProgress: (Float) -> Unit): File {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val part = File(dir, "Duet.apk.part")
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.instanceFollowRedirects = true
            if (conn.responseCode !in 200..299) throw IOException("HTTP ${conn.responseCode}")
            // A web page instead of the file means the link isn't public.
            if (conn.contentType?.startsWith("text/") == true) throw NotPublic()
            val total = conn.contentLengthLong
            conn.inputStream.use { input ->
                part.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        kotlin.coroutines.coroutineContext.ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        onProgress(if (total > 0) done.toFloat() / total else -1f)
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
        return File(dir, "Duet.apk").also { part.renameTo(it) }
    }

    private class NotPublic : IOException("The update link isn't public")
}
