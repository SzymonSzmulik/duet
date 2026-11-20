package app.duet.desktop

import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import app.duet.music.DuetGraph
import app.duet.music.builtInSources
import app.duet.music.settings.AppSettings
import io.ktor.client.HttpClient
import app.duet.music.sources.ytmusic.NewPipeStreamResolver
import app.duet.music.ui.App
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.awt.Toolkit
import java.io.File
import java.awt.datatransfer.StringSelection
import java.util.Locale

/** The version shown in Settings > About; the installer's version is in desktopApp/build.gradle.kts. */
private const val VERSION = "1.0.0 desktop"

/** The app's parts for a computer: YouTube Music and radio now, JavaFX for audio, no phone-only extras. */
private fun createGraph(): DuetGraph {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    val settings = AppSettings()
    // Cookies for the sign-in windows live in the user's own folder and survive restarts.
    val cookies = CookieJar(File(System.getProperty("user.home"), ".duet/cookies.txt")).also(CookieJar::install)
    val http = HttpClient()
    // A test switch: `DUET_NO_JAVAFX=1` starts Duet without JavaFX (no sound, no Spotify, no sign-in) to see how smooth the window is on its own.
    val noJavaFx = System.getenv("DUET_NO_JAVAFX") != null
    val locale = Locale.getDefault()
    val sources = builtInSources(
        language = locale.language.ifEmpty { "en" },
        country = locale.country.ifEmpty { "US" },
        // JavaFX plays AAC, not Opus: only ask NewPipe for the mp4 streams.
        ytStreams = NewPipeStreamResolver(accepts = { it?.contains("mp4") == true }),
        settings = settings,
        ytLogin = if (noJavaFx) null else YtMusicLogin(cookies, scope),
        // Spotify's Web Player in a hidden browser page, as on the phone; no Spotify app or Premium needed.
        spotify = if (noJavaFx) null else SpotifyWebPage(cookies, scope),
        http = http,
        scope = scope,
        // JavaFX plays MP3 radio; the raw AAC and Ogg streams fail on it, so those stations are not listed at all.
        radioCodecs = setOf("MP3"),
    )
    // A computer cannot play Spotify's own audio (no DRM in the browser), so its songs play through YouTube Music's copy;
    // switched on once, on the first run, and the person can turn it off in Settings > Sound.
    File(System.getProperty("user.home"), ".duet/first-run").let { marker ->
        if (!marker.exists()) { settings.sound.spotifyViaYt.set(true); marker.parentFile.mkdirs(); marker.writeText("1") }
    }
    val mediaCache = DesktopMediaCache()
    val downloadHttp = okhttp3.OkHttpClient.Builder().connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS).readTimeout(30, java.util.concurrent.TimeUnit.SECONDS).build()
    return DuetGraph(
        sources = sources,
        players = if (noJavaFx) emptyList() else listOf(DesktopStreamPlayer(settings.sound, scope, mediaCache)),
        downloads = DesktopDownloads(sources, scope, downloadHttp),
        cache = mediaCache,
        mainScope = scope,
        settings = settings,
        appVersion = VERSION,
        http = http,
        updates = Updates.create(http, scope),
    ).also { graph ->
        // Now Playing in macOS (Control Center, media keys, notch apps); only with sound, which JavaFX provides.
        if (!noJavaFx) MacNowPlaying.start(graph, scope)
        graph.share = { text -> Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null) }
    }
}

/**
 * `DUET_SMOKE="song name"` plays the first YouTube Music result and prints what the player does for 20 seconds,
 * then quits: a check that audio works on a machine without clicking through the app.
 */
private fun smoke(graph: DuetGraph, query: String) {
    graph.mainScope.launch {
        val tracks = graph.sources[app.duet.music.sources.ytmusic.YouTubeMusicProvider.ID]!!.search(query)
        println("SMOKE found ${tracks.size} songs, playing: ${tracks.firstOrNull()?.title}")
        graph.player.playAll(tracks, 0)
        repeat(40) {
            delay(500)
            val s = graph.player.state.value
            println("SMOKE ${s.status} position=${s.positionMs}ms of ${s.durationMs}ms ${s.error ?: ""}")
        }
        kotlin.system.exitProcess(0)
    }
}

