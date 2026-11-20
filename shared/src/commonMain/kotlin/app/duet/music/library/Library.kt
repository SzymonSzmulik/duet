package app.duet.music.library

import app.duet.music.domain.Collection
import app.duet.music.extension.SourceRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * The user's playlists, albums and artists from every signed-in service, kept in step with them:
 * loaded at start, again whenever a sign-in changes, and on [refresh].
 */
class LibraryModel(private val sources: SourceRegistry, private val scope: CoroutineScope) {
    private val _items = MutableStateFlow<List<Collection>>(emptyList())
    val items: StateFlow<List<Collection>> = _items.asStateFlow()
    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    init {
        val signIns = sources.providers.mapNotNull { it.account?.signedIn }
        scope.launch {
            if (signIns.isEmpty()) refresh() else combine(signIns) { it.toList() }.distinctUntilChanged().collect { refresh() }
        }
    }

    fun refresh() {
        scope.launch {
            _syncing.value = true
            val all = sources.providers.map { p -> async { runCatching { p.library() }.getOrDefault(emptyList()) } }.awaitAll()
            _items.value = all.flatten()
            _syncing.value = false
        }
    }

    /** Takes [item] out of your library on its service (deletes your own playlist); true when the service took it. */
    suspend fun remove(item: Collection): Boolean {
        val source = sources[item.sourceId] ?: return false
        val ok = try { source.removeFromLibrary(item) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { false }
        if (ok) _items.value = _items.value.filter { it.key != item.key }
        return ok
    }
}

/** Songs the player keeps on disk after streaming them, so they play again without the network. */
interface MediaCache {
    /** Keys ([app.duet.music.domain.Track.key]) of songs held completely in the cache. */
    val cachedKeys: StateFlow<Set<String>>
    fun bytesUsed(): Long
    fun clear()
}
