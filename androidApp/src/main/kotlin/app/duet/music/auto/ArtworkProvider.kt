package app.duet.music.auto

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Serves artwork to Android Auto. The car can only open content:// images, so each https artwork
 * URL is wrapped as `content://<application id>.artwork/<encoded url>`; the image is downloaded once
 * into the cache and served from there. Only image hosts of the music services are fetched, and
 * read access is granted only to Android Auto, so the provider is not a general web proxy.
 */
class ArtworkProvider : ContentProvider() {
    override fun onCreate() = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val url = uri.lastPathSegment ?: return null
        val host = runCatching { URL(url).host }.getOrNull() ?: return null
        if (!url.startsWith("https://") || ALLOWED.none { host == it || host.endsWith(".$it") }) return null
        val dir = File(context!!.cacheDir, "artwork").apply { mkdirs() }
        val file = File(dir, url.hashCode().toUInt().toString(16) + ".img")
        if (!file.exists()) {
            val part = File(dir, file.name + ".part")
            (URL(url).openConnection() as HttpURLConnection).run {
                connectTimeout = 10_000; readTimeout = 10_000
                try { inputStream.use { input -> part.outputStream().use { input.copyTo(it) } } } finally { disconnect() }
            }
            part.renameTo(file)
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri) = "image/*"
    override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<out String>?) = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?) = 0

    companion object {
        private const val AUTO_PACKAGE = "com.google.android.projection.gearhead"
        /** Artwork hosts of YouTube Music and Spotify. */
        private val ALLOWED = listOf("ytimg.com", "googleusercontent.com", "ggpht.com", "scdn.co", "spotifycdn.com")

        /** The content:// address of [url] for Android Auto, with read access granted to it; null for no art. */
        fun uriFor(context: Context, url: String?): Uri? {
            if (url == null || !url.startsWith("https://")) return null
            val uri = Uri.Builder().scheme("content").authority("${context.packageName}.artwork").appendPath(url).build()
            runCatching { context.grantUriPermission(AUTO_PACKAGE, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            return uri
        }
    }
}