fun main() {
    val graph = createGraph()
    // `DUET_SMOKE_BROWSER=1` opens the real browser on a blank page and prints how many cookies it reports (a check of the sign-in plumbing).
    if (System.getenv("DUET_SMOKE_BROWSER") != null) {
        graph.mainScope.launch {
            println("BROWSER found=${BrowserSignIn.findBrowser()}")
            val cookies = BrowserSignIn.run("about:blank") { true }
            println("BROWSER cookies=${cookies?.size} (null means it could not connect)")
            kotlin.system.exitProcess(0)
        }
    }
    System.getenv("DUET_SMOKE")?.let { smoke(graph, it) }
    // `DUET_SMOKE_RADIO="station name"` plays the first radio match and prints what the player does (a check that radio works).
    System.getenv("DUET_SMOKE_RADIO")?.let { q ->
        graph.mainScope.launch {
            val tracks = graph.sources[app.duet.music.sources.radio.RadioProvider.ID]!!.search(q)
            println("SMOKE found ${tracks.size} stations, playing: ${tracks.firstOrNull()?.title}")
            graph.player.playAll(tracks, 0)
            repeat(30) { delay(500); val s = graph.player.state.value; println("SMOKE ${s.status} position=${s.positionMs}ms ${s.error ?: ""}") }
            kotlin.system.exitProcess(0)
        }
    }
    // `DUET_SMOKE_SPOTIFY="query"` waits for Spotify's page to come up and prints a Spotify search (signed out is enough).
    System.getenv("DUET_SMOKE_SPOTIFY")?.let { query ->
        graph.mainScope.launch {
            val spotify = graph.sources[app.duet.music.sources.spotify.SpotifyProvider.ID]!!
            repeat(40) {
                delay(2000)
                val result = runCatching { spotify.search(query) }
                println("SPOTIFY status=${spotify.accountStatus} songs=${result.getOrNull()?.size} first=${result.getOrNull()?.firstOrNull()?.let { "${it.title} - ${it.artist}" }} error=${result.exceptionOrNull()?.message?.take(120)}")
                val songs = result.getOrNull()
                if (songs?.isNotEmpty() == true) {
                    // Then plays the first one, to check that a Spotify song plays (through YouTube Music's copy).
                    graph.player.playAll(songs, 0)
                    repeat(24) {
                        delay(1000)
                        val p = graph.player.state.value
                        println("SPOTIFY play: ${p.status} position=${p.positionMs}ms track=${p.track?.title} from=${p.track?.sourceId?.substringAfterLast('.')} ${p.error ?: ""}")
                    }
                    kotlin.system.exitProcess(0)
                }
            }
            kotlin.system.exitProcess(1)
        }
    }
    if (System.getenv("DUET_SMOKE_LOGIN") != null) {
        graph.sources[app.duet.music.sources.ytmusic.YouTubeMusicProvider.ID]?.account?.signIn()
        graph.sources[app.duet.music.sources.spotify.SpotifyProvider.ID]?.account?.signIn()
        graph.mainScope.launch { delay(25_000); kotlin.system.exitProcess(0) }
    }
    // The window's own icon (the taskbar on Windows); the installed app's icon comes from the bundle.
    val icon = object {}.javaClass.getResource("/icon.png")?.let { BitmapPainter(javax.imageio.ImageIO.read(it).toComposeImageBitmap()) }
    val commands = app.duet.music.ui.desktop.AppCommands()
    val remote = PhoneRemote(graph)
    application {
        Window(
            onPreviewKeyEvent = { e ->
                // The shortcuts the menus had, now without the menus. Only Command combinations, so typing is untouched.
                if (e.type == androidx.compose.ui.input.key.KeyEventType.KeyDown && e.key == Key.R && e.isShiftPressed && (e.isCtrlPressed || e.isMetaPressed)) {
                    // Phone remote: shows the link to open on a phone on the same Wi-Fi, and copies it.
                    javax.swing.SwingUtilities.invokeLater {
                        val link = remote.link()
                        if (link != null) java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(java.awt.datatransfer.StringSelection(link), null)
                        javax.swing.JOptionPane.showMessageDialog(null, if (link != null) "Open this on your phone (same Wi-Fi). It is copied.\n\n$link" else "The phone remote could not start.", "Duet phone remote", javax.swing.JOptionPane.PLAIN_MESSAGE)
                    }
                    true
                } else if (e.type != androidx.compose.ui.input.key.KeyEventType.KeyDown || !e.isMetaPressed) false else when (e.key) {
                    Key.P -> { graph.player.togglePlayPause(); true }
                    Key.DirectionRight -> { graph.player.next(); true }
                    Key.DirectionLeft -> { graph.player.previous(); true }
                    Key.F -> { commands.send(app.duet.music.ui.desktop.AppCommand.Search); true }
                    Key.L -> { commands.send(if (e.isShiftPressed) app.duet.music.ui.desktop.AppCommand.Lyrics else app.duet.music.ui.desktop.AppCommand.NowPlaying); true }
                    Key.Comma -> { commands.send(app.duet.music.ui.desktop.AppCommand.Settings); true }
                    else -> false
                }
            },
            onCloseRequest = ::exitApplication,
            title = "Duet",
            // Opens maximized (the whole screen, menu bar and Dock kept); the size is what "un-maximize" returns to.
            state = androidx.compose.ui.window.rememberWindowState(placement = androidx.compose.ui.window.WindowPlacement.Maximized, size = DpSize(1180.dp, 760.dp)),
            icon = icon,
        ) {
            val mac = System.getProperty("os.name").orEmpty().startsWith("Mac")
            // The Mac way: the title bar is see-through and the app draws under the traffic lights; the sidebar leaves room for them.
            if (mac) androidx.compose.runtime.LaunchedEffect(Unit) {
                window.rootPane.putClientProperty("apple.awt.fullWindowContent", true)
                window.rootPane.putClientProperty("apple.awt.transparentTitleBar", true)
                window.title = ""
            }
            // The menu bar at the top of the screen, with the usual shortcuts for playback.
            // A window too small to use is not allowed.
            androidx.compose.runtime.LaunchedEffect(Unit) { window.minimumSize = java.awt.Dimension(900, 600) }
            androidx.compose.runtime.CompositionLocalProvider(app.duet.music.ui.desktop.LocalTitleBarInset provides (if (mac) 28.dp else 0.dp)) {
                App(graph, desktop = true, commands = commands)
            }
            if (System.getenv("DUET_FRAMES") != null) FrameMeter(window)
        }
    }
}

