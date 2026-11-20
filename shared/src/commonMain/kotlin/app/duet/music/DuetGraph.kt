package app.duet.music

import app.duet.music.extension.SourceRegistry
import app.duet.music.lyrics.LyricsService
import app.duet.music.downloads.Downloads
import kotlinx.coroutines.launch
import app.duet.music.extension.WebLoginSession
import app.duet.music.player.PlaybackCoordinator
import app.duet.music.player.PlayHistory
import app.duet.music.player.SourcePlayer
import app.duet.music.search.SearchModel
import app.duet.music.settings.AppSettings
import app.duet.music.sources.ytmusic.InnerTubeYtMusicSource
import app.duet.music.sources.ytmusic.YouTubeMusicProvider
import app.duet.music.sources.ytmusic.YtStreamResolver
import app.duet.music.sources.spotify.SpotifyProvider
import app.duet.music.sources.spotify.SpotifyWebSession
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope

/**
 * Wires the app together. Platforms pass in what only they have: their players and a
 * main-thread scope.
 */
class DuetGraph(
    val sources: SourceRegistry,
    players: List<SourcePlayer>,
    val mainScope: CoroutineScope,
    val settings: AppSettings,
    /** Shown in Settings > About. */
    val appVersion: String,
    http: HttpClient = HttpClient(),
    /** Saved songs for offline play; null where the platform has none yet (desktop). */
    val downloads: Downloads? = null,
    /** Songs kept after streaming; null where the platform has no cache. */
    val cache: app.duet.music.library.MediaCache? = null,
    /** In-app updates; null where the platform can't install them (desktop). */
    val updates: app.duet.music.update.AppUpdates? = null,
    /** Duet Owner only: send builds to testers or everyone. */
    val releases: app.duet.music.update.ReleaseControls? = null,
) {
    val lyrics = LyricsService(http)
    val player = PlaybackCoordinator(
        sources, players, mainScope,
        localCopy = { t -> downloads?.localUri(t.key) },
        isCached = { t -> cache?.cachedKeys?.value?.contains(t.key) == true },
        crossfadeMs = { settings.sound.fadeSeconds.value.value * 1000L },
        standIn = { t ->
            val yt = sources[YouTubeMusicProvider.ID]
            if (t.sourceId != SpotifyProvider.ID || !settings.sound.spotifyViaYt.value.value || yt == null) null
            else app.duet.music.downloads.SongMatch.best(t, yt.search(app.duet.music.downloads.SongMatch.query(t)))
        },
    )
    val library = app.duet.music.library.LibraryModel(sources, mainScope)

    val history = PlayHistory()

    /** Scrobbling, when a ListenBrainz token is set (Settings > Playback). Radio isn't sent. */
    val listenBrainz = app.duet.music.player.ListenBrainz(
        http, mainScope, player.state, settings.listenBrainzToken,
        skip = { t -> sources[t.sourceId]?.manifest?.capabilities?.contains(app.duet.music.extension.Capability.LiveRadio) == true },
        serviceName = { t -> sources[t.sourceId]?.manifest?.name },
    )
    /** Styles people share (Settings > Appearance > Shared styles). */
    val shop = app.duet.music.shop.ThemeShop(http, settings, mainScope,
        canShare = { sources[YouTubeMusicProvider.ID]?.account?.signedIn?.value == true })
    val favouriteStations = app.duet.music.sources.radio.FavouriteStations()
    /** Duet's own playlists: Spotify and YouTube Music songs together. */
    val playlists = app.duet.music.library.Playlists()
    val likes = app.duet.music.library.LikedSongs()

    /** Opens the system share sheet with some text; set by the platform. */
    var share: (String) -> Unit = {}

    /** The heart: likes or unlikes on the phone straight away, then in the song's own service account if signed in. */
    fun toggleLike(track: app.duet.music.domain.Track) {
        val liked = likes.toggle(track)
        val source = sources[track.sourceId] ?: return
        if (source.account?.signedIn?.value == true) mainScope.launch { runCatching { source.setLiked(track, liked) } }
    }
    val searchHistory = app.duet.music.search.SearchHistory()
    val home = app.duet.music.home.HomeModel(sources, mainScope, history.recent)

    init {
        // Recently played: a song counts once it starts playing.
        mainScope.launch {
            player.state.collect { s -> if (s.status == app.duet.music.player.PlaybackStatus.Playing) s.track?.let(history::add) }
        }
        // The sleep timer lives in Settings; the player follows it.
        mainScope.launch { settings.sleepMinutes.collect { player.sleepIn(it) { settings.setSleepMinutes(0) } } }
    }
    val search = SearchModel(sources, mainScope)
}

/** The sources compiled into Duet (SPEC §3b, Phase 1). Spotify is added when the platform has a web session. */
fun builtInSources(
    language: String,
    country: String,
    ytStreams: YtStreamResolver,
    settings: AppSettings,
    http: HttpClient = HttpClient(),
    spotify: SpotifyWebSession? = null,
    ytLogin: WebLoginSession? = null,
    /** Whether the device is on mobile data now (for Settings > Sound > quality per network). */
    onMobileData: () -> Boolean = { false },
    scope: CoroutineScope? = null,
    /** Radio stream formats the player can play; null for all. */
    radioCodecs: Set<String>? = null,
) = SourceRegistry(
    listOfNotNull(
        YouTubeMusicProvider(
            InnerTubeYtMusicSource(http, ytStreams, language, country, cookie = { ytLogin?.cookieHeader() }),
            settings.forExtension(YouTubeMusicProvider.ID),
            login = ytLogin,
            scope = scope,
            quality = {
                val q = if (onMobileData()) settings.sound.mobileQuality else settings.sound.wifiQuality
                when (app.duet.music.settings.SoundSettings.Quality.entries.getOrNull(q.value.value)) {
                    app.duet.music.settings.SoundSettings.Quality.DataSaver -> app.duet.music.sources.ytmusic.StreamQuality.Low
                    app.duet.music.settings.SoundSettings.Quality.Normal -> app.duet.music.sources.ytmusic.StreamQuality.Normal
                    else -> app.duet.music.sources.ytmusic.StreamQuality.High
                }
            },
        ),
        spotify?.let { SpotifyProvider(it, http, requireNotNull(scope) { "Spotify needs a scope" }) },
        app.duet.music.sources.radio.RadioProvider(http, radioCodecs),
    ),
)
