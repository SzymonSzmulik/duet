package app.duet.music.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.duet.music.domain.Collection
import app.duet.music.domain.SearchKind
import kotlinx.coroutines.launch
import app.duet.music.ui.components.Artwork
import app.duet.music.ui.components.ScreenHeader
import app.duet.music.ui.components.ServiceSwitch
import app.duet.music.ui.components.SourceLine
import app.duet.music.ui.components.diagonal
import app.duet.music.ui.theme.DuetColors
import app.duet.music.ui.theme.duetShape
import app.duet.music.ui.theme.DuetDimens
import app.duet.music.ui.theme.DuetIcons
import app.duet.music.ui.theme.DuetType

/** What Library shows. */
data class LibraryUi(
    val downloadedCount: Int,
    val cachedCount: Int,
    /** Synced playlists, albums and artists from the signed-in services (already filtered by [only]). */
    val synced: List<Collection>,
    val syncing: Boolean,
    val anySignedIn: Boolean,
    val services: List<Triple<String, String, Long>>,
    val only: String?,
    val error: String?,
    /** Anything played yet (History and Your stats show only then). */
    val hasHistory: Boolean = true,
)

/** Library: Downloaded and Cached on top, then everything synced from Spotify and YouTube Music. */
@Composable
fun LibraryScreen(
    ui: LibraryUi,
    contentPadding: PaddingValues,
    sourceName: (String) -> Pair<String, Long>,
    onOpenDownloaded: () -> Unit,
    onOpenCached: () -> Unit,
    onOpenCollection: (Collection) -> Unit,
    onOpenHistory: () -> Unit = {},
    onOpenStats: () -> Unit = {},
    likedCount: Int = 0,
    onOpenLiked: () -> Unit = {},
    playlists: List<app.duet.music.library.Playlist> = emptyList(),
    onOpenPlaylist: (String) -> Unit = {},
    onNewPlaylist: (String) -> Unit = {},
    onDeletePlaylist: (String) -> Unit = {},
    /** Whether a synced item can be taken out of your library on its service (long press). */
    canRemove: (Collection) -> Boolean = { false },
    /** Takes it out on the service; true when the service took it. */
    onRemove: suspend (Collection) -> Boolean = { false },
    onService: (String?) -> Unit,
    onSync: () -> Unit,
    onConnectAccounts: () -> Unit,
) {
    var naming by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var deleting by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<app.duet.music.library.Playlist?>(null) }
    var removing by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<Collection?>(null) }
    var removeNote by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    if (naming) app.duet.music.ui.NewPlaylistDialog(onCreate = { naming = false; onNewPlaylist(it) }, onDismiss = { naming = false })
    deleting?.let { p -> DeletePlaylistDialog(p.name, onConfirm = { onDeletePlaylist(p.id) }, onDismiss = { deleting = null }) }
    removing?.let { c ->
        val (service, _) = sourceName(c.sourceId)
        RemoveFromLibraryDialog(c, service, onDismiss = { removing = null }, onConfirm = {
            removeNote = null
            scope.launch { if (!onRemove(c)) removeNote = "$service didn't take that. Try again, or remove it in $service." }
        })
    }
    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = contentPadding.calculateBottomPadding()),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            ScreenHeader("Library") {
                if (ui.syncing) CircularProgressIndicator(color = DuetColors.TextDim, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                else if (ui.anySignedIn) IconButton(onClick = onSync) { Icon(DuetIcons.Repeat, contentDescription = "Sync now", tint = DuetColors.Text) }
            }
        }
        if (ui.services.size > 1) item { Box(Modifier.padding(vertical = 10.dp)) { ServiceSwitch(ui.services, ui.only, onService) } }
        ui.error?.let { msg -> item { Text(msg, style = DuetType.RowSub, color = DuetColors.Text, modifier = Modifier.padding(vertical = 6.dp)) } }

        if (likedCount > 0) item { LocalRow(DuetIcons.HeartFilled, "Liked songs", songs(likedCount), onOpenLiked) }
        // Only rows with something in them: a new user sees playlists and sign-in, not four empty lists.
        if (ui.downloadedCount > 0) item { LocalRow(DuetIcons.Download, "Downloaded", songs(ui.downloadedCount), onOpenDownloaded) }
        if (ui.cachedCount > 0) item { LocalRow(DuetIcons.Library, "Played offline", songs(ui.cachedCount) + " kept on this phone", onOpenCached) }
        if (ui.hasHistory) {
            item { LocalRow(DuetIcons.History, "History", "Everything you played, newest first", onOpenHistory) }
            item { LocalRow(DuetIcons.Stats, "Your stats", "Your most played songs and artists", onOpenStats) }
        }

        // Duet's own playlists: Spotify and YouTube Music songs together. Long press one to delete it.
        item { Text(DuetType.titled("Your playlists"), style = DuetType.SectionTitle, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)) }
        item { LocalRow(DuetIcons.Add, "New playlist", "Mix Spotify and YouTube Music songs") { naming = true } }
        items(playlists, key = { "pl-" + it.id }) { p ->
            Row(
                Modifier.fillMaxWidth().clip(duetShape(DuetDimens.RadiusTile))
                    .combinedClickable(role = Role.Button, onClickLabel = "Open", onLongClickLabel = "Delete", onLongClick = { deleting = p }) { onOpenPlaylist(p.id) }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Artwork(p.tracks.firstOrNull()?.artworkUrl, diagonal(0xFF2A2A2E, 0xFF151517), size = 56, radius = DuetDimens.RadiusTile)
                Column(Modifier.weight(1f)) {
                    Text(p.name, style = DuetType.RowTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Playlist · " + songs(p.tracks.size), style = DuetType.RowSub, color = DuetColors.TextDim)
                }
            }
        }

        if (!ui.anySignedIn) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(top = 12.dp).clip(duetShape(12)).background(DuetColors.Surface)
                        .clickable(role = Role.Button, onClick = onConnectAccounts).padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Sync your playlists", style = DuetType.RowTitle)
                        Text("Sign in to Spotify or YouTube Music", style = DuetType.RowSub, color = DuetColors.TextDim)
                    }
                    Icon(DuetIcons.ChevronRight, contentDescription = null, tint = DuetColors.TextDim)
                }
            }
        } else {
            item { Text(DuetType.titled("Playlists"), style = DuetType.SectionTitle, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)) }
            removeNote?.let { msg -> item { Text(msg, style = DuetType.RowSub, color = DuetColors.Accent, modifier = Modifier.padding(vertical = 4.dp)) } }
            items(ui.synced, key = { it.key }) { c ->
                val (name, color) = sourceName(c.sourceId)
                Row(
                    Modifier.fillMaxWidth().clip(duetShape(DuetDimens.RadiusTile))
                        .then(
                            if (canRemove(c)) Modifier.combinedClickable(role = Role.Button, onClickLabel = "Open", onLongClickLabel = "Remove from library", onLongClick = { removing = c }) { onOpenCollection(c) }
                            else Modifier.clickable(role = Role.Button, onClickLabel = "Open") { onOpenCollection(c) },
                        ).padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Artwork(c.artworkUrl, diagonal(0xFF2A2A2E, 0xFF151517), size = 56,
                        radius = if (c.kind == SearchKind.Artists) 28 else DuetDimens.RadiusTile)
                    Column(Modifier.weight(1f)) {
                        Text(c.title, style = DuetType.RowTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        SourceLine(listOf(c.subtitle, name).filter { it.isNotBlank() }.joinToString(" · "))
                    }
                }
            }
            if (ui.synced.isEmpty() && !ui.syncing) item {
                Text("Nothing synced yet.", style = DuetType.RowSub, color = DuetColors.TextDim, modifier = Modifier.padding(vertical = 8.dp))
            }
        }
    }
}