/**
 * `DUET_FRAMES=1`: after the app has loaded, scrolls the real window with mouse-wheel events for six seconds and prints how
 * long its frames took (and which renderer draws them), then quits. A check of how smooth the app is on this computer.
 */
@androidx.compose.runtime.Composable
private fun FrameMeter(window: java.awt.Window) {
    androidx.compose.runtime.LaunchedEffect(Unit) {
        delay(12_000)
        val target = javax.swing.SwingUtilities.getDeepestComponentAt(window, window.width / 2, window.height / 2)
        val api = (window as? androidx.compose.ui.awt.ComposeWindow)?.renderApi
        println("FRAMES renderer=$api property=${System.getProperty("skiko.renderApi")} env=${System.getenv("SKIKO_RENDER_API")} window=${window.width}x${window.height}")
        val scroller = java.util.Timer(true)
        var n = 0
        if (System.getenv("DUET_FRAMES") != "idle") scroller.schedule(object : java.util.TimerTask() {
            override fun run() {
                val dir = if ((n++ / 90) % 2 == 0) 3 else -3
                java.awt.EventQueue.invokeLater {
                    target?.dispatchEvent(java.awt.event.MouseWheelEvent(target, java.awt.event.MouseEvent.MOUSE_WHEEL, System.currentTimeMillis(), 0,
                        target.width / 2, target.height / 2, 0, false, java.awt.event.MouseWheelEvent.WHEEL_UNIT_SCROLL, dir, dir))
                }
            }
        }, 0, 16)
        val gaps = mutableListOf<Double>()
        var last = 0L
        val end = System.nanoTime() + 6_000_000_000L
        while (System.nanoTime() < end) {
            val t = androidx.compose.runtime.withFrameNanos { it }
            if (last != 0L) gaps += (t - last) / 1e6
            last = t
        }
        val sorted = gaps.sorted()
        println("FRAMES count=${gaps.size} avg=%.1fms p95=%.1fms p99=%.1fms max=%.1fms slow(>25ms)=%d".format(
            sorted.average(), sorted[(sorted.size * 0.95).toInt()], sorted[(sorted.size * 0.99).toInt()], sorted.last(), sorted.count { it > 25 }))
        kotlin.system.exitProcess(0)
    }
}
