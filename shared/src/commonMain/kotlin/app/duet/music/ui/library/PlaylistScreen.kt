package app.duet.music.ui.library

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.duet.music.domain.Track
import app.duet.music.extension.SourceRegistry
import app.duet.music.library.Playlists
import app.duet.music.ui.components.Artwork
import app.duet.music.ui.components.EmptyState
import app.duet.music.ui.components.PageScaffold
import app.duet.music.ui.components.PrimaryButton
import app.duet.music.ui.components.diagonal
import app.duet.music.ui.components.trackClick
import app.duet.music.ui.player.formatTime
import app.duet.music.ui.search.SearchField
import app.duet.music.ui.theme.DuetColors
import app.duet.music.ui.theme.DuetIcons
import app.duet.music.ui.theme.DuetType
import app.duet.music.ui.theme.duetShape
import kotlinx.coroutines.delay

/**
 * One of your playlists: Play and Add songs on top; Edit to rename it, move songs up or down and take
 * songs out. Songs from Spotify and YouTube Music sit side by side.
 */
@Composable
fun PlaylistScreen(
    id: String, playlists: Playlists, sources: SourceRegistry, playingKey: String?,
    onPlay: (List<Track>, Int) -> Unit, onBack: () -> Unit,
) {
    val p = playlists.list.collectAsState().value.firstOrNull { it.id == id }
    if (p == null) { LaunchedEffect(Unit) { onBack() }; return }
    var editing by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    if (deleting) DeletePlaylistDialog(p.name, onConfirm = { playlists.delete(id) }, onDismiss = { deleting = false })
    if (adding) AddSongsSheet(sources, have = p.tracks.mapTo(HashSet()) { it.key }, onAdd = { playlists.add(id, it) }, onDismiss = { adding = false })
    if (renaming) NameDialog("Rename playlist", p.name, onDone = { playlists.rename(id, it); renaming = false }, onDismiss = { renaming = false })

    PageScaffold(p.name, onBack, subtitle = "Your playlist · " + if (p.tracks.size == 1) "1 song" else "${p.tracks.size} songs", scroll = false) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp)) {
            item {
                Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Artwork(p.tracks.firstOrNull()?.artworkUrl, diagonal(0xFF3A3A3A, 0xFF111111), size = 96, radius = 10)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (p.tracks.isNotEmpty()) PrimaryButton("Play", onClick = { onPlay(p.tracks, 0) })
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Chip("+ Add songs") { adding = true }
                            Chip(if (editing) "Done" else "Edit") { editing = !editing }
                        }
                    }
                }
                if (editing) Row(Modifier.padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("Rename") { renaming = true }
                    Chip("Delete playlist") { deleting = true }
                }
            }
            if (p.tracks.isEmpty()) item {
                EmptyState(icon = DuetIcons.Add, title = "No songs yet", body = "Tap Add songs, or hold any song and choose Add to playlist.")
            }
            itemsIndexed(p.tracks, key = { _, t -> t.key }) { i, t ->
                Row(
                    Modifier.fillMaxWidth().animateItem().clip(duetShape(8))
                        .then(if (editing) Modifier else Modifier.trackClick(t) { onPlay(p.tracks, i) }).padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Artwork(t.artworkUrl, diagonal(0xFF3A3A3A, 0xFF111111), size = 44, radius = 6)
                    Column(Modifier.weight(1f)) {
                        Text(t.title, style = DuetType.RowTitle, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (t.key == playingKey) DuetColors.Accent else DuetColors.Text)
                        Text(listOfNotNull(t.artist, sources[t.sourceId]?.shortName).joinToString(" · "), style = DuetType.RowSub, color = DuetColors.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (editing) {
                        IconButton(onClick = { playlists.move(id, i, i - 1) }, enabled = i > 0) {
                            Icon(DuetIcons.ChevronDown, "Move ${t.title} up", tint = DuetColors.Text, modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = 180f })
                        }
                        IconButton(onClick = { playlists.move(id, i, i + 1) }, enabled = i < p.tracks.size - 1) {
                            Icon(DuetIcons.ChevronDown, "Move ${t.title} down", tint = DuetColors.Text, modifier = Modifier.size(20.dp))
                        }
                        IconButton(onClick = { playlists.remove(id, t) }) { Icon(DuetIcons.Clear, "Remove ${t.title} from playlist", tint = DuetColors.TextDim, modifier = Modifier.size(18.dp)) }
                    } else t.durationMs?.let { Text(formatTime(it), style = DuetType.RowSub, color = DuetColors.TextDim) }
                }
            }
        }
    }
}

