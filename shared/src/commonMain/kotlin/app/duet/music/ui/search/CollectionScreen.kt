package app.duet.music.ui.search

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.duet.music.domain.Collection
import app.duet.music.domain.SearchKind
import app.duet.music.domain.Track
import app.duet.music.extension.PlaylistEdit
import app.duet.music.extension.SourceRegistry
import app.duet.music.ui.library.AddSongsSheet
import app.duet.music.ui.library.NameDialog
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import app.duet.music.ui.components.Artwork
import app.duet.music.ui.components.EmptyState
import app.duet.music.ui.components.PageScaffold
import app.duet.music.ui.components.PrimaryButton
import app.duet.music.ui.components.RemoveDownloadDialog
import app.duet.music.ui.components.trackClick
import app.duet.music.ui.components.SourceLine
import app.duet.music.ui.components.diagonal
import app.duet.music.ui.player.formatTime
import app.duet.music.ui.theme.DuetColors
import app.duet.music.ui.theme.duetShape
import app.duet.music.ui.theme.DuetDimens
import app.duet.music.ui.theme.DuetIcons
import app.duet.music.ui.theme.DuetType

/** An album's or playlist's songs, or an artist's top songs. Tap a song to play from there. */
@Composable
fun CollectionScreen(
    collection: Collection, sources: SourceRegistry, playingKey: String?, onPlay: (List<Track>, Int) -> Unit, onBack: () -> Unit,
    /** Songs already on the device (Downloaded, Cached): shown as they are, not loaded from a source. */
    preloaded: List<Track>? = null,
    /** Downloaded page: shows a remove button on each song that deletes its file. */
    onRemove: ((Track) -> Unit)? = null,
    /** Downloads ask first (a file is deleted); a playlist removes the song straight away. */
    askBeforeRemove: Boolean = true,
    /** Albums and playlists from a service: "Save to my playlists" copies the songs into a new Duet playlist. */
    onCopy: ((List<Track>) -> Unit)? = null,
    /** After a change to your playlist on the service (so Library can reload it). */
    onEdited: () -> Unit = {},
    /** Deletes your playlist on the service (or unfollows it); true when the service took it. Closes the page then. */
    onRemoveFromLibrary: (suspend (Collection) -> Boolean)? = null,
) {
    val source = sources[collection.sourceId]
    // Your own playlist on a service you're signed in to: add, take out, move and rename here; the service checks you may.
    val editable = preloaded == null && source != null && source.account?.signedIn?.collectAsState()?.value == true &&
        source.addablePlaylists(listOf(collection)).isNotEmpty()
    val scope = rememberCoroutineScope()
    var editing by remember(collection.key) { mutableStateOf(false) }
    var busy by remember(collection.key) { mutableStateOf(false) }
    var note by remember(collection.key) { mutableStateOf<String?>(null) }
    var title by remember(collection.key) { mutableStateOf(collection.title) }
    var adding by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    val refused = "${source?.manifest?.name ?: "The service"} didn't take that change. You can only edit playlists you made or can edit."
    var removing by remember { mutableStateOf<Track?>(null) }
    removing?.let { t -> RemoveDownloadDialog(t.title, onConfirm = { onRemove?.invoke(t) }, onDismiss = { removing = null }) }
    var tracks by remember(collection.key) { mutableStateOf(preloaded) }
    if (preloaded != null) tracks = preloaded
    var failed by remember(collection.key) { mutableStateOf(false) }
    if (preloaded == null) LaunchedEffect(collection.key) {
        runCatching { source?.collectionTracks(collection).orEmpty() }
            .onSuccess { tracks = it }.onFailure { failed = true; tracks = emptyList() }
    }
    /** Sends [edit] to the service and runs [onOk] once it took it. One change at a time. */
    fun change(edit: PlaylistEdit, onOk: () -> Unit) {
        if (busy || source == null) return
        busy = true; note = null
        scope.launch {
            val ok = try { source.editPlaylist(collection, edit) } catch (e: CancellationException) { throw e } catch (e: Exception) { false }
            if (ok) { onOk(); onEdited() } else note = refused
            busy = false
        }
    }
    if (adding && source != null) AddSongsSheet(
        SourceRegistry(listOf(source)), have = tracks.orEmpty().mapTo(HashSet()) { it.key },
        onAdd = { t ->
            scope.launch {
                val ok = try { source.addToPlaylist(collection, t) } catch (e: CancellationException) { throw e } catch (e: Exception) { false }
                if (ok) { tracks = tracks.orEmpty() + t; onEdited() } else note = refused
            }
        },
        onDismiss = { adding = false },
    )
    var deleting by remember { mutableStateOf(false) }
    if (deleting && source != null && onRemoveFromLibrary != null) app.duet.music.ui.library.RemoveFromLibraryDialog(
        collection, source.manifest.name, onDismiss = { deleting = false },
        onConfirm = {
            busy = true; note = null
            scope.launch { if (onRemoveFromLibrary(collection)) onBack() else note = refused; busy = false }
        },
    )
    if (renaming) NameDialog("Rename playlist", title, onDismiss = { renaming = false }, onDone = { name ->
        renaming = false
        val n = name.trim()
        if (n.isNotEmpty() && n != title) change(PlaylistEdit.Rename(n)) { title = n }
    })
    val what = when (collection.kind) {
        SearchKind.Artists -> "Top songs"
        SearchKind.Albums -> "Album"
        SearchKind.Playlists -> "Playlist"
        SearchKind.Songs, SearchKind.Videos -> ""
    }
    PageScaffold(title, onBack, subtitle = listOf(what, collection.subtitle.removePrefix("$what · "), source?.manifest?.name).filterNot { it.isNullOrBlank() }.joinToString(" · "), scroll = false) {
        val list = tracks
        when {
            list == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = DuetColors.Accent) }
            list.isEmpty() && editable -> Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                EmptyState(icon = DuetIcons.Add, title = "No songs yet", body = "Tap Add songs to fill it.")
                EditChip("+ Add songs") { adding = true }
                note?.let { Text(it, style = DuetType.RowSub, color = DuetColors.Accent) }
            }
            list.isEmpty() -> EmptyState(
                icon = DuetIcons.Info,
                title = if (failed) "Couldn't load the songs" else "No songs here",
                body = if (failed) "Check your internet and open it again." else "This one has no songs Duet can play.",
            )
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                item {
                    // A big cover in the middle, then the count and the buttons, as album pages in music apps.
                    Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            // A computer's wide window would make a 62%-wide cover enormous: cap it.
                            val side = (maxWidth * 0.62f).value.toInt().let { if (app.duet.music.ui.desktop.LocalDesktop.current) minOf(it, 240) else it }
                            Artwork(collection.artworkUrl ?: list.firstOrNull()?.artworkUrl, diagonal(0xFF3A3A3A, 0xFF111111), size = side,
                                radius = if (collection.kind == SearchKind.Artists) side / 2 else 12)
                        }
                        Text((if (list.size == 1) "1 song" else "${list.size} songs") + (list.mapNotNull { it.durationMs }.sum().takeIf { it > 0 }?.let { " · " + (it / 60000) + " min" } ?: ""),
                            style = DuetType.RowSub, color = DuetColors.TextDim)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            PrimaryButton("Play all", onClick = { onPlay(list, 0) })
                            Text(
                                "Shuffle", style = DuetType.Chip, color = DuetColors.Text,
                                modifier = Modifier.clip(duetShape(8)).border(1.dp, DuetColors.Line, duetShape(8))
                                    .clickable(role = Role.Button) { onPlay(list.shuffled(), 0) }.padding(horizontal = 14.dp, vertical = 12.dp),
                            )
                        }
                        if (editable) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            EditChip("+ Add songs") { adding = true }
                            EditChip(if (editing) "Done" else "Edit") { editing = !editing }
                            if (editing) EditChip("Rename") { renaming = true }
                        }
                        if (editing && onRemoveFromLibrary != null && source?.canRemoveFromLibrary(collection) == true) EditChip("Delete playlist") { if (!busy) deleting = true }
                        note?.let { Text(it, style = DuetType.RowSub, color = DuetColors.Accent, modifier = Modifier.padding(horizontal = 8.dp)) }
                        if (onCopy != null && collection.kind != SearchKind.Artists) Text(
                            "+ Save to my playlists", style = DuetType.Chip, color = DuetColors.TextDim,
                            modifier = Modifier.clip(duetShape(20)).clickable(role = Role.Button) { onCopy(list) }.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
                itemsIndexed(list, key = { i, t -> "$i-${t.key}" }) { i, t ->
                    Row(
                        Modifier.fillMaxWidth().clip(duetShape(DuetDimens.RadiusTile))
                            .then(if (editing) Modifier else Modifier.trackClick(t) { onPlay(list, i) }).padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Artwork(t.artworkUrl, diagonal(0xFF3A3A3A, 0xFF111111), size = 44, radius = 6)
                        Column(Modifier.weight(1f)) {
                            Text(t.title, style = DuetType.RowTitle, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                color = if (t.key == playingKey) DuetColors.Accent else DuetColors.Text)
                            SourceLine(t.artist)
                        }
                        if (editing) {
                            fun swapped(l: List<Track>, a: Int) = l.toMutableList().also { m -> val x = m[a]; m[a] = m[a - 1]; m[a - 1] = x }
                            IconButton(onClick = { change(PlaylistEdit.MoveUp(i)) { tracks = tracks?.let { swapped(it, i) } } }, enabled = !busy && i > 0) {
                                Icon(DuetIcons.ChevronDown, "Move ${t.title} up", tint = DuetColors.Text, modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = 180f })
                            }
                            IconButton(onClick = { change(PlaylistEdit.MoveUp(i + 1)) { tracks = tracks?.let { swapped(it, i + 1) } } }, enabled = !busy && i < list.lastIndex) {
                                Icon(DuetIcons.ChevronDown, "Move ${t.title} down", tint = DuetColors.Text, modifier = Modifier.size(20.dp))
                            }
                            IconButton(onClick = { change(PlaylistEdit.Remove(i, t)) { tracks = tracks?.filterIndexed { j, _ -> j != i } } }, enabled = !busy) {
                                Icon(DuetIcons.Clear, "Remove ${t.title} from playlist", tint = DuetColors.TextDim, modifier = Modifier.size(18.dp))
                            }
                        } else t.durationMs?.let { Text(formatTime(it), style = DuetType.RowSub, color = DuetColors.TextDim) }
                        if (onRemove != null) IconButton(onClick = { if (askBeforeRemove) removing = t else onRemove(t) }) {
                            Icon(DuetIcons.Clear, if (askBeforeRemove) "Remove download" else "Remove from playlist", tint = DuetColors.TextDim, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EditChip(label: String, onClick: () -> Unit) =
    Text(label, style = DuetType.Chip, color = DuetColors.Text, modifier = Modifier.clip(duetShape(8)).border(1.dp, DuetColors.Line, duetShape(8))
        .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp))
