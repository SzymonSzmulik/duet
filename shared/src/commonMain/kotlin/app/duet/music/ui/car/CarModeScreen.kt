package app.duet.music.ui.car

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.duet.music.domain.Track
import app.duet.music.player.PlaybackStatus
import app.duet.music.player.PlayerState
import app.duet.music.ui.components.Artwork
import app.duet.music.ui.components.diagonal
import app.duet.music.ui.player.ArtBackdrop
import app.duet.music.ui.player.rememberArtColor
import app.duet.music.ui.theme.DuetColors
import app.duet.music.ui.theme.duetShape
import app.duet.music.ui.theme.DuetIcons

/** One list in car mode: a big tile, and the songs it opens. */
data class CarList(val title: String, val icon: ImageVector, val songs: List<Track>)

/**
 * Car mode: an Android Auto-like screen on the phone. Everything is large and needs one tap: the
 * song and its controls, and four big lists (Quick picks, Radio, Recent, Downloaded). Lays out side
 * by side in landscape (a phone on a dashboard mount) and stacked in portrait. The screen stays on.
 */
@Composable
fun CarModeScreen(
    player: PlayerState,
    lists: List<CarList>,
    onPlay: (List<Track>, Int) -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onExit: () -> Unit,
) {
    KeepScreenOn()
    val track = player.track
    val art = rememberArtColor(track?.artworkUrl, fallback = Color(0xFF26262B))
    var open by rememberSaveable { mutableStateOf<String?>(null) }

    Box(Modifier.fillMaxSize().background(DuetColors.Ground)) {
        ArtBackdrop(track?.artworkUrl, art, showArt = false)
        BoxWithConstraints(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp)) {
            val wide = maxWidth > maxHeight
            val nowPlaying = @Composable { m: Modifier -> NowPlaying(track, player, onTogglePlay, onNext, onPrevious, onExit, m) }
            val browse = @Composable { m: Modifier ->
                val list = lists.firstOrNull { it.title == open }
                if (list == null) Tiles(lists, m) { open = it } else Songs(list, player.track?.key, m, onBack = { open = null }) { i -> onPlay(list.songs, i) }
            }
            if (wide) Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                nowPlaying(Modifier.weight(1f).fillMaxHeight()); browse(Modifier.weight(1f).fillMaxHeight())
            } else Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                nowPlaying(Modifier.fillMaxWidth()); browse(Modifier.fillMaxWidth().weight(1f))
            }
        }
    }
}

@Composable
private fun NowPlaying(track: Track?, s: PlayerState, onToggle: () -> Unit, onNext: () -> Unit, onPrevious: () -> Unit, onExit: () -> Unit, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Car mode", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.8f), modifier = Modifier.weight(1f))
            BigButton(DuetIcons.Clear, "Exit car mode", 64, Color.White.copy(alpha = 0.12f), Color.White, onExit)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Artwork(track?.artworkUrl, diagonal(0xFF2A2A2E, 0xFF151517), size = 120, radius = 12)
            Column(Modifier.weight(1f)) {
                Text(track?.title ?: "Nothing playing", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 32.sp)
                Text(track?.artist ?: "Pick something on the right", fontSize = 20.sp, color = Color.White.copy(alpha = 0.75f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        val duration = s.durationMs?.takeIf { it > 0 }
        Box(Modifier.fillMaxWidth().height(6.dp).clip(duetShape(3)).background(Color.White.copy(alpha = 0.2f))) {
            Box(Modifier.fillMaxWidth(duration?.let { (s.positionMs.toFloat() / it).coerceIn(0f, 1f) } ?: 0f).height(6.dp).background(Color.White))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            BigButton(DuetIcons.Previous, "Previous", 88, Color.White.copy(alpha = 0.12f), Color.White, onPrevious)
            BigButton(if (s.status == PlaybackStatus.Playing) DuetIcons.Pause else DuetIcons.Play,
                if (s.status == PlaybackStatus.Playing) "Pause" else "Play", 112, Color.White, Color.Black, onToggle)
            BigButton(DuetIcons.Next, "Next", 88, Color.White.copy(alpha = 0.12f), Color.White, onNext)
        }
    }
}

@Composable
private fun Tiles(lists: List<CarList>, modifier: Modifier, onOpen: (String) -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        lists.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { l ->
                    Column(
                        Modifier.weight(1f).fillMaxHeight().clip(duetShape(20)).background(Color.White.copy(alpha = 0.1f))
                            .clickable(role = Role.Button, onClickLabel = "Open ${l.title}") { onOpen(l.title) }.padding(18.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Icon(l.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp))
                        Column {
                            Text(l.title, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                            Text("${l.songs.size} songs", fontSize = 16.sp, color = Color.White.copy(alpha = 0.7f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Songs(list: CarList, playingKey: String?, modifier: Modifier, onBack: () -> Unit, onPlay: (Int) -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BigButton(DuetIcons.Back, "Back", 64, Color.White.copy(alpha = 0.12f), Color.White, onBack)
            Text(list.title, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            itemsIndexed(list.songs, key = { i, t -> "$i-${t.key}" }) { i, t ->
                Row(
                    Modifier.fillMaxWidth().height(84.dp).clip(duetShape(14))
                        .background(if (t.key == playingKey) Color.White.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.06f))
                        .clickable(role = Role.Button, onClickLabel = "Play") { onPlay(i) }.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Artwork(t.artworkUrl, diagonal(0xFF2A2A2E, 0xFF151517), size = 60, radius = 8)
                    Column(Modifier.weight(1f)) {
                        Text(t.title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(t.artist, fontSize = 16.sp, color = Color.White.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun BigButton(icon: ImageVector, label: String, size: Int, bg: Color, fg: Color, onClick: () -> Unit) {
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(bg).clickable(role = Role.Button, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = label, tint = fg, modifier = Modifier.size((size * 0.42f).dp)) }
}

/** Keeps the screen on while car mode is open. */
@Composable
expect fun KeepScreenOn()