@Composable
private fun Chip(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) =
    Text(label, style = DuetType.Chip, color = DuetColors.Text, modifier = modifier.heightIn(min = 40.dp).clip(duetShape(8)).border(1.dp, DuetColors.Line, duetShape(8))
        .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp))

/** Search every service you can play from and add songs with one tap each; the sheet stays open. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSongsSheet(sources: SourceRegistry, have: Set<String>, onAdd: (Track) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Track>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    val added = remember { mutableStateOf(setOf<String>()) }
    LaunchedEffect(query) {
        if (query.isBlank()) { results = emptyList(); return@LaunchedEffect }
        delay(350) // wait for typing to pause
        loading = true
        results = sources.music.filter { it.playableNow }
            .flatMap { s -> runCatching { s.search(query).take(10) }.getOrDefault(emptyList()) }
        loading = false
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = DuetColors.Surface, contentColor = DuetColors.Text, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().heightIn(min = 400.dp).padding(horizontal = 16.dp)) {
            Text(DuetType.titled("Add songs"), style = DuetType.SectionTitle, modifier = Modifier.padding(bottom = 10.dp))
            SearchField(query, { query = it }, onSubmit = {}, placeholder = "Find songs to add")
            Spacer(Modifier.height(8.dp))
            if (loading && results.isEmpty()) Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = DuetColors.Accent) }
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 520.dp)) {
                itemsIndexed(results, key = { i, t -> "$i-${t.key}" }) { _, t ->
                    val inIt = t.key in have || t.key in added.value
                    Row(
                        Modifier.fillMaxWidth().clip(duetShape(8)).clickable(enabled = !inIt, role = Role.Button) { onAdd(t); added.value = added.value + t.key }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Artwork(t.artworkUrl, diagonal(0xFF3A3A3A, 0xFF111111), size = 44, radius = 6)
                        Column(Modifier.weight(1f)) {
                            Text(t.title, style = DuetType.RowTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(listOfNotNull(t.artist, sources[t.sourceId]?.shortName).joinToString(" · "), style = DuetType.RowSub, color = DuetColors.TextDim, maxLines = 1)
                        }
                        Icon(if (inIt) DuetIcons.Check else DuetIcons.Add, if (inIt) "Added" else "Add ${t.title}",
                            tint = if (inIt) DuetColors.Accent else DuetColors.Text, modifier = Modifier.size(22.dp))
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Asks for a name (new playlist, rename). */
@Composable
fun NameDialog(title: String, initial: String, onDone: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DuetColors.SurfaceRaised,
        titleContentColor = DuetColors.Text,
        title = { Text(title) },
        text = {
            androidx.compose.material3.OutlinedTextField(
                name, { name = it }, singleLine = true, placeholder = { Text("Name", color = DuetColors.TextDim) },
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    focusedTextColor = DuetColors.Text, unfocusedTextColor = DuetColors.Text,
                    focusedBorderColor = DuetColors.Accent, unfocusedBorderColor = DuetColors.Line, cursorColor = DuetColors.Accent,
                ),
            )
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { onDone(name) }) { Text("Save", color = DuetColors.Accent) } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel", color = DuetColors.TextDim) } },
    )
}
