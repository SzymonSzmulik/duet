package app.duet.music.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.LibraryResult
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import app.duet.music.DuetApplication
import app.duet.music.auto.ArtworkProvider
import app.duet.music.domain.Collection
import app.duet.music.domain.Track
import app.duet.music.extension.SourceRegistry
import app.duet.music.player.PlaybackCoordinator
import app.duet.music.player.PlaybackStatus
import app.duet.music.player.playFromVoice
import app.duet.music.sources.radio.RadioProvider
import app.duet.music.settings.Flag
import app.duet.music.settings.HomeSection
import app.duet.music.lyrics.LyricsService
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Keeps playback alive in the background and gives the system the media notification,
 * lock-screen, Bluetooth/headset controls and Android Auto.
 *
 * The session wraps the app's ExoPlayer, but the queue lives in [PlaybackCoordinator]: next/previous,
 * the shown title/artwork and "play this" from a controller all go to the coordinator.
 *
 * Android Auto (Google's media-app guidelines): four tabs (Home, Radio, Recent, Library), kept shallow so
 * most things are one tap away: Home lists quick picks inline, Radio is a grid of station logos (favourites,
 * recently played, popular in your country). The queue is behind the player's own Queue button. Voice and
 * on-screen search; every item has artwork through [app.duet.music.auto.ArtworkProvider] (the car only
 * opens content:// images).
 */
class PlaybackService : MediaLibraryService() {
    private var session: MediaLibrarySession? = null
    private val scope = MainScope()

    /** Tracks the car was last shown, by media id, so picking one can start the list it came from. */
    private val shown = HashMap<String, Pair<List<Track>, Int>>()

    private val app get() = application as DuetApplication

    override fun onCreate() {
        super.onCreate()
        val player = QueuePlayer(app.exoPlayer.exo, app.graph.player)
        session = MediaLibrarySession.Builder(this, player, LibraryCallback()).build()
            // Without a connected controller, the service only posts the notification for added sessions.
            .also(::addSession)
        // After a crossfade the session follows the new player.
        app.exoPlayer.onPlayerChanged = { next -> session?.player = QueuePlayer(next, app.graph.player) }
        // Like and Shuffle buttons (car screen, notification) follow the song and its state.
        val g = app.graph
        scope.launch {
            combine(g.player.state, g.likes.list) { st, likes -> Triple(st.track?.key, st.track?.let { t -> likes.any { it.key == t.key } }, st.shuffle) }
                .distinctUntilChanged()
                .collect { @Suppress("DEPRECATION") session?.setCustomLayout(extraButtons()) }
        }
    }

    /** Like (not for radio) and Shuffle, shown by Android Auto beside the transport controls and in the notification. */
    @OptIn(UnstableApi::class)
    private fun extraButtons(): List<CommandButton> {
        val st = app.graph.player.state.value
        val track = st.track
        return buildList {
            if (track != null && track.sourceId != RadioProvider.ID) {
                val liked = app.graph.likes.isLiked(track)
                add(CommandButton.Builder(if (liked) CommandButton.ICON_HEART_FILLED else CommandButton.ICON_HEART_UNFILLED)
                    .setDisplayName(if (liked) "Unlike" else "Like").setSessionCommand(LIKE).build())
            }
            if (track != null) add(CommandButton.Builder(if (st.shuffle) CommandButton.ICON_SHUFFLE_ON else CommandButton.ICON_SHUFFLE_OFF)
                .setDisplayName(if (st.shuffle) "Shuffle off" else "Shuffle").setSessionCommand(SHUFFLE).build())
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        // Settings > "Keep playing after closing the app": when off, swiping Duet away stops the music.
        if (!app.graph.settings.flag(Flag.KeepPlayingInBackground).value) {
            // Stop everything, Spotify's hidden player included, and leave.
            app.graph.player.pause()
            stopSelf()
            return
        }
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        app.exoPlayer.onPlayerChanged = null
        scope.cancel()
        // The ExoPlayer belongs to the app, so release only the session.
        session?.release()
        session = null
        super.onDestroy()
    }

    /** Starts the song a controller (Android Auto, Assistant) picked by its media id. */
    private fun playById(mediaId: String) {
        shown[mediaId]?.let { (list, index) -> app.graph.player.playAll(list, index) }
    }

    /** Set after the car auto-start ran once, so reconnecting mid-drive doesn't restart a station you paused. */
    private var carAutoPlayed = false

    /** Settings > "Play a favourite station in the car": starts the favourite last listened to. */
    private fun autoPlayFavouriteStation() {
        val g = app.graph
        if (carAutoPlayed || !g.settings.flag(Flag.AutoPlayRadioInCar).value) return
        if (g.player.state.value.status == PlaybackStatus.Playing) return
        val favourites = g.favouriteStations.list.value
        val station = g.favouriteStations.lastListened(g.history.recent.value) ?: return
        carAutoPlayed = true
        // The other favourites follow in the queue, so next/previous on the car's screen switch station.
        g.player.playAll(favourites, favourites.indexOfFirst { it.key == station.key }.coerceAtLeast(0))
    }

    private inner class LibraryCallback : MediaLibrarySession.Callback {
        @OptIn(UnstableApi::class)
        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
            if (controller.packageName == ANDROID_AUTO_PACKAGE) scope.launch { autoPlayFavouriteStation() }
            val base = super.onConnect(session, controller)
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(base.availableSessionCommands.buildUpon().add(LIKE).add(SHUFFLE).build())
                .setAvailablePlayerCommands(base.availablePlayerCommands)
                .setCustomLayout(extraButtons())
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession, controller: MediaSession.ControllerInfo, customCommand: SessionCommand, args: Bundle,
        ): ListenableFuture<SessionResult> {
            val g = app.graph
            when (customCommand.customAction) {
                LIKE.customAction -> g.player.state.value.track?.let(g::toggleLike)
                SHUFFLE.customAction -> g.player.toggleShuffle()
                else -> return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED))
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession, browser: MediaSession.ControllerInfo, params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> {
            // Root hints: search works; folders show as grids, songs as lists, unless an item says otherwise.
            val extras = Bundle().apply {
                putBoolean(SEARCH_SUPPORTED, true)
                putInt(BROWSABLE_HINT, STYLE_GRID)
                putInt(PLAYABLE_HINT, STYLE_LIST)
            }
            return Futures.immediateFuture(LibraryResult.ofItem(folder(ROOT, "Duet"), LibraryParams.Builder().setExtras(extras).build()))
        }

        override fun onGetItem(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, mediaId: String) =
            Futures.immediateFuture(LibraryResult.ofItem(folder(mediaId, mediaId), null))

        override fun onGetChildren(
            session: MediaLibrarySession, browser: MediaSession.ControllerInfo, parentId: String,
            page: Int, pageSize: Int, params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val g = app.graph
            return when {
                // Tabs (Auto shows up to four browsable root children as tabs).
                parentId == ROOT -> ok(listOf(
                    folder(HOME, "Home", style = STYLE_LIST), folder(STATIONS, "Radio", playable = STYLE_GRID),
                    folder(RECENT, "Recent", style = STYLE_LIST), folder(LIBRARY, "Library", style = STYLE_GRID),
                ))
                parentId == HOME -> {
                    val quick = g.home.quickPicks.value
                    val shelves = g.home.visibleShelves(g.home.shelves.value, quick)
                    val hidden = g.settings.hiddenSections.value
                    ok(buildList {
                        // Quick picks play straight from Home: one tap, no folder to open first.
                        quick.take(8).forEachIndexed { i, t -> add(song(remember("quick", quick, i), t, group = "Quick picks")) }
                        val seeds = g.home.radioSeeds(g.history.recent.value)
                        if (HomeSection.Radio.id !in hidden && seeds.isNotEmpty())
                            add(folder(MIXES, "Mixes", subtitle = "Songs like the ones you played", style = STYLE_GRID, art = seeds.first().artworkUrl, group = "More"))
                        shelves.forEachIndexed { i, sh ->
                            if ("${sh.sourceId}:${sh.title}" in hidden) return@forEachIndexed
                            add(folder(SHELF_PREFIX + i, sh.title, style = STYLE_GRID,
                                art = sh.collections.firstOrNull()?.artworkUrl ?: sh.songs.firstOrNull()?.artworkUrl, group = "More"))
                        }
                    })
                }
                parentId == STATIONS -> stationItems()
                parentId == MIXES -> ok(g.home.radioSeeds(g.history.recent.value).map { seed ->
                    mixes[MIX_PREFIX + seed.key] = seed
                    val artist = LyricsService.firstArtist(seed.artist).ifBlank { seed.title }
                    folder(MIX_PREFIX + seed.key, "$artist Mix", subtitle = "Based on ${seed.title}", style = STYLE_LIST, art = seed.artworkUrl)
                })
                parentId.startsWith(MIX_PREFIX) -> {
                    val seed = mixes[parentId] ?: return ok(emptyList())
                    val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
                    scope.launch {
                        val tracks = runCatching { g.home.radioFor(seed) }.getOrDefault(listOf(seed))
                        future.set(LibraryResult.ofItemList(ImmutableList.copyOf(tracks.mapIndexed { i, t -> song(remember(parentId, tracks, i), t) }), null))
                    }
                    future
                }
                parentId.startsWith(SHELF_PREFIX) -> {
                    val sh = g.home.visibleShelves(g.home.shelves.value, g.home.quickPicks.value).getOrNull(parentId.removePrefix(SHELF_PREFIX).toIntOrNull() ?: -1)
                    if (sh == null) ok(emptyList()) else {
                        val songItems = sh.songs.mapIndexed { i, t -> song(remember("sh${sh.title}", sh.songs, i), t) }
                        ok(songItems + sh.collections.map { collectionFolder(it) })
                    }
                }
                parentId == RECENT -> songs(g.history.recent.value.take(100), "recent")
                parentId == LIBRARY -> ok(buildList {
                    // Your own lists first: liked songs and Duet playlists, then what's on the phone, then the services'.
                    g.likes.list.value.takeIf { it.isNotEmpty() }?.let { add(folder(LIKED, "Liked songs", subtitle = songCount(it.size), style = STYLE_LIST, art = it.first().artworkUrl)) }
                    g.playlists.list.value.forEach { p ->
                        add(folder(PLAYLIST_PREFIX + p.id, p.name, subtitle = songCount(p.tracks.size), style = STYLE_LIST, art = p.tracks.firstOrNull()?.artworkUrl))
                    }
                    add(folder(DOWNLOADED, "Downloaded", style = STYLE_LIST))
                    add(folder(CACHED, "Cached", style = STYLE_LIST))
                    g.library.items.value.forEach { add(collectionFolder(it)) }
                })
                parentId == LIKED -> songs(g.likes.list.value, "liked")
                parentId.startsWith(PLAYLIST_PREFIX) -> songs(g.playlists[parentId.removePrefix(PLAYLIST_PREFIX)]?.tracks.orEmpty(), parentId)
                parentId == DOWNLOADED -> songs(g.downloads?.saved?.value.orEmpty(), "downloaded")
                parentId == CACHED -> {
                    val keys = g.cache?.cachedKeys?.value.orEmpty()
                    songs(g.history.recent.value.filter { it.key in keys }, "cached")
                }
                parentId.startsWith(COLLECTION_PREFIX) -> {
                    val c = collections[parentId] ?: return ok(emptyList())
                    val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
                    scope.launch {
                        val tracks = runCatching { g.sources[c.sourceId]?.collectionTracks(c).orEmpty() }.getOrDefault(emptyList())
                        future.set(LibraryResult.ofItemList(ImmutableList.copyOf(tracks.mapIndexed { i, t -> song(remember(c.key, tracks, i), t) }), null))
                    }
                    future
                }
                parentId.startsWith(IDEA_PREFIX) -> searchItems(parentId.removePrefix(IDEA_PREFIX), params)
                else -> ok(emptyList())
            }
        }

        override fun onSearch(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, query: String, params: LibraryParams?): ListenableFuture<LibraryResult<Void>> {
            scope.launch {
                val count = runCatching { search(query).size }.getOrDefault(0)
                session.notifySearchResultChanged(browser, query, count, params)
            }
            return Futures.immediateFuture(LibraryResult.ofVoid())
        }

        override fun onGetSearchResult(
            session: MediaLibrarySession, browser: MediaSession.ControllerInfo, query: String,
            page: Int, pageSize: Int, params: LibraryParams?,
        ) = searchItems(query, params)

        /** "Play X" by voice arrives as a media item with only a search query; anything else is a picked media id. */
        override fun onAddMediaItems(
            mediaSession: MediaSession, controller: MediaSession.ControllerInfo, mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> {
            val first = mediaItems.firstOrNull() ?: return Futures.immediateFuture(mediaItems)
            val query = first.requestMetadata.searchQuery
            if (query != null) {
                scope.launch { app.graph.playFromVoice(query) }
            } else {
                playById(first.mediaId)
            }
            // The coordinator plays it; ExoPlayer gets the stream from there, not these items.
            return Futures.immediateFuture(mediaItems)
        }
    }

    /** Song mixes shown in the car, by media id. */
    private val mixes = HashMap<String, Track>()

    /** The Radio tab: favourite stations, then recently played ones, then the most popular in the phone's country. */
    private fun stationItems(): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        val g = app.graph
        val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
        scope.launch {
            val favourites = g.favouriteStations.list.value
            val seen = favourites.map { it.key }.toMutableSet()
            val recent = g.history.recent.value.filter { it.sourceId == RadioProvider.ID && seen.add(it.key) }.take(12)
            val country = java.util.Locale.getDefault().country.uppercase().takeIf { it.length == 2 }
            val popular = runCatching { (g.sources[RadioProvider.ID] as? RadioProvider)?.find(country = country).orEmpty() }
                .getOrDefault(emptyList()).filter { seen.add(it.key) }.take(30)
            val where = country?.let { java.util.Locale("", it).displayCountry } ?: "the world"
            val items = favourites.mapIndexed { i, t -> song(remember("fav", favourites, i), t, group = "Favourites") } +
                recent.mapIndexed { i, t -> song(remember("recentStations", recent, i), t, group = "Recently played") } +
                popular.mapIndexed { i, t -> song(remember("popular", popular, i), t, group = "Popular in $where") }
            future.set(LibraryResult.ofItemList(ImmutableList.copyOf(items), null))
        }
        return future
    }

    /** Collections shown in the car, by media id, so opening one can load its songs. */
    private val collections = HashMap<String, Collection>()

    private fun collectionFolder(c: Collection): MediaItem {
        val id = COLLECTION_PREFIX + c.key
        collections[id] = c
        return folder(id, c.title, subtitle = c.subtitle, style = STYLE_LIST, art = c.artworkUrl)
    }

    /** Remembers which list a song row came from, so picking it plays that list from there. */
    private fun remember(list: String, tracks: List<Track>, index: Int): String {
        val id = "$TRACK_PREFIX$list|${tracks[index].key}"
        shown[id] = tracks to index
        return id
    }

    private fun songs(tracks: List<Track>, list: String) = ok(tracks.mapIndexed { i, t -> song(remember(list, tracks, i), t) })

    private fun searchItems(query: String, params: MediaLibraryService.LibraryParams?): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
        scope.launch {
            val tracks = runCatching { search(query) }.getOrDefault(emptyList())
            future.set(LibraryResult.ofItemList(ImmutableList.copyOf(tracks.mapIndexed { i, t -> song(remember("search:$query", tracks, i), t) }), params))
        }
        return future
    }

    private suspend fun search(query: String): List<Track> =
        searchAll(app.graph.sources, query)

    /** [style] lays out the folders inside, [playable] the songs or stations inside; [group] is a heading in the car's list. */
    private fun folder(
        id: String, title: String, subtitle: String? = null, style: Int = STYLE_GRID, playable: Int = STYLE_LIST,
        art: String? = null, group: String? = null,
    ) = MediaItem.Builder().setMediaId(id).setMediaMetadata(
        MediaMetadata.Builder().setTitle(title).setSubtitle(subtitle).setIsBrowsable(true).setIsPlayable(false)
            .setArtworkUri(ArtworkProvider.uriFor(this, art))
            .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
            .setExtras(Bundle().apply {
                putInt(BROWSABLE_HINT, style); putInt(PLAYABLE_HINT, playable)
                group?.let { putString(GROUP_TITLE, it) }
            })
            .build(),
    ).build()

    private fun song(id: String, track: Track, group: String? = null) = MediaItem.Builder().setMediaId(id).setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(track.title).setArtist(track.artist).setAlbumTitle(track.album)
            .setArtworkUri(ArtworkProvider.uriFor(this, track.artworkUrl))
            .setIsBrowsable(false).setIsPlayable(true)
            .setMediaType(if (track.sourceId == RadioProvider.ID) MediaMetadata.MEDIA_TYPE_RADIO_STATION else MediaMetadata.MEDIA_TYPE_MUSIC)
            .apply { group?.let { setExtras(Bundle().apply { putString(GROUP_TITLE, it) }) } }
            .build(),
    ).build()

    @OptIn(UnstableApi::class)
    private class QueuePlayer(player: Player, private val queue: PlaybackCoordinator) : ForwardingPlayer(player) {
        override fun getAvailableCommands(): Player.Commands = super.getAvailableCommands().buildUpon()
            .addAll(
                Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
            ).build()

        override fun isCommandAvailable(command: Int) = availableCommands.contains(command)

        override fun seekToNext() = queue.next()
        override fun seekToNextMediaItem() = queue.next()
        override fun seekToPrevious() = queue.previous()
        override fun seekToPreviousMediaItem() = queue.previous()

        // A controller's "play these items" was already handed to the coordinator in onAddMediaItems.
        override fun setMediaItems(mediaItems: MutableList<MediaItem>, resetPosition: Boolean) = Unit
        override fun setMediaItems(mediaItems: MutableList<MediaItem>, startIndex: Int, startPositionMs: Long) = Unit
        override fun addMediaItems(index: Int, mediaItems: MutableList<MediaItem>) = Unit

        override fun getMediaMetadata(): MediaMetadata {
            val track = queue.state.value.track ?: return super.getMediaMetadata()
            return metadata(track, playable = true)
        }
    }

    companion object {
        /** Android Auto's app, which connects to the media session when the phone joins the car. */
        private const val ANDROID_AUTO_PACKAGE = "com.google.android.projection.gearhead"
        private const val ROOT = "root"
        private const val HOME = "home"
        private const val SHELF_PREFIX = "shelf:"
        private const val STATIONS = "stations"
        private const val MIXES = "mixes"
        private const val MIX_PREFIX = "mix:"
        private const val RECENT = "recent"
        private const val LIBRARY = "library"
        private const val DOWNLOADED = "downloaded"
        private const val LIKED = "liked"
        private const val PLAYLIST_PREFIX = "pl:"
        private val LIKE = SessionCommand("app.duet.LIKE", Bundle.EMPTY)
        private val SHUFFLE = SessionCommand("app.duet.SHUFFLE", Bundle.EMPTY)

        private fun songCount(n: Int) = if (n == 1) "1 song" else "$n songs"
        private const val CACHED = "cached"
        private const val COLLECTION_PREFIX = "c:"
        private const val IDEA_PREFIX = "idea:"
        private const val TRACK_PREFIX = "t:"

        // androidx.media.utils.MediaConstants keys and values for Android Auto.
        private const val SEARCH_SUPPORTED = "android.media.browse.SEARCH_SUPPORTED"
        private const val BROWSABLE_HINT = "android.media.browse.CONTENT_STYLE_BROWSABLE_HINT"
        private const val PLAYABLE_HINT = "android.media.browse.CONTENT_STYLE_PLAYABLE_HINT"
        private const val GROUP_TITLE = "android.media.browse.CONTENT_STYLE_GROUP_TITLE_HINT"
        private const val STYLE_LIST = 1
        private const val STYLE_GRID = 2

        /** Called when playback starts; Media3 promotes the service to foreground while playing. */
        fun start(context: Context) {
            context.startService(Intent(context, PlaybackService::class.java))
        }

        private fun ok(items: List<MediaItem>) = Futures.immediateFuture(LibraryResult.ofItemList(ImmutableList.copyOf(items), null))

        private fun metadata(track: Track, playable: Boolean) = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
            .setAlbumTitle(track.album)
            .setArtworkUri(track.artworkUrl?.let(Uri::parse))
            .setIsBrowsable(false)
            .setIsPlayable(playable)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .build()
    }
}

/** Every source that can search, results interleaved (the same order as the app's Search). */
internal suspend fun searchAll(sources: SourceRegistry, query: String): List<Track> =
    sources.music.flatMap { runCatching { it.search(query) }.getOrDefault(emptyList()) }
