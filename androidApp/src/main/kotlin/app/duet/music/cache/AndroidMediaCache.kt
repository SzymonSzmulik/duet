package app.duet.music.cache

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import app.duet.music.library.MediaCache
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * ExoPlayer's disk cache for streamed songs (Media3 SimpleCache, 1 GB, least recently played go first).
 * Entries are keyed by the song ([app.duet.music.domain.Track.key]), not the stream URL, which changes
 * every time; a song played to the end is complete in the cache and then plays without the network.
 */
@OptIn(UnstableApi::class)
class AndroidMediaCache(context: Context) : MediaCache {
    private val dir = File(context.cacheDir, "media")
    val cache = SimpleCache(dir, LeastRecentlyUsedCacheEvictor(MAX_BYTES), StandaloneDatabaseProvider(context))

    private val _cachedKeys = MutableStateFlow(complete())
    override val cachedKeys: StateFlow<Set<String>> = _cachedKeys.asStateFlow()

    /** Called by the player when it stops loading: a song may just have become complete. */
    fun refresh() { _cachedKeys.value = complete() }

    override fun bytesUsed(): Long = cache.cacheSpace

    override fun clear() {
        cache.keys.toList().forEach { cache.removeResource(it) }
        refresh()
    }

    private fun complete(): Set<String> = cache.keys.filterTo(HashSet()) { key ->
        val length = ContentMetadata.getContentLength(cache.getContentMetadata(key))
        length > 0 && cache.isCached(key, 0, length)
    }

    private companion object {
        const val MAX_BYTES = 1L shl 30
    }
}