/** Downloaded / Cached: a coloured square with an icon, like a playlist cover. */
@Composable
private fun LocalRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(duetShape(DuetDimens.RadiusTile))
            .clickable(role = Role.Button, onClickLabel = "Open", onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // One quiet tile for every entry: smoked glass and a hairline, the icon in text colour (no rainbow squares).
        Box(
            Modifier.size(56.dp).clip(duetShape(DuetDimens.RadiusCard)).background(Color.White.copy(alpha = 0.06f))
                .border(1.dp, Color.White.copy(alpha = 0.1f), duetShape(DuetDimens.RadiusCard)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, tint = DuetColors.Text, modifier = Modifier.size(24.dp)) }
        Column(Modifier.weight(1f)) {
            Text(title, style = DuetType.RowTitle)
            Text(subtitle, style = DuetType.RowSub, color = DuetColors.TextDim)
        }
    }
}

private fun songs(n: Int) = if (n == 1) "1 song" else "$n songs"

/** Asks before one of Duet's playlists is deleted. */
@Composable
fun DeletePlaylistDialog(name: String, onConfirm: () -> Unit, onDismiss: () -> Unit) =
    app.duet.music.ui.components.ConfirmDialog("Delete playlist?", "\"$name\" will be deleted. The songs stay where they are.", "Delete", onConfirm, onDismiss)

/** Asks before a playlist, album or artist leaves your library on [service]; a playlist you made is deleted there. */
@Composable
fun RemoveFromLibraryDialog(c: Collection, service: String, onConfirm: () -> Unit, onDismiss: () -> Unit) = when (c.kind) {
    SearchKind.Playlists -> app.duet.music.ui.components.ConfirmDialog(
        "Delete playlist?",
        "\"${c.title}\" will be deleted from your $service account if you made it, on every device. If it's someone else's, you'll stop following it.",
        "Delete", onConfirm, onDismiss,
    )
    else -> app.duet.music.ui.components.ConfirmDialog(
        "Remove from library?", "\"${c.title}\" will leave your $service library.", "Remove", onConfirm, onDismiss,
    )
}
