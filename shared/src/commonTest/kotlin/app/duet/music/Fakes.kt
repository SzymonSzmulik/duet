package app.duet.music

import app.duet.music.domain.Track
import app.duet.music.extension.ExtensionManifest
import app.duet.music.extension.ExtensionPermissions
import app.duet.music.extension.Playable
import app.duet.music.extension.SourceProvider
import app.duet.music.player.PlaybackStatus
import app.duet.music.player.SourcePlayer
import app.duet.music.player.SourcePlayerState
import kotlinx.coroutines.flow.MutableStateFlow

class FakeSource(
    id: String,
    override val shortName: String = id,
    override val color: Long = 0xFFFF6B6B,
    private val results: List<Track> = emptyList(),
    private val failSearch: String? = null,
    private val failResolve: String? = null,
    capabilities: Set<app.duet.music.extension.Capability> = emptySet(),
    /** A signed-out account: null for none, true when signing in is needed to play, false when optional. */
    signedOutNeeded: Boolean? = null,
) : SourceProvider {
    override val account: app.duet.music.extension.SourceAccount? = signedOutNeeded?.let { needed ->
        object : app.duet.music.extension.SourceAccount {
            override val status = kotlinx.coroutines.flow.MutableStateFlow("Not signed in")
            override val signedIn = kotlinx.coroutines.flow.MutableStateFlow(false)
            override val neededToPlay = needed
            override fun signIn() {}
            override fun signOut() {}
        }
    }
    override val manifest = ExtensionManifest(id, shortName, "1", capabilities, ExtensionPermissions())
    override val accountStatus = "No account needed"
    override suspend fun search(query: String): List<Track> = failSearch?.let { throw IllegalStateException(it) } ?: results
    override suspend fun resolve(track: Track): Playable =
        failResolve?.let { throw IllegalStateException(it) } ?: Playable.Stream("https://${manifest.id}.test/${track.id}")
}

class FakePlayer(private val accepts: (Playable) -> Boolean = { it is Playable.Stream }) : SourcePlayer {
    override val state = MutableStateFlow(SourcePlayerState())
    val played = mutableListOf<Playable>()
    var stops = 0
    override fun canPlay(playable: Playable) = accepts(playable)
    override fun play(playable: Playable) {
        played += playable
        state.value = SourcePlayerState(PlaybackStatus.Playing, positionMs = 0, durationMs = 200_000)
    }
    override fun pause() { state.value = state.value.copy(status = PlaybackStatus.Paused) }
    override fun resume() { state.value = state.value.copy(status = PlaybackStatus.Playing) }
    override fun seekTo(positionMs: Long) { state.value = state.value.copy(positionMs = positionMs) }
    override fun stop() { stops++; state.value = SourcePlayerState() }
    /** Simulates the song finishing. */
    fun finish() { state.value = state.value.copy(status = PlaybackStatus.Ended) }
    /** Simulates playback progress. */
    fun advanceTo(positionMs: Long) { state.value = state.value.copy(positionMs = positionMs) }
}

fun track(source: String, id: String, title: String = "Song $id", artist: String = "Artist") =
    Track(sourceId = source, id = id, title = title, artist = artist, durationMs = 200_000)
