package app.duet.music.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.duet.music.domain.ArtistPage
import app.duet.music.domain.Collection
import app.duet.music.domain.SearchKind
import app.duet.music.domain.Track
import app.duet.music.extension.SourceRegistry
import app.duet.music.ui.components.Artwork
import app.duet.music.ui.components.PrimaryButton
import app.duet.music.ui.components.diagonal
import app.duet.music.ui.components.trackClick
import app.duet.music.ui.player.formatTime
import app.duet.music.ui.theme.DuetColors
import app.duet.music.ui.theme.DuetDimens
import app.duet.music.ui.theme.DuetIcons
import app.duet.music.ui.theme.DuetType
import app.duet.music.ui.theme.duetShape
import coil3.compose.AsyncImage

/**
 * An artist's page, as music apps do it: their picture across the top with the name over it, Play,
 * the popular songs, then rows of albums, singles and similar artists. Falls back to the plain song
 * list when the source has no artist page.
 */
@Composable
fun ArtistScreen(
    artist: Collection, sources: SourceRegistry, playingKey: String?,
    onPlay: (List<Track>, Int) -> Unit, onOpen: (Collection) -> Unit, onBack: () -> Unit,
) {
    val source = sources[artist.sourceId]
    var page by remember(artist.key) { mutableStateOf<ArtistPage?>(null) }
    var failed by remember(artist.key) { mutableStateOf(false) }
    LaunchedEffect(artist.key) {
        runCatching { source?.artistPage(artist) }.onSuccess { if (it == null) failed = true else page = it }.onFailure { failed = true }
    }
    if (failed && page == null) {
        CollectionScreen(artist, sources, playingKey, onPlay, onBack)
        return
    }
    val p = page
    var showAll by remember(artist.key) { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(DuetColors.Ground)) {
        if (p == null) {
            CircularProgressIndicator(color = DuetColors.Accent, modifier = Modifier.align(Alignment.Center))
        } else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp)) {
            item { Header(artist.title, p, onPlay = { if (p.songs.isNotEmpty()) onPlay(p.songs, 0) }) }
            if (p.songs.isNotEmpty()) {
                item { Title("Popular") }
                val shown = if (showAll) p.songs else p.songs.take(5)
                itemsIndexed(shown, key = { i, t -> "s$i-${t.key}" }) { i, t -> SongRow(i + 1, t, t.key == playingKey) { onPlay(p.songs, i) } }
                if (p.songs.size > 5) item {
                    Text(
                        if (showAll) "Show less" else "Show all", style = DuetType.Chip, color = DuetColors.TextDim,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).clip(duetShape(16))
                            .clickable(role = Role.Button) { showAll = !showAll }.padding(horizontal = 4.dp, vertical = 6.dp),
                    )
                }
            }
            p.shelves.forEach { shelf ->
                item(key = "shelf-" + shelf.title) {
                    Title(shelf.title)
                    val row = rememberLazyListState()
                    LazyRow(
                        state = row,
                        flingBehavior = androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior(row, androidx.compose.foundation.gestures.snapping.SnapPosition.Start),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(shelf.collections, key = { it.key }) { c -> Card(c.artworkUrl, c.title, c.subtitle, round = c.kind == SearchKind.Artists) { onOpen(c) } }
                        itemsIndexed(shelf.songs, key = { i, t -> "v$i-${t.key}" }) { i, t -> Card(t.artworkUrl, t.title, t.artist, round = false, wide = true) { onPlay(shelf.songs, i) } }
                    }
                }
            }
        }
        // Back stays reachable over the picture.
        IconButton(
            onClick = onBack,
            modifier = Modifier.statusBarsPadding().padding(8.dp).size(DuetDimens.MinTouchTarget.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.35f)),
        ) { Icon(DuetIcons.Back, contentDescription = "Back", tint = Color.White) }
    }
}

/** The picture across the top, darkening into the page, with the name and Play over its bottom edge. */
@Composable
private fun Header(name: String, p: ArtistPage, onPlay: () -> Unit) {
    Box(Modifier.fillMaxWidth().aspectRatio(1.1f)) {
        Box(Modifier.matchParentSize().background(diagonal(0xFF3A3A3A, 0xFF111111)))
        if (p.imageUrl != null) AsyncImage(model = p.imageUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(0.35f to Color.Transparent, 1f to DuetColors.Ground)))
        Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(DuetType.titled(name), style = DuetType.Display(40), color = DuetColors.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            p.about?.let { Text(it, style = DuetType.RowSub, color = DuetColors.TextDim) }
            if (p.songs.isNotEmpty()) PrimaryButton("Play", onClick = onPlay)
        }
    }
}

@Composable
private fun Title(text: String) =
    Text(DuetType.titled(text), style = DuetType.SectionTitle, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 22.dp, bottom = 10.dp))

@Composable
private fun SongRow(n: Int, t: Track, playing: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().trackClick(t, onClick).padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("$n", style = DuetType.RowSub, color = DuetColors.TextDim, modifier = Modifier.width(18.dp))
        Artwork(t.artworkUrl, diagonal(0xFF3A3A3A, 0xFF111111), size = 44, radius = 6)
        Column(Modifier.weight(1f)) {
            Text(t.title, style = DuetType.RowTitle, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (playing) DuetColors.Accent else DuetColors.Text)
            Text(t.artist, style = DuetType.RowSub, color = DuetColors.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        t.durationMs?.let { Text(formatTime(it), style = DuetType.RowSub, color = DuetColors.TextDim) }
    }
}

/** An album/single (square), a similar artist (round) or a video (wide) in a row. */
@Composable
private fun Card(art: String?, title: String, subtitle: String, round: Boolean, wide: Boolean = false, onClick: () -> Unit) {
    val w = if (wide) 220 else 140
    Column(Modifier.width(w.dp).clip(duetShape(10)).clickable(role = Role.Button, onClick = onClick), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (wide) Box(Modifier.width(w.dp).aspectRatio(16f / 9f).clip(duetShape(10)).background(diagonal(0xFF3A3A3A, 0xFF111111))) {
            if (art != null) AsyncImage(model = art, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        } else Artwork(art, diagonal(0xFF3A3A3A, 0xFF111111), size = w, radius = if (round) w / 2 else 10)
        Text(title, style = DuetType.RowTitle, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = if (round) Modifier.fillMaxWidth() else Modifier)
        if (subtitle.isNotBlank()) Text(subtitle, style = DuetType.RowSub, color = DuetColors.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
