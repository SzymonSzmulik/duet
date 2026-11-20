package app.duet.music

import android.app.Application
import app.duet.music.settings.AppSettings
import app.duet.music.downloads.AndroidDownloads
import app.duet.music.spotify.AndroidSpotifyWebSession
import app.duet.music.spotify.SpotifyWebPlayer
import app.duet.music.weblogin.WebLoginHost
import app.duet.music.ytmusic.AndroidYtMusicLogin
import app.duet.music.sources.ytmusic.NewPipeStreamResolver
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.Locale

class DuetApplication : Application() {
    /** App-wide objects; outlive the Activity so search results and playback survive rotation. */
    lateinit var graph: DuetGraph
        private set
    lateinit var exoPlayer: ExoStreamPlayer
        private set
    lateinit var spotify: AndroidSpotifyWebSession
        private set
    lateinit var ytLogin: AndroidYtMusicLogin
        private set

    /** The web sign-in for [site] ([app.duet.music.weblogin.WebLoginActivity]'s extra). */
    fun loginHost(site: String?): WebLoginHost? = when (site) {
        AndroidSpotifyWebSession.SITE -> spotify
        AndroidYtMusicLogin.SITE -> ytLogin
        else -> null
    }

    override fun onCreate() {
        super.onCreate()
        // Debug builds and the owner's own app: lets Chrome DevTools inspect the Spotify page (chrome://inspect).
        if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0 || BuildConfig.FLAVOR == "owner")
            android.webkit.WebView.setWebContentsDebuggingEnabled(true)
        val locale = Locale.getDefault()
        val settings = AppSettings()
        val mainScope = MainScope()
        spotify = AndroidSpotifyWebSession(this)
        ytLogin = AndroidYtMusicLogin(this)
        val mediaCache = app.duet.music.cache.AndroidMediaCache(this)
        val sources = builtInSources(
                language = locale.language.ifEmpty { "en" },
                country = locale.country.ifEmpty { "US" },
                ytStreams = NewPipeStreamResolver(),
                settings = settings,
                spotify = spotify,
                ytLogin = ytLogin,
                onMobileData = {
                    val cm = getSystemService(android.net.ConnectivityManager::class.java)
                    cm.getNetworkCapabilities(cm.activeNetwork)?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) == true
                },
                scope = mainScope,
            )
        val pkg = packageManager.getPackageInfo(packageName, 0)
        val installer = app.duet.music.update.ApkInstaller(this)
        val http = io.ktor.client.HttpClient()
        graph = DuetGraph(
            sources = sources,
            downloads = AndroidDownloads(this, sources, settings, mainScope),
            cache = mediaCache,
            players = listOf(ExoStreamPlayer(this, settings, mainScope, mediaCache).also { exoPlayer = it }, SpotifyWebPlayer(spotify, speed = { settings.sound.speed.value.value })),
            mainScope = mainScope,
            settings = settings,
            appVersion = pkg.versionName ?: "?",
            updates = app.duet.music.update.AppUpdates(
                http, mainScope,
                currentVersionCode = androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(pkg).toInt(),
                install = installer::install,
                feedUrl = BuildConfig.UPDATE_FEED,
            ),
            releases = if (BuildConfig.FLAVOR == "owner") app.duet.music.update.ReleaseControls(
                http, mainScope,
                ownerFeed = BuildConfig.UPDATE_FEED,
                testerFeed = BuildConfig.TESTER_FEED,
                releaseFeed = BuildConfig.RELEASE_FEED,
                key = BuildConfig.OWNER_KEY,
            ) else null,
        )
        // The home screen widget follows the song and play state.
        mainScope.launch {
            graph.player.state.map { it.track?.key to (it.status == app.duet.music.player.PlaybackStatus.Playing) }
                .distinctUntilChanged().collect { app.duet.music.widget.NowPlayingWidget.update(this@DuetApplication) }
        }
        // The song menu's Share: the system share sheet (a new task, as this is the application context).
        graph.share = { text ->
            val send = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, text)
            startActivity(android.content.Intent.createChooser(send, null).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
