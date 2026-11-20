package app.duet.music.ui.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.duet.music.lyrics.Lyrics
import app.duet.music.lyrics.LyricsService
import app.duet.music.player.PlaybackCoordinator
import app.duet.music.player.PlayerState
import app.duet.music.ui.components.Artwork
import app.duet.music.ui.components.diagonal
import app.duet.music.ui.theme.DuetIcons
import app.duet.music.ui.theme.duetShape
import app.duet.music.ui.theme.DuetType

/**
 * Lyrics over the song's own colours. Synced lyrics follow the song: the current line is bright and a
 * little larger, the rest fade, the current line stays in the upper third, and tapping a line jumps there.
 */
@Composable
fun LyricsScreen(state: PlayerState, service: LyricsService, controls: PlaybackCoordinator, onClose: () -> Unit) {
    val track = state.track ?: return
    var lyrics by remember(track.key) { mutableStateOf<Lyrics?>(null) }
    var loading by remember(track.key) { mutableStateOf(true) }
    LaunchedEffect(track.key) {
        lyrics = service.lyricsFor(track)
        loading = false
    }
    val art = rememberArtColor(track.artworkUrl, fallback = Color(0xFF3A3D5A))

    val desktop = app.duet.music.ui.desktop.LocalDesktop.current
    // On a computer the window's own ground shows through (same as the sidebar); the lyrics sit in a centred column.
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        if (!desktop) ArtBackdrop(track.artworkUrl, art, showArt = false)
        Column(Modifier.fillMaxHeight().then(if (desktop) Modifier.widthIn(max = 760.dp) else Modifier.fillMaxWidth()).statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Artwork(track.artworkUrl, diagonal(0xFF2A2A2E, 0xFF151517), size = 48, radius = 6)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(track.title, style = DuetType.RowTitle, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(track.artist, style = DuetType.RowSub, color = Color.White.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) { Icon(DuetIcons.ChevronDown, contentDescription = "Close lyrics", tint = Color.White, modifier = Modifier.size(28.dp)) }
            }
            val l = lyrics
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color.White) }
                l == null -> Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("No lyrics for this song", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.8f))
                }
                else -> LyricsLines(l, state.positionMs, onSeek = controls::seekTo)
            }
        }
    }
}

@Composable
private fun LyricsLines(lyrics: Lyrics, positionMs: Long, onSeek: (Long) -> Unit) {
    val current = if (lyrics.synced) LyricsService.currentLine(lyrics.lines, positionMs) else -1
    val list = rememberLazyListState()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val third = with(LocalDensity.current) { (maxHeight / 3).roundToPx() }
        LaunchedEffect(current) { if (current >= 0) list.animateScrollToItem(current, scrollOffset = -third) }
        LazyColumn(
            state = list,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 24.dp, bottom = maxHeight),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            itemsIndexed(lyrics.lines) { i, line ->
                val active = !lyrics.synced || i == current
                val alpha by animateFloatAsState(
                    when {
                        !lyrics.synced -> 0.95f
                        i == current -> 1f
                        i < current -> 0.38f
                        else -> 0.5f
                    }, tween(350), label = "alpha",
                )
                val scale by animateFloatAsState(if (lyrics.synced && active) 1.04f else 1f, tween(350), label = "scale")
                Text(
                    line.text,
                    fontSize = 30.sp, lineHeight = 38.sp, fontWeight = FontWeight.ExtraBold,
                    color = Color.White.copy(alpha = alpha),
                    modifier = Modifier.fillMaxWidth()
                        .graphicsLayer { scaleX = scale; scaleY = scale; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f) }
                        .clip(duetShape(10))
                        .then(if (line.timeMs != null) Modifier.clickable(role = Role.Button, onClickLabel = "Jump here") { onSeek(line.timeMs) } else Modifier)
                        .padding(vertical = 2.dp),
                )
            }
            item {
                Text("Lyrics: ${lyrics.provider}", fontSize = 12.sp, color = Color.White.copy(alpha = 0.5f), modifier = Modifier.padding(top = 24.dp))
            }
        }
    }
}
