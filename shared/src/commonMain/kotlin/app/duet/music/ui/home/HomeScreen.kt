package app.duet.music.ui.home

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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.duet.music.domain.Collection
import app.duet.music.domain.SearchKind
import app.duet.music.domain.Shelf
import app.duet.music.domain.Track
import app.duet.music.ui.components.Artwork
import app.duet.music.ui.components.pressScale
import app.duet.music.ui.components.trackClick
import app.duet.music.ui.components.diagonal
import app.duet.music.ui.settings.AvatarButton
import app.duet.music.ui.theme.DuetColors
import app.duet.music.ui.theme.duetShape
import app.duet.music.ui.theme.DuetDimens
import app.duet.music.ui.theme.DuetIcons
import app.duet.music.ui.theme.DuetType

data class HomeUi(
    /** The headline: "Good morning" / "Good afternoon" / "Good evening". */
    val headline: String,
    val recent: List<Track>,
    val saved: List<Track>,
    val shelves: List<Shelf>,
    /** Songs like the last one played (YouTube Music-style "Quick picks"). */
    val quickPicks: List<Track> = emptyList(),
    /** Which of Home's own sections to show (Settings > Home). */
    val showQuickPicks: Boolean = true,
    val showJumpBackIn: Boolean = true,
    val showSaved: Boolean = true,
    val showRecent: Boolean = true,
    /** Song mixes at the bottom (Settings > Home > Mixes); internet radio lives in the Radio tab. */
    val radioSeeds: List<Track> = emptyList(),
    val showRadio: Boolean = true,
    /** The services Home can show, as (id, short name, colour); the switch only appears with two or more. */
    val services: List<Triple<String, String, Long>> = emptyList(),
    /** The service shown, or null for both. */
    val only: String? = null,
    /** Small two-column tiles at the top (your playlists, then recent songs), Spotify-style; at most six. */
    val shortcuts: List<HomeShortcut> = emptyList(),
    /** The signed-in account's name, for the header style "Greeting + your name". */
    val userName: String? = null,
)

/** One small tile at the top of Home. */
data class HomeShortcut(val key: String, val title: String, val artworkUrl: String?, val onClick: () -> Unit)

/**
 * Home, as music apps do it: a greeting, the last songs you played as a two-column grid, then
 * shelves of cards (your saved songs, and what YouTube Music suggests).
 */
