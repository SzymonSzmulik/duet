package app.duet.music.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.duet.music.extension.SourceRegistry
import app.duet.music.player.PlaybackCoordinator
import app.duet.music.player.PlayerState
import app.duet.music.ui.components.Artwork
import app.duet.music.ui.components.SourceLine
import app.duet.music.ui.components.diagonal
import app.duet.music.ui.theme.DuetColors
import app.duet.music.ui.theme.DuetDimens
import app.duet.music.ui.theme.DuetIcons
import app.duet.music.ui.theme.DuetType

/** The one queue, mixed sources. Tap to jump, X to remove. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheet(
    state: PlayerState, sources: SourceRegistry, controls: PlaybackCoordinator, onDismiss: () -> Unit,
    /** Saves the whole queue as a new Duet playlist; null hides the button. */
    onSaveAsPlaylist: (() -> Unit)? = null,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = DuetColors.Surface,
        contentColor = DuetColors.Text,
    ) {
        Column(Modifier.padding(horizontal = DuetDimens.ScreenPaddingX.dp)) {
            androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(DuetType.titled("Up next"), style = DuetType.SectionTitle, color = DuetColors.Text, modifier = Modifier.weight(1f))
                if (onSaveAsPlaylist != null && state.queue.size > 1) androidx.compose.material3.IconButton(onClick = onSaveAsPlaylist) {
                    androidx.compose.material3.Icon(DuetIcons.Add, contentDescription = "Save queue as a playlist", tint = DuetColors.Text)
                }
                if (state.index < state.queue.lastIndex) androidx.compose.material3.IconButton(onClick = controls::clearUpcoming) {
                    androidx.compose.material3.Icon(DuetIcons.Clear, contentDescription = "Clear the songs after this one", tint = DuetColors.Text)
                }
            }
            Text(
                "${state.queue.size} songs" + (if (state.shuffle) " · shuffled" else "") + ". Tap a song to jump to it, ↑ to move it up, × to remove it.",
                style = DuetType.RowSub, color = DuetColors.TextDim, modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        LazyColumn(
            state = rememberLazyListState(initialFirstVisibleItemIndex = state.index.coerceAtLeast(0)),
            modifier = Modifier.padding(bottom = 24.dp),
        ) {
            itemsIndexed(state.queue, key = { _, item -> item.id }) { i, item ->
                val current = i == state.index
                val p = sources[item.track.sourceId]
                Row(
                    Modifier.animateItem().fillMaxWidth()
                        .clickable(role = Role.Button, onClickLabel = "Play") { controls.skipTo(item.id) }
                        .padding(start = DuetDimens.ScreenPaddingX.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Artwork(item.track.artworkUrl, diagonal(0xFF3A3A3A, 0xFF111111), size = 48, radius = 6)
                    Column(Modifier.weight(1f)) {
                        Text(
                            (if (current) "Now playing: " else "") + item.track.title,
                            style = DuetType.RowTitle, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            color = if (current) DuetColors.Accent else DuetColors.Text,
                        )
                        SourceLine(
                            listOf(item.track.artist, p?.manifest?.name ?: item.track.sourceId).filter { it.isNotBlank() }.joinToString(" · "),
                        )
                    }
                    if (i > 0) IconButton(onClick = { controls.moveInQueue(item.id, -1) }, modifier = Modifier.size(DuetDimens.MinTouchTarget.dp)) {
                        Icon(DuetIcons.ChevronDown, contentDescription = "Move ${item.track.title} up", tint = DuetColors.TextDim,
                            modifier = Modifier.size(18.dp).graphicsLayer { rotationZ = 180f })
                    }
                    IconButton(onClick = { controls.removeFromQueue(item.id) }, modifier = Modifier.size(DuetDimens.MinTouchTarget.dp)) {
                        Icon(DuetIcons.Clear, contentDescription = "Remove ${item.track.title} from Up next", tint = DuetColors.TextDim, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}