@Composable
fun HomeScreen(
    ui: HomeUi,
    contentPadding: PaddingValues,
    onProfile: () -> Unit,
    onSettings: () -> Unit,
    onPlay: (List<Track>, Int) -> Unit,
    onOpenCollection: (Collection) -> Unit,
    onSearch: () -> Unit,
    onService: (String?) -> Unit = {},
    onStartRadio: (Track) -> Unit = {},
    onCarMode: () -> Unit = {},
) {
    Box(Modifier.fillMaxSize()) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(app.duet.music.ui.theme.LocalStyle.current.spacing.gap.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            val header = app.duet.music.ui.theme.LocalStyle.current.homeHeader
            if (header == app.duet.music.ui.theme.HomeHeader.None) Spacer(Modifier.weight(1f))
            else Text(
                DuetType.titled(when (header) {
                    app.duet.music.ui.theme.HomeHeader.Title -> "Home"
                    app.duet.music.ui.theme.HomeHeader.Name -> ui.headline + (ui.userName?.let { ", $it" } ?: "")
                    else -> ui.headline
                }),
                style = DuetType.Display(32), maxLines = 2, modifier = Modifier.weight(1f),
            )
            // Car mode lives in Settings; the header keeps only what everyone uses.
            AvatarButton(onProfile)
            IconButton(onClick = onSettings, modifier = Modifier.size(DuetDimens.MinTouchTarget.dp)) {
                Icon(DuetIcons.Settings, contentDescription = "Settings", tint = DuetColors.Text, modifier = Modifier.size(22.dp))
            }
        }
        if (ui.services.size > 1 && app.duet.music.ui.theme.LocalStyle.current.serviceSwitch) Box(Modifier.padding(horizontal = 16.dp)) { app.duet.music.ui.components.ServiceSwitch(ui.services, ui.only, onService) }

        if (ui.shortcuts.size >= 2 && app.duet.music.ui.theme.LocalStyle.current.home == app.duet.music.ui.theme.HomeLayout.Tiles) Shortcuts(ui.shortcuts.take(6))

        if (ui.showJumpBackIn && ui.recent.isNotEmpty()) Shelf("Recently played") {
            items(ui.recent.take(12).size) { i -> SongCard(ui.recent[i]) { onPlay(ui.recent, i) } }
        }

        // Quick picks sit high, as in YouTube Music: songs to start in one tap.
        if (ui.showQuickPicks && ui.quickPicks.isNotEmpty()) QuickPicks(ui.quickPicks) { i -> onPlay(ui.quickPicks, i) }

        // The first service shelf as wide "made for you" cards, the rest as square cards.
        ui.shelves.forEachIndexed { index, shelf ->
            if (index == 0 && shelf.collections.isNotEmpty()) Shelf(shelf.title) {
                items(shelf.collections, key = { "hero-" + it.key }) { c -> HeroCard(c) { onOpenCollection(c) } }
            } else Shelf(shelf.title) {
                items(shelf.songs.size) { i -> SongCard(shelf.songs[i]) { onPlay(shelf.songs, i) } }
                items(shelf.collections, key = { it.key }) { c -> CollectionCard(c) { onOpenCollection(c) } }
            }
        }

        if (ui.showSaved && ui.saved.isNotEmpty()) {
            Shelf("Saved on this phone") {
                items(ui.saved.size) { i -> SongCard(ui.saved[i]) { onPlay(ui.saved, i) } }
            }
        }

        // Song radio: endless mixes seeded by what you played.
        if (ui.showRadio && ui.radioSeeds.isNotEmpty()) Shelf("Mixes") {
            items(ui.radioSeeds, key = { "radio-" + it.key }) { seed -> RadioCard(seed) { onStartRadio(seed) } }
        }

        // The last few songs as a plain list.
        if (ui.showRecent && ui.recent.size > 1) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text(DuetType.titled("Jump back in"), style = DuetType.SectionTitle, modifier = Modifier.padding(bottom = 6.dp))
                ui.recent.take(5).forEachIndexed { i, t ->
                    Row(
                        Modifier.fillMaxWidth().clip(duetShape(8)).trackClick(t) { onPlay(ui.recent, i) }.padding(vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Artwork(t.artworkUrl, diagonal(0xFF2A2622, 0xFF141210), size = 48, radius = 8)
                        Column(Modifier.weight(1f)) {
                            Text(t.title, style = DuetType.RowTitle.copy(fontSize = 14.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(t.artist, style = DuetType.RowSub.copy(fontSize = 12.sp), color = DuetColors.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        val menu = app.duet.music.ui.components.LocalTrackMenu.current
                        IconButton(onClick = { menu(t) }, modifier = Modifier.size(DuetDimens.MinTouchTarget.dp)) {
                            Icon(DuetIcons.More, contentDescription = "More options for ${t.title}", tint = DuetColors.TextDim, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }

        if (ui.recent.isEmpty() && ui.saved.isEmpty() && ui.shelves.isEmpty()) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 48.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(DuetType.titled("Nothing here yet"), style = DuetType.SectionTitle)
                Text("Songs you play show up here.", style = DuetType.RowSub, color = DuetColors.TextDim)
                Text(
                    "Find music", style = DuetType.Chip, color = DuetColors.Ground,
                    modifier = Modifier.clip(duetShape(20)).background(DuetColors.Text)
                        .clickable(role = Role.Button, onClick = onSearch).padding(horizontal = 18.dp, vertical = 10.dp),
                )
            }
        }
    }
    }
}

/** Small rectangles, two to a row: art on the left, the name beside it. Pressing one sinks it slightly. */
@Composable
private fun Shortcuts(items: List<HomeShortcut>) {
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { s ->
                    val press = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                    val pressed by press.collectIsPressedAsState()
                    val scale by androidx.compose.animation.core.animateFloatAsState(
                        if (pressed) 0.96f else 1f, androidx.compose.animation.core.spring(dampingRatio = 0.6f, stiffness = 500f), label = "tile",
                    )
                    Row(
                        Modifier.weight(1f).height(56.dp).graphicsLayer { scaleX = scale; scaleY = scale }
                            .clip(duetShape(8)).background(Color.White.copy(alpha = 0.07f))
                            .clickable(interactionSource = press, indication = androidx.compose.material3.ripple(), role = Role.Button, onClick = s.onClick),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Artwork(s.artworkUrl, diagonal(0xFF2A2622, 0xFF141210), size = 56, radius = 0)
                        Text(s.title, style = DuetType.RowTitle.copy(fontSize = 13.sp), maxLines = 2, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 10.dp))
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/**
 * YouTube Music's "Quick picks": song rows, four to a column, columns swiped sideways; the next
 * column peeks in at the edge so it reads as scrollable.
 */
@Composable
private fun QuickPicks(songs: List<Track>, onPlay: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(DuetType.titled("Quick picks"), style = DuetType.SectionTitle, modifier = Modifier.padding(horizontal = 16.dp))
        // List styles: the first five as plain rows.
        if (app.duet.music.ui.theme.LocalStyle.current.home == app.duet.music.ui.theme.HomeLayout.List) {
            songs.take(5).forEachIndexed { i, t ->
                Row(
                    Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(60.dp).clip(duetShape(8)).trackClick(t) { onPlay(i) },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Artwork(t.artworkUrl, diagonal(0xFF2A2A2E, 0xFF151517), size = 48, radius = 8)
                    Column(Modifier.weight(1f)) {
                        Text(t.title, style = DuetType.RowTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(t.artist, style = DuetType.RowSub, color = DuetColors.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            return@Column
        }
        // A column is the screen's width less a peek of the next, and a swipe snaps to the next column.
        val grid = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
        val column = maxWidth - 16.dp - 40.dp
        androidx.compose.foundation.lazy.grid.LazyHorizontalGrid(
            rows = androidx.compose.foundation.lazy.grid.GridCells.Fixed(4),
            state = grid,
            flingBehavior = androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior(grid, androidx.compose.foundation.gestures.snapping.SnapPosition.Start),
            modifier = Modifier.fillMaxWidth().height((4 * 60).dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(songs.size) { i ->
                val t = songs[i]
                Row(
                    Modifier.width(column).height(60.dp).clip(duetShape(8)).trackClick(t) { onPlay(i) },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Artwork(t.artworkUrl, diagonal(0xFF2A2A2E, 0xFF151517), size = 48, radius = 8)
                    Column(Modifier.weight(1f)) {
                        Text(t.title, style = DuetType.RowTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(t.artist, style = DuetType.RowSub, color = DuetColors.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        }
    }
}



/** A station card: the seed song's art under a colour wash, "<Artist> Radio" over it. */
@Composable
private fun RadioCard(seed: Track, onClick: () -> Unit) {
    val artist = app.duet.music.lyrics.LyricsService.firstArtist(seed.artist).ifBlank { seed.title }
    val tint = app.duet.music.ui.player.rememberArtColor(seed.artworkUrl, fallback = DuetColors.Accent)
    val size = 168
    // No clip on the whole card: a rounded clip cut the first letters of the bottom line.
    Column(Modifier.width(size.dp).clickable(role = Role.Button, onClickLabel = "Play radio", onClick = onClick)) {
        Box(Modifier.size(size.dp).clip(duetShape(12))) {
            Artwork(seed.artworkUrl, diagonal(0xFF2A2A2E, 0xFF151517), size = size, radius = 12)
            Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(tint.copy(alpha = 0.15f), tint.copy(alpha = 0.85f)))))
            Text(
                "RADIO", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White, letterSpacing = 1.sp,
                modifier = Modifier.padding(10.dp).clip(duetShape(4)).background(Color.Black.copy(alpha = 0.35f)).padding(horizontal = 6.dp, vertical = 2.dp),
            )
            Text(DuetType.titled(artist), style = DuetType.SectionTitle.copy(fontSize = 17.sp), color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomStart).padding(10.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text("$artist Mix", style = DuetType.RowTitle.copy(fontSize = 14.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("Based on ${seed.title}", style = DuetType.RowSub.copy(fontSize = 12.sp), color = DuetColors.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A big landscape card: the art fills it, the title sits on a dark fade at the bottom. */
@Composable
private fun HeroCard(c: Collection, onClick: () -> Unit) {
    Box(
        Modifier.size(width = 200.dp, height = 150.dp).clip(duetShape(14)).background(DuetColors.Surface).pressScale()
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        coil3.compose.AsyncImage(
            model = c.artworkUrl, contentDescription = null,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize(),
        )
        Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(0.35f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.85f))))
        Column(Modifier.align(Alignment.BottomStart).padding(14.dp)) {
            Text(c.title, style = DuetType.RowTitle.copy(fontSize = 16.sp), maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.White)
            Text(c.subtitle, style = DuetType.RowSub.copy(fontSize = 12.sp), color = Color.White.copy(alpha = 0.75f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}


@Composable
private fun Shelf(title: String, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(DuetType.titled(title), style = DuetType.SectionTitle, modifier = Modifier.padding(horizontal = 16.dp))
        // Cards settle with one at the left edge instead of stopping half cut.
        val row = androidx.compose.foundation.lazy.rememberLazyListState()
        LazyRow(
            state = row,
            flingBehavior = androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior(row, androidx.compose.foundation.gestures.snapping.SnapPosition.Start),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

private const val CARD = 140

@Composable
private fun SongCard(t: Track, onClick: () -> Unit) = Card(t.artworkUrl, t.title, t.artist, round = false, track = t, size = cardSize(), onClick = onClick)

/** Big-card styles show shelf cards larger. */
@Composable
private fun cardSize(): Int = with(app.duet.music.ui.theme.LocalStyle.current) { ((if (home == app.duet.music.ui.theme.HomeLayout.Big) 168 else CARD) * cardScale).toInt() }

@Composable
private fun CollectionCard(c: Collection, onClick: () -> Unit) =
    Card(c.artworkUrl, c.title, c.subtitle, round = c.kind == SearchKind.Artists, size = cardSize(), onClick = onClick)

/** A card: art with tight corners (round for artists), a two-line title, one line under it. */
@Composable
private fun Card(art: String?, title: String, subtitle: String, round: Boolean, track: Track? = null, size: Int = CARD, onClick: () -> Unit) {
    // No clip on the whole card: a rounded clip cut the first letters of the bottom line.
    Column(Modifier.width(size.dp).pressScale().then(if (track != null) Modifier.trackClick(track, onClick) else Modifier.clickable(role = Role.Button, onClick = onClick))) {
        Artwork(art, diagonal(0xFF2A2A2E, 0xFF151517), size = size, radius = if (round) size / 2 else 12)
        Spacer(Modifier.height(8.dp))
        Text(title, style = DuetType.RowTitle.copy(fontSize = 14.sp, lineHeight = 18.sp), maxLines = 2, overflow = TextOverflow.Ellipsis,
            textAlign = if (round) androidx.compose.ui.text.style.TextAlign.Center else null, modifier = Modifier.fillMaxWidth())
        Text(subtitle, style = DuetType.RowSub.copy(fontSize = 12.sp), color = DuetColors.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis,
            textAlign = if (round) androidx.compose.ui.text.style.TextAlign.Center else null, modifier = Modifier.fillMaxWidth())
    }
}

/** The greeting for an hour of the day (0-23). */
fun greetingFor(hour: Int) = when (hour) {
    in 5..11 -> "Good morning"
    in 12..17 -> "Good afternoon"
    else -> "Good evening"
}
